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

/** Visual editing of one attribute, with the original accessory values visible for comparison. */
public final class CurioAttributeEditorPage extends KineticPage {
    private final JsonArray rules;
    private final Consumer<JsonArray> done;
    private final List<ItemCuriosCompat.AttributePreview> originals;
    private String attribute, mode="add", operation="ADDITION", amount="0";
    private String error="";
    public CurioAttributeEditorPage(ItemStack stack, JsonArray rules, Consumer<JsonArray> done) {
        super(KineticI18n.translatable("gui.itemcontrol.item_property.curio.edit_attributes"));
        this.rules=rules.deepCopy(); this.done=done;
        originals=ItemCuriosCompat.originalAttributes(stack, Minecraft.getInstance().player);
        attribute=originals.isEmpty()?KineticRegistries.attributes().id(ItemPropertyOverrides.attribute(KineticResourceIds.parse("minecraft:generic.armor"))).toString():originals.get(0).attribute();
        useCanvas(480, 300, 6); select(attribute);
    }
    @Override protected void build(KineticUi ui) {
        var ids=KineticRegistries.attributes().ids().stream().map(Object::toString).sorted().toList();
        var options=new ArrayList<KineticDropdown.Option>();
        for(String id:ids) {
            var type=ItemPropertyOverrides.attribute(KineticResourceIds.parse(id));
            options.add(new KineticDropdown.Option(id, KineticI18n.translatable(type.getDescriptionId()).append(" ("+id+")"), Component.literal(id)));
        }
        ui.dropdown(12, 42, 456, options).selected(attribute).onChange(id->{select(id);rebuild();}).build();
        ui.dropdown(12, 90, 140, options(List.of("add","replace","remove"),"mode")).selected(mode).onChange(value->{mode=value;rebuild();}).build();
        var value=ui.textField(160, 90, 96).placeholder(Component.literal("0")).build();
        value.setTextValue(amount); value.onTextChange(text->amount=text); value.setEnabled(!mode.equals("remove"));
        var operationBox=ui.dropdown(264, 90, 204, options(List.of("ADDITION","MULTIPLY_BASE","MULTIPLY_TOTAL"),"operation"))
                .selected(operation).onChange(text->operation=text).build();
        operationBox.setEnabled(!mode.equals("remove"));
        ui.button(12, 246, 110).text(KineticI18n.translatable("gui.itemcontrol.item_property.curio.apply_rule")).onClick(this::applyRule).build();
        ui.button(130, 246, 110).text(KineticI18n.translatable("gui.itemcontrol.item_property.curio.delete_rule")).onClick(()->{deleteRule();rebuild();}).build();
        ui.button(248, 246, 106).text(KineticI18n.translatable("gui.itemcontrol.item_property.save")).onClick(()->{done.accept(rules.deepCopy());close();}).build();
        ui.button(12, 6, 106).text(KineticI18n.translatable("gui.itemcontrol.item_property.cancel")).onClick(this::close).build();
    }
    private List<KineticDropdown.Option> options(List<String> values,String kind) {
        return values.stream().map(value->new KineticDropdown.Option(value,KineticI18n.translatable("gui.itemcontrol.item_property.curio."+kind+"."+value.toLowerCase(java.util.Locale.ROOT)))).toList();
    }
    private void select(String id) {
        attribute=id; mode="add"; operation="ADDITION"; amount="0";
        for(var raw:rules) if(raw.isJsonObject() && raw.getAsJsonObject().has("attribute") && sameAttribute(raw.getAsJsonObject().get("attribute").getAsString(), id)) {
            var row=raw.getAsJsonObject();
            mode=row.has("mode")?row.get("mode").getAsString():"add";
            operation=row.has("operation")?normalize(row.get("operation").getAsString()):"ADDITION";
            amount=row.has("amount")?row.get("amount").getAsString():"0"; return;
        }
        for(var row:originals) if(sameAttribute(row.attribute(), id)){mode="replace";operation=normalize(row.operation());amount=Double.toString(row.amount());return;}
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
                && sameAttribute(rules.get(i).getAsJsonObject().get("attribute").getAsString(), attribute)) rules.remove(i);
        error="";
    }
    private void applyRule() {
        double number=0;
        if(!mode.equals("remove")) try{number=Double.parseDouble(amount);if(!Double.isFinite(number))throw new NumberFormatException();}
        catch(NumberFormatException exception){error="gui.itemcontrol.item_property.error.invalid_value";rebuild();return;}
        deleteRule(); JsonObject row=new JsonObject(); row.addProperty("attribute",attribute);row.addProperty("mode",mode);
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
        var nativeValues=originals.stream().filter(row->sameAttribute(row.attribute(), attribute)).map(row->row.amount()+" "+row.operation()).toList();
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.original",String.join("; ",nativeValues)),12,132,456,0xFF9BB8FF,false);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.rule_count",rules.size()),12,158,456,0xFFFFFFFF,false);
        g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item_property.curio.editor_hint"),12,184,456,0xFFBBBBBB,false);
        if(!error.isEmpty())g.scrollingText(KineticI18n.translatable(error),12,218,456,0xFFFFAA00,false);
    }
}
