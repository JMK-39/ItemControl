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
    private List<String> formBaseline;
    public CurioSlotModifierEditorPage(JsonArray rules,Consumer<JsonArray> done){super(ItemRuleLabels.text("curio.slot_counts"));this.rules=rules.deepCopy();this.done=done;useCanvas(640,360,4);if(!rules.isEmpty())select(0);formBaseline=form();}
    // Header: Back, title, Apply, Delete and Save. Below it the slot and the amount on one short row, then every rule as a
    // card (slot name, signed amount in colour), four per row; clicking a card loads it into the row above.
    private static final int ACTION_W=56,DELETE_X=636-40-4-ACTION_W,APPLY_X=DELETE_X-4-ACTION_W;
    private static final int SLOT_W=160,AMOUNT_X=4+SLOT_W+4+58,AMOUNT_W=64,CARDS_Y=50,CARD_COLUMNS=4,CARD_PITCH_X=158,CARD_H=20,CARD_PITCH_Y=CARD_H+2;
    @Override protected void build(KineticUi ui){
        bindDeleteAction(() -> { for (var raw : rules) if (raw.getAsJsonObject().get("slot").getAsString().equals(slot)) return true; return false; }, () -> { remove(); rebuild(); });
        ui.button(4,4,40).text(ItemRuleLabels.text("back")).onClick(this::close).build();
        var ids=new LinkedHashSet<String>(ItemCuriosCompat.slots(Minecraft.getInstance().player));ids.add("curio");for(var raw:rules)ids.add(raw.getAsJsonObject().get("slot").getAsString());
        var options=ids.stream().map(id->new KineticDropdown.Option(id,ItemRuleLabels.translation("curios.identifier."+id),Component.literal(id))).toList();
        ui.dropdown(4,28,SLOT_W,options).selected(slot).tooltip(ItemRuleLabels.text("curio.slot_counts.tooltip")).onChange(value->{slot=value;selectExisting();rebuild();}).build();
        var field=ui.textField(AMOUNT_X,28,AMOUNT_W).tooltip(ItemRuleLabels.text("curio.slot_count_amount.tooltip")).build();field.setTextValue(amount);field.onTextChange(value->amount=value);
        ui.add(new Cards());
        ui.button(APPLY_X,4,ACTION_W).text(ItemRuleLabels.text("curio.apply_rule")).onClick(this::apply).build();
        ui.button(DELETE_X,4,ACTION_W).text(ItemRuleLabels.text("curio.delete_rule")).onClick(()->{remove();rebuild();}).build();
        ui.button(596,4,40).text(ItemRuleLabels.text("save")).tooltip(ItemRuleLabels.text("draft.apply.tooltip")).onClick(this::save).build();
    }
    private final class Cards extends dev.xyat.kineticcore.api.client.gui.widget.KineticCustomControl {
        Cards(){super(4,CARDS_Y,632,356-CARDS_Y);}
        int cardAt(double mx,double my){
            int lx=(int)mx-controlX(),ly=(int)my-controlY();if(lx<0||ly<0)return -1;
            int col=lx/CARD_PITCH_X,row=ly/CARD_PITCH_Y;if(col>=CARD_COLUMNS||lx%CARD_PITCH_X>=CARD_PITCH_X-2||ly%CARD_PITCH_Y>=CARD_H)return -1;
            int index=row*CARD_COLUMNS+col;return index<rules.size()?index:-1;
        }
        @Override protected void render(KineticGraphics g,int mx,int my,float pt){
            int hover=cardAt(mx,my);
            if(rules.isEmpty()){g.centeredText(ItemRuleLabels.text("curio.slot_counts.tooltip"),controlX()+controlWidth()/2,controlY()+8,KineticTheme.current().mutedText(),false);return;}
            for(int i=0;i<rules.size();i++){
                int x=controlX()+(i%CARD_COLUMNS)*CARD_PITCH_X,y=controlY()+(i/CARD_COLUMNS)*CARD_PITCH_Y;if(y+CARD_H>controlY()+controlHeight())break;
                var row=rules.get(i).getAsJsonObject();String id=row.get("slot").getAsString();int value=row.get("amount").getAsInt();
                boolean current=id.equals(slot);
                KineticTheme.stateSurface(g,x,y,CARD_PITCH_X-2,CARD_H,KineticTheme.Surface.PANEL_ALT,current,hover==i,false);
                String text=(value>0?"+":"")+value;int color=value>0?0xFF55FF55:value<0?0xFFFF5555:0xFFFFFFFF;
                int amountW=g.textWidth(text);
                g.scrollingText(ItemRuleLabels.translation("curios.identifier."+id),x+4,y+6,CARD_PITCH_X-2-12-amountW,0xFFFFFFFF,false);
                g.text(text,x+CARD_PITCH_X-2-4-amountW,y+6,color,false);
            }
            setTooltip(hover<0?null:Component.literal(rules.get(hover).getAsJsonObject().get("slot").getAsString()));
        }
        @Override protected boolean onMouseClick(dev.xyat.kineticcore.api.client.gui.input.MouseInput input){
            int index=cardAt(input.x(),input.y());if(index<0||!input.isLeft())return false;
            playClickSound();select(index);rebuild();return true;
        }
    }
    private List<String> form(){return List.of(slot,amount);}
    private void selectExisting(){amount="1";for(int i=0;i<rules.size();i++)if(rules.get(i).getAsJsonObject().get("slot").getAsString().equals(slot)){select(i);return;}formBaseline=form();}
    private void select(int index){var row=rules.get(index).getAsJsonObject();slot=row.get("slot").getAsString();amount=row.get("amount").getAsString();formBaseline=form();}
    private void remove(){for(int i=rules.size()-1;i>=0;i--)if(rules.get(i).getAsJsonObject().get("slot").getAsString().equals(slot))rules.remove(i);formBaseline=form();error="";}
    private void save(){if(!form().equals(formBaseline)&&!apply())return;done.accept(rules.deepCopy());close();}
    private boolean apply(){try{int value=Integer.parseInt(amount);if(value<-1024||value>1024)throw new IllegalArgumentException();remove();var row=new JsonObject();row.addProperty("slot",slot);row.addProperty("amount",value);rules.add(row);error="";}catch(RuntimeException ex){error="error.invalid_value";rebuild();return false;}rebuild();return true;}
    @Override protected void renderBackground(KineticGraphics g,int x,int y,float partial){KineticTheme.panel(g,0,0,width(),height());KineticTheme.separator(g,4,24,width()-8);g.scrollingText(title(),52,8,APPLY_X-4-52,0xFFFFFFFF,true);g.scrollingText(ItemRuleLabels.text("curio.slot_count_amount"),4+SLOT_W+4,32,54,0xFFFFFFFF,false);if(!error.isEmpty())g.scrollingText(ItemRuleLabels.text(error),AMOUNT_X+AMOUNT_W+8,32,636-AMOUNT_X-AMOUNT_W-8,0xFFFF7777,false);}
}
