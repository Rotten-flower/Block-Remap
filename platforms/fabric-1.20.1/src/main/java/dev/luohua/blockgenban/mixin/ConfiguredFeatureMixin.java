package dev.luohua.blockgenban.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.luohua.blockgenban.GenerationBans;
import dev.luohua.blockgenban.GlobalWrites;
import dev.luohua.blockgenban.BlockgenBan;
import dev.luohua.blockgenban.ReplacementMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConfiguredFeature.class)
public abstract class ConfiguredFeatureMixin {
    @WrapMethod(method="place")
    private boolean blockgen_ban$generation(WorldGenLevel level,ChunkGenerator generator,RandomSource random,BlockPos pos,Operation<Boolean> original){
        if(!(level instanceof WorldGenRegion) || BlockgenBan.replacementMode()==ReplacementMode.GLOBAL || !BlockgenBan.getRules().isActive())
            return original.call(level,generator,random,pos);
        boolean previous=GlobalWrites.enterGeneration();
        try{return original.call(level,generator,random,pos);}
        finally{GlobalWrites.restoreGeneration(previous);}
    }
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void blockgen_ban$skip(WorldGenLevel level,ChunkGenerator generator,RandomSource random,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if(level instanceof WorldGenRegion && GenerationBans.current.configured(level.registryAccess(),(ConfiguredFeature<?,?>)(Object)this))
            cir.setReturnValue(false);
    }
}
