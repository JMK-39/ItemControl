package dev.xyat.itemcontrolvalidation;

import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.trading.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*** Uses the existing client and unsaved page drafts. Never clicks or saves editor changes. */
public final class GuiLongTextValidation {
    private static final Logger LOG=LoggerFactory.getLogger(GuiLongTextValidation.class);
    private static final String ROOT=System.getProperty("itemcontrol.guiValidation.output","D:/IDEAWork/ItemControl/.gradle/gui-long-text-20261004/");
    private static final String[] NAMES={"property-combat","property-tool","property-food","property-general","property-block","property-protection","protection-list","protection-modal-new","protection-modal-existing","direct-immunity","damage-types","banned","banned-mods","banned-tags","merge-collapsed","merge-expanded","item-tags","tag-suggestions","cleaner","rules-empty","rules-blacklist","rules-area","creative-tabs","components","components-invalid","property-curio","curio-slots","curio-attributes"};
    private static boolean installed,started,screenshot,finished,originalFullscreen;
    private static String originalLanguage;
    private static int originalScale,originalWidth,originalHeight,phase=-1,page=-1,captures,failures;
    private static long due;
    private static final BitSet capturedPages = new BitSet();
    private static CompletableFuture<Void> reload;
    private static Language stressOriginal;
    private static dev.xyat.itemcontrol.tabs.TabConfig.Data originalTabEditing;
    private static boolean tabEditingCaptured;

