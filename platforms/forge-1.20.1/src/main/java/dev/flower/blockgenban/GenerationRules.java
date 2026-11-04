package dev.flower.blockgenban;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/** Immutable terminal mappings, published when a server world starts. */
public record GenerationRules(Map<Block, BlockState> replacements) {
    public static final GenerationRules EMPTY = new GenerationRules(Map.of());
    public GenerationRules { replacements = Map.copyOf(replacements); }
    public boolean isActive() { return !replacements.isEmpty(); }
    public BlockState filter(BlockState state) { return replacements.getOrDefault(state.getBlock(), state); }

}
