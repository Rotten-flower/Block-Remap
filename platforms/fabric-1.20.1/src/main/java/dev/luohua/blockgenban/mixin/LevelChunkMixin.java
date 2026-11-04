package dev.luohua.blockgenban.mixin;
import dev.luohua.blockgenban.GlobalWrites;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Shadow @Final private Level level;
    @Inject(method="<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",at=@At("RETURN"))
    private void blockgen_ban$bind(CallbackInfo ci){
        var chunk=(LevelChunk)(Object)this;var sections=chunk.getSections();
        for(int i=0;i<sections.length;i++)((GlobalWrites.SectionOwner)sections[i]).blockgen_ban$owner(level.isClientSide?null:chunk,chunk.getMinBuildHeight()+i*16);
    }
    @ModifyVariable(method="setBlockState",at=@At("HEAD"),argsOnly=true)
    private BlockState blockgen_ban$map(BlockState state){return level.isClientSide?state:GlobalWrites.mapWorld(state);}
}
