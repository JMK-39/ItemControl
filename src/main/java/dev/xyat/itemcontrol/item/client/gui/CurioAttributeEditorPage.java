package dev.xyat.itemcontrol.item.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.xyat.itemcontrol.item.compat.ItemCuriosCompat;
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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Accessory attribute rules: every attribute is listed on the left, the ones with a rule first (green); clicking one
 * loads its rule, or a new one, into the form on the right. The original accessory values stay visible for comparison.
 */
public final class CurioAttributeEditorPage extends KineticPage {
    // One layout for every rule editor: the list at the left (same ratio everywhere), the form at the right,
    // Apply and Delete next to Save in the header, short fields with their labels above them.
    private static final int LIST_X = 4, LIST_Y = 48, LIST_W = 298, LIST_H = 308, FORM_X = 312, FORM_W = 324;
    private static final int ACTION_W = 56, DELETE_X = 636 - 40 - 4 - ACTION_W, APPLY_X = DELETE_X - 4 - ACTION_W;
    private static final int ROW1_Y = 63, ROW2_Y = 101, MODE_W = 96, AMOUNT_W = 80, OPERATION_W = 112;
    private final JsonArray rules;
    private final Consumer<JsonArray> done;
    private final List<ItemCuriosCompat.AttributePreview> originals;
    private String attribute, mode = "add", operation = "ADDITION", amount = "0";
    private String error = "";
    private int selectedRule = -1;
    private List<String> formBaseline;
    private String query = "";
    private int listScroll = -1;
    private RegistryRuleList list;

    public CurioAttributeEditorPage(ItemStack stack, JsonArray rules, Consumer<JsonArray> done) {
        super(KineticI18n.translatable("gui.itemcontrol.item_property.curio.edit_attributes"));
        this.rules = rules.deepCopy(); this.done = done;
        originals = ItemCuriosCompat.originalAttributes(stack, Minecraft.getInstance().player);
        useCanvas(640, 360, 4);
        if (!this.rules.isEmpty()) selectRule(0);
        else newRule(originals.isEmpty() ? defaultAttribute() : originals.get(0).attribute());
    }

    private static String defaultAttribute() {
        return KineticRegistries.attributes().id(ItemPropertyOverrides.attribute(KineticResourceIds.parse("minecraft:generic.armor"))).toString();
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
        ui.dropdown(FORM_X, ROW1_Y, MODE_W, options(List.of("add", "replace", "remove"), "mode")).selected(mode).onChange(value -> { mode = value; rebuild(); }).build();
        var value = ui.textField(FORM_X + MODE_W + 4, ROW1_Y, AMOUNT_W).placeholder(Component.literal("0")).build();
        value.setTextValue(amount); value.onTextChange(text -> amount = text); value.setEnabled(!mode.equals("remove"));
        ui.dropdown(FORM_X + MODE_W + 4 + AMOUNT_W + 4, ROW1_Y, OPERATION_W, options(List.of("ADDITION", "MULTIPLY_BASE", "MULTIPLY_TOTAL"), "operation"))
                .selected(operation).onChange(text -> operation = text).build().setEnabled(!mode.equals("remove"));
        ui.button(APPLY_X, 4, ACTION_W).text(KineticI18n.translatable("gui.itemcontrol.item_property.curio.apply_rule"))
                .tooltip(KineticI18n.translatable("gui.itemcontrol.item_property.curio.editor_hint")).onClick(this::applyRule).build();
        ui.button(DELETE_X, 4, ACTION_W).text(KineticI18n.translatable("gui.itemcontrol.item_property.curio.delete_rule"))
                .onClick(() -> { deleteRule(); rebuild(); }).build().setEnabled(selectedRule >= 0);
        ui.button(636 - 40, 4, 40).text(KineticI18n.translatable("gui.itemcontrol.item_property.save")).onClick(this::save).build();
    }

