package dev.xyat.itemcontrol.item.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticMultiLineEditBox;
import dev.xyat.kineticcore.api.client.widget.selection.KineticDropdowns;
import dev.xyat.kineticcore.api.client.widget.selection.KineticTabs.ItemGridDensity;
import dev.xyat.kineticcore.api.client.widget.selection.KineticTabs.ItemGridItem;
import dev.xyat.kineticcore.api.client.widget.selection.KineticTabs.ItemGridOutline;
import dev.xyat.kineticcore.api.client.widget.selection.KineticTabs.ScrollableItemGrid;
import dev.xyat.kineticcore.api.client.widget.state.EditedEntryTracker;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Server-authoritative per-item vanilla-property editor. */
public final class ItemPropertyEditorScreen extends KineticScreen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int GRID_X = 12;
    private static final int GRID_Y = 44;
    private static final int GRID_W = 222;
    private static final int GRID_H = 268;
    private static final int PANEL_X = 246;
    private static final int PANEL_RIGHT = 626;
    private static final int FIELD_WIDTH = 92;

    public Screen getParent() {
        return parent;
    }

    private enum Category {
        COMBAT("combat"),
        TOOL("tool"),
        FOOD("food"),
        GENERAL("general"),
        BLOCK("block");

        private final String key;

        Category(String key) {
            this.key = key;
        }
    }

    private final Screen parent;
    private final Map<String, JsonElement> drafts = new LinkedHashMap<>();
    private final Map<String, JsonElement> baseline = new LinkedHashMap<>();
    private final EditedEntryTracker<String> editedTracker = new EditedEntryTracker<>();
    private final List<KineticItemSearch.CachedItem> allItems = new ArrayList<>();
    private final Map<String, KineticEditBox> fields = new HashMap<>();
    private final Map<String, FieldSpec> fieldSpecs = new HashMap<>();
    private final Map<String, int[]> fieldLabels = new HashMap<>();
    private final List<String> visibleIds = new ArrayList<>();

    private KineticEditBox searchBox;
    private KineticMultiLineEditBox attributesBox;
    private ScrollableItemGrid itemGrid;
    private Category category = Category.COMBAT;
    private String selectedId;
    private String transientMessage = "";
    private String lastAttributesValue;
    private boolean saving;
    private boolean populatingFields;

    public ItemPropertyEditorScreen(Screen parent, String pendingJson) {
        super(Component.translatable("gui.itemcontrol.item_property.title"));
        this.parent = parent;
        setParentScreen(parent);
        useCanvas(640F, 360F, 6);
        restoreDraft(pendingJson);
        allItems.addAll(ItemSearchCache.getAllItems());
        selectInitialEntry();
        refreshEditedEntries();
    }

    private void restoreDraft(String json) {
        drafts.clear();
        try {
            JsonElement parsed = JsonParser.parseString(json == null ? "{}" : json);
            if (parsed.isJsonObject()) {
                parsed.getAsJsonObject().entrySet().forEach(entry -> drafts.put(entry.getKey(), entry.getValue().deepCopy()));
            }
        } catch (RuntimeException ignored) {
            transientMessage = "gui.itemcontrol.item_property.error.invalid_document";
        }
        baseline.clear();
        drafts.forEach((id, value) -> baseline.put(id, value.deepCopy()));
    }

    private void selectInitialEntry() {
        if (!drafts.isEmpty()) {
            selectedId = drafts.keySet().iterator().next();
        } else if (!allItems.isEmpty()) {
            selectedId = allItems.get(0).id();
        }
    }

    @Override
    protected void buildUi() {
        fields.clear();
        fieldSpecs.clear();
        fieldLabels.clear();
        attributesBox = null;
        lastAttributesValue = null;
        searchBox = addTextField(12, 10, 220, Component.empty());
        searchBox.setPlaceholder(Component.translatable("gui.itemcontrol.item_property.search"));
        searchBox.setMaxLength(1024);
        searchBox.setResponder(value -> refreshGrid(false));

        addDropdown(
                PANEL_RIGHT - 142, 12, 142,
                categoryOptions(), category.key,
                Component.translatable("gui.itemcontrol.item_property.category.tooltip"),
                ignored -> true,
                value -> changeCategory(categoryFromKey(value))
        );

        buildCategoryFields();
        itemGrid = addScrollableItemGrid(
                GRID_X, GRID_Y, GRID_W, GRID_H,
                ItemGridDensity.COMPACT, buildGridItems(), 0,
                this::selectGridIndex
        );

        addCompactButton(12, 318, 80,
                Component.translatable("gui.itemcontrol.item_property.add_rule"), null,
                this::addSelectedRule);
        addCompactButton(98, 318, 80,
                Component.translatable("gui.itemcontrol.item_property.reset_item"), null,
                this::resetSelectedRule);
        addCompactButton(184, 318, 52,
                Component.translatable("gui.itemcontrol.item_property.delete"), null,
                this::deleteSelectedRule);
        addButton(478, 318, 70,
                Component.translatable("gui.itemcontrol.item_property.save"),
                Component.translatable("gui.itemcontrol.item_property.save.tooltip"),
                this::save);
        addButton(554, 318, 72,
                Component.translatable("gui.itemcontrol.item_property.cancel"), null,
                this::onClose);

        populateFields();
        refreshGrid(false);
    }

    private List<KineticDropdowns.Option> categoryOptions() {
        List<KineticDropdowns.Option> options = new ArrayList<>();
        for (Category value : Category.values()) {
            options.add(new KineticDropdowns.Option(
                    value.key,
                    Component.translatable("gui.itemcontrol.item_property.category." + value.key),
                    Component.empty()
            ));
        }
        return List.copyOf(options);
    }

    private Category categoryFromKey(String key) {
        for (Category value : Category.values()) {
            if (value.key.equals(key)) return value;
        }
        return Category.COMBAT;
    }

    private void buildCategoryFields() {
        switch (category) {
            case COMBAT -> {
                addNumericField("attack_damage", false, -2048, 2048, 0);
                addNumericField("attack_speed", false, -2048, 2048, 1);
                addNumericField("armor", false, -2048, 2048, 2);
                addNumericField("armor_toughness", false, -2048, 2048, 3);
                addNumericField("knockback_resistance", false, -2048, 2048, 4);
                attributesBox = addMultiLineTextField(
                        PANEL_X, 190, 240, 72,
                        Component.empty(), Component.translatable("gui.itemcontrol.item_property.attributes.placeholder"),
                        Component.translatable("gui.itemcontrol.item_property.attributes.tooltip")
                );
                fieldLabels.put("attributes", new int[]{PANEL_X, 178});
            }
            case TOOL -> {
                addNumericField("mining_speed", false, 0, 1_000_000, 0);
                addNumericField("mining_level", true, 0, 255, 1);
            }
            case FOOD -> {
                addNumericField("nutrition", true, 0, 1024, 0);
                addNumericField("saturation", false, 0, 1024, 1);
                addNumericField("eat_seconds", false, 0.05, 3600, 2);
                addBooleanButton("always_eat", 3);
            }
            case GENERAL -> {
                addNumericField("max_stack_size", true, 1, 99, 0);
                addNumericField("max_damage", true, 0, Integer.MAX_VALUE, 1);
                addNumericField("enchantability", true, 0, 100_000, 2);
                addStringField(this::validRarity);
                addBooleanButton("fire_resistant", 4);
            }
            case BLOCK -> {
                addNumericField("block_hardness", false, -1, 1_000_000, 0);
                addNumericField("block_explosion_resistance", false, 0, 1_000_000, 1);
                addBooleanButton("explosion_immune", 2);
            }
        }
    }

    private void addNumericField(String key, boolean integer, double minimum, double maximum, int index) {
        int[] bounds = fieldBounds(index);
        FieldSpec spec = new FieldSpec(key, integer, minimum, maximum, false);
        fieldSpecs.put(key, spec);
        fieldLabels.put(key, new int[]{bounds[0], bounds[1] - 11});
        KineticEditBox field = addTextField(
                bounds[0], bounds[1], FIELD_WIDTH, Component.empty(),
                Component.translatable("gui.itemcontrol.item_property.inherit"),
                value -> value == null || value.isBlank() || spec.accepts(value),
                Component.translatable("gui.itemcontrol.item_property.field." + key + ".tooltip")
        );
        field.setResponder(value -> {
            if (!populatingFields) refreshGrid(false);
        });
        field.setMaxLength(32);
        fields.put(key, field);
    }

    private void addStringField(Predicate<String> validator) {
        int[] bounds = fieldBounds(3);
        fieldSpecs.put("rarity", new FieldSpec("rarity", false, 0, 0, true));
        fieldLabels.put("rarity", new int[]{bounds[0], bounds[1] - 11});
        KineticEditBox field = addTextField(
                bounds[0], bounds[1], FIELD_WIDTH, Component.empty(),
                Component.translatable("gui.itemcontrol.item_property.inherit"),
                value -> value == null || value.isBlank() || validator.test(value),
                Component.translatable("gui.itemcontrol.item_property.field." + "rarity" + ".tooltip")
        );
        field.setResponder(value -> {
            if (!populatingFields) refreshGrid(false);
        });
        field.setMaxLength(24);
        fields.put("rarity", field);
    }

    private void addBooleanButton(String key, int index) {
        int[] bounds = fieldBounds(index);
        fieldLabels.put(key, new int[]{bounds[0], bounds[1] - 11});
        JsonObject current = currentRuleObject();
        String state = current.has(key) ? current.get(key).getAsBoolean() ? "true" : "false" : "inherit";
        addButton(
                bounds[0], bounds[1], FIELD_WIDTH,
                Component.translatable("gui.itemcontrol.item_property.boolean." + state),
                Component.translatable("gui.itemcontrol.item_property.field." + key + ".tooltip"),
                () -> cycleBoolean(key)
        );
    }

    private int[] fieldBounds(int index) {
        int column = index % 3;
        int row = index / 3;
        return new int[]{PANEL_X + column * 124, 82 + row * 48};
    }

    private void populateFields() {
        populatingFields = true;
        try {
            JsonObject rule = currentRuleObject();
            for (Map.Entry<String, KineticEditBox> entry : fields.entrySet()) {
                JsonElement value = rule.get(entry.getKey());
                entry.getValue().setValue(value == null || value.isJsonNull() ? "" : value.getAsString());
            }
            if (attributesBox != null) {
                JsonElement attributes = rule.get("attributes");
                attributesBox.setValue(attributes == null ? "" : GSON.toJson(attributes));
            }
        } finally {
            populatingFields = false;
        }
    }

    private void changeCategory(Category next) {
        if (flushFields()) return;
        category = next;
        rebuildUi();
    }

    private void selectGridIndex(int index) {
        if (index < 0 || index >= visibleIds.size() || flushFields()) return;
        selectedId = visibleIds.get(index);
        rebuildUi();
    }

    private void refreshGrid(boolean resetScroll) {
        if (itemGrid != null) itemGrid.setItems(buildGridItems());
    }

    private List<ItemGridItem> buildGridItems() {
        String query = searchBox == null ? "" : searchBox.getValue().trim();
        List<GridEntry> rows = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();

        for (String id : drafts.keySet()) {
            if (!isRegistered(id) && matches(id, query)) {
                rows.add(new GridEntry(id, new ItemStack(Items.BARRIER), true));
                usedIds.add(id);
            }
        }

        List<String> configured = new ArrayList<>(drafts.keySet());
        configured.removeIf(id -> usedIds.contains(id) || !isRegistered(id) || !matches(id, query));
        if (selectedId != null && isRegistered(selectedId) && matches(selectedId, query)) {
            rows.add(new GridEntry(selectedId, stackForId(selectedId), false));
            usedIds.add(selectedId);
        }
        configured.sort(editedTracker.comparator(Comparator.comparing(id -> id.toLowerCase(Locale.ROOT))));
        for (String id : configured) {
            rows.add(new GridEntry(id, stackForId(id), false));
            usedIds.add(id);
        }

        for (KineticItemSearch.CachedItem cached : allItems) {
            String id = cached.id();
            if (id.isBlank() || usedIds.contains(id) || !matches(cached, query)) continue;
            rows.add(new GridEntry(id, cached.stack(), false));
            usedIds.add(id);
        }

        visibleIds.clear();
        List<ItemGridItem> items = new ArrayList<>(rows.size());
        boolean selectedHasInput = hasCurrentPropertiesInput();
        for (GridEntry entry : rows) {
            visibleIds.add(entry.id());
            boolean modified = hasProperties(drafts.get(entry.id()))
                    || entry.id().equals(selectedId) && selectedHasInput;
            ItemGridOutline outline = modified && entry.id().equals(selectedId)
                    ? ItemGridOutline.SUCCESS
                    : ItemGridOutline.NONE;
            items.add(new ItemGridItem(entry.stack(), null, true, false, entry.invalid(), outline));
        }
        return List.copyOf(items);
    }

    private boolean hasCurrentPropertiesInput() {
        for (KineticEditBox field : fields.values()) {
            if (!field.getValue().isBlank()) return true;
        }
        return attributesBox != null && !attributesBox.getValue().isBlank();
    }

    private boolean matches(String value, String query) {
        return query.isEmpty() || value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private boolean matches(KineticItemSearch.CachedItem item, String query) {
        return query.isEmpty() || item.matches(query);
    }

    private boolean isRegistered(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return false;
        Item item = ForgeRegistries.ITEMS.getValue(location);
        return item != null && item != Items.AIR;
    }

    private ItemStack stackForId(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return new ItemStack(Items.BARRIER);
        Item item = ForgeRegistries.ITEMS.getValue(location);
        return item == null || item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
    }

    private void addSelectedRule() {
        if (selectedId == null || flushFields()) return;
        if (!isRegistered(selectedId)) {
            setMessage("gui.itemcontrol.item_property.error.unknown_item");
            return;
        }
        drafts.putIfAbsent(selectedId, new JsonObject());
        refreshEditedEntries();
        refreshGrid(false);
    }

    private void resetSelectedRule() {
        if (selectedId == null || flushFields()) return;
        if (isRegistered(selectedId)) drafts.put(selectedId, new JsonObject());
        refreshEditedEntries();
        rebuildUi();
    }

    private void deleteSelectedRule() {
        if (selectedId == null || flushFields()) return;
        drafts.remove(selectedId);
        baseline.remove(selectedId);
        selectedId = allItems.isEmpty() ? null : allItems.get(0).id();
        refreshEditedEntries();
        rebuildUi();
    }

    private void cycleBoolean(String key) {
        if (selectedId == null) return;
        JsonObject rule = currentRuleObject();
        if (!rule.has(key)) rule.addProperty(key, true);
        else if (rule.get(key).getAsBoolean()) rule.addProperty(key, false);
        else rule.remove(key);
        drafts.put(selectedId, rule);
        refreshEditedEntries();
        rebuildUi();
    }

    private boolean flushFields() {
        if (selectedId == null) return false;
        JsonObject rule = currentRuleObject();
        for (Map.Entry<String, KineticEditBox> entry : fields.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().getValue().trim();
            if (value.isEmpty()) {
                rule.remove(key);
                continue;
            }
            FieldSpec spec = fieldSpecs.get(key);
            if (spec == null || !spec.accepts(value)) {
                setMessage("gui.itemcontrol.item_property.error.invalid_value");
                return true;
            }
            if (spec.stringValue()) rule.addProperty(key, value.toLowerCase(Locale.ROOT));
            else if (spec.integer()) rule.addProperty(key, Integer.parseInt(value));
            else rule.addProperty(key, Double.parseDouble(value));
        }
        if (attributesBox != null) {
            String raw = attributesBox.getValue().trim();
            if (raw.isEmpty()) {
                rule.remove("attributes");
            } else {
                try {
                    JsonElement parsed = JsonParser.parseString(raw);
                    if (!parsed.isJsonArray()) throw new IllegalArgumentException("attributes must be an array");
                    rule.add("attributes", parsed);
                } catch (RuntimeException exception) {
                    setMessage("gui.itemcontrol.item_property.error.invalid_attributes");
                    return true;
                }
            }
        }
        drafts.put(selectedId, rule);
        refreshEditedEntries();
        return false;
    }

    private JsonObject currentRuleObject() {
        if (selectedId == null) return new JsonObject();
        JsonElement current = drafts.get(selectedId);
        return current != null && current.isJsonObject() ? current.getAsJsonObject().deepCopy() : new JsonObject();
    }

    private void refreshEditedEntries() {
        editedTracker.refresh(drafts.keySet(), id -> !jsonEquals(baseline.get(id), drafts.get(id)));
    }

    private boolean jsonEquals(JsonElement left, JsonElement right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean hasProperties(JsonElement value) {
        if (value == null || !value.isJsonObject()) return value != null;
        return !value.getAsJsonObject().entrySet().isEmpty();
    }

    private boolean validRarity(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "common", "uncommon", "rare", "epic" -> true;
            default -> false;
        };
    }

    private void save() {
        if (saving || flushFields()) return;
        JsonObject root = new JsonObject();
        drafts.forEach((id, value) -> root.add(id, value.deepCopy()));
        saving = true;
        ItemNetwork.saveItemPropertyConfig(GSON.toJson(root));
    }

    public void applySaveResult(boolean success, String pendingJson, String messageKey) {
        saving = false;
        if (success) {
            restoreDraft(pendingJson);
            refreshEditedEntries();
            refreshGrid(false);
        }
        transientMessage = messageKey == null ? "" : messageKey;
    }

    private void setMessage(String key) {
        transientMessage = key;
        KineticOverlays.toast("itemcontrol_item_property_error", KineticI18n.translatable(key));
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.canvasBackground(graphics, canvasWidth(), canvasHeight());
        String attributesValue = attributesBox == null ? "" : attributesBox.getValue();
        if (!attributesValue.equals(lastAttributesValue)) {
            lastAttributesValue = attributesValue;
            refreshGrid(false);
        }
        graphics.drawCenteredString(font, title, canvasWidth() / 2, 2, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.itemcontrol.item_property.items"), GRID_X, 31, 0xFFFFFF, false);
        for (Map.Entry<String, int[]> label : fieldLabels.entrySet()) {
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(
                            Component.translatable("gui.itemcontrol.item_property.field." + label.getKey()).getString(),
                            FIELD_WIDTH + 20
                    ),
                    label.getValue()[0],
                    label.getValue()[1],
                    0xFFDDDDDD,
                    false
            );
        }
        if (selectedId != null) {
            ItemStack selectedStack = stackForId(selectedId);
            String name = selectedStack.isEmpty() || selectedStack.getItem() == Items.BARRIER
                    ? Component.translatable("gui.itemcontrol.item_property.item.unresolved").getString()
                    : selectedStack.getHoverName().getString();
            graphics.drawString(font, font.plainSubstrByWidth(name, PANEL_RIGHT - PANEL_X), PANEL_X, 50, 0xFFFFFFFF, false);
        }
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderHoveredItemTooltip(graphics, mouseX, mouseY);
        if (transientMessage != null && !transientMessage.isBlank()) {
            graphics.drawString(font, Component.translatable(transientMessage), PANEL_X, 302, 0xFFFF7777, false);
        } else {
            graphics.drawString(font, Component.translatable("gui.itemcontrol.item_property.restart_hint"), PANEL_X, 302, 0xFF9BB8FF, false);
        }
    }

    private void renderHoveredItemTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (itemGrid == null) return;
        int index = itemGrid.itemAt(mouseX, mouseY);
        if (index < 0 || index >= visibleIds.size() || index >= itemGrid.items().size()) return;

        String id = visibleIds.get(index);
        ItemGridItem entry = itemGrid.items().get(index);
        ItemStack stack = entry.stack();
        if (stack == null || stack.isEmpty()) stack = new ItemStack(Items.BARRIER);
        List<Component> tooltip = new ArrayList<>(stack.getTooltipLines(
                KineticClientRuntime.localPlayer(), TooltipFlag.Default.NORMAL
        ));
        tooltip.add(Component.translatable("gui.itemcontrol.item_property.tooltip.id", id));
        if (entry.error()) {
            tooltip.add(Component.translatable("gui.itemcontrol.item_property.tooltip.invalid"));
        } else if (hasProperties(drafts.get(id)) || id.equals(selectedId) && hasCurrentPropertiesInput()) {
            tooltip.add(Component.translatable("gui.itemcontrol.item_property.tooltip.configured"));
        } else {
            tooltip.add(Component.translatable("gui.itemcontrol.item_property.tooltip.default"));
        }
        tooltip.add(Component.translatable("gui.itemcontrol.item_property.tooltip.click"));
        graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private record GridEntry(String id, ItemStack stack, boolean invalid) {
    }

    private record FieldSpec(String key, boolean integer, double minimum, double maximum, boolean stringValue) {
        private boolean accepts(String raw) {
            if (stringValue) {
                return key.equals("rarity")
                        ? raw.length() <= 24 && switch (raw.toLowerCase(Locale.ROOT)) {
                            case "common", "uncommon", "rare", "epic" -> true;
                            default -> false;
                        }
                        : false;
            }
            try {
                double value = Double.parseDouble(raw);
                return Double.isFinite(value)
                        && value >= minimum
                        && value <= maximum
                        && (!integer || value == Math.rint(value));
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
    }
}
