package dev.flower.blockgenban;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Set;

/** Registry names are resolved at generator entry, not by visiting generated blocks. */
public record GenerationBans(Set<ResourceLocation> structures, Set<ResourceLocation> features) {
    public static final GenerationBans EMPTY=new GenerationBans(Set.of(),Set.of());
    public static volatile GenerationBans current=EMPTY;
    public GenerationBans { structures=Set.copyOf(structures);features=Set.copyOf(features); }
    public boolean structure(RegistryAccess access, Structure value) {
        return !structures.isEmpty() && contains(structures,access.registryOrThrow(Registries.STRUCTURE).getKey(value));
    }
    public boolean configured(RegistryAccess access, ConfiguredFeature<?,?> value) {
        return !features.isEmpty() && (contains(features,access.registryOrThrow(Registries.CONFIGURED_FEATURE).getKey(value))
                || contains(features,ForgeRegistries.FEATURES.getKey(value.feature())));
    }
    public boolean placed(RegistryAccess access, PlacedFeature value) {
        return !features.isEmpty() && contains(features,access.registryOrThrow(Registries.PLACED_FEATURE).getKey(value));
    }
    private static boolean contains(Set<ResourceLocation> values,ResourceLocation id) { return id!=null && values.contains(id); }
}
