package dev.flower.blockgenban.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.flower.blockgenban.BlockgenBan;
import dev.flower.blockgenban.RuleNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import org.lwjgl.glfw.GLFW;

public final class ClientUi {
    public static final KeyMapping OPEN = new KeyMapping("key.blockgen_ban.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.blockgen_ban");

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                if (ClientPlayNetworking.canSend(RuleNetwork.REQUEST)) request();
                else mc.player.displayClientMessage(Component.translatable("message.blockgen_ban.server_missing"), false);
            }
        }
    }

    public static void request() { ClientPlayNetworking.send(RuleNetwork.REQUEST, PacketByteBufs.create()); }
    public static void save(Map<ResourceLocation, ResourceLocation> rules) {
        var buffer = PacketByteBufs.create();
        RuleNetwork.writeRules(buffer, rules);
        ClientPlayNetworking.send(RuleNetwork.SAVE, buffer);
    }

    public static void receive(RuleNetwork.Snapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (snapshot.open()) {
            mc.setScreen(new RuleScreen(mc.screen, snapshot.rules()));
        } else if (mc.screen instanceof RuleScreen screen) {
            screen.acceptSaved(snapshot.rules(), snapshot.error());
        } else if (!snapshot.error().getString().isEmpty() && mc.player != null) {
            mc.player.displayClientMessage(snapshot.error(), false);
        }
    }
}
