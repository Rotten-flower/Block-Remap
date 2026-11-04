package dev.flower.blockgenban.client;

import com.google.gson.GsonBuilder;
import dev.flower.blockgenban.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real client input/packet/world acceptance; never in production JAR. */
@EventBusSubscriber(modid = BlockgenBan.MODID, value = Dist.CLIENT)
public final class UiAcceptance {
    private static boolean requested, done;
    private static int tick, step, wait;
    private static long started = System.currentTimeMillis();
    private static RuleScreen screen;
    private static final Map<String, Object> results = new LinkedHashMap<>();
    private static final ResourceLocation DIAMOND = ResourceLocation.parse("minecraft:diamond_ore");
    private static final Path OUT = Path.of(System.getProperty("blockgenban.ui.output", "../../work/ui/validation"));
    private static CompletableFuture<Boolean> serverCheck;
    private static String pendingShot;
    private static double pointerX = 4, pointerY = 4;
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            Files.createDirectories(OUT);
            if (System.currentTimeMillis() - started > 300_000) throw new AssertionError("Client acceptance timed out at step " + step);
            if (!requested) {
                if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)) return;
                requested = true;
                RuleStore.save(Map.of()); // This development run uses its own isolated configuration.
                mc.options.pauseOnLostFocus = false; mc.options.guiScale().set(2);
                mc.options.renderDistance().set(2); mc.options.simulationDistance().set(5);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.options.framerateLimit().set(60);
                LevelSettings settings = new LevelSettings("Block generation UI acceptance", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, false, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("ui-acceptance-" + System.currentTimeMillis(), settings,
                        new WorldOptions(12345, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            Method move = mc.mouseHandler.getClass().getDeclaredMethod("onMove", long.class, double.class, double.class);
            move.setAccessible(true);
            move.invoke(mc.mouseHandler, mc.getWindow().getWindow(), pointerX, pointerY);
            if (pendingShot != null) {
                Screenshot.grab(mc.gameDirectory,pendingShot,mc.getMainRenderTarget(),message->{});
                pendingShot=null;
            }
            if (++tick < 40 || ++wait < 12) return;
            wait = 0;
            switch (step) {
                case 0 -> { mc.setScreen(null); ClientUi.OPEN.setDown(true); net.minecraft.client.KeyMapping.click(ClientUi.OPEN.getKey()); step++; }
                case 1 -> {
                    ClientUi.OPEN.setDown(false);
                    if (!(mc.screen instanceof RuleScreen)) { wait = 0; return; }
                    screen = (RuleScreen)mc.screen;
                    check(get("selectedSource") == null, "Chooser must begin empty");
                    check(!((EditBox)get("rightSearch")).active, "Target search active before selection");
                    shot("01-empty.png"); results.put("f9EntryAndEmptyState", true); step++;
                }
                case 2 -> {
                    EditBox input = (EditBox)get("leftSearch");
                    screen.mouseClicked(input.getX()+3, input.getY()+3, 0);
                    for(char c : "diamond_ore".toCharArray()) screen.charTyped(c,0);
                    check(((List<?>)get("sources")).size() == 2, "Registry ID source search failed");
                    selectSource(Blocks.DIAMOND_ORE); step++;
                }
                case 3 -> {
                    check(((EditBox)get("rightSearch")).active, "Target chooser did not activate");
                    clickGrid(0);
                    check(draft().get(DIAMOND).equals(ResourceLocation.parse("minecraft:air")), "Air first slot failed");
                    clickGrid(1);
                    check(draft().get(DIAMOND).equals(ResourceLocation.parse("minecraft:stone")), "Stone second slot failed");
                    ((EditBox)get("leftSearch")).setValue("iron_ore");
                    selectSource(Blocks.IRON_ORE); clickGrid(0);
                    check(draft().get(ResourceLocation.parse("minecraft:iron_ore")).getPath().equals("air")
                            && draft().get(DIAMOND).getPath().equals("stone"), "Sources do not retain independent targets");
                    ((EditBox)get("leftSearch")).setValue("diamond_ore"); selectSource(Blocks.DIAMOND_ORE);
                    results.put("independentSourceTargets",true);
                    shot("02-draft.png"); results.put("sourceSearchAirStoneAndDraft", true); step++;
                }
                case 4 -> {
                    Button next = (Button)get("nextPage"); check(next.active, "Grid paging unavailable");
                    click(next); check(num("page") == 1, "Next page did not advance");
                    clickGrid(0); check(draft().get(DIAMOND).getPath().equals("air"), "Air pin missing on page2");
                    clickGrid(1);
                    click((Button)get("prevPage")); check(num("page") == 0, "Previous page failed");
                    int tx = num("rightX") + 16 + 20 + 2;
                    screen.mouseClicked(tx, num("tabY")+5, 0);
                    check(num("category") == 1, "Creative category click failed");
                    check(((List<?>)get("targets")).size() < 500, "Category didn't filter blocks");
                    Button nextTab = (Button)get("nextTab");
                    if(nextTab.visible && nextTab.active) { click(nextTab); check(num("tabPage") == 1, "Tab overflow paging failed"); click((Button)get("prevTab")); }
                    screen.mouseClicked(num("rightX")+18,num("tabY")+5,0);
                    results.put("gridAndCreativeCategoryPaging",true); step++;
                }
                case 5 -> {
                    EditBox input = (EditBox)get("rightSearch");
                    screen.mouseClicked(input.getX()+3,input.getY()+3,0);
                    for(char c : "chest".toCharArray()) screen.charTyped(c,0);
                    List<Block> targets = (List<Block>)get("targets");
                    check(targets.contains(Blocks.CHEST) && !targets.contains(Blocks.DIAMOND_BLOCK),"Target search failed");
                    int found=targets.indexOf(Blocks.CHEST); clickGrid(found+2);
                    check(draft().get(DIAMOND).getPath().equals("chest"),"Block entity target click failed");
                    input.setValue("no_such_block_zzzz");
                    check(((List<?>)get("targets")).isEmpty(),"Empty search has stale results");
                    clickGrid(2); check(draft().get(DIAMOND).getPath().equals("chest"),"Blank slot applied stale target");
                    input.setValue(""); clickGrid(1);
                    results.put("targetSearchAndEmptyResults",true); step++;
                }
                case 6 -> {
                    mc.options.guiScale().set(3); mc.resizeDisplay();
                    check(draft().get(DIAMOND).getPath().equals("stone"),"Resize lost draft");
                    check(num("panelW") <= screen.width - 10, "Small screen panel overflows");
                    check(((Button)get("saveBtn")).getWidth()==18,"Save button is too wide");
                    screen.resize(mc, 320, 240);
                    check(num("gridX") + num("cols") * 18 <= num("x") + num("panelW") - 8,"Small grid overflows");
                    Button nextTab = (Button)get("nextTab");
                    check(nextTab.visible && nextTab.active,"Overflow categories cannot be paged");
                    click(nextTab); check(num("tabPage") == 1,"Overflow category next failed");
                    click((Button)get("prevTab")); check(num("tabPage") == 0,"Overflow category previous failed");
                    results.put("overflowCategoryPagingAt320Width",true);
                    screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                    shot("03-small-scale.png"); results.put("resizeAndCompactLayout",true); step++;
                }
                case 7 -> {
                    screen.mouseClicked(num("slotX")+4,num("listY")+20*((List<Block>)get("sources")).indexOf(Blocks.DIAMOND_ORE)+4,1);
                    check(!draft().containsKey(DIAMOND),"Right click clear failed");
                    clickGrid(1); click((Button)get("cancelBtn"));
                    check(mc.screen == null,"Cancel didn't close");
                    check(!BlockgenBan.definitions().containsKey(DIAMOND),"Cancel saved draft");
                    results.put("clearAndCancel",true); RuleNetwork.request(); step++;
                }
                case 8 -> {
                    if(!(mc.screen instanceof RuleScreen)) return;
                    screen=(RuleScreen)mc.screen;
                    ((EditBox)get("leftSearch")).setValue("diamond_ore"); selectSource(Blocks.DIAMOND_ORE);
                    clickGrid(1); click((Button)get("saveBtn"));
                    check((boolean)get("pending"),"Save didn't wait for ACK"); step++;
                }
                case 9 -> {
                    if((boolean)get("pending")) return;
                    check(BlockgenBan.definitions().get(DIAMOND).getPath().equals("stone"),"C2S save didn't publish");
                    check(!BlockgenBan.getRules().isActive(),"Saving UI settings applied rules before world restart");
                    check(((String)get("status")).equals(net.minecraft.network.chat.Component.translatable("screen.blockgen_ban.saved").getString()),"Save confirmation omitted restart requirement");
                    check(!((Button)get("saveBtn")).visible,"Success did not clear dirty state");
                    serverCheck = new CompletableFuture<>();
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            var server=mc.getSingleplayerServer();
                            check(RuleStore.load().equals(BlockgenBan.definitions()),"Shared JSON mismatch");
                            check(RuleStore.loadSettings().replacementMode()==BlockgenBan.replacementMode(),"UI save erased replacement mode");
                            check(RuleWorldData.get(server).getRules().equals(BlockgenBan.definitions()),"SavedData mismatch");
                            check(Files.isRegularFile(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/blockgen_ban.dat")),"World NBT missing");
                            boolean cycleRejected=false;
                            try{BlockgenBan.saveRules(server,Map.of(DIAMOND,ResourceLocation.parse("minecraft:stone"),ResourceLocation.parse("minecraft:stone"),DIAMOND));}
                            catch(IllegalArgumentException expectedCycle){cycleRejected=expectedCycle.getMessage().contains("Cycle in replacement rules");}
                            check(cycleRejected&&BlockgenBan.definitions().get(DIAMOND).getPath().equals("stone"),"Cyclic global rules were not rejected without changing saved rules");
                            serverCheck.complete(true);
                        } catch(Throwable failure){serverCheck.completeExceptionally(failure);}
                    });
                    shot("04-saved.png"); step++;
                }
                case 10 -> {
                    if(!serverCheck.isDone())return;
                    serverCheck.join(); results.put("liveNetworkSaveGlobalJsonAndWorldNbt",true);
                    ((EditBox)get("leftSearch")).setValue("");
                    screen.mouseScrolled(num("x")+12,num("listY")+8,0,-4);
                    check(num("offset")>0,"Source wheel failed");
                    screen.mouseClicked(num("scrollX")+2,num("listY")+num("rows")*20-3,0);
                    screen.mouseDragged(num("scrollX")+2,num("listY")+num("rows")*20-5,0,0,10);
                    screen.mouseReleased(num("scrollX")+2,num("listY")+num("rows")*20-5,0);
                    check(num("offset")>100,"Scrollbar drag failed");
                    ((EditBox)get("leftSearch")).setValue(Blocks.DIAMOND_ORE.getName().getString());
                    check(!((List<?>)get("sources")).isEmpty(),"Localized source search failed");
                    results.put("sourceScrollDragAndChineseSearch",true);
                    screen.onClose(); RuleNetwork.request(); step++;
                }
                case 11 -> {
                    if(!(mc.screen instanceof RuleScreen)) return;
                    screen=(RuleScreen)mc.screen;
                    check(draft().get(DIAMOND).getPath().equals("stone"),"Reopen lost saved rules");
                    check(((List<Block>)get("sources")).get(0)==Blocks.DIAMOND_ORE,"Configured source must sort first after reopen");
                    results.put("configuredSourcesFirst",true);
                    mc.options.guiScale().set(2); mc.resizeDisplay();
                    shot("05-overview.png");
                    results.put("reopenSavedRules",true); step++;
                }
                case 12 -> {
                    ((EditBox)get("leftSearch")).setValue("diamond_ore"); selectSource(Blocks.DIAMOND_ORE); clickGrid(0);
                    hoverButton(mc, (Button)get("saveBtn")); step++;
                }
                case 13 -> {
                    Screenshot.grab(mc.gameDirectory,"06-save-tooltip.png",mc.getMainRenderTarget(),message->{});
                    hoverButton(mc, (Button)get("cancelBtn")); step++;
                }
                case 14 -> {
                    Screenshot.grab(mc.gameDirectory,"07-cancel-tooltip.png",mc.getMainRenderTarget(),message->{});
                    RuleNetwork.save(Map.of(DIAMOND,ResourceLocation.parse("minecraft:stone"),ResourceLocation.parse("minecraft:stone"),DIAMOND));
                    step++;
                }
                case 15 -> {
                    if(!((String)get("status")).startsWith(net.minecraft.network.chat.Component.translatable("message.blockgen_ban.save_failed", "").getString()))return;
                    check(BlockgenBan.definitions().get(DIAMOND).getPath().equals("stone"),"Rejected network save changed existing settings");
                    check(screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("screen.blockgen_ban.title").getString()),"Screen title not translated");
                    results.put("translatedNetworkFailurePreservesSavedRules",true);
                    results.put("language",mc.options.languageCode);
                    shot("08-network-error.png");step++;
                }
                case 16 -> {
                    results.put("pass",true); finish(mc); step++;
                }
            }
        } catch(Throwable failure) {
            failure.printStackTrace(); results.put("pass",false);results.put("step",step);results.put("error",failure.toString());finish(mc);
        }
    }
    private static Object get(String name) throws Exception { Field f=RuleScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen); }
    private static int num(String name) throws Exception{return (Integer)get(name);}
    private static Map<ResourceLocation,ResourceLocation> draft()throws Exception{return (Map<ResourceLocation,ResourceLocation>)get("draft");}
    private static void click(Button b){screen.mouseClicked(b.getX()+b.getWidth()/2.0,b.getY()+7,0);}
    private static void clickGrid(int slot)throws Exception{screen.mouseClicked(num("gridX")+slot%num("cols")*18+8,num("gridY")+slot/num("cols")*18+8,0);}
    private static void selectSource(Block block)throws Exception{
        int index=((List<Block>)get("sources")).indexOf(block);
        check(index>=0 && index<num("rows"),"Source not visible");
        screen.mouseClicked(num("slotX")+8,num("listY")+index*20+8,0);
    }
    private static void shot(String name){
        pointerX = pointerY = 4;
        pendingShot=name;
    }
    private static void hoverButton(Minecraft mc, Button button) {
        double scale = mc.getWindow().getGuiScale();
        pointerX = (button.getX()+9)*scale;
        pointerY = (button.getY()+9)*scale;
    }
    private static void finish(Minecraft mc){
        done=true;
        try{Files.writeString(OUT.resolve("client-acceptance.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));}
        catch(Exception failure){failure.printStackTrace();}
        System.out.println("BLOCKGEN_UI_ACCEPTANCE "+results);
        mc.stop();
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
