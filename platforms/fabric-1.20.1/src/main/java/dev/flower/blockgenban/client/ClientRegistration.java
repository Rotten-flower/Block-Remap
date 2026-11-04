package dev.flower.blockgenban.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.flower.blockgenban.RuleNetwork;

public final class ClientRegistration implements ClientModInitializer {
    @Override public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(ClientUi.OPEN);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientUi.tick());
        ClientPlayNetworking.registerGlobalReceiver(RuleNetwork.SNAPSHOT, (client, handler, buffer, sender) -> {
            var snapshot = new RuleNetwork.Snapshot(RuleNetwork.readRules(buffer), buffer.readBoolean(), buffer.readComponent());
            // 先解码再切换到客户端线程。 / Decode before transferring to the client thread.
            client.execute(() -> ClientUi.receive(snapshot));
        });
    }
}
