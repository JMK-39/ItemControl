package dev.xyat.itemcontrol.item.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.xyat.itemcontrol.item.property.ItemPropertyOverrides;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticDropdown;
import dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Equipment attribute rules: the configured rules are always listed on the left; picking one loads it into the form
 * on the right. One rule exists per attribute and equipment slot. The original equipment values stay visible.
 */
public final class EquipmentAttributeEditorPage extends KineticPage {
    // One layout for every rule editor: the list at the left (same ratio everywhere), the form at the right,
    // Apply and Delete next to Save in the header, short fields with their labels above them.
    private static final int LIST_X = 4, LIST_Y = 48, LIST_W = 298, LIST_H = 308, FORM_X = 312, FORM_W = 324;
    private static final int ACTION_W = 56, DELETE_X = 636 - 40 - 4 - ACTION_W, APPLY_X = DELETE_X - 4 - ACTION_W;
    private static final int ROW1_Y = 63, ROW2_Y = 101, MODE_W = 96, AMOUNT_W = 80, OPERATION_W = 112;
    private final JsonArray rules;
    private final Consumer<JsonArray> done;
    private final List<Original> originals;
    private String attribute, mode = "add", operation = "ADDITION", amount = "0", slot = "MAINHAND";
    private String error = "";
    private int selectedRule = -1;
    private List<String> formBaseline;
    private String query = "";
    private int listScroll = -1;
    private RegistryRuleList list;

    public EquipmentAttributeEditorPage(ItemStack stack, JsonArray rules, Consumer<JsonArray> done) {
        super(KineticI18n.translatable("gui.itemcontrol.item_property.equipment.attributes"));
        this.rules = rules.deepCopy(); this.done = done;
        originals = originals(stack);
        useCanvas(640, 360, 4);
        if (!this.rules.isEmpty()) selectRule(0);
        else {
            if (!originals.isEmpty()) slot = originals.get(0).slot();
            newRule(originals.isEmpty() ? KineticRegistries.attributes().id(ItemPropertyOverrides.attribute(KineticResourceIds.parse("minecraft:generic.armor"))).toString() : originals.get(0).attribute());
        }
    }

    private record Original(String attribute, String operation, double amount, String slot) {}

