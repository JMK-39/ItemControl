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
    private String sourceId,query="",name="",selectedTemplate="none";
    private KineticItemGrid grid;
    public ItemPropertyToolsPage(JsonObject document,String selected,Consumer<JsonObject> done,Function<String,Map<String,String>> originals){
        super(ItemRuleLabels.text("tools"));this.document=document.deepCopy();this.done=done;this.originals=originals;sourceId=selected;
        source=document.has(selected)&&document.get(selected).isJsonObject()?document.getAsJsonObject(selected).deepCopy():new JsonObject();
        targets.add(selected);selectSourceFields();useCanvas(640, 360, 4);
        try{templates=ItemPropertyTemplates.load();}catch(Exception error){note("templates.error");}
    }
    private void selectSourceFields(){checked.clear();for(String path:ItemRuleDrafts.FIELDS)if(ItemRuleDrafts.value(source,path)!=null)checked.add(path);}
    // Every button sits in the header row, each as wide as its label; the fields to copy fill the left column and the
    // target items the right, under one row with the search, Select Results, Clear and the target count.
    private static final int GRID_W=17*24-2+6,GRID_X=636-GRID_W,LIST_W=GRID_X-4-8,ROW_Y=28,LIST_Y=44;
    @Override protected void build(KineticUi ui){
        ui.button(4,4,40).text(ItemRuleLabels.text("back")).onClick(this::close).build();
        ui.button(48,4,64).text(ItemRuleLabels.text("copy.source")).tooltip(ItemRuleLabels.text("copy.source.tooltip")).onClick(this::chooseSource).build();
        ui.button(116,4,72).text(ItemRuleLabels.text("compare")).tooltip(ItemRuleLabels.text("compare.tooltip")).onClick(()->openChild(new ItemPropertyComparisonPage(sourceId,source,originals.apply(sourceId)))).build();
        var field=ui.textField(196,4,96).tooltip(ItemRuleLabels.text("templates.name.tooltip")).build();field.setTextValue(name);field.setPlaceholder(ItemRuleLabels.text("templates.name"));field.onTextChange(value->name=value);
        var options=new ArrayList<KineticDropdown.Option>();options.add(new KineticDropdown.Option("none",ItemRuleLabels.text("templates.choose")));templates.keySet().forEach(value->options.add(new KineticDropdown.Option("template/"+value,Component.literal(value))));
        ui.dropdown(296,4,96,options).selected(selectedTemplate).tooltip(ItemRuleLabels.text("templates.choose.tooltip")).onChange(value->{if(!value.startsWith("template/"))return;var template=templates.get(value.substring(9));if(template==null)return;selectedTemplate=value;name=template.name();source=template.fields().deepCopy();checked.clear();checked.addAll(template.selectedFields());rebuild();}).build();
        ui.button(396,4,48).text(ItemRuleLabels.text("templates.store")).tooltip(ItemRuleLabels.text("templates.store.tooltip")).onClick(this::store).build();
        ui.button(448,4,48).text(ItemRuleLabels.text("templates.import")).tooltip(ItemRuleLabels.text("templates.import.tooltip")).onClick(this::importTemplate).build();
        ui.button(500,4,48).text(ItemRuleLabels.text("templates.export")).tooltip(ItemRuleLabels.text("templates.export.tooltip")).onClick(this::exportTemplate).build();
        ui.button(636-56,4,56).text(ItemRuleLabels.text("batch.apply")).tooltip(ItemRuleLabels.text("batch.apply.tooltip")).onClick(this::apply).build();
        var rows=new ArrayList<ToggleItem>();for(String path:ItemRuleDrafts.FIELDS){String key=fieldKey(path);rows.add(new ToggleItem(ItemRuleLabels.text(key),ItemRuleLabels.text("batch.field.tooltip",ItemRuleLabels.text(key)).copy().append("\n"+path),checked.contains(path),true));}
        ui.toggleList(4,LIST_Y,LIST_W,356-LIST_Y,rows).textRows().onToggle((index,value)->{String path=ItemRuleDrafts.FIELDS.get(index);if(value)checked.add(path);else checked.remove(path);}).build();
        var search=ui.textField(GRID_X,ROW_Y,200).build();search.setTextValue(query);search.setPlaceholder(ItemRuleLabels.text("search"));search.onTextChange(value->{query=value;refresh();});
        ui.button(GRID_X+204,ROW_Y,72).text(ItemRuleLabels.text("batch.select_results")).tooltip(ItemRuleLabels.text("batch.select_results.tooltip")).onClick(()->{visible.forEach(item->targets.add(item.id()));refresh();}).build();
        ui.button(GRID_X+280,ROW_Y,56).text(ItemRuleLabels.text("batch.clear_targets")).onClick(()->{targets.clear();refresh();}).build();
        grid=ui.itemGrid(GRID_X,LIST_Y+2,GRID_W,356-LIST_Y-2,ItemGridDensity.COMPACT_FLUSH,gridItems()).onClick(index->{String id=visible.get(index).id();if(!targets.add(id))targets.remove(id);refresh();}).build();
    }
    private List<ItemGridItem> gridItems(){visible=items.stream().filter(item->item.matches(query,KineticItemSearch.ItemCategory.GENERAL)).toList();return visible.stream().map(item->new ItemGridItem(item.stack(),Component.literal(item.id()),true,targets.contains(item.id()),false,targets.contains(item.id())?ItemGridOutline.SUCCESS:ItemGridOutline.NONE)).toList();}
    private void refresh(){if(grid!=null)grid.setItems(gridItems());}
    private void chooseSource(){var options=KineticSelectors.ItemSelectorOptions.itemsOnly(KineticSelectors.ItemSelectorPreset.defaults(),List.of(),stack->true);KineticSelectors.openItemSelectorWithOptions(options,result->{sourceId=result.value();var raw=document.get(sourceId);source=raw!=null&&raw.isJsonObject()?raw.getAsJsonObject().deepCopy():new JsonObject();selectSourceFields();selectedTemplate="none";rebuild();});}
    private void apply(){
        if(checked.isEmpty()||targets.isEmpty()){note("batch.empty");return;}
        try{var changed=document.deepCopy();for(String id:targets){var raw=changed.get(id);if(raw!=null&&!raw.isJsonObject())throw new IllegalArgumentException("malformed target");changed.add(id,ItemRuleDrafts.merge(raw==null?new JsonObject():raw.getAsJsonObject(),source,checked));}done.accept(changed);close();}
        catch(RuntimeException ex){note("error.invalid_rule");}
    }
    private void note(String key){dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays.toast(ItemRuleLabels.text(key));}
    private String encoded(){return ItemRuleDrafts.encodeTemplate(name,source,checked);}
    private void store(){try{var template=ItemRuleDrafts.decodeTemplate(encoded());templates.put(template.name(),template);ItemPropertyTemplates.save(templates);selectedTemplate="template/"+template.name();note("templates.stored");rebuild();}catch(Exception ex){note("templates.error");}}
    private void importTemplate(){try{var path=ItemPropertyTemplates.selectFile(false);if(path==null)return;var template=ItemPropertyTemplates.read(path);templates.put(template.name(),template);ItemPropertyTemplates.save(templates);source=template.fields().deepCopy();name=template.name();selectedTemplate="template/"+name;checked.clear();checked.addAll(template.selectedFields());note("templates.imported");rebuild();}catch(Exception ex){note("templates.error");}}
    private void exportTemplate(){try{String encoded=encoded();var path=ItemPropertyTemplates.selectFile(true);if(path==null)return;ItemPropertyTemplates.write(path,encoded);note("templates.exported");}catch(Exception ex){note("templates.error");}}
    static String fieldKey(String path){return switch(path){case "curio.enabled"->"field.curio_enabled";case "curio.slots"->"curio.slots_label";case "curio.can_unequip"->"field.curio_unequip";case "curio.attributes"->"curio.edit_attributes";case "curio.slot_modifiers"->"curio.slot_counts";case "food_effects"->"food.effects";case "food_effects_mode"->"food.mode";case "food_remainder"->"food.remainder";default->"field."+path;};}
    @Override protected void renderBackground(KineticGraphics g,int x,int y,float partial){KineticTheme.panel(g,0,0,width(),height());KineticTheme.separator(g,4,24,632);KineticTheme.verticalSeparator(g,GRID_X-4,28,328);
        g.scrollingText(ItemRuleLabels.text("batch.fields"),4,ROW_Y+4,LIST_W,0xFFFFFFFF,false);
        g.scrollingText(ItemRuleLabels.text("batch.targets",targets.size()),GRID_X+340,ROW_Y+4,GRID_W-340,0xFFFFFFFF,false);
        if(grid!=null&&grid.hoveredStack()!=null&&!grid.hoveredStack().isEmpty())showItemTooltip(grid.hoveredStack());}
}
