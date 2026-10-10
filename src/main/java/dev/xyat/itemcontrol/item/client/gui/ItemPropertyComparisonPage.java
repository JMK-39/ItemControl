package dev.xyat.itemcontrol.item.client.gui;

import com.google.gson.JsonObject;
import dev.xyat.itemcontrol.item.config.ItemRuleDrafts;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.Map;

/** Original values versus the detached draft; timing is displayed for each property. */
public final class ItemPropertyComparisonPage extends KineticPage {
    private final String id;private final JsonObject draft;private final Map<String,String> originals;
    public ItemPropertyComparisonPage(String id,JsonObject draft,Map<String,String> originals){super(ItemRuleLabels.text("compare"));this.id=id;this.draft=draft.deepCopy();this.originals=Map.copyOf(originals);useCanvas(620,360,6);}
    @Override protected void build(KineticUi ui){ui.button(12,6,90).text(ItemRuleLabels.text("cancel")).onClick(this::close).build();
        var rows=new ArrayList<SelectionItem>();for(String path:ItemRuleDrafts.FIELDS){var value=ItemRuleDrafts.value(draft,path);String original=originals.getOrDefault(path,"");if(value==null&&original.isBlank())continue;
            String before=original.isBlank()?ItemRuleLabels.text("inherit").getString():original;String after=value==null?ItemRuleLabels.text("inherit").getString():display(path,value);
            String timing="compare.live";
            //? if >=26.1 {
            /*if(path.equals("enchantability"))timing="compare.restart";*/
            //?}
            var label=ItemRuleLabels.text(ItemPropertyToolsPage.fieldKey(path));var details=ItemRuleLabels.text("compare.values",before,after).copy().append("  · ").append(ItemRuleLabels.text(timing));
            rows.add(new SelectionItem(label,details,details.copy().append("\n").append(ItemRuleLabels.text(timing)).append("\n"+path),true,false));
        }ui.selectionList(12,66,596,264,rows).textRows().build();}
    private static String display(String path,com.google.gson.JsonElement value){
        if(value.isJsonPrimitive()){
            if(value.getAsJsonPrimitive().isBoolean())return ItemRuleLabels.text("boolean."+value.getAsBoolean()).getString();
            if(path.equals("equipment_slot"))return ItemRuleLabels.text("equipment.slot."+value.getAsString()).getString();
            if(path.equals("attack_damage"))return value.getAsDouble()==-2?"-1":value.getAsBigDecimal().add(java.math.BigDecimal.ONE).stripTrailingZeros().toPlainString();
            if(path.equals("attack_speed"))return value.getAsBigDecimal().add(java.math.BigDecimal.valueOf(4)).stripTrailingZeros().toPlainString();
            return value.getAsString();
        }return value.toString();
    }
    @Override protected void renderBackground(KineticGraphics g,int x,int y,float partial){KineticTheme.panel(g,0,0,width(),height());g.scrollingText(title().copy().append(" — "+id),112,12,496,0xFFFFFFFF,true);g.scrollingText(ItemRuleLabels.text("compare.header"),12,46,596,0xFFFFFFFF,false);g.scrollingText(ItemRuleLabels.text("compare.hint"),12,340,596,0xFF9BB8FF,false);}
}
