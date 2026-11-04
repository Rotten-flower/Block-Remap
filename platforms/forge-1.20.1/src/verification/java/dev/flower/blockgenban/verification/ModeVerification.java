package dev.flower.blockgenban.verification;

import com.google.gson.*;
import dev.flower.blockgenban.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Isolated production-server acceptance for both replacement scopes and mode switching. */
final class ModeVerification {
    private static final BlockPos OLD=new BlockPos(520*16+8,210,520*16+8);
    private static final Path CONFIG=Path.of("config/blockgen_ban.json");
    private static void check(boolean value,String message){GlobalVerification.check(value,message);}

    static void run(MinecraftServer server,String phase){
        Map<String,Object> result=new LinkedHashMap<>();result.put("phase",phase);
        try{
            var level=server.overworld();var chunk=level.getChunk(520,520);
            boolean global=phase.startsWith("mode-global");
            check(BlockgenBan.replacementMode()==(global?ReplacementMode.GLOBAL:ReplacementMode.GENERATION_ONLY),"Incorrect active mode");
            result.put("mode",BlockgenBan.replacementMode().id());
            if(phase.equals("mode-prepare")){
                check(!BlockgenBan.getRules().isActive(),"Fresh server must have no rules");
                level.setBlockAndUpdate(OLD,Blocks.DIAMOND_ORE.defaultBlockState());
                level.setBlockAndUpdate(OLD.east(),Blocks.GOLD_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(OLD.south(),Blocks.BARREL.defaultBlockState());
                ((BarrelBlockEntity)level.getBlockEntity(OLD.south())).setItem(0,new ItemStack(Items.DIAMOND,17));
                level.setBlockAndUpdate(OLD.west(),Blocks.COAL_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(OLD.north(),Blocks.IRON_ORE.defaultBlockState());
                var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
                player.getInventory().setItem(8,new ItemStack(Items.DIAMOND_ORE,23));
                CompoundTag inventory=new CompoundTag();inventory.put("Inventory",player.getInventory().save(new ListTag()));
                NbtIo.writeCompressed(inventory,Path.of("player-inventory.nbt").toFile());
                BlockgenBan.saveRules(server,GlobalVerification.expected());
                check(!BlockgenBan.getRules().isActive(),"Saving rules applied before re-entry");
                configValidation(result);
                result.put("preparedExistingWorldAndPlayerInventory",true);
            }else{
                check(BlockgenBan.definitions().equals(GlobalVerification.expected()),"Rules changed on restart");
                boolean converted=global || phase.equals("mode-generation-again");
                check(level.getBlockState(OLD).is(converted?Blocks.STONE:Blocks.DIAMOND_ORE),"Old saved source did not respect mode");
                if(!converted){
                    check(level.getBlockState(OLD.south()).is(Blocks.BARREL),"Generation-only changed old barrel");
                    check(((BarrelBlockEntity)level.getBlockEntity(OLD.south())).getItem(0).getCount()==17,"Old barrel content lost");
                    check(level.getBlockState(OLD.east()).is(Blocks.GOLD_BLOCK),"Generation-only changed old gold block");
                    result.put("existingBlocksAndContainerContentsPreserved",true);
                }else{
                    check(level.getBlockState(OLD.south()).isAir() && level.getBlockEntity(OLD.south())==null,"Global mode did not remove old barrel");
                    check(level.getBlockState(OLD.east()).is(Blocks.CHEST) && level.getBlockEntity(OLD.east())!=null,"Global target entity missing");
                    check(level.getBlockState(OLD.west()).is(Blocks.GLOWSTONE) && level.getBrightness(LightLayer.BLOCK,OLD.west())==15,"Global target lighting incorrect");
                    result.put("oldPaletteEntityAndLightingConverted",true);
                }
                if(phase.equals("mode-generation-restart")){
                    check(level.getBlockState(OLD.above(6)).is(Blocks.DIAMOND_ORE),"Placed original changed after re-entry");
                    check(level.getBlockState(OLD.above(9)).is(Blocks.DIAMOND_ORE),"Command block changed after re-entry");
                    check(level.getBlockState(OLD.above(10)).is(Blocks.DIAMOND_ORE),"Direct FULL write changed after re-entry");
                    result.put("playerCommandAndFullWritesPreservedAfterRestart",true);
                }
                var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
                player.getInventory().load(NbtIo.readCompressed(Path.of("player-inventory.nbt").toFile()).getList("Inventory",Tag.TAG_COMPOUND));
                check(player.getInventory().getItem(8).is(Items.DIAMOND_ORE) && player.getInventory().getItem(8).getCount()==23,"Existing inventory item converted");
                result.put("existingInventoryItemsPreserved",true);
                placement(level,global,result);
                commandFeedback(server,result);
                GenerationBenchmark.verifyHeightmaps(chunk);
                if(phase.equals("mode-generation") || phase.equals("mode-global")){
                    // The preceding phase intentionally banned these entries. Restore the baseline before generating new probes.
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"blockgenban feature allow "+VerificationMod.ID+":probe");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"blockgenban structure allow minecraft:mineshaft");
                    freshChunks(level,global?660:600,result);
                    featureScopes(level,result);
                    GlobalVerification.bans(server,result);
                }
                if(phase.equals("mode-generation-again"))result.put("previousGlobalConversionRemainsPermanent",true);
                BlockgenBan.saveRules(server,BlockgenBan.definitions());
                check(RuleStore.loadSettings().replacementMode()==BlockgenBan.replacementMode(),"UI-style save erased mode");
                result.put("ruleAndBanSavesPreserveMode",true);
            }
            check(RuleStore.load().equals(BlockgenBan.definitions()),"Config rules mismatch");
            check(RuleWorldData.get(server).getRules().equals(BlockgenBan.definitions()),"World snapshot mismatch");
            result.put("pass",true);
        }catch(Throwable error){error.printStackTrace();result.put("pass",false);result.put("error",error.toString());}
        try{Files.writeString(Path.of("verification-"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
        catch(IOException error){throw new RuntimeException(error);}
        System.out.println("BLOCKGEN_MODE_ACCEPTANCE "+result);server.halt(false);
    }

    private static void placement(ServerLevel level,boolean global,Map<String,Object> result){
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        BlockPos placed=OLD.above(6);level.setBlockAndUpdate(placed,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(placed.below(),Blocks.STONE.defaultBlockState());
        var stack=new ItemStack(Items.DIAMOND_ORE,64);stack.setHoverName(Component.literal("原矿物品"));
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3.atCenterOf(placed.below()),Direction.UP,placed.below(),false);
        var interaction=((BlockItem)Items.DIAMOND_ORE).place(new BlockPlaceContext(level,player,InteractionHand.MAIN_HAND,stack,hit));
        var expected=global?Blocks.STONE:Blocks.DIAMOND_ORE;
        check(interaction.consumesAction() && level.getBlockState(placed).is(expected),"Real BlockItem placement incorrect");
        check(stack.is(Items.DIAMOND_ORE) && stack.getCount()>0 && stack.getHoverName().getString().equals("原矿物品"),"Remaining inventory items changed");
        var command=OLD.above(9);
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),"setblock "+command.getX()+" "+command.getY()+" "+command.getZ()+" minecraft:diamond_ore");
        check(level.getBlockState(command).is(expected),"Command write incorrect");
        var direct=OLD.above(10);level.setBlockAndUpdate(direct,Blocks.AIR.defaultBlockState());var chunk=level.getChunkAt(direct);
        chunk.getSection(chunk.getSectionIndex(direct.getY())).setBlockState(direct.getX()&15,direct.getY()&15,direct.getZ()&15,Blocks.DIAMOND_ORE.defaultBlockState());
        check(level.getBlockState(direct).is(expected),"Direct FULL section write incorrect");
        // Direct-section callers maintain heightmaps when the write is deliberately not intercepted.
        if(!global)for(var type:ChunkStatus.FULL.heightmapsAfter())chunk.getOrCreateHeightmapUnprimed(type).update(direct.getX()&15,direct.getY(),direct.getZ()&15,chunk.getBlockState(direct));
        result.put("realPlayerPlacementCommandsAndFullSectionWrites",true);
    }

    private static void freshChunks(ServerLevel level,int start,Map<String,Object> result){
        int remnants=0;
        for(int x=start;x<start+2;x++)for(int z=start;z<start+2;z++){
            var chunk=level.getChunk(x,z);var p=new BlockPos(x*16+4,192,z*16+4);
            check(chunk.getBlockState(p).is(Blocks.CHEST) && chunk.getBlockEntity(p)!=null,"Generation target entity missing");
            check(chunk.getBlockState(p.east()).is(Blocks.STONE) && chunk.getBlockState(p.west()).isAir() && chunk.getBlockState(p.above()).isAir(),"Generation chunk/section/region writes incorrect");
            GenerationBenchmark.verifyHeightmaps(chunk);
            for(var section:chunk.getSections())for(int y=0;y<16;y++)for(int z1=0;z1<16;z1++)for(int x1=0;x1<16;x1++)
                if(GlobalWrites.map(section.getBlockState(x1,y,z1))!=section.getBlockState(x1,y,z1))remnants++;
        }
        check(remnants==0,"New chunks contain source remnants: "+remnants);
        result.put("newChunks",4);result.put("sourceRemnants",remnants);
    }

    private static void featureScopes(ServerLevel level,Map<String,Object> result){
        var generator=level.getChunkSource().getGenerator();var chunks=new ArrayList<ChunkAccess>();
        for(int z=619;z<=621;z++)for(int x=619;x<=621;x++)chunks.add(level.getChunk(x,z));
        var region=new WorldGenRegion(level,chunks,ChunkStatus.FEATURES,1);var origin=new BlockPos(620*16,192,620*16);
        var configured=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(new net.minecraft.resources.ResourceLocation(VerificationMod.ID,"probe"));
        configured.place(region,generator,RandomSource.create(1),origin);
        var p=origin.offset(4,0,4);
        check(level.getBlockState(p).is(Blocks.CHEST) && level.getBlockState(p.east()).is(Blocks.STONE) && level.getBlockState(p.west()).isAir(),"Generation into existing FULL neighbors incorrect");
        if(BlockgenBan.replacementMode()==ReplacementMode.GENERATION_ONLY){
            check(!GlobalWrites.mapsWorldWrites(),"Generation context leaked after feature");
            var inner=feature(ctx->check(GlobalWrites.mapsWorldWrites(),"Nested feature missing context"));
            var outer=feature(ctx->{
                inner.place(region,generator,RandomSource.create(2),origin);
                check(GlobalWrites.mapsWorldWrites(),"Nested feature cleared outer context");
                throw new IllegalStateException("expected-scope-test");
            });
            boolean caught=false;
            try{outer.place(region,generator,RandomSource.create(3),origin);}catch(IllegalStateException error){caught=error.getMessage().equals("expected-scope-test");}
            check(caught && !GlobalWrites.mapsWorldWrites(),"Exceptional feature leaked context");
            configured.place(level,generator,RandomSource.create(4),origin.offset(0,0,8));
            var manual=p.offset(0,0,8);
            check(level.getBlockState(manual).is(Blocks.GOLD_BLOCK) && level.getBlockState(manual.east()).is(Blocks.DIAMOND_ORE) && level.getBlockState(manual.west()).is(Blocks.IRON_ORE),"Non-generation feature was replaced");
            result.put("nestedAndExceptionalScopesAndNonGenerationFeatures",true);
        }
        result.put("generationIntoFullNeighbors",true);
    }

    private static ConfiguredFeature<NoneFeatureConfiguration,Feature<NoneFeatureConfiguration>> feature(Consumer<FeaturePlaceContext<NoneFeatureConfiguration>> body){
        return new ConfiguredFeature<>(new Feature<NoneFeatureConfiguration>(NoneFeatureConfiguration.CODEC){
            public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){body.accept(context);return true;}
        },NoneFeatureConfiguration.INSTANCE);
    }

