package dev.flower.blockgenban.verification;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.server.TickTask;
@Mod("blockgen_ban_server_test")
public final class ServerTest {
    public ServerTest() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            var server=event.getServer();
            server.tell(new TickTask(server.getTickCount()+1, () -> ServerProbe.run(server)));
        });
    }
}
