package dev.luohua.blockgenban.mixin;
import dev.luohua.blockgenban.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LevelChunkSection.class)
public abstract class SectionMixin implements GlobalWrites.SectionOwner {
    @Unique private volatile ChunkAccess blockgen_ban$owner;
    @Unique private int blockgen_ban$minY;
    public void blockgen_ban$owner(ChunkAccess owner,int minY){blockgen_ban$minY=minY;blockgen_ban$owner=owner;}
    @ModifyVariable(method="setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",at=@At("HEAD"),argsOnly=true)
    private boolean blockgen_ban$lock(boolean lock){
        return lock && !(blockgen_ban$owner!=null && BlockgenBan.getRules().isActive() && GlobalWrites.alreadyLocked((LevelChunkSection)(Object)this));
    }
    @Inject(method="setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",at=@At("HEAD"),cancellable=true)
    private void blockgen_ban$direct(int x,int y,int z,BlockState state,boolean lock,CallbackInfoReturnable<BlockState> cir){
        var owner=blockgen_ban$owner;
        if(owner==null || !BlockgenBan.getRules().isActive() || !GlobalWrites.mapsSection(owner))return;
        var mapped=GlobalWrites.map(state);
        if(mapped==state)return;
        var pos=new BlockPos(owner.getPos().getMinBlockX()+x,blockgen_ban$minY+y,owner.getPos().getMinBlockZ()+z);
        // Terminal targets prevent nested chunk and section writes mapping again.
        var before=GlobalWrites.enter((LevelChunkSection)(Object)this,lock);
        try{cir.setReturnValue(owner.setBlockState(pos,mapped,false));}
        finally{GlobalWrites.restore(before);}
    }
    @Inject(method="setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",at=@At("RETURN"))
    private void blockgen_ban$entity(int x,int y,int z,BlockState state,boolean lock,CallbackInfoReturnable<BlockState> cir){
        var owner=blockgen_ban$owner;var previous=cir.getReturnValue();
        if(owner instanceof ProtoChunk && previous!=null && (state.hasBlockEntity() || previous.hasBlockEntity()))
            GlobalWrites.entity(owner,new BlockPos(owner.getPos().getMinBlockX()+x,blockgen_ban$minY+y,owner.getPos().getMinBlockZ()+z),previous,state);
    }
}
