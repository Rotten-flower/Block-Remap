package dev.flower.blockgenban.client;

import dev.flower.blockgenban.BlockgenBan;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BlockgenBan.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientRegistration {
    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) { event.register(ClientUi.OPEN); dev.flower.blockgenban.RuleNetwork.clientReceiver = ClientUi::receive; }
}
