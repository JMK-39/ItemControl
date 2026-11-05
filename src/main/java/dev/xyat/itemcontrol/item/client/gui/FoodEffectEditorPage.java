package dev.xyat.itemcontrol.item.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.xyat.itemcontrol.item.config.ItemCapabilitySettings;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticDropdown;
import dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Visual food effects, editing a detached draft until Save returns it to the parent. */
public final class FoodEffectEditorPage extends KineticPage {
    private final JsonArray rules;
    private final BiConsumer<JsonArray,String> done;
    private String selected="minecraft:speed",seconds="10",level="1",probability="100",mode,error="";
    private List<String> formBaseline;
    public FoodEffectEditorPage(JsonArray rules,String mode,BiConsumer<JsonArray,String> done) {
        super(ItemRuleLabels.text("food.effects")); this.rules=rules.deepCopy();this.mode=mode;this.done=done;useCanvas(640, 360, 4);
        if(!rules.isEmpty())select(0);
        formBaseline = form();
    }
    // Every effect is listed on the left, the food's own effects first (green); clicking one edits it on the right.
    // Apply and Delete sit next to Save in the header; the three numbers share one row, the mode is below them.
    private static final int LIST_X=4,LIST_Y=48,LIST_W=298,LIST_H=308,FORM_X=312,FORM_W=324;
    private static final int ACTION_W=56,DELETE_X=636-40-4-ACTION_W,APPLY_X=DELETE_X-4-ACTION_W,NUMBER_W=80,ROW1_Y=63,ROW2_Y=101;
    private String query="";
    private int listScroll=-1;
    private RegistryRuleList list;
    @Override protected void build(KineticUi ui) {
        bindDeleteAction(() -> hasRule(selected), () -> { remove(); rebuild(); });
        ui.button(4,4,40).text(ItemRuleLabels.text("back")).onClick(this::close).build();
        ui.textField(LIST_X,28,LIST_W).value(query).placeholder(ItemRuleLabels.text("effect_search")).onChange(text->{query=text;listScroll=0;refreshList();}).build();
        list=ui.add(new RegistryRuleList(LIST_X,LIST_Y,LIST_W,LIST_H,true,this::hasRule,this::summary));
        list.setOnSelect(index->{listScroll=list.scrollOffset();selected=list.items().get(index);selectExisting();rebuild();});
        refreshList();
        var duration=ui.textField(FORM_X,ROW1_Y,NUMBER_W).tooltip(ItemRuleLabels.text("food.duration.tooltip")).build();duration.setTextValue(seconds);duration.onTextChange(value->seconds=value);
        var amplifier=ui.textField(FORM_X+NUMBER_W+4,ROW1_Y,NUMBER_W).tooltip(ItemRuleLabels.text("food.level.tooltip")).build();amplifier.setTextValue(level);amplifier.onTextChange(value->level=value);
        var chance=ui.textField(FORM_X+2*(NUMBER_W+4),ROW1_Y,NUMBER_W).tooltip(ItemRuleLabels.text("food.probability.tooltip")).build();chance.setTextValue(probability);chance.onTextChange(value->probability=value);
        ui.dropdown(FORM_X,ROW2_Y,112,List.of(new KineticDropdown.Option("append",ItemRuleLabels.text("food.mode.append")),new KineticDropdown.Option("replace",ItemRuleLabels.text("food.mode.replace")))).selected(mode).tooltip(ItemRuleLabels.text("food.mode.tooltip")).onChange(value->mode=value).build();
        ui.button(APPLY_X,4,ACTION_W).text(ItemRuleLabels.text("curio.apply_rule")).tooltip(ItemRuleLabels.text("food.add.tooltip")).onClick(this::applyRule).build();
        ui.button(DELETE_X,4,ACTION_W).text(ItemRuleLabels.text("curio.delete_rule")).onClick(()->{remove();rebuild();}).build().setEnabled(hasRule(selected));
        ui.button(636-40,4,40).text(ItemRuleLabels.text("save")).tooltip(ItemRuleLabels.text("draft.apply.tooltip")).onClick(this::save).build();
    }
    /** The food's effects first, in rule order, then every other effect; filtered by the search. */
    private void refreshList(){
        var ids=new ArrayList<String>();
        for(var raw:rules){String id=raw.getAsJsonObject().get("effect").getAsString();if(!ids.contains(id))ids.add(id);}
        KineticRegistries.mobEffects().ids().stream().map(Object::toString).sorted().filter(id->!ids.contains(id)).forEach(ids::add);
        var visible=ids.stream().filter(id->dev.xyat.kineticcore.api.client.search.KineticSearch.match(id+" "+RegistryRuleList.name(true,id).getString(),query)).toList();
        list.setItems(visible);
        int current=visible.indexOf(selected);
        list.setSelectedIndex(current);
        if(listScroll>=0)list.setScrollOffset(listScroll);else if(current>=0)list.scrollTo(current);
    }
    private boolean hasRule(String id){for(var raw:rules)if(raw.getAsJsonObject().get("effect").getAsString().equals(id))return true;return false;}
    private Component summary(String id){
        for(var raw:rules){var row=raw.getAsJsonObject();if(!row.get("effect").getAsString().equals(id))continue;
            return Component.literal((row.get("amplifier").getAsInt()+1)+" / "+row.get("duration").getAsInt()/20.0+"s / "+Math.round(row.get("probability").getAsDouble()*100)+"%");}
        return Component.empty();
    }
    private List<String> form(){return List.of(selected,seconds,level,probability);}
    private void selectExisting(){seconds="10";level="1";probability="100";for(int i=0;i<rules.size();i++)if(rules.get(i).getAsJsonObject().get("effect").getAsString().equals(selected)){select(i);return;}formBaseline=form();}
    private void select(int index){var row=rules.get(index).getAsJsonObject();selected=row.get("effect").getAsString();seconds=Double.toString(row.get("duration").getAsInt()/20.0);level=Integer.toString(row.get("amplifier").getAsInt()+1);probability=Double.toString(row.get("probability").getAsDouble()*100);formBaseline=form();}
    private void remove(){for(int i=rules.size()-1;i>=0;i--)if(rules.get(i).getAsJsonObject().get("effect").getAsString().equals(selected))rules.remove(i);formBaseline=form();error="";}
    private void save(){if(!form().equals(formBaseline)&&!applyRule())return;done.accept(rules.deepCopy(),mode);close();}
    private boolean applyRule(){
        try{double duration=Double.parseDouble(seconds),chance=Double.parseDouble(probability);int tier=Integer.parseInt(level);
            if(!Double.isFinite(duration)||duration<0||duration>360000||!Double.isFinite(chance)||chance<0||chance>100||tier<1||tier>256)throw new IllegalArgumentException();
            var row=new JsonObject();row.addProperty("effect",selected);row.addProperty("duration",Math.round(duration*20));row.addProperty("amplifier",tier-1);row.addProperty("probability",chance/100);
            var candidate=rules.deepCopy();for(int i=candidate.size()-1;i>=0;i--)if(candidate.get(i).getAsJsonObject().get("effect").getAsString().equals(selected))candidate.remove(i);candidate.add(row);
            var root=new JsonObject();root.add("food_effects",candidate);ItemCapabilitySettings.parse(root);
            remove();rules.add(row);error="";
        }catch(RuntimeException ex){error="error.invalid_value";rebuild();return false;}rebuild();return true;
    }
    @Override protected void renderBackground(KineticGraphics g,int x,int y,float partial){KineticTheme.panel(g,0,0,width(),height());KineticTheme.separator(g,4,24,632);KineticTheme.verticalSeparator(g,FORM_X-5,28,328);g.scrollingText(title(),52,8,APPLY_X-4-52,0xFFFFFFFF,true);
        // The effect being edited; another one is picked in the list on the left.
        KineticTheme.surface(g,FORM_X,30,FORM_W,16,KineticTheme.Surface.PANEL_ALT);
        var effect=KineticRegistries.mobEffects().get(KineticResourceIds.tryParse(selected));
        if(effect!=null)g.effectIcon(effect,FORM_X+2,30,16);
        g.scrollingText(RegistryRuleList.name(true,selected).copy().withStyle(net.minecraft.ChatFormatting.GOLD).append(Component.literal("  "+selected).withStyle(net.minecraft.ChatFormatting.GRAY)),FORM_X+21,34,FORM_W-25,0xFFFFFFFF,false);
        String[] labels={"food.duration","food.level","food.probability","food.mode"};int[] xs={FORM_X,FORM_X+NUMBER_W+4,FORM_X+2*(NUMBER_W+4),FORM_X};int[] ys={ROW1_Y-11,ROW1_Y-11,ROW1_Y-11,ROW2_Y-11};int[] widths={NUMBER_W,NUMBER_W,NUMBER_W,112};
        for(int i=0;i<labels.length;i++)g.scrollingText(ItemRuleLabels.text(labels[i]),xs[i],ys[i],widths[i],0xFFFFFFFF,false);
        if(!error.isEmpty())g.scrollingText(ItemRuleLabels.text(error),FORM_X,ROW2_Y+24,FORM_W,0xFFFF7777,false);
    }
}
