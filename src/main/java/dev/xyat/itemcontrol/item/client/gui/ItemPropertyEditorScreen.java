package dev.xyat.itemcontrol.item.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.mixin.ItemPropertyMixins.BlockPropertyAccess;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticMultiLineEditBox;
import dev.xyat.kineticcore.api.client.widget.selection.KineticDropdowns;
import dev.xyat.kineticcore.api.client.widget.selection.KineticTabs.ItemGridDensity;
import dev.xyat.kineticcore.api.client.widget.selection.KineticTabs.ItemGridItem;
import dev.xyat.kineticcore.api.client.widget.selection.KineticTabs.ItemGridOutline;
import dev.xyat.kineticcore.api.client.widget.selection.KineticTabs.ScrollableItemGrid;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Server-authoritative per-item vanilla-property editor. */
public final class ItemPropertyEditorScreen extends KineticScreen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int GRID_X = 12;
    private static final int GRID_Y = 44;
    private static final int GRID_W = 222;
    private static final int GRID_H = 285;
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
    private final List<KineticItemSearch.CachedItem> allItems = new ArrayList<>();
    private final Map<String, KineticEditBox> fields = new HashMap<>();
    private final Map<String, FieldSpec> fieldSpecs = new HashMap<>();
    private final Map<String, int[]> fieldLabels = new HashMap<>();
    private final Map<String, Boolean> categoryMatches = new HashMap<>();
    private final List<String> visibleIds = new ArrayList<>();

    private KineticEditBox searchBox;
    private KineticMultiLineEditBox attributesBox;
    private ScrollableItemGrid itemGrid;
    private Category category = Category.COMBAT;
    private String selectedId;
    private String searchQuery = "";
    private String transientMessage = "";
    private String lastAttributesValue;
    private int gridScrollOffset;
    private boolean saving;
    private boolean populatingFields;

    public ItemPropertyEditorScreen(Screen parent, String pendingJson) {
        super(Component.translatable("gui.itemcontrol.item_property.title"));
        this.parent = parent;
        setParentScreen(parent);
        useCanvas(640F, 360F, 6);
        restoreDraft(pendingJson);
        allItems.addAll(ItemSearchCache.getAllItems());
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
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value;
            refreshGrid();
        });

        addDropdown(
                PANEL_RIGHT - 142, 12, 142,
                categoryOptions(), categoryDropdownValue(category),
                Component.translatable("gui.itemcontrol.item_property.category.tooltip"),
                ignored -> true,
                this::changeCategoryByValue
        );

        buildCategoryFields();
        itemGrid = addScrollableItemGrid(
                GRID_X, GRID_Y, GRID_W, GRID_H,
                ItemGridDensity.COMPACT, buildGridItems(), gridScrollOffset,
                this::selectGridIndex
        );
        gridScrollOffset = 0;

        addCompactButton(12, 337, 80,
                Component.translatable("gui.itemcontrol.item_property.add_rule"), null,
                this::addSelectedRule);
        addCompactButton(98, 337, 80,
                Component.translatable("gui.itemcontrol.item_property.reset_item"), null,
                this::resetSelectedRule);
        addCompactButton(184, 337, 52,
                Component.translatable("gui.itemcontrol.item_property.delete"), null,
                this::deleteSelectedRule);
        addButton(478, 337, 70,
                Component.translatable("gui.itemcontrol.item_property.save"),
                Component.translatable("gui.itemcontrol.item_property.save.tooltip"),
                this::save);
        addButton(554, 337, 72,
                Component.translatable("gui.itemcontrol.item_property.cancel"), null,
                this::onClose);

        populateFields();
        refreshGrid();
    }

    private List<KineticDropdowns.Option> categoryOptions() {
        List<KineticDropdowns.Option> options = new ArrayList<>();
        for (Category value : Category.values()) {
            options.add(new KineticDropdowns.Option(
                    categoryDropdownValue(value),
                    Component.empty(),
                    Component.empty()
            ));
        }
        return List.copyOf(options);
    }

    private String categoryDropdownValue(Category value) {
        return Component.translatable("gui.itemcontrol.item_property.category.current." + value.key).getString();
    }

    private void changeCategoryByValue(String selectedValue) {
        for (Category value : Category.values()) {
            if (categoryDropdownValue(value).equals(selectedValue)) {
                changeCategory(value);
                return;
            }
        }
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
                addNumericField("attack_damage", false, -2048, 2048, 0);
                addNumericField("attack_speed", false, -2048, 2048, 1);
                addNumericField("mining_speed", false, 0, 1_000_000, 2);
                addNumericField("mining_level", true, 0, 255, 3);
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
                addBooleanButton("explosion_immune", 5);
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
                spec::acceptsOrBlank,
                Component.translatable("gui.itemcontrol.item_property.field." + key + ".tooltip")
        );
        field.setResponder(value -> {
            if (!populatingFields) {
                updateFieldColor(field);
                refreshGrid();
            }
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
                validator.or(String::isBlank),
                Component.translatable("gui.itemcontrol.item_property.field." + "rarity" + ".tooltip")
        );
        field.setResponder(value -> {
            if (!populatingFields) {
                updateFieldColor(field);
                refreshGrid();
            }
        });
        field.setMaxLength(24);
        fields.put("rarity", field);
    }

    private void addBooleanButton(String key, int index) {
        int[] bounds = fieldBounds(index);
        fieldLabels.put(key, new int[]{bounds[0], bounds[1] - 11});
        JsonObject current = currentRuleObject();
        boolean overridden = current.has(key);
        String state = overridden ? current.get(key).getAsBoolean() ? "true" : "false" : originalBooleanValue(key);
        Component label;
        if (selectedId == null || !isRegistered(selectedId)) {
            label = Component.empty();
        } else if (overridden) {
            label = Component.translatable("gui.itemcontrol.item_property.boolean." + state)
                    .withStyle(style -> style.withColor(GuiTheme.indicatorColor(GuiTheme.Indicator.SUCCESS)));
        } else {
            label = Component.translatable("gui.itemcontrol.item_property.boolean.original",
                    Component.translatable("gui.itemcontrol.item_property.boolean." + state))
                    .withStyle(style -> style.withColor(GuiTheme.current().mutedText()));
        }
        StateButton button = addButton(
                bounds[0], bounds[1], FIELD_WIDTH,
                label,
                Component.translatable("gui.itemcontrol.item_property.field." + key + ".tooltip"),
                () -> cycleBoolean(key)
        );
        button.active = selectedId != null && isRegistered(selectedId);
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
                KineticEditBox field = entry.getValue();
                field.setValue(value == null || value.isJsonNull() ? "" : value.getAsString());
                field.setPlaceholder(Component.literal(originalValue(entry.getKey())));
                field.setEnabled(selectedId != null && isFieldApplicable(entry.getKey()));
                updateFieldColor(field);
            }
            if (attributesBox != null) {
                JsonElement attributes = rule.get("attributes");
                attributesBox.setValue(attributes == null ? "" : GSON.toJson(attributes));
            }
        } finally {
            populatingFields = false;
        }
    }

    private void updateFieldColor(KineticEditBox field) {
        field.setTextColor(field.getValue().isBlank()
                ? GuiTheme.current().mutedText()
                : GuiTheme.indicatorColor(GuiTheme.Indicator.SUCCESS));
    }

    private boolean isFieldApplicable(String key) {
        if (selectedId == null || !isRegistered(selectedId)) return false;
        if (key.equals("armor") || key.equals("armor_toughness") || key.equals("knockback_resistance")) {
            return stackForId(selectedId).getItem() instanceof ArmorItem;
        }
        return true;
    }

    private String originalBooleanValue(String key) {
        if (selectedId == null || !isRegistered(selectedId)) return "inherit";
        ItemStack stack = stackForId(selectedId);
        boolean value = switch (key) {
            case "always_eat" -> {
                FoodProperties food = stack.getFoodProperties(null);
                yield food != null && food.canAlwaysEat();
            }
            case "fire_resistant" -> stack.getItem().isFireResistant();
            case "explosion_immune" -> stack.is(Items.NETHER_STAR);
            default -> false;
        };
        return value ? "true" : "false";
    }

    private String originalValue(String key) {
        if (selectedId == null || !isRegistered(selectedId) || !isFieldApplicable(key)) return "";
        ItemStack stack = stackForId(selectedId);
        Item item = stack.getItem();
        FoodProperties food = stack.getFoodProperties(null);
        return switch (key) {
            case "attack_damage" -> number(attributeAmount(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE));
            case "attack_speed" -> number(attributeAmount(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_SPEED));
            case "armor" -> number(attributeAmount(stack, ((ArmorItem) item).getEquipmentSlot(), Attributes.ARMOR));
            case "armor_toughness" -> number(attributeAmount(stack, ((ArmorItem) item).getEquipmentSlot(), Attributes.ARMOR_TOUGHNESS));
            case "knockback_resistance" -> number(attributeAmount(stack, ((ArmorItem) item).getEquipmentSlot(), Attributes.KNOCKBACK_RESISTANCE));
            case "mining_speed" -> number(miningSpeed(stack));
            case "mining_level" -> item instanceof TieredItem tiered ? Integer.toString(numericMiningLevel(tiered.getTier())) : "";
            case "nutrition" -> food == null ? "" : Integer.toString(food.getNutrition());
            case "saturation" -> food == null ? "" : number(food.getSaturationModifier());
            case "eat_seconds" -> food == null ? "" : number(stack.getUseDuration() / 20.0);
            case "max_stack_size" -> Integer.toString(stack.getMaxStackSize());
            case "max_damage" -> Integer.toString(stack.getMaxDamage());
            case "enchantability" -> Integer.toString(stack.getEnchantmentValue());
            case "rarity" -> stack.getRarity().name().toLowerCase(Locale.ROOT);
            case "block_hardness" -> number(((BlockItem) item).getBlock().defaultBlockState()
                    .getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            case "block_explosion_resistance" -> number(((BlockPropertyAccess) ((BlockItem) item).getBlock())
                    .itemcontrol$getExplosionResistance());
            default -> "";
        };
    }

    private static double attributeAmount(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        return stack.getAttributeModifiers(slot).get(attribute).stream()
                .filter(modifier -> modifier.getOperation() == AttributeModifier.Operation.ADDITION)
                .mapToDouble(AttributeModifier::getAmount)
                .sum();
    }

    private static double miningSpeed(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof TieredItem tiered) return tiered.getTier().getSpeed();
        return Math.max(stack.getDestroySpeed(Blocks.STONE.defaultBlockState()),
                Math.max(stack.getDestroySpeed(Blocks.DIRT.defaultBlockState()),
                        stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState())));
    }

    @SuppressWarnings("deprecation")
    private static int numericMiningLevel(Tier tier) {
        // ItemControl's numeric mining-level override intentionally follows vanilla tier levels.
        return tier.getLevel();
    }

    private static String number(double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() : "";
    }

    private void changeCategory(Category next) {
        if (!flushFields()) return;
        category = next;
        selectedId = null;
        categoryMatches.clear();
        if (itemGrid != null) itemGrid.setScrollOffset(0);
        rebuildEditor();
    }

    private void selectGridIndex(int index) {
        if (index < 0 || index >= visibleIds.size() || !flushFields()) return;
        selectedId = visibleIds.get(index);
        rebuildEditor();
    }

    private void rebuildEditor() {
        if (itemGrid != null) gridScrollOffset = itemGrid.scrollOffset();
        rebuildUi();
    }

    private void refreshGrid() {
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

        for (String id : baseline.keySet()) {
            if (!hasProperties(baseline.get(id))
                    || usedIds.contains(id)
                    || !isRegistered(id)
                    || !matches(id, query)
                    || !matchesCategory(id, stackForId(id))) continue;
            rows.add(new GridEntry(id, stackForId(id), false));
            usedIds.add(id);
        }

        for (KineticItemSearch.CachedItem cached : allItems) {
            String id = cached.id();
            if (id.isBlank() || usedIds.contains(id) || !matches(cached, query)
                    || !matchesCategory(id, cached.stack())) continue;
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
            ItemGridOutline outline = modified
                    ? ItemGridOutline.SUCCESS
                    : ItemGridOutline.NONE;
            items.add(new ItemGridItem(entry.stack(), null, true,
                    entry.id().equals(selectedId), entry.invalid(), outline));
        }
        return List.copyOf(items);
    }

    private boolean matchesCategory(String id, ItemStack stack) {
        return categoryMatches.computeIfAbsent(id, ignored -> matchesCategory(stack));
    }

    private boolean matchesCategory(ItemStack stack) {
        Item item = stack.getItem();
        return switch (category) {
            case COMBAT -> item instanceof ArmorItem
                    || item instanceof SwordItem
                    || item instanceof ProjectileWeaponItem
                    || item instanceof TridentItem
                    || item instanceof ShieldItem
                    || !(item instanceof BlockItem)
                    && !isTool(item)
                    && stack.getFoodProperties(null) == null
                    && (!stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).isEmpty()
                    || !stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED).isEmpty());
            case TOOL -> isTool(item);
            case FOOD -> stack.getFoodProperties(null) != null;
            case GENERAL -> true;
            case BLOCK -> item instanceof BlockItem;
        };
    }

    private static boolean isTool(Item item) {
        return item instanceof DiggerItem
                || item instanceof TieredItem && !(item instanceof SwordItem)
                || item instanceof ShearsItem
                || item instanceof FishingRodItem
                || item instanceof FlintAndSteelItem;
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
        if (selectedId == null || !flushFields()) return;
        if (!isRegistered(selectedId)) {
            setMessage("gui.itemcontrol.item_property.error.unknown_item");
            return;
        }
        drafts.putIfAbsent(selectedId, new JsonObject());
        refreshGrid();
    }

    private void resetSelectedRule() {
        if (selectedId == null || !flushFields()) return;
        if (isRegistered(selectedId)) drafts.put(selectedId, new JsonObject());
        rebuildEditor();
    }

    private void deleteSelectedRule() {
        if (selectedId == null || !flushFields()) return;
        drafts.remove(selectedId);
        selectedId = null;
        rebuildEditor();
    }

    private void cycleBoolean(String key) {
        if (selectedId == null || !flushFields()) return;
        JsonObject rule = currentRuleObject();
        if (!rule.has(key)) rule.addProperty(key, true);
        else if (rule.get(key).getAsBoolean()) rule.addProperty(key, false);
        else rule.remove(key);
        drafts.put(selectedId, rule);
        rebuildEditor();
    }

    private boolean flushFields() {
        if (selectedId == null) return true;
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
                return false;
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
                    return false;
                }
            }
        }
        if (!rule.entrySet().isEmpty() || drafts.containsKey(selectedId)) {
            drafts.put(selectedId, rule);
        }
        return true;
    }

    private JsonObject currentRuleObject() {
        if (selectedId == null) return new JsonObject();
        JsonElement current = drafts.get(selectedId);
        return current != null && current.isJsonObject() ? current.getAsJsonObject().deepCopy() : new JsonObject();
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
        if (saving || !flushFields()) return;
        JsonObject root = new JsonObject();
        drafts.forEach((id, value) -> root.add(id, value.deepCopy()));
        saving = true;
        ItemNetwork.saveItemPropertyConfig(GSON.toJson(root));
    }

    public void applySaveResult(boolean success, String pendingJson, String messageKey) {
        saving = false;
        if (success) {
            restoreDraft(pendingJson);
            refreshGrid();
            if (itemGrid != null) itemGrid.setScrollOffset(0);
        }
        transientMessage = messageKey == null ? "" : messageKey;
    }

    private void setMessage(String key) {
        transientMessage = key;
        KineticOverlays.toast("itemcontrol_item_property_error", KineticI18n.translatable(key));
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 0, 0, canvasWidth(), canvasHeight());
        String attributesValue = attributesBox == null ? "" : attributesBox.getValue();
        if (!attributesValue.equals(lastAttributesValue)) {
            lastAttributesValue = attributesValue;
            refreshGrid();
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
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        showHoveredVanillaTooltip();
        if (transientMessage != null && !transientMessage.isBlank()) {
            graphics.drawString(font, Component.translatable(transientMessage), PANEL_X, 302, 0xFFFF7777, false);
        } else {
            graphics.drawString(font, Component.translatable("gui.itemcontrol.item_property.restart_hint"), PANEL_X, 302, 0xFF9BB8FF, false);
        }
    }

    private void showHoveredVanillaTooltip() {
        if (itemGrid == null) return;
        ItemStack hoveredStack = itemGrid.hoveredStack();
        if (hoveredStack != null && !hoveredStack.isEmpty()) showItemTooltip(hoveredStack);
    }

    private record GridEntry(String id, ItemStack stack, boolean invalid) {
    }

    private record FieldSpec(String key, boolean integer, double minimum, double maximum, boolean stringValue) {
        private boolean acceptsOrBlank(String raw) {
            return raw == null || raw.isBlank() || accepts(raw);
        }

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
