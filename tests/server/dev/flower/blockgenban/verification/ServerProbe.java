package dev.flower.blockgenban.verification;

import com.google.gson.GsonBuilder;
import dev.flower.blockgenban.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;

/** 隔离专服的跨端回归夹具，不进入正式包。 / Isolated server fixture, excluded from release JARs. */
public final class ServerProbe {
    private static final BlockPos OLD = new BlockPos(1032, 210, 1032);
    private static ResourceLocation id(String value) { return Objects.requireNonNull(ResourceLocation.tryParse(value)); }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
    private static Map<ResourceLocation,ResourceLocation> rules() {
        return Map.of(id("minecraft:stone"),id("minecraft:andesite"),
                id("minecraft:diamond_ore"),id("minecraft:andesite"),
                id("minecraft:bedrock"),id("minecraft:andesite"),
                id("minecraft:iron_ore"),id("minecraft:air"),
                id("minecraft:deepslate_iron_ore"),id("minecraft:air"),
                id("minecraft:gold_block"),id("minecraft:chest"),
                id("minecraft:barrel"),id("minecraft:air"),
                id("minecraft:coal_block"),id("minecraft:glowstone"));
    }
    public static void run(MinecraftServer server) {
        String phase = System.getProperty("blockgenban.probe.phase", "prepare");
        Map<String,Object> report = new LinkedHashMap<>(); report.put("phase",phase);
        try {
            var level = server.overworld();
            level.getChunkAt(OLD);
            boolean global = BlockgenBan.replacementMode()==ReplacementMode.GLOBAL;
            report.put("mode",BlockgenBan.replacementMode().id());
            if (phase.equals("prepare")) {
                check(!BlockgenBan.getRules().isActive(),"Prepare needs an empty config");
                level.setBlockAndUpdate(OLD,Blocks.DIAMOND_ORE.defaultBlockState());
                level.setBlockAndUpdate(OLD.east(),Blocks.GOLD_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(OLD.south(),Blocks.BARREL.defaultBlockState());
                ((BarrelBlockEntity)level.getBlockEntity(OLD.south())).setItem(0,new ItemStack(Items.DIAMOND,17));
                level.setBlockAndUpdate(OLD.west(),Blocks.COAL_BLOCK.defaultBlockState());
                BlockgenBan.saveRules(server,rules());
                check(!BlockgenBan.getRules().isActive(),"Saved rules applied before restart");
                report.put("oldBlocksAndContainerPrepared",true);
                report.put("saveDefersUntilRestart",true);
            } else {
                check(global == phase.equals("global"),"Unexpected active replacement mode");
                check(BlockgenBan.definitions().equals(rules()),"Restart lost rules");
                boolean converted = global || phase.equals("generation-again");
                check(level.getBlockState(OLD).is(converted?Blocks.ANDESITE:Blocks.DIAMOND_ORE),"Old palette conversion disagrees with mode");
                if (converted) {
                    check(level.getBlockState(OLD.south()).isAir() && level.getBlockEntity(OLD.south())==null,"Old barrel entity not removed");
                    check(level.getBlockState(OLD.east()).is(Blocks.CHEST) && level.getBlockEntity(OLD.east())!=null,"Old target chest entity missing");
                    check(level.getBlockState(OLD.west()).is(Blocks.GLOWSTONE),"Old light target missing");
                } else {
                    check(((BarrelBlockEntity)level.getBlockEntity(OLD.south())).getItem(0).getCount()==17,"Generation mode altered old inventory");
                }
                report.put("oldPaletteAndBlockEntities",true);
                var placed = OLD.above(6);
                level.setBlockAndUpdate(placed,Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(placed.below(),Blocks.DIRT.defaultBlockState());
                var stack = new ItemStack(Items.DIAMOND_ORE,64);
                var hit = new BlockHitResult(Vec3.atCenterOf(placed.below()),Direction.UP,placed.below(),false);
                var action=((BlockItem)Items.DIAMOND_ORE).place(new BlockPlaceContext(level,null,InteractionHand.MAIN_HAND,stack,hit));
                Block expected=global?Blocks.ANDESITE:Blocks.DIAMOND_ORE;
                check(action.consumesAction() && level.getBlockState(placed).is(expected),"BlockItem placement disagrees with mode");
                check(stack.is(Items.DIAMOND_ORE) && stack.getCount()>0,"Remaining item converted");
                var cmd=OLD.above(9);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"setblock "+cmd.getX()+" "+cmd.getY()+" "+cmd.getZ()+" minecraft:diamond_ore");
                check(level.getBlockState(cmd).is(expected),"Command write disagrees with mode");
                var direct=OLD.above(10);var chunk=level.getChunkAt(direct);
                chunk.getSection(chunk.getSectionIndex(direct.getY())).setBlockState(direct.getX()&15,direct.getY()&15,direct.getZ()&15,Blocks.DIAMOND_ORE.defaultBlockState());
                check(level.getBlockState(direct).is(expected),"FULL section write disagrees with mode");
                report.put("blockItemCommandAndDirectSectionWrites",true);
                report.put("inventoryItemsPreserved",true);
                if (!phase.equals("generation-again")) {
                    int start=global?120:100, remnants=0, targetCount=0;
                    for(int x=start;x<start+2;x++) for(int z=start;z<start+2;z++) {
                        var fresh=level.getChunk(x,z);
                        for(var section:fresh.getSections()) for(int y=0;y<16;y++) for(int a=0;a<16;a++) for(int b=0;b<16;b++) {
                            var state=section.getBlockState(a,y,b);
                            if(GlobalWrites.map(state)!=state) remnants++;
                            if(state.is(Blocks.ANDESITE)) targetCount++;
                        }
                    }
                    check(remnants==0 && targetCount>1000,"Fresh terrain or ores bypassed replacement: "+remnants);
                    report.put("freshChunks",4);report.put("sourceRemnants",remnants);report.put("targetBlocks",targetCount);
                }
                var generator=level.getChunkSource().getGenerator();var access=server.registryAccess();
                var structure=access.registryOrThrow(Registries.STRUCTURE).get(id("minecraft:mineshaft"));
                ChunkPos valid=null;
                for(int i=0;i<256 && valid==null;i++) {
                    var attempt=new ChunkPos(800+i,800);
                    if(structure.generate(access,generator,generator.getBiomeSource(),level.getChunkSource().randomState(),server.getStructureManager(),level.getSeed(),attempt,0,level,b->true).isValid()) valid=attempt;
                }
                check(valid!=null,"No valid mineshaft control");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"blockgenban structure ban minecraft:mineshaft");
                check(structure.generate(access,generator,generator.getBiomeSource(),level.getChunkSource().randomState(),server.getStructureManager(),level.getSeed(),valid,0,level,b->true)==StructureStart.INVALID_START,"Banned structure still generated");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"blockgenban structure allow minecraft:mineshaft");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"blockgenban feature ban minecraft:ore");
                check(GenerationBans.current.features().contains(id("minecraft:ore")),"Feature ban command failed");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"blockgenban feature allow minecraft:ore");
                report.put("structureEntryBanAndFeatureCommands",true);
                boolean rejected=false;
                try { BlockgenBan.saveRules(server,Map.of(id("minecraft:stone"),id("minecraft:dirt"),id("minecraft:dirt"),id("minecraft:stone"))); }
                catch(IllegalArgumentException expectedCycle) { rejected=true; }
                check(rejected && BlockgenBan.definitions().equals(rules()),"Cycle rejection changed rules");
                report.put("cycleRejectedWithoutChangingRules",true);
                BlockgenBan.saveRules(server,BlockgenBan.definitions());
                check(RuleStore.loadSettings().replacementMode()==BlockgenBan.replacementMode(),"Save erased mode");
            }
            check(RuleStore.load().equals(BlockgenBan.definitions()),"JSON mismatch");
            check(RuleWorldData.get(server).getRules().equals(BlockgenBan.definitions()),"World SavedData mismatch");
            report.put("jsonAndWorldSnapshot",true);report.put("pass",true);
        } catch(Throwable failure) { failure.printStackTrace();report.put("pass",false);report.put("error",failure.toString()); }
        try { Files.writeString(Path.of("probe-"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch(Exception failure) { throw new RuntimeException(failure); }
        System.out.println("BLOCK_REMAP_SERVER_PROBE "+report);
        server.halt(false);
    }
}
