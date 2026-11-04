package dev.flower.blockgenban.mixin;
import dev.flower.blockgenban.ChunkRemapping;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {
    @Inject(method="read",at=@At("HEAD"))
    private static void blockgen_ban$load(ServerLevel level,PoiManager poi,ChunkPos pos,CompoundTag tag,CallbackInfoReturnable<ProtoChunk> cir){ChunkRemapping.apply(pos,tag);}
}
