package dev.flower.blockgenban.mixin;
import dev.flower.blockgenban.GlobalWrites;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Level.class)
public abstract class LevelMixin {
    @ModifyVariable(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),argsOnly=true)
    private BlockState blockgen_ban$map(BlockState state){return ((Level)(Object)this).isClientSide?state:GlobalWrites.mapWorld(state);}
}
