package dev.flower.blockgenban.verification;

import com.google.gson.GsonBuilder;
import dev.flower.blockgenban.BlockgenBan;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import java.nio.file.*;
import java.util.*;

/** Same seed, coordinates and request order in separate fresh worlds. Never delivered. */
final class GenerationBenchmark {
    static void run(MinecraftServer server) {
        Map<String,Object> report = new LinkedHashMap<>();
        String mode = System.getProperty("blockgenban.bench.mode", "empty");
        report.put("mode", mode);
        try {
            Map<ResourceLocation,ResourceLocation> rules = new LinkedHashMap<>();
            if (mode.equals("sparse")) {
                rules.put(new ResourceLocation("minecraft:diamond_ore"), new ResourceLocation("minecraft:stone"));
                rules.put(new ResourceLocation("minecraft:deepslate_diamond_ore"), new ResourceLocation("minecraft:deepslate"));
            } else if (mode.equals("dense")) {
                rules.put(new ResourceLocation("minecraft:stone"), new ResourceLocation("minecraft:andesite"));
                rules.put(new ResourceLocation("minecraft:deepslate"), new ResourceLocation("minecraft:tuff"));
            }
            if(!BlockgenBan.definitions().equals(rules))throw new AssertionError("Benchmark rules were not applied at server startup");
            var level = server.overworld();
            for (int x=400;x<404;x++) for (int z=400;z<404;z++) level.getChunk(x,z);
            List<Double> durations = new ArrayList<>();
            long started=System.nanoTime();
            for(int x=600;x<608;x++) for(int z=600;z<608;z++) {
                long chunkStarted=System.nanoTime(); level.getChunk(x,z);
                durations.add((System.nanoTime()-chunkStarted)/1_000_000.0);
            }
            double total=(System.nanoTime()-started)/1_000_000.0;
            report.put("chunks",64); report.put("totalMs",total); report.put("meanMs",total/64);
            report.put("requestMs",durations);
            durations.sort(Double::compare); report.put("p95Ms",durations.get(60));
            long sourceRemnants=0;
            for(int cx=600;cx<608;cx++)for(int cz=600;cz<608;cz++)
                for(var section:level.getChunk(cx,cz).getSections())for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)
                    if(rules.containsKey(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(section.getBlockState(x,y,z).getBlock())))sourceRemnants++;
            report.put("sourceRemnants",sourceRemnants);
            if(!rules.isEmpty()&&sourceRemnants!=0)throw new AssertionError("Source blocks remain: "+sourceRemnants);
            for(int cx=600;cx<608;cx++)for(int cz=600;cz<608;cz++) verifyHeightmaps(level.getChunk(cx,cz));
            report.put("heightmapsMatch",true);
            report.put("pass",true);
        } catch(Throwable failure) {failure.printStackTrace();report.put("pass",false);report.put("error",failure.toString());}
        try {Files.writeString(Path.of("benchmark-"+mode+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception failure){failure.printStackTrace();}
        System.out.println("BLOCKGEN_BENCHMARK "+report);
        server.halt(false);
    }
    static void verifyHeightmaps(net.minecraft.world.level.chunk.ChunkAccess chunk) {
        var pos=new net.minecraft.core.BlockPos.MutableBlockPos();
        for(var type:net.minecraft.world.level.chunk.ChunkStatus.POST_FEATURES)
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                int expected=chunk.getMinBuildHeight()-1;
                for(int y=chunk.getMaxBuildHeight()-1;y>=chunk.getMinBuildHeight();y--){
                    pos.set(chunk.getPos().getMinBlockX()+x,y,chunk.getPos().getMinBlockZ()+z);
                    if(type.isOpaque().test(chunk.getBlockState(pos))){expected=y;break;}
                }
                if(chunk.getHeight(type,x,z)!=expected)throw new AssertionError("Heightmap mismatch "+type+" at "+chunk.getPos()+" "+x+","+z);
            }
    }
}
