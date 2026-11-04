package dev.flower.blockgenban;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RuleWorldData extends SavedData {
    private Map<ResourceLocation, ResourceLocation> rules = Map.of();
    public static RuleWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                RuleWorldData::load, RuleWorldData::new, "blockgen_ban");
    }
    public void replace(Map<ResourceLocation, ResourceLocation> definitions) {
        rules = Map.copyOf(definitions);
        setDirty();
    }
    public Map<ResourceLocation, ResourceLocation> getRules() { return rules; }
    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag entries = new CompoundTag();
        rules.forEach((source, target) -> entries.putString(source.toString(), target.toString()));
        tag.put("Rules", entries);
        return tag;
    }
    private static RuleWorldData load(CompoundTag tag) {
        RuleWorldData data = new RuleWorldData();
        CompoundTag entries = tag.getCompound("Rules");
        Map<ResourceLocation, ResourceLocation> result = new LinkedHashMap<>();
        for (String key : entries.getAllKeys()) {
            ResourceLocation source = ResourceLocation.tryParse(key);
            ResourceLocation target = ResourceLocation.tryParse(entries.getString(key));
            if (source != null && target != null) result.put(source, target);
        }
        data.rules = Map.copyOf(result);
        return data;
    }
}
