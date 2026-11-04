package dev.flower.blockgenban.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.flower.blockgenban.GlobalWrites;
import dev.flower.blockgenban.BlockgenBan;
import dev.flower.blockgenban.ReplacementMode;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @ModifyExpressionValue(method="place",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState blockgen_ban$map(BlockState state,BlockPlaceContext context){
        return state==null || context.getLevel().isClientSide || BlockgenBan.replacementMode()!=ReplacementMode.GLOBAL?state:GlobalWrites.map(state);
    }
}
