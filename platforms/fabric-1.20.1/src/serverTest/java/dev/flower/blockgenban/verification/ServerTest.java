package dev.flower.blockgenban.verification;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.TickTask;
public final class ServerTest implements ModInitializer {
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.tell(new TickTask(server.getTickCount()+1, () -> ServerProbe.run(server))));
    }
}
