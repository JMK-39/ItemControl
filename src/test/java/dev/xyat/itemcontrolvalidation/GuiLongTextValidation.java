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
    private static final String[] NAMES={"property-combat","property-tool","property-food","property-general","property-block","property-protection","protection-list","protection-modal-new","protection-modal-existing","direct-immunity","damage-types","banned","banned-mods","banned-tags","merge-collapsed","merge-expanded","item-tags","tag-suggestions","cleaner","rules-empty","rules-blacklist","rules-area","creative-tabs","components","components-invalid"};
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
            if(!screenshot && now>=due) { capture("start");screenshot=true;due=now+(phase==4?3400:550);return; }
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
        if(phase>=5){finish();return;}
        var mc=Minecraft.getInstance();
        mc.setScreen(null);
        String lang=phase==2 || phase==3?"zh_cn":"en_us";
        mc.getLanguageManager().setSelected(lang);
        mc.options.languageCode=lang;
        int width=phase==1 || phase==3?1920:854,height=phase==1 || phase==3?1080:480;
        mc.getWindow().setWindowed(width,height);mc.resizeDisplay();
        reload=mc.reloadResourcePacks();
        LOG.info("ITEM_GUI_PHASE phase={} language={} requested={}x{} autoScale=true",phase,lang,width,height);
    }
    private static void nextPage() throws Exception {
        page++;
        String selectedPages=System.getProperty("itemcontrol.guiValidation.pages", "");
        while(page<NAMES.length && !selectedPages.isBlank() && !List.of(selectedPages.split(",")).contains(String.valueOf(page)))page++;
        if(page>=NAMES.length){nextPhase();return;}
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
        }
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
    private static void finish() {
        finished=true;
        var mc=Minecraft.getInstance();
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
