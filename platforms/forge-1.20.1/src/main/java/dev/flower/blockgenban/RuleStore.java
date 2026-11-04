package dev.flower.blockgenban;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.LinkedHashSet;

public class RuleStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("blockgen_ban.json");
    public record Settings(Map<ResourceLocation,ResourceLocation> rules, GenerationBans bans, ReplacementMode replacementMode) {}

    public static Map<ResourceLocation, ResourceLocation> load() throws IOException {
        return loadSettings().rules();
    }
    public static Settings loadSettings() throws IOException {
        if (!Files.exists(CONFIG_PATH)) {
            Map<ResourceLocation, ResourceLocation> empty = new LinkedHashMap<>();
            save(empty,GenerationBans.EMPTY,ReplacementMode.GENERATION_ONLY);
            return new Settings(empty,GenerationBans.EMPTY,ReplacementMode.GENERATION_ONLY);
        }

        try {
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            JsonElement rootElement = JsonParser.parseString(json);
            if (!rootElement.isJsonObject()) {
                throw new IOException("Configuration root must be a JSON object");
            }
            
            JsonObject root = rootElement.getAsJsonObject();
            if (!root.has("rules") || !root.get("rules").isJsonObject()) {
                throw new IOException("Configuration must contain a 'rules' object");
            }

            Map<ResourceLocation, ResourceLocation> rules = new LinkedHashMap<>();
            JsonObject rulesObject = root.getAsJsonObject("rules");
            
            for (Map.Entry<String, JsonElement> entry : rulesObject.entrySet()) {
                ResourceLocation source = ResourceLocation.tryParse(entry.getKey());
                if (source == null) {
                    throw new IOException("Invalid resource location syntax: " + entry.getKey());
                }

                if (!ForgeRegistries.BLOCKS.containsKey(source)) {
                    BlockgenBan.LOGGER.warn("Skipping rule for missing source block: {}", source);
                    continue;
                }

                Block sourceBlock = ForgeRegistries.BLOCKS.getValue(source);
                if (sourceBlock == null || sourceBlock.defaultBlockState().isAir()) {
                    throw new IOException("Source block cannot be air-like: " + source);
                }

                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                    throw new IOException("Target for " + source + " must be a string");
                }

                String targetString = entry.getValue().getAsString();
                ResourceLocation target = ResourceLocation.tryParse(targetString);
                if (target == null) {
                    throw new IOException("Invalid target resource location syntax: " + targetString);
                }

                if (!ForgeRegistries.BLOCKS.containsKey(target)) {
                    BlockgenBan.LOGGER.warn("Skipping rule with missing target block: {}", target);
                    continue;
                }

                if (!source.equals(target)) {
                    rules.put(source, target);
                }
            }

            ReplacementMode mode=ReplacementMode.GENERATION_ONLY;
            if(root.has("replacementMode")){
                if(!root.get("replacementMode").isJsonPrimitive() || !root.get("replacementMode").getAsJsonPrimitive().isString())
                    throw new IOException("replacementMode must be a string: generation_only or global");
                mode=ReplacementMode.parse(root.get("replacementMode").getAsString());
            }
            return new Settings(rules,new GenerationBans(readIds(root,"disabledStructures"),readIds(root,"disabledFeatures")),mode);
        } catch (JsonParseException e) {
            throw new IOException("Malformed blockgen_ban.json configuration: " + e.getMessage(), e);
        }
    }

    public static void save(Map<ResourceLocation, ResourceLocation> rules) throws IOException {
        save(rules,GenerationBans.current,BlockgenBan.replacementMode());
    }
    private static void save(Map<ResourceLocation, ResourceLocation> rules,GenerationBans bans,ReplacementMode mode) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());

        Map<ResourceLocation, ResourceLocation> sorted = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        sorted.putAll(rules);

        JsonObject rulesObject = new JsonObject();
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : sorted.entrySet()) {
            rulesObject.addProperty(entry.getKey().toString(), entry.getValue().toString());
        }

        JsonObject root = new JsonObject();
        root.addProperty("replacementMode",mode.id());
        root.add("rules", rulesObject);
        root.add("disabledStructures",ids(bans.structures()));
        root.add("disabledFeatures",ids(bans.features()));

        String json = GSON.toJson(root);
        Path tempPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
        Files.writeString(tempPath, json, StandardCharsets.UTF_8);
        Files.move(tempPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
    }
    private static Set<ResourceLocation> readIds(JsonObject root,String key) throws IOException {
        if(!root.has(key))return Set.of();
        if(!root.get(key).isJsonArray())throw new IOException(key+" must be an array");
        Set<ResourceLocation> values=new LinkedHashSet<>();
        for(JsonElement entry:root.getAsJsonArray(key)){
            if(!entry.isJsonPrimitive()||!entry.getAsJsonPrimitive().isString())throw new IOException(key+" must contain registry IDs");
            ResourceLocation id=ResourceLocation.tryParse(entry.getAsString());
            if(id==null)throw new IOException("Invalid registry ID: "+entry);
            values.add(id);
        }
        return Set.copyOf(values);
    }
    private static JsonArray ids(Set<ResourceLocation> values) {
        JsonArray array=new JsonArray();values.stream().map(ResourceLocation::toString).sorted().forEach(array::add);return array;
    }
}
