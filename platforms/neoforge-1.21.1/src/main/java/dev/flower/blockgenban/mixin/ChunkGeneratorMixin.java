package dev.flower.blockgenban.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.flower.blockgenban.BlockgenBan;
import dev.flower.blockgenban.GlobalWrites;
import dev.flower.blockgenban.ReplacementMode;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @WrapMethod(method="applyBiomeDecoration")
    private void blockgen_ban$generation(WorldGenLevel level,ChunkAccess chunk,StructureManager structures,Operation<Void> original){
        if(!(level instanceof WorldGenRegion) || BlockgenBan.replacementMode()==ReplacementMode.GLOBAL || !BlockgenBan.getRules().isActive()){
            original.call(level,chunk,structures);
            return;
        }
        // A generation task can also place blocks into an already-complete neighbor.
        boolean previous=GlobalWrites.enterGeneration();
        try{original.call(level,chunk,structures);}
        finally{GlobalWrites.restoreGeneration(previous);}
    }
}
