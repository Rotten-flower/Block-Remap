package dev.luohua.blockgenban.mixin;
import dev.luohua.blockgenban.GlobalWrites;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
    @ModifyVariable(method="setBlock",at=@At("HEAD"),argsOnly=true)
    private BlockState blockgen_ban$map(BlockState state){return GlobalWrites.map(state);}
}
