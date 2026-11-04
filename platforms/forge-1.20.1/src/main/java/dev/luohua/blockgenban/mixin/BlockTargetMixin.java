package dev.luohua.blockgenban.mixin;
import dev.luohua.blockgenban.GlobalWrites;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
@Mixin(Block.class)
public abstract class BlockTargetMixin implements GlobalWrites.BlockTarget {
    @Unique private volatile BlockState blockgen_ban$replacement;
    public BlockState blockgen_ban$target(){return blockgen_ban$replacement;}
    public void blockgen_ban$target(BlockState target){blockgen_ban$replacement=target;}
}
