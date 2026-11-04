package dev.flower.blockgenban;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;


public final class RuleNetwork {
    public static final ResourceLocation REQUEST = new ResourceLocation(BlockgenBan.MODID, "request_v3");
    public static final ResourceLocation SAVE = new ResourceLocation(BlockgenBan.MODID, "save_v3");
    public static final ResourceLocation SNAPSHOT = new ResourceLocation(BlockgenBan.MODID, "snapshot_v3");

    public record Request() {}
    public record Save(Map<ResourceLocation, ResourceLocation> rules) {}
    public record Snapshot(Map<ResourceLocation, ResourceLocation> rules, boolean open, Component error) {}

public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, responseSender) -> {

            // 解码后再调度到主线程执行，避免网络线程触碰游戏逻辑。
            // Decode first, then schedule on the main thread.
            server.execute(() -> openFor(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(SAVE, (server, player, handler, buf, responseSender) -> {
            Map<ResourceLocation, ResourceLocation> rules = readRules(buf);
            // 主线程执行保存，保证世界访问安全。
            // Run save on the main thread for world access safety.
            server.execute(() -> handleSave(player, rules));
        });
    }

    public static boolean mayEdit(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    public static void openFor(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, SNAPSHOT)) {
            // Keep this readable without the client mod's translations. / 未装客户端模组也能读懂。
            player.sendSystemMessage(Component.translatableWithFallback("message.blockgen_ban.client_missing",
                    "Install Block Remap on your client to use this screen."));
        } else if (!mayEdit(player)) {
            send(player, false, Component.translatable("message.blockgen_ban.permission"));
        } else {
            send(player, true, Component.empty());
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("blockgenban")
                .requires(source -> source.hasPermission(2) || source.getEntity() instanceof ServerPlayer player && mayEdit(player))
                .executes(context -> { openFor(context.getSource().getPlayerOrException()); return 1; })
                .then(GenerationBanCommands.branch("structure", true))
                .then(GenerationBanCommands.branch("feature", false)));
    }

    private static void send(ServerPlayer player, boolean open, Component error) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        writeRules(buffer, BlockgenBan.definitions());
        buffer.writeBoolean(open);
        buffer.writeComponent(error);
        ServerPlayNetworking.send(player, SNAPSHOT, buffer);
    }

    private static void handleSave(ServerPlayer player, Map<ResourceLocation, ResourceLocation> rules) {
        if (!mayEdit(player)) {
            send(player, false, Component.translatable("message.blockgen_ban.permission"));
            return;
        }
        try {
            BlockgenBan.saveRules(player.server, rules);
            send(player, false, Component.empty());
        } catch (IOException | IllegalArgumentException failure) {
            BlockgenBan.LOGGER.error("Cannot save block generation rules", failure);
            send(player, false, Component.translatable("message.blockgen_ban.save_failed", failure.getMessage()));
        }
    }

    public static void writeRules(FriendlyByteBuf buffer, Map<ResourceLocation, ResourceLocation> rules) {
        buffer.writeVarInt(rules.size());
        rules.forEach((source, target) -> {
            buffer.writeResourceLocation(source);
            buffer.writeResourceLocation(target);
        });
    }

    public static Map<ResourceLocation, ResourceLocation> readRules(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > BuiltInRegistries.BLOCK.size()) {
            throw new IllegalArgumentException("Invalid rule count");
        }
        Map<ResourceLocation, ResourceLocation> rules = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            ResourceLocation source = buffer.readResourceLocation();
            ResourceLocation target = buffer.readResourceLocation();
            if (rules.put(source, target) != null) {
                throw new IllegalArgumentException("Duplicate source block");
            }
        }
        return Map.copyOf(rules);
    }
}
