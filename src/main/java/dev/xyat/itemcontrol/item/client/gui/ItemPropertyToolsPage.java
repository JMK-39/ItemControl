package dev.xyat.itemcontrol.item.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.config.ItemRuleDrafts;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticDropdown;
import dev.xyat.kineticcore.api.client.gui.widget.list.*;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Selective draft copy, batch application and reusable local templates. */
public final class ItemPropertyToolsPage extends KineticPage {
    private final JsonObject document;
    private final Consumer<JsonObject> done;
    private final Function<String,Map<String,String>> originals;
    private final Set<String> checked=new LinkedHashSet<>(),targets=new LinkedHashSet<>();
    private final List<KineticItemSearch.CachedItem> items=ItemSearchCache.getAllItems();
    private List<KineticItemSearch.CachedItem> visible=List.of();
    private Map<String,ItemRuleDrafts.Template> templates=new LinkedHashMap<>();
    private JsonObject source;
    private String sourceId,query="",name="",selectedTemplate="none",message="";
    private KineticItemGrid grid;
    public ItemPropertyToolsPage(JsonObject document,String selected,Consumer<JsonObject> done,Function<String,Map<String,String>> originals){
        super(ItemRuleLabels.text("tools"));this.document=document.deepCopy();this.done=done;this.originals=originals;sourceId=selected;
        source=document.has(selected)&&document.get(selected).isJsonObject()?document.getAsJsonObject(selected).deepCopy():new JsonObject();
        targets.add(selected);selectSourceFields();useCanvas(640,360,6);
        try{templates=ItemPropertyTemplates.load();}catch(Exception error){message="templates.error";}
    }
    private void selectSourceFields(){checked.clear();for(String path:ItemRuleDrafts.FIELDS)if(ItemRuleDrafts.value(source,path)!=null)checked.add(path);}
    @Override protected void build(KineticUi ui){
        ui.button(8,6,68).text(ItemRuleLabels.text("cancel")).onClick(this::close).build();
        ui.button(420,6,100).text(ItemRuleLabels.text("copy.source")).tooltip(ItemRuleLabels.text("copy.source.tooltip")).onClick(this::chooseSource).build();
        ui.button(528,6,104).text(ItemRuleLabels.text("compare")).tooltip(ItemRuleLabels.text("compare.tooltip")).onClick(()->KineticGui.open(new ItemPropertyComparisonPage(sourceId,source,originals.apply(sourceId)))).build();
        var field=ui.textField(8,44,184).tooltip(ItemRuleLabels.text("templates.name.tooltip")).build();field.setTextValue(name);field.setPlaceholder(ItemRuleLabels.text("templates.name"));field.onTextChange(value->name=value);
        var options=new ArrayList<KineticDropdown.Option>();options.add(new KineticDropdown.Option("none",ItemRuleLabels.text("templates.choose")));templates.keySet().forEach(value->options.add(new KineticDropdown.Option("template/"+value,Component.literal(value))));
        ui.dropdown(200,44,188,options).selected(selectedTemplate).tooltip(ItemRuleLabels.text("templates.choose.tooltip")).onChange(value->{if(!value.startsWith("template/"))return;var template=templates.get(value.substring(9));if(template==null)return;selectedTemplate=value;name=template.name();source=template.fields().deepCopy();checked.clear();checked.addAll(template.selectedFields());rebuild();}).build();
        ui.button(396,44,72).text(ItemRuleLabels.text("templates.store")).tooltip(ItemRuleLabels.text("templates.store.tooltip")).onClick(this::store).build();
        ui.button(476,44,72).text(ItemRuleLabels.text("templates.import")).tooltip(ItemRuleLabels.text("templates.import.tooltip")).onClick(this::importTemplate).build();
        ui.button(556,44,76).text(ItemRuleLabels.text("templates.export")).tooltip(ItemRuleLabels.text("templates.export.tooltip")).onClick(this::exportTemplate).build();
        var rows=new ArrayList<ToggleItem>();for(String path:ItemRuleDrafts.FIELDS){String key=fieldKey(path);rows.add(new ToggleItem(ItemRuleLabels.text(key),ItemRuleLabels.text("batch.field.tooltip",ItemRuleLabels.text(key)).copy().append("\n"+path),checked.contains(path),true));}
        ui.toggleList(8,96,194,220,rows).textRows().onToggle((index,value)->{String path=ItemRuleDrafts.FIELDS.get(index);if(value)checked.add(path);else checked.remove(path);}).build();
        var search=ui.textField(214,84,266).build();search.setTextValue(query);search.setPlaceholder(ItemRuleLabels.text("search"));search.onTextChange(value->{query=value;refresh();});
        ui.button(488,84,144).text(ItemRuleLabels.text("batch.select_results")).tooltip(ItemRuleLabels.text("batch.select_results.tooltip")).onClick(()->{visible.forEach(item->targets.add(item.id()));refresh();}).build();
        grid=ui.itemGrid(214,112,418,204,ItemGridDensity.COMPACT,gridItems()).onClick(index->{String id=visible.get(index).id();if(!targets.add(id))targets.remove(id);refresh();}).build();
        ui.button(8,334,92).text(ItemRuleLabels.text("batch.clear_targets")).onClick(()->{targets.clear();refresh();}).build();
        ui.button(498,334,134).text(ItemRuleLabels.text("batch.apply")).tooltip(ItemRuleLabels.text("batch.apply.tooltip")).onClick(this::apply).build();
    }
    private List<ItemGridItem> gridItems(){visible=items.stream().filter(item->item.matches(query,KineticItemSearch.ItemCategory.GENERAL)).toList();return visible.stream().map(item->new ItemGridItem(item.stack(),Component.literal(item.id()),true,targets.contains(item.id()),false,targets.contains(item.id())?ItemGridOutline.SUCCESS:ItemGridOutline.NONE)).toList();}
    private void refresh(){if(grid!=null)grid.setItems(gridItems());}
    private void chooseSource(){var options=KineticSelectors.ItemSelectorOptions.itemsOnly(KineticSelectors.ItemSelectorPreset.defaults(),List.of(),stack->true);KineticSelectors.openItemSelectorWithOptions(options,result->{sourceId=result.value();var raw=document.get(sourceId);source=raw!=null&&raw.isJsonObject()?raw.getAsJsonObject().deepCopy():new JsonObject();selectSourceFields();selectedTemplate="none";rebuild();});}
    private void apply(){
        if(checked.isEmpty()||targets.isEmpty()){message="batch.empty";return;}
        try{var changed=document.deepCopy();for(String id:targets){var raw=changed.get(id);if(raw!=null&&!raw.isJsonObject())throw new IllegalArgumentException("malformed target");changed.add(id,ItemRuleDrafts.merge(raw==null?new JsonObject():raw.getAsJsonObject(),source,checked));}done.accept(changed);close();}
        catch(RuntimeException ex){message="error.invalid_rule";}
    }
    private String encoded(){return ItemRuleDrafts.encodeTemplate(name,source,checked);}
    private void store(){try{var template=ItemRuleDrafts.decodeTemplate(encoded());templates.put(template.name(),template);ItemPropertyTemplates.save(templates);selectedTemplate="template/"+template.name();message="templates.stored";rebuild();}catch(Exception ex){message="templates.error";}}
    private void importTemplate(){try{var path=ItemPropertyTemplates.selectFile(false);if(path==null)return;var template=ItemPropertyTemplates.read(path);templates.put(template.name(),template);ItemPropertyTemplates.save(templates);source=template.fields().deepCopy();name=template.name();selectedTemplate="template/"+name;checked.clear();checked.addAll(template.selectedFields());message="templates.imported";rebuild();}catch(Exception ex){message="templates.error";}}
    private void exportTemplate(){try{String encoded=encoded();var path=ItemPropertyTemplates.selectFile(true);if(path==null)return;ItemPropertyTemplates.write(path,encoded);message="templates.exported";}catch(Exception ex){message="templates.error";}}
    static String fieldKey(String path){return switch(path){case "curio.enabled"->"field.curio_enabled";case "curio.slots"->"curio.slots_label";case "curio.can_unequip"->"field.curio_unequip";case "curio.attributes"->"curio.edit_attributes";case "curio.slot_modifiers"->"curio.slot_counts";case "food_effects"->"food.effects";case "food_effects_mode"->"food.mode";case "food_remainder"->"food.remainder";default->"field."+path;};}
    @Override protected void renderBackground(KineticGraphics g,int x,int y,float partial){KineticTheme.panel(g,0,0,width(),height());g.scrollingText(title(),84,12,326,0xFFFFFFFF,true);g.scrollingText(ItemRuleLabels.text("batch.fields"),8,82,194,0xFFFFFFFF,false);g.scrollingText(ItemRuleLabels.text("batch.targets",targets.size()),108,340,374,0xFFFFFFFF,false);if(!message.isEmpty())g.scrollingText(ItemRuleLabels.text(message),214,320,418,0xFFFFAA00,false);if(grid!=null&&grid.hoveredStack()!=null&&!grid.hoveredStack().isEmpty())showItemTooltip(grid.hoveredStack());}
}
