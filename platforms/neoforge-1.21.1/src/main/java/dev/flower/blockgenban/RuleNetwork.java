package dev.flower.blockgenban;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class RuleNetwork {
    public record Request() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Request> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BlockgenBan.MODID, "request_v3"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { },
                buf -> new Request()
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Save(Map<ResourceLocation, ResourceLocation> rules) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Save> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BlockgenBan.MODID, "save_v3"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Save> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> writeRules(buf, value.rules()),
                buf -> new Save(readRules(buf))
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Snapshot(Map<ResourceLocation, ResourceLocation> rules, boolean open, Component error) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Snapshot> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BlockgenBan.MODID, "snapshot_v3"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    writeRules(buf, value.rules());
                    buf.writeBoolean(value.open());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.error());
                },
                buf -> {
                    Map<ResourceLocation, ResourceLocation> rules = readRules(buf);
                    boolean open = buf.readBoolean();
                    Component error = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    return new Snapshot(rules, open, error);
                }
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static Consumer<Snapshot> clientReceiver = snapshot -> { };

    public static void init(IEventBus modBus) {
        modBus.addListener(RuleNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3").optional();

        registrar.playToServer(Request.TYPE, Request.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            openFor(player);
        });

        registrar.playToServer(Save.TYPE, Save.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            handleSave(player, payload.rules());
        });

        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC, (payload, context) -> clientReceiver.accept(payload));
    }

    public static void request() {
        PacketDistributor.sendToServer(new Request());
    }

    public static void save(Map<ResourceLocation, ResourceLocation> rules) {
        PacketDistributor.sendToServer(new Save(rules));
    }

    public static boolean mayEdit(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    public static void openFor(ServerPlayer player) {
        if (!player.connection.hasChannel(Snapshot.TYPE.id())) {
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
                .executes(context -> {
                    openFor(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(GenerationBanCommands.branch("structure", true))
                .then(GenerationBanCommands.branch("feature", false)));
    }

    private static void send(ServerPlayer player, boolean open, Component error) {
        PacketDistributor.sendToPlayer(player, new Snapshot(BlockgenBan.definitions(), open, error));
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
