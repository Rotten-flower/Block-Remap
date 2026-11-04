package dev.flower.blockgenban;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
/** Only direct section forwarding preserves an already-held palette lock. */
public final class GlobalWrites {
    private static final ThreadLocal<LevelChunkSection> LOCKED=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> GENERATING=new ThreadLocal<>();
    public static boolean enterGeneration(){
        boolean previous=Boolean.TRUE.equals(GENERATING.get());
        GENERATING.set(true);
        return previous;
    }
    public static void restoreGeneration(boolean previous){
        if(previous)GENERATING.set(true);else GENERATING.remove();
    }
    public static boolean mapsWorldWrites(){
        return BlockgenBan.replacementMode()==ReplacementMode.GLOBAL || Boolean.TRUE.equals(GENERATING.get());
    }
    public static boolean mapsSection(ChunkAccess owner){
        return owner instanceof ProtoChunk && !(owner instanceof ImposterProtoChunk) || mapsWorldWrites();
    }
    public static BlockState mapWorld(BlockState state){return mapsWorldWrites()?map(state):state;}
    public static boolean alreadyLocked(LevelChunkSection section){return LOCKED.get()==section;}
    public static LevelChunkSection enter(LevelChunkSection section,boolean lock){var before=LOCKED.get();if(!lock)LOCKED.set(section);return before;}
    public static void restore(LevelChunkSection before){if(before==null)LOCKED.remove();else LOCKED.set(before);}
    public static BlockState map(BlockState state){
        var mapped=((BlockTarget)state.getBlock()).blockgen_ban$target();
        return mapped==null?state:mapped;
    }
    public interface BlockTarget {
        BlockState blockgen_ban$target();
        void blockgen_ban$target(BlockState target);
    }
    public static void entity(ChunkAccess chunk,BlockPos pos,BlockState previous,BlockState state){
        if(!(chunk instanceof ProtoChunk) || chunk instanceof ImposterProtoChunk)return;
        if(previous.hasBlockEntity() && previous.getBlock()!=state.getBlock())chunk.removeBlockEntity(pos);
        if(state.hasBlockEntity() && state.getBlock() instanceof EntityBlock factory
                && chunk.getBlockEntity(pos)==null && chunk.getBlockEntityNbt(pos)==null){
            var entity=factory.newBlockEntity(pos.immutable(),state);
            if(entity!=null)chunk.setBlockEntity(entity);
        }
    }
    public interface SectionOwner {void blockgen_ban$owner(ChunkAccess owner,int minY);}
    private GlobalWrites(){}
}