    private static List<Original> originals(ItemStack stack) {
        return dev.xyat.itemcontrol.item.config.ItemPropertyConfig.previewOriginal(() -> {
            var result = new ArrayList<Original>();
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                //? if >=1.21 {
                /*stack.forEachModifier(slot, (attribute, modifier) -> result.add(new Original(KineticRegistries.attributes().id(attribute.value()).toString(),modifier.operation().name(),modifier.amount(),slot.name())));*/
                //?} else {
                stack.getAttributeModifiers(slot).forEach((attribute, modifier) -> result.add(new Original(KineticRegistries.attributes().id(attribute).toString(),modifier.getOperation().name(),modifier.getAmount(),slot.name())));
                //?}
            }
            return List.copyOf(result);
        });
    }

    @Override protected void build(KineticUi ui) {
        bindDeleteAction(() -> selectedRule >= 0 && selectedRule < rules.size(), () -> { deleteRule(); rebuild(); });
        ui.button(4, 4, 40).text(KineticI18n.translatable("gui.itemcontrol.item_property.back")).onClick(this::close).build();
        ui.textField(LIST_X, 28, LIST_W).value(query).placeholder(KineticI18n.translatable("gui.itemcontrol.item_property.attribute_search"))
                .onChange(text -> { query = text; listScroll = 0; refreshList(); }).build();
        list = ui.add(new RegistryRuleList(LIST_X, LIST_Y, LIST_W, LIST_H, false, this::hasRule, this::ruleSummaryFor));
        list.setOnSelect(index -> {
            listScroll = list.scrollOffset();
            select(list.items().get(index));
            rebuild();
        });
        refreshList();
        ui.dropdown(FORM_X, ROW1_Y, MODE_W, options(List.of("add", "replace", "remove"), "mode")).selected(mode).tooltip(ItemRuleLabels.text("equipment.mode.tooltip")).onChange(value -> { mode = value; rebuild(); }).build();
        var value = ui.textField(FORM_X + MODE_W + 4, ROW1_Y, AMOUNT_W).placeholder(Component.literal("0")).tooltip(ItemRuleLabels.text("equipment.amount.tooltip")).build();
        value.setTextValue(amount); value.onTextChange(text -> amount = text); value.setEnabled(!mode.equals("remove"));
        ui.dropdown(FORM_X + MODE_W + 4 + AMOUNT_W + 4, ROW1_Y, OPERATION_W, options(List.of("ADDITION", "MULTIPLY_BASE", "MULTIPLY_TOTAL"), "operation"))
                .selected(operation).tooltip(ItemRuleLabels.text("equipment.operation.tooltip")).onChange(text -> operation = text).build().setEnabled(!mode.equals("remove"));
        ui.dropdown(FORM_X, ROW2_Y, MODE_W, java.util.Arrays.stream(net.minecraft.world.entity.EquipmentSlot.values())
                        .map(entry -> new KineticDropdown.Option(entry.name(), KineticI18n.translatable("gui.itemcontrol.item_property.equipment.slot." + entry.getName()))).toList())
                .selected(slot).tooltip(KineticI18n.translatable("gui.itemcontrol.item_property.equipment.slot.tooltip")).onChange(entry -> { slot = entry; select(attribute); rebuild(); }).build();
        ui.button(APPLY_X, 4, ACTION_W).text(KineticI18n.translatable("gui.itemcontrol.item_property.curio.apply_rule")).tooltip(ItemRuleLabels.text("equipment.apply.tooltip")).onClick(this::applyRule).build();
        ui.button(DELETE_X, 4, ACTION_W).text(KineticI18n.translatable("gui.itemcontrol.item_property.curio.delete_rule")).tooltip(ItemRuleLabels.text("equipment.delete.tooltip"))
                .onClick(() -> { deleteRule(); rebuild(); }).build().setEnabled(selectedRule >= 0);
        ui.button(636 - 40, 4, 40).text(KineticI18n.translatable("gui.itemcontrol.item_property.save")).tooltip(ItemRuleLabels.text("draft.apply.tooltip")).onClick(this::save).build();
    }

    /** Attributes with a rule first, in rule order, then every other attribute; filtered by the search. */
    private void refreshList() {
        var ids = new ArrayList<String>();
        for (var raw : rules) {
            String id = raw.getAsJsonObject().get("attribute").getAsString();
            if (ids.stream().noneMatch(listed -> sameAttribute(listed, id))) ids.add(id);
        }
        KineticRegistries.attributes().ids().stream().map(Object::toString).sorted()
                .filter(id -> ids.stream().noneMatch(ruled -> sameAttribute(ruled, id))).forEach(ids::add);
        var visible = ids.stream().filter(id -> dev.xyat.kineticcore.api.client.search.KineticSearch.match(
                id + " " + RegistryRuleList.name(false, id).getString(), query)).toList();
        list.setItems(visible);
        int current = -1;
        for (int i = 0; i < visible.size() && current < 0; i++) if (sameAttribute(visible.get(i), attribute)) current = i;
        list.setSelectedIndex(current);
        if (listScroll >= 0) list.setScrollOffset(listScroll);
        else if (current >= 0) list.scrollTo(current);
    }

    private boolean hasRule(String id) {
        for (var raw : rules) if (sameAttribute(raw.getAsJsonObject().get("attribute").getAsString(), id)) return true;
        return false;
    }

    /** The first rule's slot and value; "+N" when the attribute has rules in more slots. */
    private Component ruleSummaryFor(String id) {
        JsonObject first = null;
        int count = 0;
        for (var raw : rules) if (sameAttribute(raw.getAsJsonObject().get("attribute").getAsString(), id)) { if (first == null) first = raw.getAsJsonObject(); count++; }
        if (first == null) return Component.empty();
        return Component.literal(slotName(first.get("slot").getAsString()) + " · " + ruleSummary(first) + (count > 1 ? " +" + (count - 1) : ""));
    }

    private List<KineticDropdown.Option> options(List<String> values, String kind) {
        return values.stream().map(value -> new KineticDropdown.Option(value, KineticI18n.translatable("gui.itemcontrol.item_property.curio." + kind + "." + value.toLowerCase(Locale.ROOT)))).toList();
    }

    private static Component attributeName(String id) {
        var type = ItemPropertyOverrides.attribute(KineticResourceIds.parse(id));
        return type == null ? Component.literal(id) : ItemRuleLabels.translation(type.getDescriptionId());
    }

    private static String slotName(String slot) {
        for (var entry : net.minecraft.world.entity.EquipmentSlot.values())
            if (entry.name().equalsIgnoreCase(slot)) return KineticI18n.translatable("gui.itemcontrol.item_property.equipment.slot." + entry.getName()).getString();
        return slot;
    }

    private static String ruleSummary(JsonObject row) {
        String mode = row.has("mode") ? row.get("mode").getAsString() : "replace";
        String modeText = KineticI18n.translatable("gui.itemcontrol.item_property.curio.mode." + mode).getString();
        if (mode.equals("remove")) return modeText;
        String op = normalize(row.has("operation") ? row.get("operation").getAsString() : "ADDITION");
        return modeText + " " + row.get("amount").getAsString() + " · "
                + KineticI18n.translatable("gui.itemcontrol.item_property.curio.operation." + op.toLowerCase(Locale.ROOT)).getString();
    }

    private List<String> form() { return List.of(attribute, mode, operation, amount, slot); }

    /** Loads the rule for this attribute in the current slot into the form, or starts a new rule for it. */
    private void select(String id) {
        for (int i = 0; i < rules.size(); i++) {
            var row = rules.get(i).getAsJsonObject();
            if (sameAttribute(row.get("attribute").getAsString(), id) && row.get("slot").getAsString().equalsIgnoreCase(slot)) { selectRule(i); return; }
        }
        newRule(id);
    }

    private void selectRule(int index) {
        if (index < 0 || index >= rules.size()) return;
        var row = rules.get(index).getAsJsonObject();
        selectedRule = index;
        attribute = row.get("attribute").getAsString();
        slot = row.get("slot").getAsString().toUpperCase(Locale.ROOT);
        mode = row.has("mode") ? row.get("mode").getAsString() : "replace";
        operation = row.has("operation") ? normalize(row.get("operation").getAsString()) : "ADDITION";
        amount = row.has("amount") ? row.get("amount").getAsString() : "0";
        error = "";
        formBaseline = form();
    }

    /** A fresh form; an attribute the item already has in this slot starts as a replacement of its original value. */
    private void newRule(String id) {
        selectedRule = -1;
        attribute = id; mode = "add"; operation = "ADDITION"; amount = "0";
        for (var row : originals) if (sameAttribute(row.attribute(), id) && row.slot().equals(slot)) { mode = "replace"; operation = normalize(row.operation()); amount = Double.toString(row.amount()); break; }
        error = "";
        formBaseline = form();
    }

    private static boolean sameAttribute(String left, String right) {
        if (left.equals(right)) return true;
        var type = ItemPropertyOverrides.attribute(KineticResourceIds.parse(left));
        return type != null && type == ItemPropertyOverrides.attribute(KineticResourceIds.parse(right));
    }

    private static String normalize(String operation) {
        return switch (operation) { case "ADD_VALUE" -> "ADDITION"; case "ADD_MULTIPLIED_BASE" -> "MULTIPLY_BASE"; case "ADD_MULTIPLIED_TOTAL" -> "MULTIPLY_TOTAL"; default -> operation; };
    }

    private void deleteRule() {
        if (selectedRule < 0 || selectedRule >= rules.size()) return;
        rules.remove(selectedRule);
        if (rules.isEmpty()) newRule(attribute); else selectRule(Math.min(selectedRule, rules.size() - 1));
    }

    private void save() {
        if (!form().equals(formBaseline) && !applyRule()) return;
        done.accept(rules.deepCopy());
        close();
    }

    /** Writes the form as one rule; an older rule for the same attribute and slot is folded into it. */
    private boolean applyRule() {
        double number = 0;
        if (!mode.equals("remove")) try { number = Double.parseDouble(amount); if (!Double.isFinite(number)) throw new NumberFormatException(); }
        catch (NumberFormatException exception) { error = "gui.itemcontrol.item_property.error.invalid_value"; rebuild(); return false; }
        JsonObject row = new JsonObject(); row.addProperty("attribute", attribute); row.addProperty("mode", mode); row.addProperty("slot", slot);
        if (!mode.equals("remove")) { row.addProperty("operation", operation); row.addProperty("amount", number); }
        var updated = new JsonArray();
        int position = -1;
        for (int i = 0; i < rules.size(); i++) {
            var existing = rules.get(i).getAsJsonObject();
            boolean same = sameAttribute(existing.get("attribute").getAsString(), attribute) && existing.get("slot").getAsString().equalsIgnoreCase(slot);
            if (i == selectedRule || same) {
                if (position < 0) { position = updated.size(); updated.add(row); }
                continue;
            }
            updated.add(existing);
        }
        if (position < 0) { position = updated.size(); updated.add(row); }
        while (!rules.isEmpty()) rules.remove(0);
        rules.addAll(updated);
        selectRule(position);
        error = "";
        rebuild();
        return true;
    }

    @Override protected void renderBackground(KineticGraphics g, int x, int y, float partialTick) {
        KineticTheme.panel(g, 0, 0, width(), height());
        KineticTheme.separator(g, 4, 24, 632);
        KineticTheme.verticalSeparator(g, FORM_X - 5, 28, 328);
        g.scrollingText(title(), 52, 8, APPLY_X - 4 - 52, 0xFFFFFFFF, true);
        // The attribute being edited (a new rule when it has none yet); another one is picked in the list on the left.
        KineticTheme.surface(g, FORM_X, 30, FORM_W, 16, KineticTheme.Surface.PANEL_ALT);
        g.scrollingText(attributeName(attribute).copy().withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(Component.literal("  " + attribute).withStyle(net.minecraft.ChatFormatting.GRAY)), FORM_X + 4, 34, FORM_W - 8, 0xFFFFFFFF, false);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.mode"), FORM_X, ROW1_Y - 11, MODE_W, 0xFFFFFFFF, false);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.amount"), FORM_X + MODE_W + 4, ROW1_Y - 11, AMOUNT_W, 0xFFFFFFFF, false);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.operation"), FORM_X + MODE_W + 4 + AMOUNT_W + 4, ROW1_Y - 11, OPERATION_W, 0xFFFFFFFF, false);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.equipment.slot.label"), FORM_X, ROW2_Y - 11, MODE_W, 0xFFFFFFFF, false);
        var nativeValues = originals.stream().filter(row -> sameAttribute(row.attribute(), attribute) && row.slot().equals(slot))
                .map(row -> row.amount() + " " + KineticI18n.translatable("gui.itemcontrol.item_property.curio.operation." + normalize(row.operation()).toLowerCase(Locale.ROOT)).getString()).toList();
        g.scrollingText(ItemRuleLabels.text("equipment.original", nativeValues.isEmpty()
                ? KineticI18n.translatable("gui.itemcontrol.item_property.curio.summary.none").getString() : String.join("; ", nativeValues)), FORM_X, ROW2_Y + 24, FORM_W, 0xFF9BB8FF, false);
        if (!error.isEmpty()) g.scrollingText(KineticI18n.translatable(error), FORM_X, ROW2_Y + 38, FORM_W, 0xFFFF7777, false);
    }
}
