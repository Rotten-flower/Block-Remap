package dev.flower.blockgenban.mixin;

import dev.flower.blockgenban.GlobalWrites;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
public abstract class ChunkOwnershipMixin {
    @Shadow @Final protected LevelChunkSection[] sections;
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void blockgen_ban$bindSections(CallbackInfo ci) {
        ChunkAccess chunk = (ChunkAccess)(Object)this;
        if(chunk instanceof ImposterProtoChunk)return;
        for (int index=0; index<sections.length; index++)
            ((GlobalWrites.SectionOwner)sections[index]).blockgen_ban$owner(
                    chunk instanceof ProtoChunk ? chunk : null, levelHeightAccessor.getMinBuildHeight() + index*16);
    }
}