    private static void commandFeedback(MinecraftServer server,Map<String,Object> result){
        var messages=new ArrayList<Component>();
        var output=new net.minecraft.commands.CommandSource(){
            public void sendSystemMessage(Component message){messages.add(message);}
            public boolean acceptsSuccess(){return true;}
            public boolean acceptsFailure(){return true;}
            public boolean shouldInformAdmins(){return false;}
        };
        var source=server.createCommandSourceStack().withSource(output);
        server.getCommands().performPrefixedCommand(source,"blockgenban structure list");
        server.getCommands().performPrefixedCommand(source,"blockgenban feature ban blockgen_ban:missing_acceptance_probe");
        check(messages.size()==2,"Expected list feedback and invalid-ID feedback");
        var serialized=new ArrayList<String>();
        for(var message:messages){
            String packet=Component.Serializer.toJson(message);serialized.add(packet);
            var json=JsonParser.parseString(packet).getAsJsonObject();
            // Vanilla wraps failures in a red empty component. / 原版把错误正文包在红色空组件内。
            if(!json.has("translate") && json.has("extra"))json=json.getAsJsonArray("extra").get(0).getAsJsonObject();
            check(json.has("translate") && json.has("fallback") && !json.get("fallback").getAsString().isBlank(),"Command feedback lacks fallback for clients without mod translations");
            var decoded=Component.Serializer.fromJson(packet);
            check(decoded!=null && Component.Serializer.toJson(decoded).contains("fallback"),"Chat serialization discarded fallback");
        }
        result.put("commandFeedbackJson",serialized);
        result.put("commandFeedbackIncludesVanillaFallback",true);
    }

    private static void configValidation(Map<String,Object> result)throws IOException{
        String original=Files.readString(CONFIG);
        try{
            JsonObject root=JsonParser.parseString(original).getAsJsonObject();root.remove("replacementMode");
            Files.writeString(CONFIG,root.toString());
            check(RuleStore.loadSettings().replacementMode()==ReplacementMode.GENERATION_ONLY,"Absent setting default incorrect");
            for(JsonElement invalid:List.of(new JsonPrimitive("bogus"),new JsonPrimitive(true),JsonNull.INSTANCE)){
                root.add("replacementMode",invalid);Files.writeString(CONFIG,root.toString());
                boolean rejected=false;try{RuleStore.loadSettings();}catch(IOException expected){rejected=true;}
                check(rejected,"Invalid mode silently accepted: "+invalid);
            }
        }finally{Files.writeString(CONFIG,original);}
        result.put("missingModeDefaultsAndInvalidValuesRejected",true);
    }
}