    /** Attributes with a rule first, in rule order, then every other attribute; filtered by the search. */
    private void refreshList() {
        var ids = new ArrayList<String>();
        for (var raw : rules) ids.add(raw.getAsJsonObject().get("attribute").getAsString());
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

    private Component ruleSummaryFor(String id) {
        for (var raw : rules) if (sameAttribute(raw.getAsJsonObject().get("attribute").getAsString(), id)) return Component.literal(ruleSummary(raw.getAsJsonObject()));
        return Component.empty();
    }

    private List<KineticDropdown.Option> options(List<String> values, String kind) {
        return values.stream().map(value -> new KineticDropdown.Option(value, KineticI18n.translatable("gui.itemcontrol.item_property.curio." + kind + "." + value.toLowerCase(java.util.Locale.ROOT)))).toList();
    }

    private static Component attributeName(String id) {
        var type = ItemPropertyOverrides.attribute(KineticResourceIds.parse(id));
        return type == null ? Component.literal(id) : ItemRuleLabels.translation(type.getDescriptionId());
    }

    private static String ruleSummary(JsonObject row) {
        String mode = row.has("mode") ? row.get("mode").getAsString() : "add";
        String modeText = KineticI18n.translatable("gui.itemcontrol.item_property.curio.mode." + mode).getString();
        if (mode.equals("remove")) return modeText;
        String op = normalize(row.has("operation") ? row.get("operation").getAsString() : "ADDITION");
        return modeText + " " + row.get("amount").getAsString() + " · "
                + KineticI18n.translatable("gui.itemcontrol.item_property.curio.operation." + op.toLowerCase(java.util.Locale.ROOT)).getString();
    }

    private List<String> form() { return List.of(attribute, mode, operation, amount); }

    /** Loads the rule for this attribute into the form, or starts a new rule for it. */
    private void select(String id) {
        for (int i = 0; i < rules.size(); i++)
            if (sameAttribute(rules.get(i).getAsJsonObject().get("attribute").getAsString(), id)) { selectRule(i); return; }
        newRule(id);
    }

    private void selectRule(int index) {
        if (index < 0 || index >= rules.size()) return;
        var row = rules.get(index).getAsJsonObject();
        selectedRule = index;
        attribute = row.get("attribute").getAsString();
        mode = row.has("mode") ? row.get("mode").getAsString() : "add";
        operation = row.has("operation") ? normalize(row.get("operation").getAsString()) : "ADDITION";
        amount = row.has("amount") ? row.get("amount").getAsString() : "0";
        error = "";
        formBaseline = form();
    }

    /** A fresh form; an attribute the accessory already has starts as a replacement of its original value. */
    private void newRule(String id) {
        selectedRule = -1;
        attribute = id; mode = "add"; operation = "ADDITION"; amount = "0";
        for (var row : originals) if (sameAttribute(row.attribute(), id)) { mode = "replace"; operation = normalize(row.operation()); amount = Double.toString(row.amount()); break; }
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

    /** Writes the form as one rule; one attribute has at most one rule, so an older rule for it is replaced. */
    private boolean applyRule() {
        double number = 0;
        if (!mode.equals("remove")) try { number = Double.parseDouble(amount); if (!Double.isFinite(number)) throw new NumberFormatException(); }
        catch (NumberFormatException exception) { error = "gui.itemcontrol.item_property.error.invalid_value"; rebuild(); return false; }
        JsonObject row = new JsonObject(); row.addProperty("attribute", attribute); row.addProperty("mode", mode);
        if (!mode.equals("remove")) { row.addProperty("operation", operation); row.addProperty("amount", number); }
        // The edited rule keeps its place; any other rule for the same attribute is folded into it.
        var updated = new JsonArray();
        int position = -1;
        for (int i = 0; i < rules.size(); i++) {
            var existing = rules.get(i).getAsJsonObject();
            if (i == selectedRule || sameAttribute(existing.get("attribute").getAsString(), attribute)) {
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
        var nativeValues = originals.stream().filter(row -> sameAttribute(row.attribute(), attribute))
                .map(row -> row.amount() + " " + KineticI18n.translatable("gui.itemcontrol.item_property.curio.operation." + normalize(row.operation()).toLowerCase(java.util.Locale.ROOT)).getString()).toList();
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.original", nativeValues.isEmpty()
                ? KineticI18n.translatable("gui.itemcontrol.item_property.curio.summary.none").getString() : String.join("; ", nativeValues)), FORM_X, ROW1_Y + 24, FORM_W, 0xFF9BB8FF, false);
        if (!error.isEmpty()) g.scrollingText(KineticI18n.translatable(error), FORM_X, ROW1_Y + 38, FORM_W, 0xFFFF7777, false);
    }
}
