package dev.luohua.blockgenban;

import dev.luohua.blockgenban.client.ClientUi;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = BlockgenBan.MODID)
public final class RuleNetwork {
    private static SimpleChannel channel;
    public record Request() {}
    public record Save(Map<ResourceLocation, ResourceLocation> rules) {}
    public record Snapshot(Map<ResourceLocation, ResourceLocation> rules, boolean open, Component error) {}

    public static void init() {
        channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(BlockgenBan.MODID, "rules"),
                () -> "3", RuleNetwork::accepts, RuleNetwork::accepts);
        channel.registerMessage(0, Request.class, (m, b) -> {}, b -> new Request(),
                RuleNetwork::handleRequest, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(1, Save.class, (m, b) -> writeRules(b, m.rules()),
                b -> new Save(readRules(b)), RuleNetwork::handleSave, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(2, Snapshot.class, (m, b) -> {
            writeRules(b, m.rules()); b.writeBoolean(m.open()); b.writeComponent(m.error());
        }, b -> new Snapshot(readRules(b), b.readBoolean(), b.readComponent()),
                RuleNetwork::handleSnapshot, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static boolean accepts(String version) {
        return version.equals("3") || version.equals(NetworkRegistry.ABSENT) || version.equals(NetworkRegistry.ACCEPTVANILLA);
    }

    public static boolean isPresent(net.minecraft.network.Connection connection) {
        return channel.isRemotePresent(connection);
    }

    public static void request() { channel.sendToServer(new Request()); }
    public static void save(Map<ResourceLocation, ResourceLocation> rules) {
        channel.sendToServer(new Save(Map.copyOf(rules)));
    }

    public static boolean mayEdit(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    public static void openFor(ServerPlayer player) {
        if (!isPresent(player.connection.connection)) {
            // Keep this readable without the client mod's translations. / 未装客户端模组也能读懂。
            player.sendSystemMessage(Component.translatableWithFallback("message.blockgen_ban.client_missing",
                    "Install Block Remap on your client to use this screen."));
        } else if (!mayEdit(player)) {
            send(player, false, Component.translatable("message.blockgen_ban.permission"));
        } else {
            send(player, true, Component.empty());
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("blockgenban")
                .requires(source -> source.hasPermission(2) || source.getEntity() instanceof ServerPlayer player && mayEdit(player))
                .executes(context -> { openFor(context.getSource().getPlayerOrException()); return 1; })
                .then(GenerationBanCommands.branch("structure",true))
                .then(GenerationBanCommands.branch("feature",false)));
    }

    private static void send(ServerPlayer player, boolean open, Component error) {
        channel.sendTo(new Snapshot(BlockgenBan.definitions(), open, error),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static void handleRequest(Request message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) openFor(context.getSender());
        });
        context.setPacketHandled(true);
    }

    private static void handleSave(Save message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!mayEdit(player)) { send(player, false, Component.translatable("message.blockgen_ban.permission")); return; }
            try {
                BlockgenBan.saveRules(player.server, message.rules());
                send(player, false, Component.empty());
            } catch (IOException | IllegalArgumentException failure) {
                BlockgenBan.LOGGER.error("Cannot save block generation rules", failure);
                send(player, false, Component.translatable("message.blockgen_ban.save_failed", failure.getMessage()));
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleSnapshot(Snapshot message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientUi.receive(message)));
        context.setPacketHandled(true);
    }

    private static void writeRules(FriendlyByteBuf buffer, Map<ResourceLocation, ResourceLocation> rules) {
        buffer.writeVarInt(rules.size());
        rules.forEach((source, target) -> { buffer.writeResourceLocation(source); buffer.writeResourceLocation(target); });
    }

    private static Map<ResourceLocation, ResourceLocation> readRules(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > ForgeRegistries.BLOCKS.getKeys().size()) throw new IllegalArgumentException("Invalid rule count");
        Map<ResourceLocation, ResourceLocation> rules = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            ResourceLocation source = buffer.readResourceLocation();
            ResourceLocation target = buffer.readResourceLocation();
            if (rules.put(source, target) != null) throw new IllegalArgumentException("Duplicate source block");
        }
        return Map.copyOf(rules);
    }
}