    public static void install() { if(installed)return;installed=true;KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,GuiLongTextValidation::tick); }
    private static void tick() {
        if(finished)return;
        try {
            var mc=Minecraft.getInstance();
            if(!started) {
                if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
                if(Boolean.getBoolean("itemcontrol.propertyValidation") && !ItemPropertyRuntimeChecks.done)return;
                if(ItemPropertyRuntimeChecks.failed)throw new AssertionError("Property runtime validation failed");
                started=true;originalLanguage=mc.getLanguageManager().getSelected();originalScale=mc.options.guiScale().get();
                originalWidth=mc.getWindow().getWidth();originalHeight=mc.getWindow().getHeight();originalFullscreen=mc.getWindow().isFullscreen();
                mc.options.guiScale().set(0);
                if(originalFullscreen)mc.getWindow().toggleFullScreen();
                nextPhase();
                return;
            }
            if(reload!=null) {
                if(!reload.isDone() || mc.getOverlay()!=null)return;
                reload.join();reload=null;
                if(phase==4) {
                    stressOriginal=Language.getInstance();
                    Language.inject(new StressLanguage(stressOriginal));
                }
                nextPage();return;
            }
            long now=System.currentTimeMillis();
            if(!screenshot && now>=due) { if(page==14)verifyMergeGridBoundary();capture("start");screenshot=true;due=now+(phase==4?3400:550);return; }
            if(screenshot && now>=due) {
                if(phase==4)capture("scroll");
                nextPage();
            }
        } catch(Throwable error) {
            failures++;LOG.error("ITEM_GUI_FAIL phase="+phase+" page="+page,error);
            finish();
        }
    }
    private static void nextPhase() {
        if(stressOriginal!=null){Language.inject(stressOriginal);stressOriginal=null;}
        phase++;page=-1;
        boolean fullHd=Boolean.getBoolean("itemcontrol.guiValidation.fullHdOnly");
        if(phase>=(fullHd?2:5)){finish();return;}
        var mc=Minecraft.getInstance();
        mc.setScreen(null);
        String lang=fullHd?(phase==1?"zh_cn":"en_us"):phase==2 || phase==3?"zh_cn":"en_us";
        mc.getLanguageManager().setSelected(lang);
        mc.options.languageCode=lang;
        int width=fullHd||phase==1 || phase==3?1920:854,height=fullHd||phase==1 || phase==3?1080:480;
        mc.getWindow().setWindowed(width,height);mc.resizeDisplay();
        reload=mc.reloadResourcePacks();
        LOG.info("ITEM_GUI_PHASE phase={} language={} requested={}x{} autoScale=true",phase,lang,width,height);
    }
    private static void nextPage() throws Exception {
        page++;
        String selectedPages=System.getProperty("itemcontrol.guiValidation.pages", "");
        while(page<NAMES.length && !selectedPages.isBlank() && !List.of(selectedPages.split(",")).contains(String.valueOf(page)))page++;
        if(page>=NAMES.length){nextPhase();return;}
        if(phase==0 && capturedPages.isEmpty())verifyTextListSpacing();
        openPage(page);
        screenshot=false;due=System.currentTimeMillis()+1000;
        LOG.info("ITEM_GUI_OPEN phase={} case={} page={}",phase,NAMES[page],KineticGui.currentPage()!=null?KineticGui.currentPage().getClass().getName():String.valueOf(Minecraft.getInstance().screen));
    }
    @SuppressWarnings("unchecked")
    private static void openPage(int index) throws Exception {
        var mc=Minecraft.getInstance();
        String longText="A deliberately very long named sword to exercise bounded source and target scrolling without changing player inventory ".repeat(3);
        String longItem = "minecraft:diamond_sword[custom_name='\"" + longText + "\"']";
        switch(index) {
            case 0,1,2,3,4,5 -> {
                var p=new dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage("{}");KineticGui.open(p);
                var categories=Class.forName("dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage$EditorCategory").getEnumConstants();
                if(index>0)invoke(p,"changeCategory",categories[index]);
                invoke(p,"selectGridIndex",0);
            }
            case 6,7,8 -> {
                var p=new dev.xyat.itemcontrol.item.client.gui.ProtectionItemEditorPage(List.of("minecraft:diamond_sword;true;true;false;false"));KineticGui.open(p);
                if(index>=7)invoke(p,"openRuleEditor","minecraft:diamond_sword",new ItemStack(Items.DIAMOND_SWORD),index==8?invoke(p,"findRule","minecraft:diamond_sword"):null);
            }
            case 9 -> KineticGui.open(new dev.xyat.itemcontrol.item.client.gui.DirectEntityImmunityEditorPage(List.of("minecraft:zombie","#example:deliberately_long_entity_tag_for_rule_view")));
            case 10 -> KineticGui.open(new dev.xyat.itemcontrol.item.client.gui.DamageTypeEditorPage(List.of("minecraft:in_fire","#minecraft:is_fire","example:deliberately_long_damage_type_identifier_for_scrolling")));
            case 11,12,13 -> {
                var p=new dev.xyat.itemcontrol.item.client.gui.BannedItemPage();KineticGui.open(p);
                if(index>=12){
                    setField(p,"isAutoCompleteMode",true);
                    var rows=(List<String>)field(p,"autoCompleteList");rows.clear();rows.add((index==12?"@":"#")+"example_a_deliberately_very_long_identifier_to_exercise_the_autocomplete_row_viewport".repeat(3));
                }
            }
            case 14,15 -> {
                var p=new dev.xyat.itemcontrol.item.client.gui.MergeItemPage();KineticGui.open(p);
                var rules=(Map<String,List<String>>)field(p,"tempRules");rules.clear();rules.put(longItem,new ArrayList<>(List.of(longItem,"minecraft:diamond")));
                setField(p,"selectedTarget",longItem);
                if(index==15)((Set<String>)field(p,"expandedTargets")).add(longItem);
                invoke(p,"updateLeftEntries");invoke(p,"updateRightPanel");
            }
            case 16,17 -> {
                var p=new dev.xyat.itemcontrol.item.client.gui.ItemTagEditorPage();KineticGui.open(p);
                var items=(List<dev.xyat.kineticcore.api.client.search.KineticItemSearch.CachedItem>)field(p,"allItems");
                invoke(p,"selectItem",items.stream().filter(i->i.id().equals("minecraft:diamond_sword")).findFirst().orElseThrow());
                var rows=(List<Object>)field(p,"displayedTags");rows.clear();
                for(var source:Class.forName("dev.xyat.itemcontrol.item.client.gui.ItemTagEditorPage$TagSource").getEnumConstants())rows.add(construct("dev.xyat.itemcontrol.item.client.gui.ItemTagEditorPage$TagEntry","example:a_very_long_tag_identifier_to_exercise_the_entire_tag_row",source));
                if(index==17){
                    var input=field(p,"tagInput");invoke(input,"setTextValue","example");invoke(p,"focus",input);
                    setField(p,"tagSuggestions",new ArrayList<>(List.of("example:a_very_long_tag_identifier_to_exercise_the_entire_suggestion_row".repeat(3))));
                }
            }
            case 18 -> {
                var container=new net.minecraft.world.SimpleContainer(54);container.setItem(0,new ItemStack(Items.DIAMOND,1234));
                var menu=new dev.xyat.itemcontrol.cleaner.client.gui.CleanerMenu(0,mc.player.getInventory(),container);
                var p=new dev.xyat.itemcontrol.cleaner.client.gui.CleanerPage(menu,dev.xyat.kineticcore.api.text.KineticI18n.translatable("gui.itemcontrol.cleaner.cleaner.title"));
                mc.setScreen((net.minecraft.client.gui.screens.Screen)construct("dev.xyat.kineticcore.internal.client.gui.page.PageContainerScreen",p,mc.player.getInventory(),p.title()));
            }
            case 19,20,21 -> KineticGui.open(new dev.xyat.itemcontrol.cleaner.client.gui.CleanerItemRuleEditorPage(dev.xyat.itemcontrol.cleaner.client.gui.CleanerItemRuleEditorPage.Mode.values()[index-19],index==19?List.of():List.of("minecraft:diamond"),values->false));
            case 22 -> {
                if(!tabEditingCaptured){originalTabEditing=dev.xyat.itemcontrol.tabs.TabConfig.currentEditing;tabEditingCaptured=true;}
                dev.xyat.itemcontrol.tabs.TabConfig.currentEditing=new dev.xyat.itemcontrol.tabs.TabConfig.Data();
                var p=new dev.xyat.itemcontrol.tabs.gui.TabUnifiedPage();KineticGui.open(p);
                var tabs=(List<?>)field(p,"mainTabs");
                for(int i=0;i<tabs.size();i++)if("minecraft:building_blocks".equals(String.valueOf(field(tabs.get(i),"id")))){
                    setField(p,"mainSelectedTabIdx",i);invoke(p,"refreshData");break;
                }
                if(((List<?>)field(p,"mainItems")).isEmpty())throw new AssertionError("Creative tab contents were not initialized");
            }
            case 23,24 -> dev.xyat.itemcontrol.item.client.gui.ItemDataEditor.open(null,"minecraft:diamond_sword",index==23?"[damage=1]":"[invalid=]",value->{});
            case 25,26,27 -> {
                var p=new dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage("{\"minecraft:stick\":{\"curio\":{\"enabled\":true,\"slots\":[\"curio\"],\"can_unequip\":false,\"attributes\":[{\"attribute\":\"minecraft:generic.armor\",\"operation\":\"ADDITION\",\"amount\":3}]}}}");
                KineticGui.open(p);
                var categories=Class.forName("dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage$EditorCategory").getEnumConstants();
                invoke(p,"changeCategory",categories[6]); invoke(p,"selectGridIndex",0);
                if(((dev.xyat.kineticcore.api.client.gui.widget.list.KineticItemGrid)field(p,"itemGrid")).columns()!=9)throw new AssertionError("Property browser fits nine complete columns inside its existing divider");
                if(!dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) {
                    var slots=(dev.xyat.kineticcore.api.client.gui.widget.KineticButton)field(p,"curioSlotButton");
                    if(slots.isEnabled())throw new AssertionError("Curios controls must be disabled without the dependency");
                    LOG.info("ITEM_CURIO_ABSENT_PASS optional startup and disabled controls");
                }
                if(index==26) {
                    if(phase==0 && dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available())verifyNativeSlotDraft(p);
                    invoke(p,"openCurioSlots");
                    if(dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) {
                        invoke(p,"changeCurioSlot","ring",true); invoke(p,"changeCurioSlot","necklace",true);
                        invoke(p,"changeCurioSlot","curio",false);
                        var curio=(com.google.gson.JsonObject)invoke(p,"curioObject");
                        if(curio.getAsJsonArray("slots").size()!=2 || !Boolean.TRUE.equals(field(p,"curioSlotsExpanded")))throw new AssertionError("Slot multi-selection must remain expanded");
                        invoke(p,"openCurioSlots"); invoke(p,"openCurioSlots");
                        var list=(dev.xyat.kineticcore.api.client.gui.widget.list.KineticToggleList)field(p,"curioSlotList");
                        if(!list.textRows() || list.items().stream().filter(dev.xyat.kineticcore.api.client.gui.widget.list.ToggleItem::value).count()!=2)throw new AssertionError("Slot ID list retains both selected slots");
                        boolean english=dev.xyat.kineticcore.api.runtime.KineticClientRuntime.selectedLanguage().startsWith("en_");
                        if(english && list.items().stream().anyMatch(row->row.label().getString().contains(" — ")))throw new AssertionError("English slot rows must show only IDs");
                        if(!english && list.items().stream().noneMatch(row->row.label().getString().equals("ring — "+dev.xyat.kineticcore.api.text.KineticI18n.string("gui.itemcontrol.item_property.curio.slot.ring"))))throw new AssertionError("Translated slot rows retain ID and localized name");
                        for(var row:list.items()) {
                            if(row.label().getStyle().getColor()==null || row.label().getStyle().getColor().getValue()!=0xFFFFFF)throw new AssertionError("Slot IDs stay white, including selected rows");
                            if(row.label().getString().contains(" — ")) {
                                var parts=row.label().getSiblings();
                                if(parts.get(parts.size()-1).getStyle().getColor().getValue()!=0xFFAA00)throw new AssertionError("Localized slot names stay gold");
                            }
                        }
                        if(phase==3)list.setScrollOffset(list.maxScrollOffset());
                    }
                }
                if(index==27 && dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) {
                    invoke(p,"openCurioAttributes");
                    var editor=KineticGui.currentPage();
                    if(!"3".equals(field(editor,"amount")))throw new AssertionError("Original attribute ID aliases must select the existing rule");
                    invoke(editor,"deleteRule");
                    if(!((com.google.gson.JsonArray)field(editor,"rules")).isEmpty())throw new AssertionError("Deleting a native attribute must also remove its old ID alias");
                    invoke(editor,"applyRule");
                    var rules=(com.google.gson.JsonArray)field(editor,"rules");
                    if(rules.size()!=1)throw new AssertionError("Attribute edit must not duplicate aliases");
                }
            }
        }
    }
    private static void verifyNativeSlotDraft(KineticPage returnPage) throws Exception {
        var nativeEditor=new dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage("{}");
        KineticGui.open(nativeEditor);
        var categories=Class.forName("dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage$EditorCategory").getEnumConstants();
        invoke(nativeEditor,"changeCategory",categories[6]);
        setField(nativeEditor,"selectedId","minecraft:blaze_rod"); invoke(nativeEditor,"rebuildEditor");
        var original=(com.google.gson.JsonObject)invoke(nativeEditor,"curioObject");
        var displayed=(com.google.gson.JsonArray)invoke(nativeEditor,"displayedCurioSlots",original);
        if(original.has("slots") || displayed.asList().stream().noneMatch(value->value.getAsString().equals("ring")))throw new AssertionError("Existing accessory editor must inherit and preselect native slots: "+displayed);
        invoke(nativeEditor,"searchExistingAccessories");
        if(!"#curios:".equals(field(nativeEditor,"searchQuery")))throw new AssertionError("Existing accessories shortcut uses the normal tag query");
        var visible=(java.util.List<?>)field(nativeEditor,"visibleIds");
        if(!visible.contains("minecraft:blaze_rod") || visible.contains("minecraft:stick"))throw new AssertionError("Tag filtering shows existing accessories and hides ordinary items");
        if(!((java.util.Map<?,?>)field(nativeEditor,"drafts")).isEmpty())throw new AssertionError("Browsing native slots must not create an override rule");
        invoke(nativeEditor,"openCurioSlots");
        invoke(nativeEditor,"changeCurioSlot","ring",false); invoke(nativeEditor,"changeCurioSlot","curio",false); invoke(nativeEditor,"changeCurioSlot","necklace",true);
        var changed=(com.google.gson.JsonObject)invoke(nativeEditor,"curioObject");
        if(!changed.get("enabled").getAsBoolean() || !changed.getAsJsonArray("slots").toString().equals("[\"necklace\"]"))throw new AssertionError("Editing an existing accessory slot enables the override draft");
        LOG.info("ITEM_CURIO_NATIVE_EDITOR_PASS native slot preselection and replacement draft");
        KineticGui.open(returnPage);
    }
    private static void verifyTextListSpacing() {
        var probe=new KineticPage(Component.literal("List spacing check")) {
            @Override protected void build(dev.xyat.kineticcore.api.client.gui.ui.KineticUi ui) {
                var toggle=ui.toggleList(12,40,120,50,List.of(
                        new dev.xyat.kineticcore.api.client.gui.widget.list.ToggleItem(Component.literal("first"),null,false,true),
                        new dev.xyat.kineticcore.api.client.gui.widget.list.ToggleItem(Component.literal("second"),null,true,true))).textRows().build();
                var single=ui.selectionList(140,40,120,50,List.of(
                        new dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem(Component.literal("first"),null,null,true,false),
                        new dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem(Component.literal("second"),null,null,true,false))).textRows().build();
                if(toggle.itemAt(16,44)!=0 || single.itemAt(144,44)!=0)throw new AssertionError("First plain-text rows remain clickable");
                for(int y=54;y<56;y++)if(toggle.itemAt(16,y)!=-1 || single.itemAt(144,y)!=-1)throw new AssertionError("Plain-text row gaps must be 2 px and must not select a row");
                if(toggle.itemAt(16,56)!=1 || single.itemAt(144,56)!=1)throw new AssertionError("Second plain-text rows follow the 2 px gap");
            }
        };
        KineticGui.open(probe);
        LOG.info("ITEM_TEXT_LIST_SPACING_PASS single and multiple selection gaps");
    }
    private static void setField(Object target,String name,Object value)throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static Object field(Object target,String name)throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try {
            var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static Object invoke(Object target,String name,Object...args)throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())for(var m:type.getDeclaredMethods()) {
            if(m.getName().equals(name)&&compatible(m.getParameterTypes(),args)) {
                m.setAccessible(true);return m.invoke(target,args);
            }
        }
        throw new NoSuchMethodException(name);
    }
    private static Object construct(String name,Object...args)throws Exception {
        for(var c:Class.forName(name).getDeclaredConstructors())if(compatible(c.getParameterTypes(),args)){c.setAccessible(true);return c.newInstance(args);}
        throw new NoSuchMethodException(name+" constructor");
    }
    private static boolean compatible(Class<?>[]types,Object[]args) {
        if(types.length!=args.length)return false;
        for(int i=0;i<types.length;i++)if(args[i]!=null && !(types[i].isInstance(args[i]) || types[i]==int.class && args[i] instanceof Integer || types[i]==boolean.class && args[i] instanceof Boolean))return false;
        return true;
    }
    private static void capture(String frame)throws Exception {
        var mc=Minecraft.getInstance();Path path=Path.of(ROOT,String.format("%d-%02d-%s-%s.png",phase,page,NAMES[page],frame));Files.createDirectories(path.getParent());
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
        capturedPages.set(page);captures++;LOG.info("ITEM_GUI_CAPTURE phase={} case={} image={}x{}",phase,NAMES[page],mc.getWindow().getWidth(),mc.getWindow().getHeight());
    }
    @SuppressWarnings("unchecked")
    private static void verifyMergeGridBoundary()throws Exception {
        var page=KineticGui.currentPage();
        int x=(int)field(page,"rightX"),y=(int)field(page,"rightY"),h=(int)field(page,"gridAreaH");
        var rules=(Map<String,List<String>>)field(page,"tempRules");
        var target=(String)field(page,"selectedTarget");
        int before=rules.get(target).size();
        for(double[] point:new double[][]{{x+1,y+3},{x+3,y+1},{x+3,y+2+h+3}}) {
            var input=new dev.xyat.kineticcore.api.client.gui.input.MouseInput(point[0],point[1],dev.xyat.kineticcore.api.client.gui.input.MouseButton.LEFT,0,0);
            invoke(page,"onMouseClickCapture",input);
            if(rules.get(target).size()!=before)throw new AssertionError("Merge grid padding selected an invisible item at "+Arrays.toString(point));
        }
        LOG.info("ITEM_MERGE_GRID_BOUNDARY_PASS language={}",Minecraft.getInstance().getLanguageManager().getSelected());
    }
    private static void finish() {
        finished=true;
        var mc=Minecraft.getInstance();
        if(!started) {
            LOG.error("ITEM_GUI_FAIL before capture initialization");
            dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
            return;
        }
        if(stressOriginal!=null){Language.inject(stressOriginal);stressOriginal=null;}
        if(tabEditingCaptured)dev.xyat.itemcontrol.tabs.TabConfig.currentEditing=originalTabEditing;
        mc.options.guiScale().set(originalScale);
        mc.getLanguageManager().setSelected(originalLanguage);mc.options.languageCode=originalLanguage;
        mc.setScreen(null);
        mc.getWindow().setWindowed(originalWidth,originalHeight);
        if(originalFullscreen && !mc.getWindow().isFullscreen())mc.getWindow().toggleFullScreen();
        LOG.info("ITEM_GUI_{} pages={} captures={} failures={} userSettingsRestored=true",failures==0?"PASS":"FAIL",capturedPages.cardinality(),captures,failures);
        dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
    }
    private static final class StressLanguage extends Language {
        private final Language delegate;
        StressLanguage(Language delegate){this.delegate=delegate;}
        @Override public String getOrDefault(String key,String fallback) {
            String text=delegate.getOrDefault(key,fallback);
            return key.startsWith("gui.itemcontrol.") && !key.contains(".value.") && !key.endsWith(".add_mark") && !key.endsWith(".remove_mark") && !key.endsWith(".common.expand") && !key.endsWith(".common.collapse")?text+" - deliberately extended translation to verify text stays inside its own region":text;
        }
        @Override public boolean has(String key){return delegate.has(key);}
        @Override public boolean isDefaultRightToLeft(){return delegate.isDefaultRightToLeft();}
        @Override public net.minecraft.util.FormattedCharSequence getVisualOrder(net.minecraft.network.chat.FormattedText text){return delegate.getVisualOrder(text);}
    }
}
