package dev.flower.blockgenban.client;

import dev.flower.blockgenban.RuleNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class RuleScreen extends Screen {
    private static final ResourceLocation CRAFTING = ResourceLocation.parse("textures/gui/container/crafting_table.png");
    private static final ResourceLocation FURNACE = ResourceLocation.parse("textures/gui/container/furnace.png");
    private static final ResourceLocation TABS = ResourceLocation.parse("textures/gui/container/creative_inventory/tabs.png");
    private final Screen parent;
    private final BlockCatalog catalog;
    private final Map<ResourceLocation, ResourceLocation> baseline, draft;
    private EditBox leftSearch, rightSearch;
    private Button saveBtn, cancelBtn, prevPage, nextPage, prevTab, nextTab;
    private List<Block> sources, targets;
    private List<Component> hover = List.of();
    private Block selectedSource;
    private int x, y, panelW, panelH, leftW, rightX, rightW, listY, rows, slotX, scrollX;
    private int gridX, gridY, cols, gridRows, footerY, tabY, tabSlots;
    private int offset, page, category, tabPage;
    private boolean pending, dragging;
    private double dragOffset;
    private String status = text("screen.blockgen_ban.restart_required");
    private int statusColor = 0x404040;

    public RuleScreen(Screen parent, Map<ResourceLocation, ResourceLocation> initial) {
        super(Component.translatable("screen.blockgen_ban.title"));
        this.parent = parent;
        baseline = new LinkedHashMap<>(initial);
        draft = new LinkedHashMap<>(initial);
        catalog = new BlockCatalog();
        sources = catalog.sources;
        targets = catalog.targets("", 0);
    }

    @Override
    protected void init() {
        String lq = leftSearch == null ? "" : leftSearch.getValue();
        String rq = rightSearch == null ? "" : rightSearch.getValue();
        panelW = Math.min(460, width - 12); panelH = Math.min(258, height - 16);
        x = (width - panelW) / 2; y = (height - panelH) / 2;
        leftW = (panelW - 16) / 3;
        rightX = x + 8 + leftW + 8; rightW = panelW - leftW - 24;
        listY = y + 44; rows = Math.max(1, (panelH - 70) / 20);
        scrollX = x + 8 + leftW - 5; slotX = scrollX - 22;
        gridX = rightX; tabY = y + 42; gridY = y + 68;
        cols = Math.max(3, rightW / 18); gridRows = Math.max(1, (panelH - 98) / 18);
        footerY = y + panelH - 24;
        tabSlots = Math.max(1, (rightW - 32) / 20);
        leftSearch = box(x + 8, y + 22, leftW, text("screen.blockgen_ban.source_search"), lq);
        rightSearch = box(rightX, y + 22, rightW, text("screen.blockgen_ban.target_search"), rq);
        leftSearch.setResponder(q -> { offset = 0; filterSources(); });
        rightSearch.setResponder(q -> { page = 0; filterTargets(); });
        prevTab = button(rightX, tabY + 3, 14, "‹", text("screen.blockgen_ban.previous_categories"), () -> { tabPage--; updateControls(); });
        nextTab = button(rightX + rightW - 14, tabY + 3, 14, "›", text("screen.blockgen_ban.next_categories"), () -> { tabPage++; updateControls(); });
        prevPage = button(rightX, footerY, 18, "‹", text("screen.blockgen_ban.previous_page"), () -> { page--; updateControls(); });
        nextPage = button(rightX + 76, footerY, 18, "›", text("screen.blockgen_ban.next_page"), () -> { page++; updateControls(); });
        saveBtn = button(x + panelW - 46, footerY, 18, "✓", text("screen.blockgen_ban.save"), this::save);
        cancelBtn = button(x + panelW - 26, footerY, 18, "×", text("screen.blockgen_ban.cancel"), this::onClose);
        filterSources(); filterTargets();
    }
    private EditBox box(int bx, int by, int bw, String hint, String value) {
        EditBox box = new EditBox(font, bx, by, bw, 16, Component.literal(hint));
        box.setMaxLength(128); box.setHint(Component.literal(hint)); box.setValue(value);
        return addRenderableWidget(box);
    }
    private Button button(int bx, int by, int bw, String label, String tip, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(bx, by, bw, 18).tooltip(Tooltip.create(Component.literal(tip))).build());
    }
    private void filterSources() {
        sources = catalog.sources.stream()
                .filter(b -> BlockCatalog.matches(b, leftSearch.getValue()))
                .sorted(Comparator.comparing((Block block) -> !draft.containsKey(BlockCatalog.id(block)))
                        .thenComparing(block -> BlockCatalog.id(block).toString()))
                .toList();
        offset = Math.max(0, Math.min(offset, maxOffset())); updateControls();
    }
    private void filterTargets() {
        targets = catalog.targets(rightSearch.getValue(), category);
        page = Math.max(0, Math.min(page, maxPage())); updateControls();
    }
    private int maxOffset() { return Math.max(0, sources.size() - rows); }
    private int capacity() { return cols * gridRows - 2; }
    private int maxPage() { return Math.max(0, (targets.size() - 1) / capacity()); }
    private boolean dirty() { return !draft.equals(baseline); }
    private void updateControls() {
        if (saveBtn == null) return;
        boolean choosing = selectedSource != null;
        leftSearch.active = !pending; rightSearch.active = choosing && !pending;
        saveBtn.visible = cancelBtn.visible = dirty();
        saveBtn.active = cancelBtn.active = !pending;
        prevPage.visible = nextPage.visible = choosing;
        prevPage.active = !pending && page > 0; nextPage.active = !pending && page < maxPage();
        prevTab.visible = nextTab.visible = choosing && catalog.categories.size() > tabSlots;
        prevTab.active = !pending && tabPage > 0;
        nextTab.active = !pending && (tabPage + 1) * tabSlots < catalog.categories.size();
    }
    private void save() {
        if (pending || !dirty()) return;
        pending = true; status = text("screen.blockgen_ban.saving"); statusColor = 0x404040; updateControls();
        RuleNetwork.save(draft);
    }
    public void acceptSaved(Map<ResourceLocation, ResourceLocation> values, Component error) {
        pending = false;
        if (!error.getString().isEmpty()) { status = error.getString(); statusColor = 0xA02020; }
        else {
            baseline.clear(); baseline.putAll(values); draft.clear(); draft.putAll(values);
            status = text("screen.blockgen_ban.saved"); statusColor = 0x27622A;
        }
        updateControls();
    }
    @Override public void onClose() { if (!pending) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
    private Block targetAt(int slot) {
        if (slot == 0) return Blocks.AIR;
        if (slot == 1) return Blocks.STONE;
        int index = page * capacity() + slot - 2;
        return index < targets.size() ? targets.get(index) : null;
    }
    private boolean inside(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (pending) return true;
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && inside(mx, my, scrollX, listY, 5, rows * 20) && maxOffset() > 0) {
            dragging = true;
            dragOffset = inside(mx, my, scrollX, thumbY(), 5, thumbH()) ? my - thumbY() : thumbH() / 2.0;
            dragScroll(my); return true;
        }
        if (inside(mx, my, slotX, listY, 18, rows * 20)) {
            int index = offset + (int)(my - listY) / 20;
            if (index >= sources.size() || (int)(my - listY) % 20 >= 18) return true;
            Block source = sources.get(index);
            if (button == 1) { draft.remove(BlockCatalog.id(source)); status = ""; filterSources(); }
            else if (button == 0) { selectedSource = source; updateControls(); }
            return true;
        }
        if (button == 0 && selectedSource != null) {
            int tabStart = rightX + 16;
            if (inside(mx, my, tabStart, tabY, tabSlots * 20, 22)) {
                int index = tabPage * tabSlots + (int)(mx - tabStart) / 20;
                if (index < catalog.categories.size()) { category = index; page = 0; filterTargets(); }
                return true;
            }
            if (inside(mx, my, gridX, gridY, cols * 18, gridRows * 18)) {
                Block target = targetAt((int)(my - gridY) / 18 * cols + (int)(mx - gridX) / 18);
                if (target != null) {
                    ResourceLocation sourceId = BlockCatalog.id(selectedSource), targetId = BlockCatalog.id(target);
                    if (sourceId.equals(targetId)) draft.remove(sourceId); else draft.put(sourceId, targetId);
                    status = ""; filterSources(); updateControls();
                }
                return true;
            }
        }
        return false;
    }
    private int thumbH() { return Math.min(rows * 20, Math.max(16, rows * 20 * rows / Math.max(1, sources.size()))); }
    private int thumbY() { return listY + (rows * 20 - thumbH()) * offset / Math.max(1, maxOffset()); }
    private void dragScroll(double my) {
        int travel = rows * 20 - thumbH();
        if (travel > 0) offset = Math.max(0, Math.min(maxOffset(), (int)Math.round((my - listY - dragOffset) * maxOffset() / travel)));
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && !pending) { dragScroll(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        dragging = false; return super.mouseReleased(mx, my, button);
    }
    @Override public boolean mouseScrolled(double mx, double my, double deltaX, double delta) {
        if (pending) return true;
        if (inside(mx, my, x + 8, listY, leftW, rows * 20)) {
            offset = Math.max(0, Math.min(maxOffset(), offset - (int)Math.signum(delta) * 3)); return true;
        }
        if (selectedSource != null && inside(mx, my, gridX, gridY, cols * 18, gridRows * 18)) {
            page = Math.max(0, Math.min(maxPage(), page - (int)Math.signum(delta))); updateControls(); return true;
        }
        return super.mouseScrolled(mx, my, deltaX, delta);
    }
    // 1.21 的 Screen.render 会再次调用背景；这里只在面板之前绘制一次。
    // Screen.render calls the background in 1.21; draw it once before the custom panel.
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        hover = List.of(); super.renderBackground(g, mx, my, partial); panel(g);
        g.drawString(font, title, x + 8, y + 7, 0x404040, false);
        if (!status.isEmpty()) {
            String message = font.plainSubstrByWidth(status, Math.max(0, panelW - font.width(title) - 28));
            g.drawString(font, message, x + panelW - 8 - font.width(message), y + 7, statusColor, false);
        }
        renderSources(g, mx, my);
        if (selectedSource == null) {
            g.drawCenteredString(font, Component.translatable("screen.blockgen_ban.choose_source"), rightX + rightW / 2, y + panelH / 2, 0x777777);
        } else { renderTabs(g, mx, my); renderTargets(g, mx, my); }
        String count = Component.translatable("screen.blockgen_ban.block_count", sources.size()).getString();
        g.drawString(font, count, x + 8, footerY + 5, 0x606060, false);
        if (selectedSource != null) g.drawCenteredString(font, (page + 1) + "/" + (maxPage() + 1), rightX + 47, footerY + 5, 0x404040);
        super.render(g, mx, my, partial);
        if (!hover.isEmpty()) g.renderTooltip(font, hover, Optional.empty(), mx, my);
    }
    private void panel(GuiGraphics g) {
        g.fill(x, y, x + panelW, y + panelH, 0xFF000000);
        g.fill(x + 1, y + 1, x + panelW - 1, y + panelH - 1, 0xFF555555);
        g.fill(x + 1, y + 1, x + panelW - 2, y + 3, 0xFFFFFFFF);
        g.fill(x + 1, y + 3, x + 3, y + panelH - 2, 0xFFFFFFFF);
        g.fill(x + 3, y + 3, x + panelW - 3, y + panelH - 3, 0xFFC6C6C6);
        g.fill(rightX - 5, y + 22, rightX - 4, footerY + 18, 0xFF8B8B8B);
        g.fill(rightX - 4, y + 22, rightX - 3, footerY + 18, 0xFFFFFFFF);
    }
    private void slot(GuiGraphics g, int sx, int sy) { g.blit(CRAFTING, sx, sy, 29, 16, 18, 18); }
    private void renderSources(GuiGraphics g, int mx, int my) {
        g.enableScissor(x + 8, listY, scrollX - 1, listY + rows * 20);
        for (int row = 0; row < rows && row + offset < sources.size(); row++) {
            Block block = sources.get(row + offset); int sy = listY + row * 20;
            if (block == selectedSource) g.fill(x + 8, sy, scrollX - 1, sy + 18, 0x55FFFFFF);
            icon(g, block, x + 8, sy + 1);
            int arrowX = slotX - 14;
            String name = font.plainSubstrByWidth(block.getName().getString(), Math.max(0, arrowX - x - 28));
            g.drawString(font, name, x + 26, sy + 5, 0x404040, false);
            g.blit(FURNACE, arrowX, sy + 4, 12, 9, 176f, 14f, 24, 17, 256, 256);
            slot(g, slotX, sy);
            ResourceLocation replacement = draft.get(BlockCatalog.id(block));
            if (replacement != null) icon(g, BuiltInRegistries.BLOCK.get(replacement), slotX + 1, sy + 1);
            if (block == selectedSource) g.renderOutline(slotX, sy, 18, 18, 0xFFE9D36A);
            if (inside(mx, my, x + 8, sy, leftW - 7, 18)) {
                if (mx >= slotX) {
                    ArrayList<Component> tip = new ArrayList<>();
                    tip.add(Component.translatable("screen.blockgen_ban.replacement").append(replacement == null ? Component.translatable("screen.blockgen_ban.unset") : BuiltInRegistries.BLOCK.get(replacement).getName()));
                    if (replacement != null) tip.add(gray(replacement.toString()));
                    tip.add(gray(text("screen.blockgen_ban.slot_controls"))); hover = tip;
                } else hover = tooltip(block);
            }
        }
        g.disableScissor();
        if (sources.isEmpty()) g.drawString(font, text("screen.blockgen_ban.no_sources"), x + 10, listY + 6, 0x777777, false);
        g.fill(scrollX, listY, scrollX + 5, listY + rows * 20, 0xFF8B8B8B);
        if (maxOffset() > 0) {
            g.fill(scrollX, thumbY(), scrollX + 5, thumbY() + thumbH(), 0xFF555555);
            g.fill(scrollX + 1, thumbY() + 1, scrollX + 4, thumbY() + thumbH() - 1, 0xFFC6C6C6);
        }
    }
    private void renderTabs(GuiGraphics g, int mx, int my) {
        for (int index = 0; index < tabSlots; index++) {
            int cat = tabPage * tabSlots + index;
            if (cat >= catalog.categories.size()) break;
            int tx = rightX + 16 + index * 20;
            g.blit(TABS, tx, tabY, 20, 22, 0f, cat == category ? 32f : 0f, 26, 32, 256, 256);
            g.renderItem(catalog.categories.get(cat).icon(), tx + 2, tabY + 3);
            if (inside(mx, my, tx, tabY, 20, 22)) hover = List.of(catalog.categories.get(cat).name());
        }
    }
    private void renderTargets(GuiGraphics g, int mx, int my) {
        for (int index = 0; index < cols * gridRows; index++) {
            int sx = gridX + index % cols * 18, sy = gridY + index / cols * 18;
            slot(g, sx, sy); Block target = targetAt(index);
            if (target == null) continue;
            icon(g, target, sx + 1, sy + 1);
            if (BlockCatalog.id(target).equals(draft.get(BlockCatalog.id(selectedSource)))) g.renderOutline(sx, sy, 18, 18, 0xFFE9D36A);
            if (inside(mx, my, sx, sy, 18, 18)) {
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x55FFFFFF); hover = tooltip(target);
            }
        }
        if (targets.isEmpty()) g.drawString(font, text("screen.blockgen_ban.no_targets"), gridX + 2, gridY + 22, 0x777777, false);
    }
    private void icon(GuiGraphics g, Block block, int ix, int iy) {
        if (block == Blocks.AIR) {
            g.renderOutline(ix + 3, iy + 3, 10, 10, 0xFFBBBBBB);
            for (int n = 0; n < 6; n++) { g.fill(ix + 5 + n, iy + 5 + n, ix + 6 + n, iy + 6 + n, 0xFFE7E7E7); }
            return;
        }
        ItemStack stack = new ItemStack(block.asItem());
        if (block == Blocks.WATER) stack = new ItemStack(Items.WATER_BUCKET);
        if (block == Blocks.LAVA) stack = new ItemStack(Items.LAVA_BUCKET);
        if (!stack.isEmpty()) g.renderItem(stack, ix, iy);
        else g.blit(ix, iy, 0, 16, 16, minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(block.defaultBlockState()));
    }
    private List<Component> tooltip(Block block) { return List.of(block.getName(), gray(BlockCatalog.id(block).toString())); }
    private static String text(String key) { return Component.translatable(key).getString(); }
    private Component gray(String text) { return Component.literal(text).withStyle(style -> style.withColor(0xAAAAAA)); }
}
