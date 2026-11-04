package dev.flower.blockgenban.mixin;

import dev.flower.blockgenban.GenerationBans;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Predicate;

@Mixin(Structure.class)
public abstract class StructureMixin {
    @Inject(method="generate",at=@At("HEAD"),cancellable=true)
    private void blockgen_ban$skip(RegistryAccess access,ChunkGenerator generator,BiomeSource biomes,RandomState random,
            StructureTemplateManager templates,long seed,ChunkPos pos,int references,LevelHeightAccessor height,
            Predicate<Holder<Biome>> valid,CallbackInfoReturnable<StructureStart> cir) {
        if(GenerationBans.current.structure(access,(Structure)(Object)this)) cir.setReturnValue(StructureStart.INVALID_START);
    }
}
