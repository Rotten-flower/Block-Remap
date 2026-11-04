package dev.luohua.blockgenban;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class GenerationBanCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> branch(String name, boolean structure) {
        var root=Commands.literal(name);
        for(boolean disable:new boolean[]{true,false}) {
            root.then(Commands.literal(disable?"ban":"allow").then(Commands.argument("id",ResourceLocationArgument.id())
                    .suggests((context,builder)->SharedSuggestionProvider.suggestResource(ids(context.getSource(),structure),builder))
                    .executes(context->change(context.getSource(),structure,disable,ResourceLocationArgument.getId(context,"id")))));
        }
        return root.then(Commands.literal("list").executes(context->{
            var values=structure?GenerationBans.current.structures():GenerationBans.current.features();
            // Commands also serve clients without the mod. / 命令也供未安装模组的客户端使用。
            context.getSource().sendSuccess(()->Component.translatableWithFallback("message.blockgen_ban.disabled","Disabled: %s",values.stream().map(Object::toString).sorted().toList().toString()),false);return values.size();
        }));
    }
    private static Collection<ResourceLocation> ids(CommandSourceStack source,boolean structure) {
        var access=source.registryAccess();
        if(structure)return access.registryOrThrow(Registries.STRUCTURE).keySet();
        Set<ResourceLocation> ids=new HashSet<>(access.registryOrThrow(Registries.PLACED_FEATURE).keySet());
        ids.addAll(access.registryOrThrow(Registries.CONFIGURED_FEATURE).keySet());ids.addAll(ForgeRegistries.FEATURES.getKeys());return ids;
    }
    private static int change(CommandSourceStack source,boolean structure,boolean disable,ResourceLocation id) {
        if(disable&&!ids(source,structure).contains(id)){source.sendFailure(Component.translatableWithFallback("message.blockgen_ban.unknown_generator","Unknown worldgen registry ID: %s",id.toString()));return 0;}
        GenerationBans old=GenerationBans.current;
        Set<ResourceLocation> structures=new HashSet<>(old.structures()),features=new HashSet<>(old.features());
        Set<ResourceLocation> values=structure?structures:features;
        if(disable)values.add(id);else values.remove(id);
        try {
            GenerationBans.current=new GenerationBans(structures,features);
            RuleStore.save(BlockgenBan.definitions());
            source.sendSuccess(()->Component.translatableWithFallback(disable?"message.blockgen_ban.banned":"message.blockgen_ban.allowed",disable?"Generation disabled: %s":"Generation enabled: %s",id.toString()),true);return 1;
        }catch(java.io.IOException failure){GenerationBans.current=old;source.sendFailure(Component.translatableWithFallback("message.blockgen_ban.save_failed","Save failed: %s",failure.getMessage()));return 0;}
    }
    private GenerationBanCommands(){}
}
