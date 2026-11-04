package dev.flower.blockgenban.client;

import dev.flower.blockgenban.BlockgenBan;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BlockgenBan.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientRegistration {
    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) { event.register(ClientUi.OPEN); }
}
