package dev.xyat.itemcontrolvalidation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage;
import dev.xyat.itemcontrol.item.compat.ItemCuriosCompat;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.itemcontrol.item.property.ItemEquipmentRefresh;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.internal.client.gui.screen.KineticScreenHost;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;

/**
 * Plays the item property editor the way a player does: real clicks and typing on the real pages, then real use of
 * the changed items in the world (eating, placing, equipping, wearing accessories) and the resulting player values.
 * Rule files are only read to restore the original state at the end.
 */
public final class ItemPlaytestValidation {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ItemPlaytestValidation.class);
    // Item property editor canvas positions.
    private static final int SEARCH_X = 100, SEARCH_Y = 12, PITCH = 24, CATEGORY_Y = 12;

    // Positions follow the editor's own layout constants, read at run time, so layout changes do not break the test.
    private static int layout(String name) {
        try { var field = ItemPropertyEditorPage.class.getDeclaredField(name); field.setAccessible(true); return field.getInt(null); }
        catch (ReflectiveOperationException missing) { throw new AssertionError("item editor layout constant missing: " + name, missing); }
    }
    private static int attributeLayout(String name) {
        try { var field = dev.xyat.itemcontrol.item.client.gui.CurioAttributeEditorPage.class.getDeclaredField(name); field.setAccessible(true); return field.getInt(null); }
        catch (ReflectiveOperationException missing) { throw new AssertionError("attribute editor layout constant missing: " + name, missing); }
    }
    private static int panelX() { return layout("PANEL_X"); }
    // The category dropdown (142 wide) sits left of Save (40 wide, 4 px gap).
    private static int categoryX() { return layout("PANEL_RIGHT") - 44 - 71; }
    private static int saveX() { return layout("PANEL_RIGHT") - 20; }
    // Save sits top right in the header row.
    private static int saveY() { return 4 + 8; }
    private static int addY() { return layout("ACTION_ROW_Y") + 8; }
    private static int addX() { int w = layout("ACTION_W"); return panelX() + w + 4 + w / 2; }
    private static int toggleX() { return panelX() + 59; }
    private static int slotListX() { return panelX() + layout("CURIO_SLOT_LIST_WIDTH") / 2; }
    private static int editAttributesX() { return (panelX() + layout("CURIO_SLOT_LIST_WIDTH") + 6 + layout("PANEL_RIGHT")) / 2; }
    private static int gridX() { return layout("GRID_X") + 2; }
    private static int gridY() { return layout("GRID_Y") + 2; }
    private static int columns() throws Exception { return (int) field(KineticGui.currentPage(), "itemGrid").getClass().getMethod("columns").invoke(field(KineticGui.currentPage(), "itemGrid")); }
    private static final int COMBAT = 0, FOOD = 2, CURIO = 6, SLOT_HEAD = 2;
    private record Step(String name, Check check) { }
    private interface Check { boolean run() throws Exception; }

    private static final List<Step> steps = new ArrayList<>();
    private static boolean installed, finished;
    private static int index, wait, waited;
    private static String originalPending;
    private static GameType originalMode;
    private static Vec3 originalPosition;
    private static boolean originalInvulnerable;
    private static int originalFood;
    private static ItemStack originalHand = ItemStack.EMPTY, originalHead = ItemStack.EMPTY;
    private static final Map<BlockPos, BlockState> touchedBlocks = new LinkedHashMap<>();
    private static double before;
    private static String nativeRing, nativeFrom, nativeTo, chosenSlot, firstSlot, secondSlot;
    private static final List<String> passes = new ArrayList<>();

    private ItemPlaytestValidation() { }

    public static void install() {
        if (installed) return;
        installed = true;
        buildSteps();
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, ItemPlaytestValidation::tick);
    }

    private static void tick() {
        if (finished) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (++wait < 6) return;
        Step step = steps.get(index);
        try {
            if (step.check().run()) {
                LOG.info("ITEM_PLAYTEST_STEP_PASS {}", step.name());
                index++; wait = 0; waited = 0;
                if (index >= steps.size()) finish(null);
            } else if (++waited > 400) {
                throw new AssertionError("step did not complete: " + step.name());
            }
        } catch (Throwable error) {
            finish(new AssertionError("step " + step.name() + ": " + error.getMessage(), error));
        }
    }

    private static void buildSteps() {
        // A world saved with a dead player opens on the death screen; respawn first, as its Respawn button does.
        steps.add(new Step("respawn", () -> {
            var mc = Minecraft.getInstance();
            if (mc.player.isDeadOrDying()) { mc.player.respawn(); return false; }
            if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) { mc.setScreen(null); return false; }
            return true;
        }));
        steps.add(new Step("prepare", () -> {
            onServer(player -> {
                originalPending = ItemPropertyConfig.pendingJson();
                //? if >=26.1 {
                /*Minecraft.getInstance().getSingleplayerServer().getPlayerList().op(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
                *///?} else {
                Minecraft.getInstance().getSingleplayerServer().getPlayerList().op(player.getGameProfile());
                //?}
                originalMode = player.gameMode.getGameModeForPlayer();
                originalFood = player.getFoodData().getFoodLevel();
                originalHand = player.getMainHandItem().copy();
                originalHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
                player.setGameMode(GameType.SURVIVAL);
                // The test world is superflat bedrock (pack-tools/make-flat-worlds.sh): nothing to fall into or drown
                // in. Clear the air around the player to eat and equip in. Invulnerable, so the survival player
                // cannot die meanwhile; items are still used up as in survival.
                originalPosition = player.position();
                originalInvulnerable = player.getAbilities().invulnerable;
                BlockPos center = player.blockPosition();
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (player.level().getBlockState(pos).isAir()) continue;
                    touchedBlocks.putIfAbsent(pos.immutable(), player.level().getBlockState(pos));
                    player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
                player.getAbilities().invulnerable = true;
                player.onUpdateAbilities();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                return null;
            });
            return true;
        }));

        // Stone becomes food: edible, 6 nutrition.
        openEditorSteps("food-open");
        steps.add(new Step("food-select", () -> selectItem("minecraft:stone")));
        steps.add(new Step("food-category", () -> chooseDropdown(categoryX(), CATEGORY_Y, FOOD)));
        steps.add(new Step("food-edit", () -> {
            click(addX(), addY());
            int[] edible = fieldCenter(5); click(edible[0], edible[1]);
            require(rule("minecraft:stone").has("edible") && rule("minecraft:stone").get("edible").getAsBoolean(), "Edible did not switch on");
            int[] nutrition = fieldCenter(0); click(nutrition[0], nutrition[1]); typeText("6");
            return true;
        }));
        saveSteps("food-save", "minecraft:stone");
        steps.add(new Step("food-eat-in-air", () -> {
            onServer(player -> { player.getFoodData().setFoodLevel(6); player.getFoodData().setSaturation(0); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4)); return null; });
            lookAtSky();
            var mc = Minecraft.getInstance();
            mc.options.keyUse.setDown(true);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return true;
        }));
        steps.add(new Step("food-eaten", () -> {
            int food = onServer(player -> player.getFoodData().getFoodLevel());
            if (food <= 6) return false;
            Minecraft.getInstance().options.keyUse.setDown(false);
            int count = onServer(player -> player.getMainHandItem().getCount());
            require(count == 3, "eating used " + (4 - count) + " stones instead of one");
            require(food == 12, "eating restored " + (food - 6) + " hunger instead of 6");
            passes.add("stone eaten in the air (+6 hunger)");
            return true;
        }));
        steps.add(new Step("food-place-on-block", () -> placeOnBlock(Blocks.STONE)));
        steps.add(new Step("food-placed", () -> placed(Blocks.STONE, "stone placed on a block while edible")));

        // Dirt becomes a helmet: head slot, 3 armor; the slot is a single choice.
        openEditorSteps("helmet-open");
        steps.add(new Step("helmet-select", () -> selectItem("minecraft:dirt")));
        steps.add(new Step("helmet-category", () -> chooseDropdown(categoryX(), CATEGORY_Y, COMBAT)));
        steps.add(new Step("helmet-slot", () -> { click(addX(), addY()); return chooseDropdown(toggleX(), 198, SLOT_HEAD); }));
        steps.add(new Step("helmet-armor", () -> {
            require("head".equals(text(rule("minecraft:dirt"), "equipment_slot")), "Head was not chosen as the slot");
            int[] armor = fieldCenter(2); click(armor[0], armor[1]); typeText("3");
            return true;
        }));
        saveSteps("helmet-save", "minecraft:dirt");
        steps.add(new Step("helmet-equip-in-air", () -> {
            before = onServer(player -> { player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 2)); return player.getAttributeValue(Attributes.ARMOR); });
            lookAtSky();
            var mc = Minecraft.getInstance();
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return true;
        }));
        steps.add(new Step("helmet-worn", () -> {
            boolean worn = onServer(player -> player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIRT));
            if (!worn) return false;
            double armor = onServer(player -> player.getAttributeValue(Attributes.ARMOR));
            require(Math.abs(armor - before - 3) < 1e-6, "worn dirt gave " + (armor - before) + " armor instead of 3");
            passes.add("dirt put on the head in the air (+3 armor)");
            return true;
        }));
        steps.add(new Step("helmet-place-on-block", () -> placeOnBlock(Blocks.DIRT)));
        steps.add(new Step("helmet-placed", () -> {
            if (!placed(Blocks.DIRT, "dirt placed on a block while wearable")) return false;
            onServer(player -> { player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); return null; });
            return true;
        }));

        // Iron helmet keeps its slot with a changed armor value.
        openEditorSteps("iron-open");
        steps.add(new Step("iron-select", () -> selectItem("minecraft:iron_helmet")));
        steps.add(new Step("iron-category", () -> chooseDropdown(categoryX(), CATEGORY_Y, COMBAT)));
        steps.add(new Step("iron-armor", () -> { click(addX(), addY()); int[] armor = fieldCenter(2); click(armor[0], armor[1]); typeText("10"); return true; }));
        saveSteps("iron-save", "minecraft:iron_helmet");
        steps.add(new Step("iron-worn", () -> {
            double armor = onServer(player -> {
                double empty = player.getAttributeValue(Attributes.ARMOR);
                player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                return empty;
            });
            before = armor;
            return true;
        }));
        steps.add(new Step("iron-armor-value", () -> {
            double armor = onServer(player -> player.getAttributeValue(Attributes.ARMOR));
            if (Math.abs(armor - before - 10) > 1e-6) return false;
            onServer(player -> { player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); return null; });
            passes.add("iron helmet gives the edited 10 armor");
            return true;
        }));

        if (ItemCuriosCompat.available()) {
            // Stick becomes an accessory: generic and specific slots exclude each other; +4 max health when worn.
            openEditorSteps("curio-open");
            steps.add(new Step("curio-select", () -> selectItem("minecraft:stick")));
            steps.add(new Step("curio-category", () -> chooseDropdown(categoryX(), CATEGORY_Y, CURIO)));
            steps.add(new Step("curio-enable", () -> {
                click(addX(), addY()); click(toggleX(), 90);
                require(curio("minecraft:stick").has("enabled") && curio("minecraft:stick").get("enabled").getAsBoolean(), "accessory switch did not turn on");
                click(slotListX(), 138);
                return true;
            }));
            steps.add(new Step("curio-pick-slots", () -> {
                // The list offers every registered slot type, also ones only mobs have; wearing is checked on slots the
                // test player really has.
                var worn = playerSlots();
                var specific = slotRowIds().stream().filter(id -> !id.equals("curio") && worn.contains(id)).toList();
                require(specific.size() >= 2, "the pack needs two specific accessory slots");
                firstSlot = specific.get(0); secondSlot = specific.get(1); chosenSlot = firstSlot;
                return true;
            }));
            slotStep("curio-first-specific", () -> firstSlot, "minecraft:stick", () -> Set.of(firstSlot), "a specific slot did not replace the generic slot");
            slotStep("curio-second-specific", () -> secondSlot, "minecraft:stick", () -> Set.of(firstSlot, secondSlot), "specific slots did not combine");
            slotStep("curio-generic", () -> "curio", "minecraft:stick", () -> Set.of("curio"), "the generic slot did not clear the specific slots");
            slotStep("curio-specific-again", () -> firstSlot, "minecraft:stick", () -> Set.of(firstSlot), "a specific slot did not clear the generic slot");
            steps.add(new Step("curio-slots-done", () -> {
                passes.add("accessory slots: generic and specific slots exclude each other, specific slots combine");
                escape();
                click(editAttributesX(), 138);
                return true;
            }));
            steps.add(new Step("curio-attribute-page", () -> {
                if (KineticGui.currentPage() == null || !KineticGui.currentPage().getClass().getSimpleName().equals("CurioAttributeEditorPage")) return false;
                // Every attribute is listed on the left: search for max health and click its row.
                click(attributeLayout("LIST_X") + 40, 38); typeText(maxHealthId());
                return true;
            }));
            steps.add(new Step("curio-attribute-pick", () -> {
                if (waited < 2) return false;
                click(attributeLayout("LIST_X") + 40, attributeLayout("LIST_Y") + 10);
                return true;
            }));
            steps.add(new Step("curio-attribute-value", () -> {
                int form = attributeLayout("FORM_X");
                click(form + 136, 108); typeText("4"); click(form + 37, 348); click(616, 12);
                return true;
            }));
            steps.add(new Step("curio-back", () -> KineticGui.currentPage() instanceof ItemPropertyEditorPage));
            saveSteps("curio-save", "minecraft:stick");
            steps.add(new Step("curio-wear", () -> {
                String ring = chosenSlot;
                before = onServer(player -> {
                    double health = player.getAttributeValue(Attributes.MAX_HEALTH);
                    var slots = curiosOf(player);
                    var handler = slots.get(ring);
                    require(handler != null && handler.getSlots() > 0 && handler.isItemValid(0, new ItemStack(Items.STICK)), "stick does not fit its configured slot " + ring);
                    slots.entrySet().stream().filter(row -> !row.getKey().equals(ring) && row.getValue().getSlots() > 0).findFirst()
                            .ifPresent(row -> require(!row.getValue().isItemValid(0, new ItemStack(Items.STICK)), "stick fits the unselected slot " + row.getKey()));
                    return health;
                });
                return true;
            }));
            guiEquipSteps("curio-gui", () -> "minecraft:stick", () -> chosenSlot, true, true);
            steps.add(new Step("curio-health", () -> {
                double health = onServer(player -> player.getAttributeValue(Attributes.MAX_HEALTH));
                if (Math.abs(health - before - 4) > 1e-6) return false;
                String ring = chosenSlot;
                onServer(player -> { curiosOf(player).get(ring).setStackInSlot(0, ItemStack.EMPTY); return null; });
                passes.add("stick worn as an accessory (+4 max health), only in its slot");
                return true;
            }));
            // A block item made an accessory with only the generic slot, as players do with stone: it is worn through
            // the Curios screen in a slot the player has.
            openEditorSteps("block-curio-open");
            steps.add(new Step("block-curio-select", () -> selectItem("minecraft:cobblestone")));
            steps.add(new Step("block-curio-category", () -> chooseDropdown(categoryX(), CATEGORY_Y, CURIO)));
            steps.add(new Step("block-curio-enable", () -> {
                click(addX(), addY()); click(toggleX(), 90);
                require(curioSlots("minecraft:cobblestone").equals(Set.of("curio")), "enabling did not default to the generic slot: " + curioSlots("minecraft:cobblestone"));
                return true;
            }));
            saveSteps("block-curio-save", "minecraft:cobblestone");
            guiEquipSteps("block-curio-gui", () -> "minecraft:cobblestone", () -> firstSlot, true, false);
            steps.add(new Step("block-curio-worn", () -> {
                passes.add("cobblestone (a block item) with the generic slot is worn through the Curios screen in the " + firstSlot + " slot");
                return true;
            }));
            // An existing accessory moves to another slot: a ring to the necklace slot when the game has one, else any
            // item worn in exactly one slot moves to the first other common slot.
            steps.add(new Step("ring-find", () -> {
                var player = Minecraft.getInstance().player;
                var available = playerSlots();
                nativeRing = null; nativeFrom = null;
                // The AdventureSystems wallet (worn on the belt) moves to the back when both exist, as players tried.
                var wallet = KineticRegistries.items().get(KineticResourceIds.parse("adventuresystems:currency_wallet"));
                boolean walletCase = wallet != null && wallet != Items.AIR && available.contains("back")
                        && ItemCuriosCompat.originalSlots(new ItemStack(wallet), player).equals(List.of("belt"));
                if (walletCase) { nativeRing = "adventuresystems:currency_wallet"; nativeFrom = "belt"; }
                // Otherwise a real mod's accessory (a ring first) before the validation fixtures on vanilla items.
                int best = walletCase ? 99 : -1;
                for (var key : walletCase ? java.util.Set.<net.minecraft.resources.ResourceLocation>of() : KineticRegistries.items().ids()) {
                    var item = KineticRegistries.items().get(key);
                    if (item == null) continue;
                    var original = ItemCuriosCompat.originalSlots(new ItemStack(item), player);
                    if (original.size() != 1 || original.get(0).equals("curio") || !available.contains(original.get(0))) continue;
                    int rank = (key.getNamespace().equals("minecraft") ? 0 : 2) + (original.get(0).equals("ring") ? 1 : 0);
                    if (rank > best) { best = rank; nativeRing = key.toString(); nativeFrom = original.get(0); }
                    if (best == 3) break;
                }
                String from = nativeFrom;
                nativeTo = walletCase ? "back" : from == null ? null : java.util.stream.Stream.of("necklace", "ring", "belt", "charm", "hands", "back", "body", "head", "feet", "bracelet")
                        .filter(slot -> !slot.equals(from) && available.contains(slot)).findFirst().orElse(null);
                if (nativeRing == null || nativeTo == null) { steps.removeIf(step -> step.name().startsWith("ring-") && !step.name().equals("ring-find")); }
                return true;
            }));
            openEditorSteps("ring-open");
            steps.add(new Step("ring-select", () -> selectItem(nativeRing)));
            steps.add(new Step("ring-category", () -> chooseDropdown(categoryX(), CATEGORY_Y, CURIO)));
            steps.add(new Step("ring-open-slots", () -> { click(addX(), addY()); click(slotListX(), 138); return true; }));
            slotStep("ring-add-target", () -> nativeTo, null, () -> Set.of(nativeFrom, nativeTo), "adding the new slot was not recorded");
            slotStep("ring-remove-original", () -> nativeFrom, null, () -> Set.of(nativeTo), "removing the original slot was not recorded");
            steps.add(new Step("ring-slots-done", () -> { escape(); return true; }));
            saveSteps("ring-save", () -> nativeRing);
            steps.add(new Step("ring-moved", () -> {
                onServer(player -> {
                    var slots = curiosOf(player);
                    var stack = new ItemStack(KineticRegistries.items().get(KineticResourceIds.parse(nativeRing)));
                    require(slots.get(nativeTo).isItemValid(0, stack), nativeRing + " does not fit the " + nativeTo + " slot");
                    require(slots.get(nativeFrom) == null || slots.get(nativeFrom).getSlots() == 0 || !slots.get(nativeFrom).isItemValid(0, stack), nativeRing + " still fits the " + nativeFrom + " slot");
                    return null;
                });
                return true;
            }));
            guiEquipSteps("ring-gui-new", () -> nativeRing, () -> nativeTo, true, false);
            guiEquipSteps("ring-gui-old", () -> nativeRing, () -> nativeFrom, false, false);
            steps.add(new Step("ring-moved-worn", () -> {
                passes.add(nativeRing + " moved from the " + nativeFrom + " slot to the " + nativeTo + " slot, worn there through the Curios screen and refused by the old slot");
                return true;
            }));
        }
        steps.add(new Step("restore", () -> { restore(); return true; }));
    }

    // ------------------------------------------------------------------ editor helpers

    private static void openEditorSteps(String name) {
        steps.add(new Step(name, () -> {
            if (KineticGui.currentPage() instanceof ItemPropertyEditorPage) KineticGui.closeScreen();
            dev.xyat.itemcontrol.item.client.ItemClientProxy.requestOpenEditorFromCurrentScreen(dev.xyat.itemcontrol.item.network.ItemNetwork.EDITOR_ITEM_PROPERTIES);
            return true;
        }));
        steps.add(new Step(name + "-wait", () -> KineticGui.currentPage() instanceof ItemPropertyEditorPage));
    }

    private static void saveSteps(String name, String id) { saveSteps(name, () -> id); }

    /** Save, then the rule is stored and active without a restart; the ID is read when the step runs. */
    private static void saveSteps(String name, java.util.function.Supplier<String> item) {
        steps.add(new Step(name, () -> { click(saveX(), saveY()); return true; }));
        steps.add(new Step(name + "-applied", () -> {
            var page = KineticGui.currentPage();
            if (!(page instanceof ItemPropertyEditorPage) || (boolean) field(page, "saving")) return false;
            String id = item.get();
            @SuppressWarnings("unchecked") var baseline = (Map<String, JsonElement>) field(page, "baseline");
            require(baseline.containsKey(id), "Save did not store the rule for " + id);
            boolean active = onServer(player -> dev.xyat.itemcontrol.item.property.ItemPropertyOverrides.active(
                    new ItemStack(KineticRegistries.items().get(KineticResourceIds.parse(id)))) != null);
            require(active, "the saved rule for " + id + " is not active without a restart");
            KineticGui.closeScreen();
            return true;
        }));
    }

    private static boolean selectItem(String id) throws Exception {
        var page = KineticGui.currentPage();
        click(SEARCH_X, SEARCH_Y);
        for (int i = 0; i < 64; i++) key(259);
        typeText(id);
        @SuppressWarnings("unchecked") var visible = (List<String>) field(page, "visibleIds");
        int index = visible.indexOf(id);
        int columns = columns();
        require(index >= 0 && index < columns * 12, id + " is not on the first page of the grid");
        click(gridX() + (index % columns) * PITCH + 11, gridY() + (index / columns) * PITCH + 11);
        require(id.equals(field(KineticGui.currentPage(), "selectedId")), "clicking the grid did not select " + id);
        return true;
    }

    private static int[] fieldCenter(int index) {
        return new int[]{panelX() + (index % 3) * layout("FIELD_COLUMN_PITCH") + layout("FIELD_WIDTH") / 2, 82 + (index / 3) * 48 + 8};
    }

    private static JsonObject rule(String id) throws Exception {
        @SuppressWarnings("unchecked") var drafts = (Map<String, JsonElement>) field(KineticGui.currentPage(), "drafts");
        var rule = drafts.get(id);
        return rule != null && rule.isJsonObject() ? rule.getAsJsonObject() : new JsonObject();
    }

    private static JsonObject curio(String id) throws Exception {
        var raw = rule(id).get("curio");
        return raw != null && raw.isJsonObject() ? raw.getAsJsonObject() : new JsonObject();
    }

    private static Set<String> curioSlots(String id) throws Exception {
        var curio = curio(id);
        var slots = new LinkedHashSet<String>();
        if (curio.has("slots")) curio.getAsJsonArray("slots").forEach(value -> slots.add(value.getAsString()));
        return slots;
    }

    private static String text(JsonObject object, String key) { return object.has(key) ? object.get(key).getAsString() : null; }

    /** One slot-row click as its own step, followed by the expected slot set of the item ({@code null} = the native ring). */
    private static void slotStep(String name, java.util.function.Supplier<String> slot, String item,
                                 java.util.function.Supplier<Set<String>> expected, String failure) {
        steps.add(new Step(name, () -> {
            if (!clickSlotRow(slot.get())) return false;
            String id = item == null ? nativeRing : item;
            require(curioSlots(id).equals(expected.get()), failure + ": " + curioSlots(id) + " rows=" + slotRowStates());
            return true;
        }));
    }

    private static List<String> slotRowIds() throws Exception {
        var list = field(KineticGui.currentPage(), "curioSlotList");
        require(list != null, "accessory slot list is not open");
        var ids = new ArrayList<String>();
        for (Object item : (List<?>) field(list, "items")) {
            var label = (net.minecraft.network.chat.Component) item.getClass().getMethod("label").invoke(item);
            ids.add(label.getString().split(" ", 2)[0]);
        }
        return ids;
    }

    /**
     * Clicks the accessory slot row of the slot ID. When the row is outside the list's view, the list scrolls one
     * wheel step and the click waits for the next tick, as the list scrolls smoothly.
     */
    private static String lastRowSpan;

    private static boolean clickSlotRow(String slot) throws Exception {
        var list = field(KineticGui.currentPage(), "curioSlotList");
        require(list != null, "accessory slot list is not open");
        int row = slotRowIds().indexOf(slot);
        require(row >= 0, "accessory slot row missing: " + slot);
        var itemAt = list.getClass().getMethod("itemAt", double.class, double.class);
        int top = -1, bottom = -1, firstVisible = -1;
        for (int candidate = 152; candidate < 292; candidate++) {
            int at = (int) itemAt.invoke(list, (double) slotListX(), (double) candidate);
            if (at >= 0 && firstVisible < 0) firstVisible = at;
            if (at == row) { if (top < 0) top = candidate; bottom = candidate; }
        }
        if (top < 0) { wheel(slotListX(), 220, firstVisible > row ? 1 : -1); lastRowSpan = null; return false; }
        // The list scrolls smoothly: click only once the row stands still, in its middle, as a player would.
        String span = row + ":" + top + "-" + bottom;
        if (!span.equals(lastRowSpan)) { lastRowSpan = span; return false; }
        lastRowSpan = null;
        int y = (top + bottom) / 2;
        LOG.info("ITEM_PLAYTEST_SLOT_CLICK slot={} row={} y={} firstVisible={} states={}", slot, row, y, firstVisible, slotRowStates());
        // Diagnostics: every listener that reports the mouse over the click point, in dispatch order.
        var under = new ArrayList<String>();
        for (var child : Minecraft.getInstance().screen.children())
            if (child.isMouseOver(slotListX(), y)) under.add(child.getClass().getSimpleName());
        click(slotListX(), y);
        var focused = Minecraft.getInstance().screen.getFocused();
        var current = field(KineticGui.currentPage(), "curioSlotList");
        String clicked = current == null ? "closed" : current == list
                ? String.valueOf(list.getClass().getMethod("value", int.class).invoke(list, row)) + " at=" + itemAt.invoke(list, (double) slotListX(), (double) y)
                        + " button=" + rowButton(list, row) : "rebuilt";
        LOG.info("ITEM_PLAYTEST_SLOT_UNDER over={} focused={} row={} after={} curio={}", under, focused == null ? null : focused.getClass().getSimpleName(),
                clicked, slotRowStates(), curio((String) field(KineticGui.currentPage(), "selectedId")));
        return true;
    }

    /** Position and state of a list row button, for diagnostics. */
    private static String rowButton(Object list, int row) throws Exception {
        var button = (net.minecraft.client.gui.components.AbstractWidget) ((List<?>) field(list, "rowButtons")).get(row);
        return button.getX() + "," + button.getY() + " " + button.getWidth() + "x" + button.getHeight()
                + " visible=" + button.visible + " active=" + button.active + " scroll=" + list.getClass().getMethod("scrollOffset").invoke(list);
    }

    /** The slot rows as id=ticked, for failure messages. */
    private static String slotRowStates() throws Exception {
        var list = field(KineticGui.currentPage(), "curioSlotList");
        if (list == null) return "closed";
        var ids = slotRowIds();
        var states = new ArrayList<String>();
        var value = list.getClass().getMethod("value", int.class);
        for (int i = 0; i < ids.size() && i < 6; i++) states.add(ids.get(i) + "=" + value.invoke(list, i));
        return states.toString();
    }

    private static String maxHealthId() {
        //? if >=1.21 {
        /*return KineticRegistries.attributes().id(Attributes.MAX_HEALTH.value()).toString();
        *///?} else {
        return KineticRegistries.attributes().id(Attributes.MAX_HEALTH).toString();
        //?}
    }

    // ------------------------------------------------------------------ world helpers

    private static void lookAtSky() {
        var player = Minecraft.getInstance().player;
        player.setXRot(-90);
    }

    /** Uses the held item on the top face of a fresh glass block two blocks beside the player. */
    private static boolean placeOnBlock(net.minecraft.world.level.block.Block expected) throws Exception {
        var mc = Minecraft.getInstance();
        // First call sets up the block and the held item on the server; the click waits until the client sees both
        // and the player is no longer eating, as a real player would.
        if (preparedFor != expected) {
            if (mc.player.isUsingItem()) { mc.options.keyUse.setDown(false); mc.gameMode.releaseUsingItem(mc.player); }
            lastBase = onServer(player -> {
                BlockPos pos = player.blockPosition().east(2);
                for (BlockPos touched : List.of(pos, pos.above())) touchedBlocks.putIfAbsent(touched.immutable(), player.level().getBlockState(touched));
                player.level().setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
                player.level().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
                player.stopUsingItem();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(expected.asItem(), 2));
                return pos.immutable();
            });
            preparedFor = expected; preparedTicks = 0;
            return false;
        }
        if (++preparedTicks < 10 || mc.player.isUsingItem() || !mc.player.getMainHandItem().is(expected.asItem())
                || !mc.level.getBlockState(lastBase).is(Blocks.GLASS)) {
            if (preparedTicks % 40 == 39) LOG.info("ITEM_PLAYTEST_WAIT place using={} hand={} base={} screen={}", mc.player.isUsingItem(),
                    mc.player.getMainHandItem(), mc.level.getBlockState(lastBase), mc.screen);
            return false;
        }
        var hit = new BlockHitResult(Vec3.atCenterOf(lastBase).add(0, 0.5, 0), Direction.UP, lastBase, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        preparedFor = null;
        return true;
    }
    private static BlockPos lastBase;
    private static net.minecraft.world.level.block.Block preparedFor;
    private static int preparedTicks;

    private static boolean placed(net.minecraft.world.level.block.Block expected, String pass) throws Exception {
        boolean done = onServer(player -> player.level().getBlockState(lastBase.above()).is(expected));
        if (!done) return false;
        int count = onServer(player -> player.getMainHandItem().getCount());
        require(count == 1, "placing used " + (2 - count) + " items instead of one");
        passes.add(pass);
        return true;
    }

    private static final int GUI_INVENTORY_SLOT = 17;

    /**
     * Equips an item the way a player does: the Curios inventory opens, the item is picked up from the inventory and
     * clicked onto the accessory slot, so both the client's and the server's slot checks run. {@code keep} leaves an
     * accepted item worn for later checks; otherwise it is taken off again.
     */
    private static void guiEquipSteps(String name, java.util.function.Supplier<String> item, java.util.function.Supplier<String> slot,
                                      boolean expectWorn, boolean keep) {
        steps.add(new Step(name + "-open", () -> {
            String id = item.get();
            onServer(player -> {
                player.getInventory().setItem(GUI_INVENTORY_SLOT, new ItemStack(KineticRegistries.items().get(KineticResourceIds.parse(id))));
                openCuriosMenu(player);
                return null;
            });
            return true;
        }));
        steps.add(new Step(name + "-click", () -> {
            var mc = Minecraft.getInstance();
            var menu = mc.player.containerMenu;
            if (menu == mc.player.inventoryMenu || !menu.getClass().getName().contains("curios")) return false;
            int from = -1, to = -1;
            for (int index = 0; index < menu.slots.size(); index++) {
                var candidate = menu.slots.get(index);
                if (candidate.container == mc.player.getInventory() && candidate.getContainerSlot() == GUI_INVENTORY_SLOT) from = index;
                if (to < 0 && candidate.getClass().getSimpleName().equals("CurioSlot") && candidate.getContainerSlot() == 0
                        && slot.get().equals(curioSlotId(candidate))) to = index;
            }
            if (from < 0 || !menu.slots.get(from).hasItem()) return false;
            require(to >= 0, "the Curios screen shows no " + slot.get() + " slot");
            dev.xyat.kineticcore.api.minecraft.MinecraftContainers.clickSlot(menu.containerId, from, 0, net.minecraft.world.inventory.ClickType.PICKUP);
            dev.xyat.kineticcore.api.minecraft.MinecraftContainers.clickSlot(menu.containerId, to, 0, net.minecraft.world.inventory.ClickType.PICKUP);
            return true;
        }));
        steps.add(new Step(name + "-check", () -> {
            if (waited < 10) return false;
            String id = item.get(), target = slot.get();
            boolean worn = onServer(player -> {
                var handler = curiosOf(player).get(target);
                boolean result = handler != null && handler.getStackInSlot(0).is(KineticRegistries.items().get(KineticResourceIds.parse(id)));
                player.containerMenu.setCarried(ItemStack.EMPTY);
                player.getInventory().setItem(GUI_INVENTORY_SLOT, ItemStack.EMPTY);
                if (result && !keep) handler.setStackInSlot(0, ItemStack.EMPTY);
                player.closeContainer();
                return result;
            });
            require(worn == expectWorn, id + (expectWorn ? " could not be placed in the " : " could be placed in the ") + target + " slot through the Curios screen");
            return true;
        }));
    }

    private static void openCuriosMenu(ServerPlayer player) {
        //? if >=26.1 {
        /*player.openMenu(new top.theillusivec4.curios.common.inventory.container.CuriosMenuProvider());
        *///?} else if >=1.21 {
        /*player.openMenu(new top.theillusivec4.curios.common.inventory.container.CuriosContainerProvider());
        *///?} else {
        net.minecraftforge.network.NetworkHooks.openScreen(player, new top.theillusivec4.curios.common.inventory.container.CuriosContainerProvider());
        //?}
    }

    private static String curioSlotId(net.minecraft.world.inventory.Slot slot) {
        try { return String.valueOf(slot.getClass().getMethod("getIdentifier").invoke(slot)); }
        catch (ReflectiveOperationException notCurio) { return ""; }
    }

    /** Accessory slots the test player has (at least one slot of the type). */
    private static Set<String> playerSlots() throws Exception {
        return onServer(player -> {
            var slots = new LinkedHashSet<String>();
            curiosOf(player).forEach((slot, handler) -> { if (handler.getSlots() > 0) slots.add(slot); });
            return slots;
        });
    }

    private static Map<String, top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler> curiosOf(ServerPlayer player) {
        var result = new LinkedHashMap<String, top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler>();
        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(inventory ->
                inventory.getCurios().forEach((slot, handler) -> result.put(slot, handler.getStacks())));
        return result;
    }

    private static <T> T onServer(Function<ServerPlayer, T> action) throws Exception {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        var id = Minecraft.getInstance().player.getUUID();
        return server.submit(() -> action.apply(server.getPlayerList().getPlayer(id))).get();
    }

    private static void restore() throws Exception {
        onServer(player -> {
            if (originalPending != null) require(ItemPropertyConfig.savePending(originalPending).success(), "original item rules could not be restored");
            ItemEquipmentRefresh.applyPending(Minecraft.getInstance().getSingleplayerServer());
            touchedBlocks.forEach((pos, state) -> player.level().setBlockAndUpdate(pos, state));
            player.setItemInHand(InteractionHand.MAIN_HAND, originalHand);
            player.setItemSlot(EquipmentSlot.HEAD, originalHead);
            player.getFoodData().setFoodLevel(originalFood);
            if (originalMode != null) player.setGameMode(originalMode);
            player.getAbilities().invulnerable = originalInvulnerable;
            player.onUpdateAbilities();
            if (originalPosition != null) player.teleportTo(originalPosition.x, originalPosition.y, originalPosition.z);
            return null;
        });
        Minecraft.getInstance().options.keyUse.setDown(false);
    }

    private static void finish(Throwable failure) {
        finished = true;
        if (failure != null) {
            LOG.error("ITEM_PLAYTEST_FAIL", failure);
            try { restore(); } catch (Throwable restoreFailure) { LOG.error("ITEM_PLAYTEST_RESTORE_FAIL", restoreFailure); }
        }
        passes.forEach(pass -> LOG.info("ITEM_PLAYTEST_CHECK {}", pass));
        LOG.info("ITEM_PLAYTEST_GUI_{} checks={} steps={}/{}", failure == null ? "PASS" : "FAIL", passes.size(), index, steps.size());
        KineticGui.closeScreen();
        dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
    }

    // ------------------------------------------------------------------ input helpers

    private static void click(double canvasX, double canvasY) {
        var screen = Minecraft.getInstance().screen;
        require(screen instanceof KineticScreenHost, "no Kinetic screen is open");
        var host = (KineticScreenHost) screen;
        double x = host.toScreenX(canvasX), y = host.toScreenY(canvasY);
        //? if >=26.1 {
        /*var input = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0));
        screen.mouseClicked(input, false); screen.mouseReleased(input);
        *///?} else {
        screen.mouseClicked(x, y, 0); screen.mouseReleased(x, y, 0);
        //?}
    }

    private static void wheel(double canvasX, double canvasY, double delta) {
        var screen = Minecraft.getInstance().screen;
        var host = (KineticScreenHost) screen;
        //? if >=1.21 {
        /*screen.mouseScrolled(host.toScreenX(canvasX), host.toScreenY(canvasY), 0, delta);
        *///?} else {
        screen.mouseScrolled(host.toScreenX(canvasX), host.toScreenY(canvasY), delta);
        //?}
    }

    /** Clicks a dropdown and then its row at {@code row} in the opened menu. */
    private static boolean chooseDropdown(double canvasX, double canvasY, int row) throws Exception {
        click(canvasX, canvasY);
        List<?> rows = menuRows();
        require(row < rows.size(), "dropdown has only " + rows.size() + " rows");
        clickMenuRow(rows.get(row));
        return true;
    }

    private static boolean chooseDropdownByLabel(double canvasX, double canvasY, String label) throws Exception {
        click(canvasX, canvasY);
        typeText(label);
        for (Object row : menuRows()) {
            var item = (dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays.MenuItem) field(field(row, "control"), "item");
            if (item.label().getString().equals(label)) { clickMenuRow(row); return true; }
        }
        throw new AssertionError("dropdown row missing: " + label);
    }

    private static void clickMenuRow(Object row) throws Exception {
        Object bounds = menuBounds();
        var screen = Minecraft.getInstance().screen;
        double x = (int) field(bounds, "x") + 10, y = (int) field(row, "y") + (int) field(row, "height") / 2.0;
        //? if >=26.1 {
        /*var input = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0));
        screen.mouseClicked(input, false); screen.mouseReleased(input);
        *///?} else {
        screen.mouseClicked(x, y, 0); screen.mouseReleased(x, y, 0);
        //?}
    }

    private static List<?> menuRows() throws Exception { return (List<?>) field(menuBounds(), "rows"); }

    private static Object menuBounds() throws Exception {
        var screen = Minecraft.getInstance().screen;
        Object runtime = field(screen, "runtime");
        Object overlays = runtime.getClass().getMethod("overlays").invoke(runtime);
        Object menu = field(overlays, "contextMenu");
        require(menu != null, "dropdown did not open");
        for (var method : overlays.getClass().getDeclaredMethods()) {
            if (method.getName().equals("menuBounds")) {
                method.setAccessible(true);
                return method.invoke(null, menu, screen.width, screen.height, Minecraft.getInstance().font, field(overlays, "menuScroll"));
            }
        }
        throw new AssertionError("core menu bounds unavailable");
    }

    private static void typeText(String text) {
        for (char character : text.toCharArray()) {
            //? if >=26.1 {
            /*Minecraft.getInstance().screen.charTyped(new net.minecraft.client.input.CharacterEvent(character));*/
            //?} else {
            Minecraft.getInstance().screen.charTyped(character, 0);
            //?}
        }
    }

    private static void key(int code) {
        //? if >=26.1 {
        /*Minecraft.getInstance().screen.keyPressed(new net.minecraft.client.input.KeyEvent(code, 0, 0));*/
        //?} else {
        Minecraft.getInstance().screen.keyPressed(code, 0, 0);
        //?}
    }

    private static void escape() { key(256); }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
