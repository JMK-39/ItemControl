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
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Visual editing of one attribute, with the original equipment values visible for comparison. */
public final class EquipmentAttributeEditorPage extends KineticPage {
    private final JsonArray rules;
    private final Consumer<JsonArray> done;
    private final List<Original> originals;
    private String attribute, mode="add", operation="ADDITION", amount="0", slot="MAINHAND";
    private String error="";
    public EquipmentAttributeEditorPage(ItemStack stack, JsonArray rules, Consumer<JsonArray> done) {
        super(KineticI18n.translatable("gui.itemcontrol.item_property.equipment.attributes"));
        this.rules=rules.deepCopy(); this.done=done;
        originals=originals(stack);
        attribute=originals.isEmpty()?KineticRegistries.attributes().id(ItemPropertyOverrides.attribute(KineticResourceIds.parse("minecraft:generic.armor"))).toString():originals.get(0).attribute();
        useCanvas(480, 300, 6); select(attribute);
    }
    private record Original(String attribute,String operation,double amount,String slot) {}
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
        var ids=KineticRegistries.attributes().ids().stream().map(Object::toString).sorted().toList();
        var options=new ArrayList<KineticDropdown.Option>();
        for(String id:ids) {
            var type=ItemPropertyOverrides.attribute(KineticResourceIds.parse(id));
            options.add(new KineticDropdown.Option(id, ItemRuleLabels.id(id,type.getDescriptionId()), Component.literal(id)));
        }
        ui.dropdown(12, 42, 456, options).selected(attribute).tooltip(ItemRuleLabels.text("equipment.attribute.tooltip")).onChange(id->{select(id);rebuild();}).build();
        ui.dropdown(12, 90, 140, options(List.of("add","replace","remove"),"mode")).selected(mode).tooltip(ItemRuleLabels.text("equipment.mode.tooltip")).onChange(value->{mode=value;rebuild();}).build();
        var numberField=ui.textField(160, 90, 96).placeholder(Component.literal("0")).tooltip(ItemRuleLabels.text("equipment.amount.tooltip")).build();
        numberField.setTextValue(amount); numberField.onTextChange(text->amount=text); numberField.setEnabled(!mode.equals("remove"));
        var operationBox=ui.dropdown(264, 90, 204, options(List.of("ADDITION","MULTIPLY_BASE","MULTIPLY_TOTAL"),"operation"))
                .selected(operation).tooltip(ItemRuleLabels.text("equipment.operation.tooltip")).onChange(text->operation=text).build();
        operationBox.setEnabled(!mode.equals("remove"));
        ui.dropdown(12, 166, 140, java.util.Arrays.stream(net.minecraft.world.entity.EquipmentSlot.values()).map(value -> new KineticDropdown.Option(value.name(), KineticI18n.translatable("gui.itemcontrol.item_property.equipment.slot."+value.getName()))).toList()).selected(slot).tooltip(KineticI18n.translatable("gui.itemcontrol.item_property.equipment.slot.tooltip")).onChange(value->{slot=value;select(attribute);rebuild();}).build();
        ui.button(12, 246, 110).text(KineticI18n.translatable("gui.itemcontrol.item_property.curio.apply_rule")).tooltip(ItemRuleLabels.text("equipment.apply.tooltip")).onClick(this::applyRule).build();
        ui.button(130, 246, 110).text(KineticI18n.translatable("gui.itemcontrol.item_property.curio.delete_rule")).tooltip(ItemRuleLabels.text("equipment.delete.tooltip")).onClick(()->{deleteRule();rebuild();}).build();
        ui.button(248, 246, 106).text(KineticI18n.translatable("gui.itemcontrol.item_property.save")).tooltip(ItemRuleLabels.text("draft.apply.tooltip")).onClick(()->{done.accept(rules.deepCopy());close();}).build();
        ui.button(12, 6, 106).text(KineticI18n.translatable("gui.itemcontrol.item_property.cancel")).onClick(this::close).build();
    }
    private List<KineticDropdown.Option> options(List<String> values,String kind) {
        return values.stream().map(value->new KineticDropdown.Option(value,KineticI18n.translatable("gui.itemcontrol.item_property.curio."+kind+"."+value.toLowerCase(java.util.Locale.ROOT)))).toList();
    }
    private void select(String id) {
        attribute=id; mode="add"; operation="ADDITION"; amount="0";
        for(var raw:rules) if(raw.isJsonObject() && raw.getAsJsonObject().has("attribute") && sameAttribute(raw.getAsJsonObject().get("attribute").getAsString(), id) && raw.getAsJsonObject().get("slot").getAsString().equalsIgnoreCase(slot)) {
            var row=raw.getAsJsonObject();
            mode=row.has("mode")?row.get("mode").getAsString():"replace";
            operation=row.has("operation")?normalize(row.get("operation").getAsString()):"ADDITION";
            amount=row.has("amount")?row.get("amount").getAsString():"0"; return;
        }
        for(var row:originals) if(sameAttribute(row.attribute(), id) && row.slot().equals(slot)){mode="replace";operation=normalize(row.operation());amount=Double.toString(row.amount());return;}
    }
    private static boolean sameAttribute(String left, String right) {
        if (left.equals(right)) return true;
        var type = ItemPropertyOverrides.attribute(KineticResourceIds.parse(left));
        return type != null && type == ItemPropertyOverrides.attribute(KineticResourceIds.parse(right));
    }
    private static String normalize(String operation) {
        return switch(operation){case "ADD_VALUE"->"ADDITION";case "ADD_MULTIPLIED_BASE"->"MULTIPLY_BASE";case "ADD_MULTIPLIED_TOTAL"->"MULTIPLY_TOTAL";default->operation;};
    }
    private void deleteRule() {
        for(int i=rules.size()-1;i>=0;i--) if(rules.get(i).isJsonObject() && rules.get(i).getAsJsonObject().has("attribute")
                && sameAttribute(rules.get(i).getAsJsonObject().get("attribute").getAsString(), attribute) && rules.get(i).getAsJsonObject().get("slot").getAsString().equalsIgnoreCase(slot)) rules.remove(i);
        error="";
    }
    private void applyRule() {
        double number=0;
        if(!mode.equals("remove")) try{number=Double.parseDouble(amount);if(!Double.isFinite(number))throw new NumberFormatException();}
        catch(NumberFormatException exception){error="gui.itemcontrol.item_property.error.invalid_value";rebuild();return;}
        deleteRule(); JsonObject row=new JsonObject(); row.addProperty("attribute",attribute);row.addProperty("mode",mode);row.addProperty("slot",slot);
        if(!mode.equals("remove")){row.addProperty("operation",operation);row.addProperty("amount",number);}
        rules.add(row); error="gui.itemcontrol.item_property.curio.rule_ready";rebuild();
    }
    @Override protected void renderBackground(KineticGraphics g,int x,int y,float partialTick) {
        KineticTheme.panel(g,0,0,width(),height());
        g.scrollingText(title(),126,12,width()-138,0xFFFFFFFF,true);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.attribute"),12,29,456,0xFFFFFFFF,false);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.mode"),12,77,140,0xFFFFFFFF,false);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.amount"),160,77,96,0xFFFFFFFF,false);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.operation"),264,77,204,0xFFFFFFFF,false);
        var nativeValues=originals.stream().filter(row->sameAttribute(row.attribute(), attribute) && row.slot().equals(slot)).map(row->row.amount()+" "+KineticI18n.translatable("gui.itemcontrol.item_property.curio.operation."+normalize(row.operation()).toLowerCase(java.util.Locale.ROOT)).getString()).toList();
        g.scrollingText(ItemRuleLabels.text("equipment.original",String.join("; ",nativeValues)),12,132,456,0xFF9BB8FF,false);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.rule_count",rules.size()),164,174,304,0xFFFFFFFF,false);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.editor_hint"),12,202,456,0xFFBBBBBB,false);
        if(!error.isEmpty())g.scrollingText(KineticI18n.translatable(error),12,228,456,0xFFFFAA00,false);
    }
}
