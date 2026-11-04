package dev.flower.blockgenban.verification;
import dev.flower.blockgenban.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
@Mod(VerificationMod.ID)
public final class VerificationMod {
    public static final String ID="blockgen_ban_verification";
    static final java.util.concurrent.atomic.AtomicInteger FEATURE_CALLS=new java.util.concurrent.atomic.AtomicInteger();
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,ID);
    public static final RegistryObject<Block> PROBE=BLOCKS.register("probe",()->new Block(BlockBehaviour.Properties.of().strength(1)));
    private static final RegistryObject<Feature<?>> FEATURE=FEATURES.register("probe",()->new Feature<NoneFeatureConfiguration>(NoneFeatureConfiguration.CODEC){
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx){
            FEATURE_CALLS.incrementAndGet();
            BlockPos p=new BlockPos(ctx.origin().getX()+4,192,ctx.origin().getZ()+4);var chunk=ctx.level().getChunk(p);
            chunk.setBlockState(p,Blocks.GOLD_BLOCK.defaultBlockState(),false);
            chunk.setBlockState(p.east(),Blocks.DIAMOND_ORE.defaultBlockState(),false);
            ctx.level().setBlock(p.above(),PROBE.get().defaultBlockState(),2);
            var direct=p.west();chunk.getSection(chunk.getSectionIndex(direct.getY())).setBlockState(direct.getX()&15,direct.getY()&15,direct.getZ()&15,Blocks.IRON_ORE.defaultBlockState());
            // A direct-section generator is responsible for updating its own heightmaps.
            for(var type:chunk.getStatus().heightmapsAfter())chunk.getOrCreateHeightmapUnprimed(type).update(direct.getX()&15,direct.getY(),direct.getZ()&15,chunk.getBlockState(direct));
            return true;
        }
    });
    public VerificationMod(){var bus=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);FEATURES.register(bus);MinecraftForge.EVENT_BUS.addListener(this::started);}
    private void started(ServerStartedEvent event){
        String phase=System.getProperty("blockgenban.verify.phase","global-prepare");
        event.getServer().tell(new net.minecraft.server.TickTask(event.getServer().getTickCount()+1,()->{
            if(phase.equals("performance"))GenerationBenchmark.run(event.getServer());
            else if(phase.startsWith("mode-"))ModeVerification.run(event.getServer(),phase);
            else GlobalVerification.run(event.getServer(),phase);
        }));
    }
}
