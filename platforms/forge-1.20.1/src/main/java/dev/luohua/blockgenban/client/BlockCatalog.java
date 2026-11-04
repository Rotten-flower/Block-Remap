package dev.luohua.blockgenban.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

final class BlockCatalog {
    record Category(Component name, ItemStack icon, Set<Block> blocks) {}
    final List<Block> all;
    final List<Block> sources;
    final List<Category> categories = new ArrayList<>();
    BlockCatalog() {
        all = ForgeRegistries.BLOCKS.getValues().stream().sorted(Comparator.comparing(b -> id(b).toString())).toList();
        sources = all.stream().filter(b -> !b.defaultBlockState().isAir()).toList();
        categories.add(new Category(Component.translatable("screen.blockgen_ban.all_blocks"), new ItemStack(Items.COMPASS), Set.of()));
        Minecraft mc = Minecraft.getInstance();
        CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), mc.player.hasPermissions(2), mc.level.registryAccess());
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            Set<Block> blocks = new HashSet<>();
            for (ItemStack stack : tab.getDisplayItems()) if (stack.getItem() instanceof BlockItem item) blocks.add(item.getBlock());
            if (!blocks.isEmpty()) categories.add(new Category(tab.getDisplayName(), tab.getIconItem().copy(), Set.copyOf(blocks)));
        }
    }
    static ResourceLocation id(Block block) { return ForgeRegistries.BLOCKS.getKey(block); }
    static boolean matches(Block block, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        return id(block).toString().contains(q) || block.getName().getString().toLowerCase(Locale.ROOT).contains(q);
    }
    List<Block> targets(String query, int category) {
        Set<Block> allowed = categories.get(category).blocks();
        return all.stream().filter(b -> b != Blocks.AIR && b != Blocks.STONE)
                .filter(b -> category == 0 || allowed.contains(b)).filter(b -> matches(b, query)).toList();
    }
}
