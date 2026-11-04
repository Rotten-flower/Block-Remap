package dev.flower.blockgenban.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.flower.blockgenban.GlobalWrites;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseGeneratorMixin {
    @ModifyExpressionValue(method="doFill",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/NoiseBasedChunkGenerator;debugPreliminarySurfaceLevel(Lnet/minecraft/world/level/levelgen/NoiseChunk;IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState blockgen_ban$mapTerrain(BlockState state){return GlobalWrites.map(state);}
}
