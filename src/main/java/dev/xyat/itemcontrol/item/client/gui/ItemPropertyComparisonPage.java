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
    public ItemPropertyComparisonPage(String id,JsonObject draft,Map<String,String> originals){super(ItemRuleLabels.text("compare"));this.id=id;this.draft=draft.deepCopy();this.originals=Map.copyOf(originals);useCanvas(640, 360, 4);}
    private record Row(String path,Component label,String before,boolean known,String after,boolean inherited,String timing){}
    // A table: the item in the header next to Back, column titles, then one row per field with the original value
    // (grey, "—" when this page cannot read it), an arrow and the draft value (green, or yellow when it is inherited).
    private static final int LABEL_X=8,BEFORE_X=176,ARROW_X=326,AFTER_X=346,ROW_H=20,LIST_Y=44;
    @Override protected void build(KineticUi ui){ui.button(4,4,40).text(ItemRuleLabels.text("back")).onClick(this::close).build();
        var rows=new ArrayList<Row>();for(String path:ItemRuleDrafts.FIELDS){var value=ItemRuleDrafts.value(draft,path);String original=originals.getOrDefault(path,"");if(value==null&&original.isBlank())continue;
            String timing="compare.live";
            //? if >=26.1 {
            /*if(path.equals("enchantability"))timing="compare.restart";*/
            //?}
            rows.add(new Row(path,ItemRuleLabels.text(ItemPropertyToolsPage.fieldKey(path)),original.isBlank()?"—":original,!original.isBlank(),
                    value==null?ItemRuleLabels.text("inherit").getString():display(path,value),value==null,timing));
        }
        var table=new Table();table.setItems(rows);ui.add(table);}
    private final class Table extends dev.xyat.kineticcore.api.client.gui.widget.list.KineticRowList<Row>{
        Table(){super(4,LIST_Y,632,356-LIST_Y,ROW_H);}
        @Override protected void renderRow(KineticGraphics g,Row row,int index,int x,int y,int width,int height,boolean hovered,boolean selected){
            int ty=y+(height-8)/2;
            g.scrollingText(row.label(),x+LABEL_X-4,ty,BEFORE_X-LABEL_X-8,0xFFFFFFFF,false);
            g.scrollingText(Component.literal(row.before()),x+BEFORE_X-4,ty,ARROW_X-BEFORE_X-8,row.known()?0xFFBBBBBB:KineticTheme.current().mutedText(),false);
            g.text("→",x+ARROW_X-4,ty,KineticTheme.current().mutedText(),false);
            g.scrollingText(Component.literal(row.after()),x+AFTER_X-4,ty,width-(AFTER_X-4)-8,row.inherited()?0xFFFFDD55:0xFF55FF55,false);
        }
        @Override protected boolean onRowClick(Row row,int index,dev.xyat.kineticcore.api.client.gui.input.MouseInput input){return false;}
        @Override protected Component rowTooltip(Row row,int index){
            return row.label().copy().append("\n").append(ItemRuleLabels.text("compare.values",row.known()?row.before():ItemRuleLabels.text("compare.unavailable").getString(),row.after()))
                    .append("\n").append(ItemRuleLabels.text(row.timing())).append("\n").append(Component.literal(row.path()).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
    private static String display(String path,com.google.gson.JsonElement value){
        if(value.isJsonPrimitive()){
            if(value.getAsJsonPrimitive().isBoolean())return ItemRuleLabels.text("boolean."+value.getAsBoolean()).getString();
            if(path.equals("equipment_slot"))return ItemRuleLabels.text("equipment.slot."+value.getAsString()).getString();
            if(path.equals("attack_damage"))return value.getAsDouble()==-2?"-1":value.getAsBigDecimal().add(java.math.BigDecimal.ONE).stripTrailingZeros().toPlainString();
            if(path.equals("attack_speed"))return value.getAsBigDecimal().add(java.math.BigDecimal.valueOf(4)).stripTrailingZeros().toPlainString();
            return value.getAsString();
        }return value.toString();
    }
    @Override protected void renderBackground(KineticGraphics g,int x,int y,float partial){KineticTheme.panel(g,0,0,width(),height());KineticTheme.separator(g,4,24,632);
        // The item being compared, in a standard slot, then the title.
        int slot=dev.xyat.kineticcore.api.client.gui.theme.KineticSizes.ITEM_SLOT;
        var itemId=dev.xyat.kineticcore.api.resource.KineticResourceIds.tryParse(id);
        var item=itemId==null?null:dev.xyat.kineticcore.api.registry.KineticRegistries.items().get(itemId);
        KineticTheme.itemSlot(g,48,1,slot,false);
        if(item!=null)KineticTheme.item(g,new net.minecraft.world.item.ItemStack(item),48,1,slot,1.0F,false);
        g.scrollingText(title().copy().append(" · ").append(item==null?Component.literal(id):new net.minecraft.world.item.ItemStack(item).getHoverName()),48+slot+4,8,636-(48+slot+4),0xFFFFFFFF,true);
        int muted=KineticTheme.current().mutedText();
        g.scrollingText(ItemRuleLabels.text("compare.column.field"),LABEL_X,30,BEFORE_X-LABEL_X-8,muted,false);
        g.scrollingText(ItemRuleLabels.text("compare.column.original"),BEFORE_X,30,ARROW_X-BEFORE_X-8,muted,false);
        g.scrollingText(ItemRuleLabels.text("compare.column.draft"),AFTER_X,30,632-AFTER_X,muted,false);}
}
