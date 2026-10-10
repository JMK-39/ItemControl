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

/** Visual food effects, editing a detached draft until Apply is pressed. */
public final class FoodEffectEditorPage extends KineticPage {
    private final JsonArray rules;
    private final BiConsumer<JsonArray,String> done;
    private String selected="minecraft:speed",seconds="10",level="1",probability="100",mode,error="";
    public FoodEffectEditorPage(JsonArray rules,String mode,BiConsumer<JsonArray,String> done) {
        super(ItemRuleLabels.text("food.effects")); this.rules=rules.deepCopy();this.mode=mode;this.done=done;useCanvas(520,320,6);
        if(!rules.isEmpty())select(0);
    }
    @Override protected void build(KineticUi ui) {
        ui.button(12,6,90).text(ItemRuleLabels.text("cancel")).onClick(this::close).build();
        var options=new ArrayList<KineticDropdown.Option>();
        for(var id:KineticRegistries.mobEffects().ids().stream().sorted(java.util.Comparator.comparing(Object::toString)).toList()) {
            var effect=KineticRegistries.mobEffects().get(id);
            options.add(new KineticDropdown.Option(id.toString(),ItemRuleLabels.id(id.toString(),effect.getDescriptionId())));
        }
        ui.dropdown(12,50,496,options).selected(selected).tooltip(ItemRuleLabels.text("food.effect.tooltip")).onChange(value->{selected=value;selectExisting();rebuild();}).build();
        var duration=ui.textField(12,98,104).tooltip(ItemRuleLabels.text("food.duration.tooltip")).build();duration.setTextValue(seconds);duration.onTextChange(value->seconds=value);
        var amplifier=ui.textField(126,98,94).tooltip(ItemRuleLabels.text("food.level.tooltip")).build();amplifier.setTextValue(level);amplifier.onTextChange(value->level=value);
        var chance=ui.textField(230,98,94).tooltip(ItemRuleLabels.text("food.probability.tooltip")).build();chance.setTextValue(probability);chance.onTextChange(value->probability=value);
        ui.dropdown(334,98,174,List.of(new KineticDropdown.Option("append",ItemRuleLabels.text("food.mode.append")),new KineticDropdown.Option("replace",ItemRuleLabels.text("food.mode.replace")))).selected(mode).tooltip(ItemRuleLabels.text("food.mode.tooltip")).onChange(value->mode=value).build();
        List<SelectionItem> rows=new ArrayList<>();
        for(var raw:rules){var row=raw.getAsJsonObject();String id=row.get("effect").getAsString();var type=KineticRegistries.mobEffects().get(KineticResourceIds.parse(id));
            rows.add(new SelectionItem(type==null?Component.literal(id):ItemRuleLabels.id(id,type.getDescriptionId()),Component.literal("  "+(row.get("amplifier").getAsInt()+1)+" / "+row.get("duration").getAsInt()/20.0+"s / "+row.get("probability").getAsDouble()*100+"%"),ItemRuleLabels.text("food.effect_row.tooltip"),true,false));}
        ui.selectionList(12,150,496,104,rows).textRows().onSelect(index->{select(index);rebuild();}).build();
        ui.button(12,278,124).text(ItemRuleLabels.text("curio.apply_rule")).tooltip(ItemRuleLabels.text("food.add.tooltip")).onClick(this::applyRule).build();
        ui.button(144,278,124).text(ItemRuleLabels.text("curio.delete_rule")).onClick(()->{remove();rebuild();}).build();
        ui.button(374,278,134).text(ItemRuleLabels.text("save")).tooltip(ItemRuleLabels.text("draft.apply.tooltip")).onClick(()->{done.accept(rules.deepCopy(),mode);close();}).build();
    }
    private void selectExisting(){for(int i=0;i<rules.size();i++)if(rules.get(i).getAsJsonObject().get("effect").getAsString().equals(selected)){select(i);return;}}
    private void select(int index){var row=rules.get(index).getAsJsonObject();selected=row.get("effect").getAsString();seconds=Double.toString(row.get("duration").getAsInt()/20.0);level=Integer.toString(row.get("amplifier").getAsInt()+1);probability=Double.toString(row.get("probability").getAsDouble()*100);}
    private void remove(){for(int i=rules.size()-1;i>=0;i--)if(rules.get(i).getAsJsonObject().get("effect").getAsString().equals(selected))rules.remove(i);}
    private void applyRule(){
        try{double duration=Double.parseDouble(seconds),chance=Double.parseDouble(probability);int tier=Integer.parseInt(level);
            if(!Double.isFinite(duration)||duration<0||duration>360000||!Double.isFinite(chance)||chance<0||chance>100||tier<1||tier>256)throw new IllegalArgumentException();
            var row=new JsonObject();row.addProperty("effect",selected);row.addProperty("duration",Math.round(duration*20));row.addProperty("amplifier",tier-1);row.addProperty("probability",chance/100);
            var candidate=rules.deepCopy();for(int i=candidate.size()-1;i>=0;i--)if(candidate.get(i).getAsJsonObject().get("effect").getAsString().equals(selected))candidate.remove(i);candidate.add(row);
            var root=new JsonObject();root.add("food_effects",candidate);ItemCapabilitySettings.parse(root);
            remove();rules.add(row);error="";
        }catch(RuntimeException ex){error="error.invalid_value";}rebuild();
    }
    @Override protected void renderBackground(KineticGraphics g,int x,int y,float partial){KineticTheme.panel(g,0,0,width(),height());g.scrollingText(title(),112,12,396,0xFFFFFFFF,true);
        String[] labels={"food.duration","food.level","food.probability","food.mode"};int[] xs={12,126,230,334};int[] widths={104,94,94,174};for(int i=0;i<labels.length;i++)g.scrollingText(ItemRuleLabels.text(labels[i]),xs[i],84,widths[i],0xFFFFFFFF,false);
        if(!error.isEmpty())g.scrollingText(ItemRuleLabels.text(error),12,260,496,0xFFFFAA00,false);
    }
}
