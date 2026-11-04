package dev.luohua.blockgenban.mixin;

import dev.luohua.blockgenban.GenerationBans;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureMixin {
    @Inject(method="placeWithContext",at=@At("HEAD"),cancellable=true)
    private void blockgen_ban$skip(PlacementContext context,RandomSource random,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if(context.getLevel() instanceof WorldGenRegion && GenerationBans.current.placed(context.getLevel().registryAccess(),(PlacedFeature)(Object)this))
            cir.setReturnValue(false);
    }
}
