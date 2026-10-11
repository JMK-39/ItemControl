package dev.xyat.itemcontrolvalidation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.itemcontrol.item.property.ItemEquipmentRefresh;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.widget.KineticTextField;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Opt-in real UI handlers, packet round-trip and disk persistence; restores the original rules. */
public final class ItemEditorSaveValidation {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ItemEditorSaveValidation.class);
    private static ItemPropertyEditorPage editor;
    private static String before;
    private static String beforeTags;
    private static dev.xyat.itemcontrol.item.client.gui.ItemTagEditorPage tagEditor;
    private static int step;
    private static long deadline;
    private static CompletableFuture<Void> serverCheck;
    private static boolean done;
    private static net.minecraft.server.players.ServerOpListEntry originalOpEntry;
    private static boolean permissionCaptured;
    public static void install() {
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, ItemEditorSaveValidation::tick);
    }
    @SuppressWarnings("unchecked")
    private static void tick() {
        if (done) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        try {
            if (step == 0) {
                if (serverCheck == null) {
                    serverCheck = new CompletableFuture<>();
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            var players = mc.getSingleplayerServer().getPlayerList();
                            var player = players.getPlayers().get(0);
                            //? if >=26.1 {
                            /*var identity = new net.minecraft.server.players.NameAndId(player.getGameProfile());
                            *///?} else {
                            var identity = player.getGameProfile();
                            //?}
                            originalOpEntry = players.getOps().get(identity);
                            permissionCaptured = true;
                            players.op(identity);
                            require(player.hasPermissions(2), "owned validation player has editor permission");
                            serverCheck.complete(null);
                        } catch (Throwable failure) { serverCheck.completeExceptionally(failure); }
                    });
                    return;
                }
                if (!serverCheck.isDone()) return;
                serverCheck.join();
                mc.options.pauseOnLostFocus = false;
                before = ItemPropertyConfig.pendingJson();
                editor = new ItemPropertyEditorPage(before);
                KineticGui.open(editor);
                verifyProtectionPreview();
                ChildRuleSaveUiChecks.run();
                set(editor, "selectedId", "minecraft:brick");
                invoke(editor, "rebuildEditor");
                invoke(editor, "openTools");
                var tools = KineticGui.currentPage();
                pressSave("gui.itemcontrol.item_property.compare");
                require(KineticGui.currentPage() instanceof dev.xyat.itemcontrol.item.client.gui.ItemPropertyComparisonPage,
                        "tools comparison opened");
                KineticGui.currentPage().close();
                require(KineticGui.currentPage() == tools, "comparison returns to exact tools child");
                tools.close();
                require(KineticGui.currentPage() == editor, "tools returns to exact parent editor");
                fields().get("armor").setTextValue("11");
                invoke(editor, "openEquipmentAttributes");
                require(KineticGui.currentPage() instanceof dev.xyat.itemcontrol.item.client.gui.EquipmentAttributeEditorPage,
                        "equipment child opened");
                var child = KineticGui.currentPage();
                amountInput().setTextValue("not a number");
                pressSave();
                require(KineticGui.currentPage() == child, "invalid current attribute stays in child");
                amountInput().setTextValue("7");
                pressSave();
                require(KineticGui.currentPage() == editor, "equipment save returns to exact parent editor");
                JsonObject rule = drafts().getAsJsonObject("minecraft:brick");
                require(rule.getAsJsonArray("attributes").get(0).getAsJsonObject().get("amount").getAsDouble() == 7,
                        "save includes current attribute without Apply");
                require(rule.get("armor").getAsInt() == 11, "opening child flushes current parent field");
                fields().get("armor").setTextValue("12");
                pressSave();
                require(KineticGui.currentPage() == editor, "parent save stays open before reply");
                // Open a child while the reply is in flight: proxy must find the editor in its back chain.
                invoke(editor, "openEquipmentAttributes");
                deadline = System.currentTimeMillis() + 15000;
                step = 1;
                return;
            }
            if (step == 1) {
                require(System.currentTimeMillis() < deadline, "server save callback timeout");
                if ((boolean) get(editor, "saving")) return;
                require("msg.itemcontrol.item_property.saved_applied".equals(get(editor, "transientMessage")),
                        "actual proxy save callback reaches editor under child; actual=" + get(editor, "transientMessage"));
                KineticGui.currentPage().close();
                require(KineticGui.currentPage() == editor, "back navigation survives network reply");
                require("minecraft:brick".equals(get(editor, "selectedId")), "save keeps selected item");
                require(drafts().getAsJsonObject("minecraft:brick").get("armor").getAsInt() == 12,
                        "save flushes latest parent text field");
                serverCheck = new CompletableFuture<>();
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var disk = JsonParser.parseString(KineticPaths.readConfigText("kineticcore/item_properties.json")).getAsJsonObject();
                        require(disk.getAsJsonObject("minecraft:brick").get("armor").getAsInt() == 12, "real save persists disk JSON");
                        require(ItemPropertyConfig.active(dev.xyat.kineticcore.api.resource.KineticResourceIds.parse("minecraft:brick")).armor() == 12,
                                "real save activates runtime snapshot");
                        ItemPropertyConfig.load();
                        require(JsonParser.parseString(ItemPropertyConfig.pendingJson()).getAsJsonObject().getAsJsonObject("minecraft:brick").get("armor").getAsInt() == 12,
                                "reload retains saved fields");
                        serverCheck.complete(null);
                    } catch (Throwable failure) { serverCheck.completeExceptionally(failure); }
                });
                step = 2;
                return;
            }
            if (step == 2) {
                if (!serverCheck.isDone()) return;
                serverCheck.join();
                if (dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) {
                    var categories = Class.forName("dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage$EditorCategory").getEnumConstants();
                    set(editor, "category", categories[categories.length - 1]);
                    invoke(editor, "rebuildEditor");
                    invoke(editor, "openCurioAttributes");
                    amountInput().setTextValue("5");
                    pressSave();
                    require(KineticGui.currentPage() == editor, "Curio save returns to exact parent");
                    require(drafts().getAsJsonObject("minecraft:brick").getAsJsonObject("curio").getAsJsonArray("attributes")
                            .get(0).getAsJsonObject().get("amount").getAsDouble() == 5, "Curio save applies current rule without Apply");
                }
                KineticGui.open(new ItemPropertyEditorPage(ItemPropertyConfig.pendingJson()));
                require(drafts(KineticGui.currentPage()).getAsJsonObject("minecraft:brick").get("armor").getAsInt() == 12,
                        "reopened editor receives saved document");
                LOG.info("ITEM_EDITOR_SAVE_PASS real child/parent Save handlers, current input, proxy callback, activation, disk, reload, reopen");
                beforeTags = dev.xyat.itemcontrol.item.config.BanItemConfig.getNetworkJson();
                tagEditor = new dev.xyat.itemcontrol.item.client.gui.ItemTagEditorPage();
                KineticGui.open(tagEditor);
                set(tagEditor, "selectedItemId", "minecraft:paper");
                ((KineticTextField) get(tagEditor, "tagInput")).setTextValue("curios:ring");
                invoke(tagEditor, "addTagFromInput");
                pressSave("gui.itemcontrol.item.item_tag.save");
                require(KineticGui.currentPage() == tagEditor, "tag save stays open");
                deadline = System.currentTimeMillis() + 15000;
                step = 3;
                return;
            }
            if (step == 3 || step == 5) {
                require(System.currentTimeMillis() < deadline, "tag disk persistence timeout");
                boolean shouldContain = step == 3;
                var disk = JsonParser.parseString(KineticPaths.readConfigText("kineticcore/banitem.json")).getAsJsonObject();
                var configured = disk.getAsJsonObject("addedItemTags");
                boolean contains = configured != null && configured.has("minecraft:paper")
                        && configured.getAsJsonArray("minecraft:paper").asList().stream().anyMatch(tag -> tag.getAsString().equals("curios:ring"));
                if (contains != shouldContain) return;
                serverCheck = new CompletableFuture<>();
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
                        var tag = net.minecraft.tags.ItemTags.create(dev.xyat.kineticcore.api.resource.KineticResourceIds.parse("curios:ring"));
                        require(stack.is(tag) == shouldContain, "server live ItemStack tag membership");
                        if (dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) {
                            var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                            var ring = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow(() -> new AssertionError("Curios fixture inventory missing"))
                                    .getCurios().get("ring").getStacks();
                            require(ring.isItemValid(0, stack) == shouldContain, "Curios native slot admission follows saved/removed tag immediately");
                        }
                        serverCheck.complete(null);
                    } catch (Throwable failure) { serverCheck.completeExceptionally(failure); }
                });
                step++;
                return;
            }
            if (step == 4 || step == 6) {
                if (!serverCheck.isDone()) return;
                serverCheck.join();
                if (step == 6) {
                    LOG.info("ITEM_TAG_SAVE_PASS actual editor Save, disk JSON, server membership, live Curios add/remove admission");
                    restore();
                    return;
                }
                var remove = tagEditor.getClass().getDeclaredMethod("removeManualTag", String.class);
                remove.setAccessible(true); remove.invoke(tagEditor, "curios:ring");
                pressSave("gui.itemcontrol.item.item_tag.save");
                deadline = System.currentTimeMillis() + 15000;
                step = 5;
            }
        } catch (Throwable failure) {
            LOG.error("ITEM_EDITOR_SAVE_FAIL step=" + step, failure);
            restore();
        }
    }
    private static void pressSave() {
        pressSave("gui.itemcontrol.item_property.save");
    }
    private static void pressSave(String key) {
        String label = KineticI18n.translatable(key).getString();
        for (var control : Minecraft.getInstance().screen.children()) {
            if (control instanceof AbstractButton button && button.getMessage().getString().equals(label)) {
                //? if >=26.1 {
                /*button.onPress(new net.minecraft.client.input.MouseButtonInfo(0, 0));*/
                //?} else {
                button.onPress();
                //?}
                return;
            }
        }
        throw new AssertionError("actual Save button missing");
    }
    private static KineticTextField amountInput() {
        for (var control : Minecraft.getInstance().screen.children()) {
            if (control instanceof KineticTextField field && field.controlX() == 160 && field.controlY() == 90) return field;
        }
        throw new AssertionError("actual attribute amount field missing");
    }
    private static void verifyProtectionPreview() throws Exception {
        //? if >=1.21 {
        /*String id = "minecraft:diamond_sword[damage=5,custom_name='\"validation-preview\"']";*/
        //?} else {
        String id = "minecraft:diamond_sword{Damage:5,display:{Name:'\"validation-preview\"'}}";
        //?}
        var expected = dev.xyat.itemcontrol.item.config.BanItemConfig.parseItemStack(id);
        require(!expected.isEmpty() && expected.getDamageValue() == 5, "valid metadata-bearing protection fixture");
        var preview = editor.getClass().getDeclaredMethod("specialStack", String.class);
        preview.setAccessible(true);
        var actual = (net.minecraft.world.item.ItemStack) preview.invoke(editor, id);
        require(net.minecraft.world.item.ItemStack.isSameItemSameTags(expected, actual), "protection grid preview preserves NBT/components");
        LOG.info("ITEM_PROPERTY_NBT_PREVIEW_PASS rule icon and hovered stack retain metadata");
    }
    @SuppressWarnings("unchecked") private static Map<String, KineticTextField> fields() throws Exception {
        return (Map<String, KineticTextField>) get(editor, "fields");
    }
    private static JsonObject drafts() throws Exception { return drafts(editor); }
    @SuppressWarnings("unchecked") private static JsonObject drafts(Object page) throws Exception {
        JsonObject result = new JsonObject();
        ((Map<String, com.google.gson.JsonElement>) get(page, "drafts")).forEach(result::add);
        return result;
    }
    private static Object get(Object page, String key) throws Exception {
        var field = page.getClass().getDeclaredField(key); field.setAccessible(true); return field.get(page);
    }
    private static void set(Object page, String key, Object value) throws Exception {
        var field = page.getClass().getDeclaredField(key); field.setAccessible(true); field.set(page, value);
    }
    private static void invoke(Object page, String method) throws Exception {
        var action = page.getClass().getDeclaredMethod(method); action.setAccessible(true); action.invoke(page);
    }
    private static void restore() {
        done = true;
        KineticGui.closeScreen();
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server != null) server.execute(() -> {
            boolean restored = true;
            try {
                if (before != null) {
                    require(ItemPropertyConfig.savePending(before).success(), "restore fixture rules");
                    ItemEquipmentRefresh.applyPending(server);
                    if (beforeTags != null) require(dev.xyat.itemcontrol.item.config.BanItemConfig.applyJson(beforeTags, "editor validation restore", true), "restore fixture tags");
                    dev.xyat.itemcontrol.item.network.ItemNetwork.syncServerConfigToAllPlayers();
                }
            } catch (Throwable failure) { restored = false; LOG.error("ITEM_EDITOR_SAVE_RESTORE_FAIL rules", failure); }
            finally {
                try {
                    if (permissionCaptured) {
                        var players = server.getPlayerList();
                        var player = players.getPlayers().get(0);
                        //? if >=26.1 {
                        /*var identity = new net.minecraft.server.players.NameAndId(player.getGameProfile());*/
                        //?} else {
                        var identity = player.getGameProfile();
                        //?}
                        players.deop(identity);
                        if (originalOpEntry != null) players.getOps().add(originalOpEntry);
                        players.sendPlayerPermissionLevel(player);
                        require(players.getOps().get(identity) == originalOpEntry, "restore exact original operator entry");
                        LOG.info("ITEM_EDITOR_SAVE_PERMISSION_RESTORED originalEntry={}", originalOpEntry != null);
                    }
                } catch (Throwable failure) { restored = false; LOG.error("ITEM_EDITOR_SAVE_RESTORE_FAIL permission", failure); }
                finally {
                    if (restored) LOG.info("ITEM_EDITOR_SAVE_RESTORED");
                    mc.execute(mc::stop);
                }
            }
        });
        else mc.execute(mc::stop);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
