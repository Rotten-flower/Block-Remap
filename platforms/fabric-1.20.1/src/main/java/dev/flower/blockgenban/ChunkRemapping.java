package dev.flower.blockgenban;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Remaps the saved section palette once on load; keeps indices and packed voxel data intact. */
public final class ChunkRemapping {
    private record Section(int y, BlockState[] targets, SimpleBitStorage storage) {
        BlockState target(int index){return targets[storage==null?0:storage.get(index)];}
        BlockState at(BlockPos pos){return target((pos.getY()&15)<<8 | (pos.getZ()&15)<<4 | pos.getX()&15);}
    }
    public static void apply(ChunkPos chunkPos,CompoundTag tag){
        if(BlockgenBan.replacementMode()!=ReplacementMode.GLOBAL || !BlockgenBan.getRules().isActive())return;
        Map<Integer,Section> changed=new HashMap<>();
        ListTag sections=tag.getList("sections",Tag.TAG_COMPOUND);
        for(int s=0;s<sections.size();s++){
            CompoundTag section=sections.getCompound(s), states=section.getCompound("block_states");
            ListTag palette=states.getList("palette",Tag.TAG_COMPOUND);
            BlockState[] targets=new BlockState[palette.size()];boolean hit=false;
            for(int i=0;i<palette.size();i++){
                ResourceLocation id=ResourceLocation.tryParse(palette.getCompound(i).getString("Name"));
                if(id==null || !BuiltInRegistries.BLOCK.containsKey(id))continue;
                var block=BuiltInRegistries.BLOCK.get(id);
                var target=((GlobalWrites.BlockTarget)block).blockgen_ban$target();
                if(target==null)continue;
                targets[i]=target;palette.set(i,NbtUtils.writeBlockState(target));hit=true;
            }
            if(hit){
                int bits=Math.max(4,Mth.ceillog2(palette.size()));
                SimpleBitStorage storage=palette.size()==1?null:new SimpleBitStorage(bits,4096,states.getLongArray("data"));
                int y=section.getByte("Y");changed.put(y,new Section(y,targets,storage));
            }
        }
        if(changed.isEmpty())return;
        ListTag entities=tag.getList("block_entities",Tag.TAG_COMPOUND);
        entities.removeIf(value->{var entity=(CompoundTag)value;return target(changed,new BlockPos(entity.getInt("x"),entity.getInt("y"),entity.getInt("z")))!=null;});
        removeTicks(tag,"block_ticks",changed);removeTicks(tag,"fluid_ticks",changed);
        ListTag fluidTicks=tag.getList("fluid_ticks",Tag.TAG_COMPOUND);
        for(Section section:changed.values()){
            boolean extra=Arrays.stream(section.targets).anyMatch(state->state!=null && (state.hasBlockEntity() || !state.getFluidState().isEmpty()));
            if(!extra)continue;
            for(int index=0;index<4096;index++){
                var state=section.target(index);if(state==null)continue;
                int x=chunkPos.getMinBlockX()+(index&15),y=section.y*16+(index>>8),z=chunkPos.getMinBlockZ()+(index>>4&15);
                if(state.hasBlockEntity()){
                    CompoundTag entity=position(x,y,z);entity.putString("id","DUMMY");entity.putBoolean("keepPacked",true);entities.add(entity);
                }
                if(!state.getFluidState().isEmpty()){
                    CompoundTag tick=position(x,y,z);
                    tick.putString("i",BuiltInRegistries.FLUID.getKey(state.getFluidState().getType()).toString());
                    tick.putInt("t",1);tick.putInt("p",0);fluidTicks.add(tick);
                }
            }
        }
        tag.put("block_entities",entities);tag.put("fluid_ticks",fluidTicks);
        tag.remove("Heightmaps");tag.putBoolean("isLightOn",false);tag.putBoolean("shouldSave",true);
        for(int s=0;s<sections.size();s++){sections.getCompound(s).remove("BlockLight");sections.getCompound(s).remove("SkyLight");}
    }
    private static BlockState target(Map<Integer,Section> changed,BlockPos pos){var section=changed.get(pos.getY()>>4);return section==null?null:section.at(pos);}
    private static void removeTicks(CompoundTag tag,String key,Map<Integer,Section> changed){
        ListTag ticks=tag.getList(key,Tag.TAG_COMPOUND);
        ticks.removeIf(value->{var tick=(CompoundTag)value;return target(changed,new BlockPos(tick.getInt("x"),tick.getInt("y"),tick.getInt("z")))!=null;});tag.put(key,ticks);
    }
    private static CompoundTag position(int x,int y,int z){CompoundTag tag=new CompoundTag();tag.putInt("x",x);tag.putInt("y",y);tag.putInt("z",z);return tag;}
    private ChunkRemapping(){}
}
