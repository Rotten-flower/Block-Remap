package dev.flower.blockgenban.mixin;
import dev.flower.blockgenban.GlobalWrites;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ProtoChunk.class)
public abstract class ProtoChunkMixin {
    @ModifyVariable(method="setBlockState",at=@At("HEAD"),argsOnly=true)
    private BlockState blockgen_ban$map(BlockState state){return GlobalWrites.map(state);}
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void blockgen_ban$entity(BlockPos pos,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> cir){
        var previous=cir.getReturnValue();
        if(previous!=null && (state.hasBlockEntity() || previous.hasBlockEntity()))
            GlobalWrites.entity((ProtoChunk)(Object)this,pos,previous,state);
    }
}
