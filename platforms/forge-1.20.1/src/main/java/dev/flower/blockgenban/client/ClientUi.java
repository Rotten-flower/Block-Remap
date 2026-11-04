package dev.flower.blockgenban.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.flower.blockgenban.BlockgenBan;
import dev.flower.blockgenban.RuleNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BlockgenBan.MODID, value = Dist.CLIENT)
public final class ClientUi {
    public static final KeyMapping OPEN = new KeyMapping("key.blockgen_ban.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.blockgen_ban");

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                if (RuleNetwork.isPresent(mc.getConnection().getConnection())) RuleNetwork.request();
                else mc.player.displayClientMessage(Component.translatable("message.blockgen_ban.server_missing"), false);
            }
        }
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
