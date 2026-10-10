package dev.xyat.itemcontrol.item.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.xyat.itemcontrol.item.compat.ItemCuriosCompat;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticDropdown;
import dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

/** Equipped slot counts use Curios modifiers and are removed by Curios on unequip. */
public final class CurioSlotModifierEditorPage extends KineticPage {
    private final JsonArray rules;private final Consumer<JsonArray> done;
    private String slot="curio",amount="1",error="";
    public CurioSlotModifierEditorPage(JsonArray rules,Consumer<JsonArray> done){super(ItemRuleLabels.text("curio.slot_counts"));this.rules=rules.deepCopy();this.done=done;useCanvas(480,300,6);if(!rules.isEmpty())select(0);}
    @Override protected void build(KineticUi ui){
        ui.button(12,6,90).text(ItemRuleLabels.text("cancel")).onClick(this::close).build();
        var ids=new LinkedHashSet<String>(ItemCuriosCompat.slots(Minecraft.getInstance().player));ids.add("curio");for(var raw:rules)ids.add(raw.getAsJsonObject().get("slot").getAsString());
        var options=ids.stream().map(id->new KineticDropdown.Option(id,ItemRuleLabels.id(id,"curios.identifier."+id))).toList();
        ui.dropdown(12,58,302,options).selected(slot).tooltip(ItemRuleLabels.text("curio.slot_counts.tooltip")).onChange(value->{slot=value;rebuild();}).build();
        var field=ui.textField(326,58,142).tooltip(ItemRuleLabels.text("curio.slot_count_amount.tooltip")).build();field.setTextValue(amount);field.onTextChange(value->amount=value);
        var rows=new ArrayList<SelectionItem>();for(var raw:rules){var row=raw.getAsJsonObject();String id=row.get("slot").getAsString();rows.add(new SelectionItem(ItemRuleLabels.id(id,"curios.identifier."+id),Component.literal("  "+row.get("amount").getAsInt()),ItemRuleLabels.text("curio.slot_counts.tooltip"),true,false));}
        ui.selectionList(12,104,456,120,rows).textRows().onSelect(index->{select(index);rebuild();}).build();
        ui.button(12,256,110).text(ItemRuleLabels.text("curio.apply_rule")).onClick(this::apply).build();
        ui.button(130,256,110).text(ItemRuleLabels.text("curio.delete_rule")).onClick(()->{remove();rebuild();}).build();
        ui.button(358,256,110).text(ItemRuleLabels.text("save")).tooltip(ItemRuleLabels.text("draft.apply.tooltip")).onClick(()->{done.accept(rules.deepCopy());close();}).build();
    }
    private void select(int index){var row=rules.get(index).getAsJsonObject();slot=row.get("slot").getAsString();amount=row.get("amount").getAsString();}
    private void remove(){for(int i=rules.size()-1;i>=0;i--)if(rules.get(i).getAsJsonObject().get("slot").getAsString().equals(slot))rules.remove(i);}
    private void apply(){try{int value=Integer.parseInt(amount);if(value<-1024||value>1024)throw new IllegalArgumentException();remove();var row=new JsonObject();row.addProperty("slot",slot);row.addProperty("amount",value);rules.add(row);error="";}catch(RuntimeException ex){error="error.invalid_value";}rebuild();}
    @Override protected void renderBackground(KineticGraphics g,int x,int y,float partial){KineticTheme.panel(g,0,0,width(),height());g.scrollingText(title(),112,12,356,0xFFFFFFFF,true);g.scrollingText(ItemRuleLabels.text("curio.slot_count_amount"),326,44,142,0xFFFFFFFF,false);if(!error.isEmpty())g.scrollingText(ItemRuleLabels.text(error),12,236,456,0xFFFFAA00,false);}
}
