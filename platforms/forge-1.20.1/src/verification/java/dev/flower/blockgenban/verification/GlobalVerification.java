package dev.flower.blockgenban.verification;
import com.google.gson.GsonBuilder;
import dev.flower.blockgenban.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;

final class GlobalVerification {
    private static ResourceLocation id(String value){return new ResourceLocation(value);}
    private static final BlockPos OLD=new BlockPos(520*16+8,210,520*16+8);
    static Map<ResourceLocation,ResourceLocation> expected(){
        Map<ResourceLocation,ResourceLocation> rules=new LinkedHashMap<>();
        rules.put(id("minecraft:diamond_ore"),id("minecraft:stone"));
        rules.put(id("minecraft:bedrock"),id("minecraft:stone"));
        rules.put(id("minecraft:iron_ore"),id("minecraft:air"));
        rules.put(id("minecraft:deepslate_iron_ore"),id("minecraft:air"));
        rules.put(id("minecraft:gold_block"),id("minecraft:chest"));
        rules.put(id("minecraft:barrel"),id("minecraft:air"));
        rules.put(id("minecraft:coal_block"),id("minecraft:glowstone"));
        rules.put(id(VerificationMod.ID+":probe"),id("minecraft:air"));return rules;
    }
    static void run(MinecraftServer server,String phase){
        Map<String,Object> result=new LinkedHashMap<>();result.put("phase",phase);
        try{
            var level=server.overworld();var chunk=level.getChunk(520,520);
            if(phase.equals("global-prepare")){
                check(!BlockgenBan.getRules().isActive(),"Expected clean config");
                level.setBlockAndUpdate(OLD,Blocks.DIAMOND_ORE.defaultBlockState());
                level.setBlockAndUpdate(OLD.east(),Blocks.GOLD_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(OLD.south(),Blocks.BARREL.defaultBlockState());
                ((net.minecraft.world.level.block.entity.BarrelBlockEntity)level.getBlockEntity(OLD.south())).setItem(0,new ItemStack(Items.DIAMOND,17));
                level.setBlockAndUpdate(OLD.west(),Blocks.COAL_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(OLD.north(),Blocks.IRON_ORE.defaultBlockState());
                // Force a hash palette with unchanged markers and source+target duplicates after remapping.
                int marker=0;for(var dye:net.minecraft.world.item.DyeColor.values()){
                    var block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(id("minecraft:"+dye.getName()+"_wool"));
                    chunk.setBlockState(OLD.offset((marker++%8)-4,2,marker/8+2),block.defaultBlockState(),false);
                }
                chunk.setBlockState(OLD.above(4),Blocks.STONE.defaultBlockState(),false);
                BlockgenBan.saveRules(server,expected());
                check(!BlockgenBan.getRules().isActive() && level.getBlockState(OLD).is(Blocks.DIAMOND_ORE),"Save applied before restart");
                boolean rejected=false;try{BlockgenBan.saveRules(server,Map.of(id("minecraft:stone"),id("minecraft:dirt"),id("minecraft:dirt"),id("minecraft:stone")));}catch(IllegalArgumentException e){rejected=true;}
                check(rejected && BlockgenBan.definitions().equals(expected()),"Cyclic rule validation failed");
                result.put("existingWorldPreparedAndSaveDeferred",true);
            }else if(phase.equals("global-cleared")){
                check(!BlockgenBan.getRules().isActive(),"Rules not cleared on restart");
                check(level.getBlockState(OLD).is(Blocks.STONE),"Converted old block reverted after rule removal");
                level.setBlockAndUpdate(OLD.above(7),Blocks.DIAMOND_ORE.defaultBlockState());
                check(level.getBlockState(OLD.above(7)).is(Blocks.DIAMOND_ORE),"Removed rule still maps writes");
                result.put("conversionPersistedAfterRuleRemoval",true);
            }else{
                check(BlockgenBan.definitions().equals(expected()),"Settings missing");
                check(level.getBlockState(OLD).is(Blocks.STONE),"Existing diamond wasn't remapped on load");
                check(level.getBlockState(OLD.east()).is(Blocks.CHEST) && level.getBlockEntity(OLD.east()) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity,"Old gold target entity missing");
                check(level.getBlockState(OLD.south()).isAir() && level.getBlockEntity(OLD.south())==null,"Old barrel or its inventory entity remains");
                check(level.getBlockState(OLD.west()).is(Blocks.GLOWSTONE),"Old light emitter not remapped");
                check(level.getBlockState(OLD.north()).isAir(),"Old iron wasn't removed");
                check(chunk.isLightCorrect(),"Old chunk light wasn't rebuilt");
                check(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,OLD.west())==15,"Old replacement emitter has incorrect block light");
                GenerationBenchmark.verifyHeightmaps(chunk);
                int marker=0;for(var dye:DyeColor.values()){
                    var block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(id("minecraft:"+dye.getName()+"_wool"));
                    check(level.getBlockState(OLD.offset((marker++%8)-4,2,marker/8+2)).is(block),"Hash palette marker corrupted");
                }
                result.put("oldPaletteEntitiesHeightmapsAndLight",true);
                if(phase.equals("global-active")){
                    var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
                    BlockPos placed=OLD.above(6);level.setBlockAndUpdate(placed,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(placed.below(),Blocks.STONE.defaultBlockState());
                    var stack=new ItemStack(Items.DIAMOND_ORE);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                    var hit=new BlockHitResult(Vec3.atCenterOf(placed.below()),Direction.UP,placed.below(),false);
                    var interaction=((BlockItem)Items.DIAMOND_ORE).place(new BlockPlaceContext(level,player,InteractionHand.MAIN_HAND,stack,hit));
                    check(interaction.consumesAction() && level.getBlockState(placed).is(Blocks.STONE),"Real BlockItem placement wasn't mapped");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"setblock "+OLD.getX()+" 219 "+OLD.getZ()+" minecraft:diamond_ore");
                    check(level.getBlockState(new BlockPos(OLD.getX(),219,OLD.getZ())).is(Blocks.STONE),"Command placement wasn't mapped");
                    var direct=OLD.above(10);level.setBlockAndUpdate(direct,Blocks.AIR.defaultBlockState());chunk.getSection(chunk.getSectionIndex(direct.getY())).setBlockState(direct.getX()&15,direct.getY()&15,direct.getZ()&15,Blocks.DIAMOND_ORE.defaultBlockState());
                    check(chunk.getBlockState(direct).is(Blocks.STONE),"FULL direct section write wasn't mapped");
                    GenerationBenchmark.verifyHeightmaps(chunk);
                    int remnants=0;
                    for(int x=600;x<604;x++)for(int z=600;z<604;z++){
                        var fresh=level.getChunk(x,z);var probe=new BlockPos(x*16+4,192,z*16+4);
                        check(fresh.getBlockState(probe).is(Blocks.CHEST) && fresh.getBlockEntity(probe) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity,"Feature target chest/entity missing");
                        check(fresh.getBlockState(probe.east()).is(Blocks.STONE) && fresh.getBlockState(probe.west()).isAir(),"Feature chunk/section writes failed");
                        GenerationBenchmark.verifyHeightmaps(fresh);
                        for(var section:fresh.getSections())for(int sx=0;sx<16;sx++)for(int sy=0;sy<16;sy++)for(int sz=0;sz<16;sz++){
                            var state=section.getBlockState(sx,sy,sz);if(GlobalWrites.map(state)!=state)remnants++;
                        }
                    }
                    check(remnants==0,"Source remnants: "+remnants);result.put("newChunks",16);result.put("sourceRemnants",remnants);
                    result.put("blockItemCommandAndFullSectionWrites",true);bans(server,result);
                }
                if(phase.equals("global-restart")){BlockgenBan.saveRules(server,Map.of());result.put("rulesRemovedForNextRestart",true);}
            }
            check(RuleStore.load().equals(BlockgenBan.definitions()),"JSON mismatch");
            check(RuleWorldData.get(server).getRules().equals(BlockgenBan.definitions()),"World snapshot mismatch");result.put("pass",true);
        }catch(Throwable e){e.printStackTrace();result.put("pass",false);result.put("error",e.toString());}
        try{Files.writeString(Path.of("verification-"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){e.printStackTrace();}
        System.out.println("BLOCKGEN_GLOBAL_ACCEPTANCE "+result);server.halt(false);
    }
    static void bans(MinecraftServer server,Map<String,Object> result)throws Exception{
        var level=server.overworld();var access=server.registryAccess();var generator=level.getChunkSource().getGenerator();
        var probe=id(VerificationMod.ID+":probe");List<ChunkAccess> chunks=new ArrayList<>();
        for(int z=619;z<=621;z++)for(int x=619;x<=621;x++)chunks.add(level.getChunk(x,z));
        var region=new WorldGenRegion(level,chunks,ChunkStatus.FEATURES,1);var pos=new BlockPos(620*16,192,620*16);
        var feature=access.registryOrThrow(Registries.CONFIGURED_FEATURE).get(probe);var placed=access.registryOrThrow(Registries.PLACED_FEATURE).get(probe);
        int count=VerificationMod.FEATURE_CALLS.get();feature.place(region,generator,RandomSource.create(1),pos);check(VerificationMod.FEATURE_CALLS.get()==count+1,"Allowed feature didn't execute");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"blockgenban feature ban "+probe);
        count=VerificationMod.FEATURE_CALLS.get();check(!feature.place(region,generator,RandomSource.create(1),pos) && !placed.placeWithBiomeCheck(region,generator,RandomSource.create(1),pos) && VerificationMod.FEATURE_CALLS.get()==count,"Feature body ran despite ban");
        var structure=access.registryOrThrow(Registries.STRUCTURE).get(id("minecraft:mineshaft"));ChunkPos valid=null;
        for(int i=0;i<256 && valid==null;i++){var attempt=new ChunkPos(800+i,800);if(structure.generate(access,generator,generator.getBiomeSource(),level.getChunkSource().randomState(),server.getStructureManager(),level.getSeed(),attempt,0,level,b->true).isValid())valid=attempt;}
        check(valid!=null,"No valid baseline structure");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"blockgenban structure ban minecraft:mineshaft");
        check(structure.generate(access,generator,generator.getBiomeSource(),level.getChunkSource().randomState(),server.getStructureManager(),level.getSeed(),valid,0,level,b->true)==StructureStart.INVALID_START,"Banned structure generated");
        BlockgenBan.saveRules(server,BlockgenBan.definitions());check(RuleStore.loadSettings().bans().equals(GenerationBans.current),"Save erased generation bans");result.put("structureAndFeatureEntryBans",true);
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
