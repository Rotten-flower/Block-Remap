package dev.luohua.blockgenban;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Mod(BlockgenBan.MODID)
public class BlockgenBan {
    public static final String MODID = "blockgen_ban";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile GenerationRules rules = GenerationRules.EMPTY;
    private static volatile ReplacementMode replacementMode = ReplacementMode.GENERATION_ONLY;
    private static volatile Map<ResourceLocation, ResourceLocation> definitions = Collections.emptyMap();

    public BlockgenBan(net.neoforged.bus.api.IEventBus modBus) {
        RuleNetwork.init(modBus);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) -> RuleNetwork.registerCommands(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            RuleStore.Settings settings=RuleStore.loadSettings();
            Map<ResourceLocation, ResourceLocation> loaded = validateAndClean(settings.rules());
            replacementMode=settings.replacementMode();
            GenerationBans.current=settings.bans();
            definitions = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
            publish(compileRules(loaded));
            LOGGER.info("Loaded {} block replacement rules in {} mode", loaded.size(), replacementMode.id());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load blockgen_ban configuration", e);
        }
    }

    private void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            RuleWorldData data = RuleWorldData.get(server);
            data.replace(definitions);
            data.setDirty();
            server.overworld().getDataStorage().save();
            LOGGER.info("Saved current block generation rules to world data");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save rules to world data", e);
        }
    }

    public static GenerationRules getRules() {
        return rules;
    }
    public static ReplacementMode replacementMode() { return replacementMode; }
    private void onServerStopped(ServerStoppedEvent event){
        publish(GenerationRules.EMPTY);
        replacementMode=ReplacementMode.GENERATION_ONLY;
    }
    private static void publish(GenerationRules next){
        rules.replacements().keySet().forEach(block->((GlobalWrites.BlockTarget)block).blockgen_ban$target(null));
        next.replacements().forEach((block,state)->((GlobalWrites.BlockTarget)block).blockgen_ban$target(state));
        rules=next;
    }

    public static Map<ResourceLocation, ResourceLocation> definitions() {
        return definitions;
    }

    public static void saveRules(MinecraftServer server, Map<ResourceLocation, ResourceLocation> newRules) throws IOException {
        if (!server.isSameThread()) {
            throw new IllegalStateException("saveRules must be called on server thread");
        }

        Map<ResourceLocation, ResourceLocation> validated = validateAndClean(newRules);

        RuleStore.save(validated);
        LOGGER.info("Saved {} block generation rules to global config", validated.size());

        RuleWorldData data = RuleWorldData.get(server);
        data.replace(validated);
        data.setDirty();
        server.overworld().getDataStorage().save();

        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(validated));
        LOGGER.info("Saved global replacement settings; re-enter the world or restart server to apply");
    }

    static GenerationRules compileRules(Map<ResourceLocation, ResourceLocation> definitions) {
        Map<Block, net.minecraft.world.level.block.state.BlockState> compiled = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : definitions.entrySet()) {
            Block sourceBlock = BuiltInRegistries.BLOCK.get(entry.getKey());
            Block targetBlock = BuiltInRegistries.BLOCK.get(entry.getValue());
            if (sourceBlock != null && targetBlock != null && !sourceBlock.defaultBlockState().isAir()) {
                compiled.put(sourceBlock, targetBlock.defaultBlockState());
            }
        }
        return new GenerationRules(compiled);
    }

    private static Map<ResourceLocation, ResourceLocation> validateAndClean(Map<ResourceLocation, ResourceLocation> input) {
        Map<ResourceLocation, ResourceLocation> cleaned = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : input.entrySet()) {
            ResourceLocation source = entry.getKey();
            ResourceLocation target = entry.getValue();

            if (source == null || target == null) {
                throw new IllegalArgumentException("Invalid rule: null ResourceLocation");
            }

            if (!BuiltInRegistries.BLOCK.containsKey(source)) {
                throw new IllegalArgumentException("Unknown source block: " + source);
            }

            Block sourceBlock = BuiltInRegistries.BLOCK.get(source);
            if (sourceBlock == null || sourceBlock.defaultBlockState().isAir()) {
                throw new IllegalArgumentException("Source block cannot be air-like: " + source);
            }

            if (!BuiltInRegistries.BLOCK.containsKey(target)) {
                throw new IllegalArgumentException("Unknown target block: " + target);
            }

            if (source.equals(target)) {
                continue; // Skip self-mappings
            }

            cleaned.put(source, target);
        }
        Map<ResourceLocation,ResourceLocation> terminal=new LinkedHashMap<>();
        for(var source:cleaned.keySet()){
            java.util.Set<ResourceLocation> visited=new java.util.HashSet<>();
            ResourceLocation target=source;
            while(cleaned.containsKey(target)){
                if(!visited.add(target))throw new IllegalArgumentException("Cycle in replacement rules: "+source+" → "+target);
                target=cleaned.get(target);
            }
            terminal.put(source,target);
        }
        return terminal;
    }
}
