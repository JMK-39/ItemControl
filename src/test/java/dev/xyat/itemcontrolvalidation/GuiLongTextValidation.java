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
    private static final String[] NAMES={"property-combat","property-tool","property-food","property-general","property-block","property-protection","protection-list","protection-modal-new","protection-modal-existing","direct-immunity","damage-types","banned","banned-mods","banned-tags","merge-collapsed","merge-expanded","item-tags","tag-suggestions","cleaner","rules-empty","rules-blacklist","rules-area","creative-tabs","components","components-invalid","property-curio","curio-slots","curio-attributes","equipment-attributes","food-effects","curio-slot-counts","batch-templates","property-comparison","property-empty-protection","trash-button-hud"};
    private static final List<String> layoutResults = new ArrayList<>();
    private static boolean installed,started,screenshot,finished,originalFullscreen;
    private static String originalLanguage;
    private static int originalScale,originalWidth,originalHeight,phase=-1,page=-1,captures,failures;
    private static long due;
    private static final BitSet capturedPages = new BitSet();
    private static CompletableFuture<Void> reload;
    private static Language stressOriginal;
    private static dev.xyat.itemcontrol.tabs.TabConfig.Data originalTabEditing;
    private static boolean tabEditingCaptured;

    public static void install() { if(installed)return;installed=true;if(Boolean.getBoolean("itemcontrol.editorSaveValidation")){ItemEditorSaveValidation.install();return;}KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,GuiLongTextValidation::tick); }
    private static void tick() {
        if(finished)return;
        try {
            var mc=Minecraft.getInstance();
            if(Boolean.getBoolean("itemcontrol.propertyValidation"))mc.options.pauseOnLostFocus=false;
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
            if(!screenshot && now>=due) {
                if(page==14)verifyMergeGridBoundary();verifyUpdatedLayout();capture("start");
                if(page==9)((dev.xyat.kineticcore.api.client.gui.widget.list.KineticEntityGrid)field(KineticGui.currentPage(),"entityGrid")).setScrollOffset(Integer.MAX_VALUE);
                if(page==16)((dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController)field(KineticGui.currentPage(),"tagScroll")).setOffset(Integer.MAX_VALUE);
                screenshot=true;due=now+(phase==4?3400:550);return;
            }
            if(screenshot && now>=due) {
                if(phase==4 || page==9 || page==16)capture("scroll");
                nextPage();
            }
        } catch(Throwable error) {
            failures++;LOG.error("ITEM_GUI_FAIL phase="+phase+" page="+page,error);
            var trace = new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(trace)); layoutResults.add(trace.toString());
            finish();
        }
    }
    private static void nextPhase() {
        if(stressOriginal!=null){Language.inject(stressOriginal);stressOriginal=null;}
        phase++;page=-1;
        // One English 1080p round by default; list phases (0-4) in itemcontrol.guiValidation.phases to run more.
        String selectedPhases=System.getProperty("itemcontrol.guiValidation.phases","1");
        while(phase<5 && !Boolean.getBoolean("itemcontrol.guiValidation.fullHdOnly") && !List.of(selectedPhases.split(",")).contains(String.valueOf(phase)))phase++;
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
        if(capturedPages.isEmpty()){verifyTextListSpacing();verifyTemplateFilesAndBatch();verifyLoadedTagEdits();verifyPropertyRules();verifyBanEnforcement();}
        openPage(page);
        screenshot=false;due=System.currentTimeMillis()+1000;
        LOG.info("ITEM_GUI_OPEN phase={} case={} page={}",phase,NAMES[page],KineticGui.currentPage()!=null?KineticGui.currentPage().getClass().getName():String.valueOf(Minecraft.getInstance().screen));
    }
    /**
     * Bans reach every way into the world: an item entity of a banned item holds nothing, an item given into an
     * inventory is used up, #tag and @mod rules match, and merged-item lookups resolve. Runs on the server thread of
     * the test world and restores the rules afterwards.
     */
    private static int diamonds(net.minecraft.world.entity.player.Inventory inventory) {
        int count=0;
        for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).getItem()==Items.DIAMOND)count+=inventory.getItem(i).getCount();
        return count;
    }

    /** The configured tag edits are in the loaded item tags themselves (registry level, not only ItemStack.is). */
    /**
     * The active property rules of the pack hold in game: no-durability items never wear out (and elytra chestplates
     * keep gliding when nearly broken), stack sizes apply, and block hardness / blast resistance reach the blocks.
     */
    private static void verifyPropertyRules() {
        int checked=0;
        for(var entry:dev.xyat.itemcontrol.item.config.ItemPropertyConfig.activeSnapshot().entrySet()){
            var rule=entry.getValue();
            var item=dev.xyat.kineticcore.api.registry.KineticRegistries.items().get(entry.getKey());
            if(item==null||item==Items.AIR)continue;
            var stack=new ItemStack(item);
            if(rule.maxDamage()!=null&&rule.maxDamage()<=0){
                if(stack.isDamageableItem())throw new AssertionError("no-durability item still wears out: "+entry.getKey());
                //? if <26.1 {
                // A real wearer: pack mods (Attributeslib, Quark) read the wearer's attributes in canElytraFly.
                var wearer=Minecraft.getInstance().player;
                if(stack.canElytraFly(wearer)){
                    stack.setDamageValue(Math.max(0,stack.getMaxDamage()-1));
                    if(!stack.canElytraFly(wearer))throw new AssertionError("no-durability elytra stopped gliding: "+entry.getKey());
                    LOG.info("ITEM_PROPERTY_ELYTRA {}",entry.getKey());
                }
                //?}
            }
            if(rule.maxStackSize()!=null&&stack.getMaxStackSize()!=rule.maxStackSize())throw new AssertionError("stack size not applied: "+entry.getKey()+" "+stack.getMaxStackSize());
            if(item instanceof BlockItem blockItem){
                var block=blockItem.getBlock();
                if(rule.blockHardness()!=null&&block.defaultBlockState().getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO)!=rule.blockHardness().floatValue())throw new AssertionError("block hardness not applied: "+entry.getKey());
                if(rule.blockExplosionResistance()!=null&&block.getExplosionResistance()!=rule.blockExplosionResistance().floatValue())throw new AssertionError("blast resistance not applied: "+entry.getKey());
            }
            checked++;
        }
        LOG.info("ITEM_PROPERTY_PASS checked={}",checked);
    }

    private static void verifyLoadedTagEdits() {
        var data=dev.xyat.itemcontrol.item.config.BanItemConfig.data;
        int checked=0;
        java.util.Set<String> ids=new java.util.TreeSet<>(data.addedItemTags.keySet());
        ids.addAll(data.removedItemTags.keySet());
        for(String id:ids){
            var location=dev.xyat.kineticcore.api.resource.KineticResourceIds.tryParse(id);
            var item=location==null?null:dev.xyat.kineticcore.api.registry.KineticRegistries.items().get(location);
            if(item==null||item==net.minecraft.world.item.Items.AIR)continue;
            var holder=net.minecraft.core.registries.BuiltInRegistries.ITEM.wrapAsHolder(item);
            java.util.Set<String> tags=new java.util.TreeSet<>();
            holder.tags().forEach(tag->tags.add(tag.location().toString()));
            java.util.List<String> added=data.addedItemTags.getOrDefault(id,java.util.List.of());
            java.util.List<String> removed=data.removedItemTags.getOrDefault(id,java.util.List.of());
            if(!tags.containsAll(added))throw new AssertionError("added tags missing on "+id+": "+tags);
            for(String tag:removed){
                if(tag.equals(dev.xyat.itemcontrol.item.config.BanItemConfig.ALL_TAGS)){
                    if(!added.containsAll(tags))throw new AssertionError("original tags left on "+id+": "+tags);
                }else if(tags.contains(tag)&&!added.contains(tag))throw new AssertionError("removed tag still on "+id+": "+tag);
            }
            LOG.info("ITEM_TAG_EDIT {} tags={}",id,tags);
            checked++;
        }
        LOG.info("ITEM_TAG_EDIT_PASS checked={}",checked);
    }

    private static void verifyBanEnforcement() throws Exception {
        var server=Minecraft.getInstance().getSingleplayerServer();
        if(server==null)throw new AssertionError("ban enforcement needs the integrated server");
        var before=dev.xyat.itemcontrol.item.config.BanItemConfig.snapshotData();
        boolean enabled=dev.xyat.itemcontrol.item.util.ItemBanControl.isReplacementEnabled();
        try {
            server.submit(() -> {
                var data=new dev.xyat.itemcontrol.item.config.BanItemConfig.Data();
                data.bannedItems.addAll(List.of("minecraft:diamond","#minecraft:wool","@itemcontrol_missing_mod"));
                data.mergedItems.put("minecraft:emerald_block",new ArrayList<>(List.of("minecraft:emerald")));
                dev.xyat.itemcontrol.item.config.BanItemConfig.restoreDataSnapshot(data);
                dev.xyat.itemcontrol.item.util.ItemBanControl.setReplacementEnabled(true);
                return null;
            }).join();
            // The client keeps banned items, so editors, JEI and recipe viewers can still show them.
            if(new ItemStack(Items.DIAMOND).isEmpty())throw new AssertionError("client-side stacks of banned items stay displayable");
            server.submit(() -> {
                // On the server a banned item is air from the moment it is created (ID, #tag rules).
                if(!new ItemStack(Items.DIAMOND,3).isEmpty())throw new AssertionError("a banned item created on the server must be air");
                if(!new ItemStack(Items.WHITE_WOOL).isEmpty())throw new AssertionError("#tag ban rule at creation");
                if(new ItemStack(Items.STICK,3).getCount()!=3)throw new AssertionError("allowed items stay untouched");
                var player=server.getPlayerList().getPlayers().get(0);
                var level=(net.minecraft.server.level.ServerLevel)player.level();
                var dropped=new net.minecraft.world.entity.item.ItemEntity(level,player.getX(),player.getY(),player.getZ(),new ItemStack(Items.DIAMOND,3));
                if(!dropped.getItem().isEmpty())throw new AssertionError("a banned item entity must hold nothing");
                int diamondsBefore=diamonds(player.getInventory());
                player.getInventory().add(new ItemStack(Items.DIAMOND,2));
                if(diamonds(player.getInventory())!=diamondsBefore)throw new AssertionError("a banned item given into an inventory must not arrive");
                if(dev.xyat.itemcontrol.item.config.BanItemConfig.mergeTargetOf(Items.EMERALD)!=Items.EMERALD_BLOCK)throw new AssertionError("merge source lookup");
                if(dev.xyat.itemcontrol.item.config.BanItemConfig.mergeTargetOf(Items.STICK)!=null)throw new AssertionError("no merge for unrelated items");
                return null;
            }).join();
        } finally {
            server.submit(() -> {
                dev.xyat.itemcontrol.item.config.BanItemConfig.restoreDataSnapshot(before);
                dev.xyat.itemcontrol.item.util.ItemBanControl.setReplacementEnabled(enabled);
                return null;
            }).join();
        }
        LOG.info("ITEM_BAN_ENFORCEMENT_PASS server-side air at creation, client display kept, item entities, inventory, tag rules, merge lookups");
    }

    @SuppressWarnings("unchecked")
    private static void verifyTemplateFilesAndBatch() throws Exception {
        String active=dev.xyat.itemcontrol.item.config.ItemPropertyConfig.pendingJson();
        var document=com.google.gson.JsonParser.parseString("{\"minecraft:brick\":{\"armor\":8,\"nutrition\":4},\"minecraft:stick\":{\"armor\":1,\"nutrition\":2,\"max_damage\":16,\"external\":true}}").getAsJsonObject();
        var changed=new java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject>();
        var page=new dev.xyat.itemcontrol.item.client.gui.ItemPropertyToolsPage(document,"minecraft:brick",changed::set,id->Map.of());KineticGui.open(page);
        var selected=(Set<String>)field(page,"checked");selected.clear();selected.add("armor");
        var targets=(Set<String>)field(page,"targets");targets.clear();targets.add("minecraft:stick");invoke(page,"apply");
        var result=changed.get().getAsJsonObject("minecraft:stick");
        if(result.get("armor").getAsInt()!=8||result.get("nutrition").getAsInt()!=2||result.get("max_damage").getAsInt()!=16||!result.get("external").getAsBoolean()||!document.getAsJsonObject("minecraft:stick").get("armor").getAsString().equals("1"))throw new AssertionError("selective batch draft preservation");
        var io=Class.forName("dev.xyat.itemcontrol.item.client.gui.ItemPropertyTemplates");var write=io.getDeclaredMethod("write",Path.class,String.class);var read=io.getDeclaredMethod("read",Path.class);write.setAccessible(true);read.setAccessible(true);
        Files.createDirectories(Path.of(ROOT));Path file=Files.createTempFile(Path.of(ROOT),"template-check-",".json");
        try{
            var fields=document.getAsJsonObject("minecraft:brick");var encoded=dev.xyat.itemcontrol.item.config.ItemRuleDrafts.encodeTemplate("装备模板",fields,Set.of("armor","max_damage"));write.invoke(null,file,encoded);
            var imported=(dev.xyat.itemcontrol.item.config.ItemRuleDrafts.Template)read.invoke(null,file);
            if(!imported.name().equals("装备模板")||!imported.selectedFields().contains("max_damage")||imported.fields().has("max_damage")||imported.fields().get("armor").getAsInt()!=8)throw new AssertionError("template file round trip including checked inheritance");
            write.invoke(null,file,dev.xyat.itemcontrol.item.config.ItemRuleDrafts.encodeTemplate("无效模板",com.google.gson.JsonParser.parseString("{\"equipment_slot\":\"not_a_slot\"}").getAsJsonObject(),Set.of("equipment_slot")));
            try{read.invoke(null,file);throw new AssertionError("invalid template must be rejected");}catch(java.lang.reflect.InvocationTargetException expected){if(!(expected.getCause() instanceof java.io.IOException))throw expected;}
        }finally{Files.deleteIfExists(file);}
        if(!active.equals(dev.xyat.itemcontrol.item.config.ItemPropertyConfig.pendingJson()))throw new AssertionError("tools and template import must not save server rules");
        LOG.info("ITEM_TEMPLATE_BATCH_PASS real JSON file import/export, validation, checked inheritance, selective draft application");
    }
    @SuppressWarnings("unchecked")
    private static void openPage(int index) throws Exception {
        var mc=Minecraft.getInstance();
        String longText="A deliberately very long named sword to exercise bounded source and target scrolling without changing player inventory ".repeat(3);
        String longItem = "minecraft:diamond_sword[custom_name='\"" + longText + "\"']";
        switch(index) {
            case 34 -> KineticGui.open(new dev.xyat.itemcontrol.cleaner.client.gui.TrashBinButtonEditorPage());
            case 28 -> KineticGui.open(new dev.xyat.itemcontrol.item.client.gui.EquipmentAttributeEditorPage(new ItemStack(Items.DIAMOND_SWORD),com.google.gson.JsonParser.parseString("[{\"attribute\":\"minecraft:generic.attack_damage\",\"slot\":\"mainhand\",\"mode\":\"add\",\"amount\":3,\"operation\":\"ADDITION\"}]").getAsJsonArray(),rules->{}));
            case 29 -> KineticGui.open(new dev.xyat.itemcontrol.item.client.gui.FoodEffectEditorPage(com.google.gson.JsonParser.parseString("[{\"effect\":\"minecraft:speed\",\"duration\":200,\"amplifier\":1,\"probability\":0.5}]").getAsJsonArray(),"append",(rules,mode)->{}));
            case 30 -> KineticGui.open(new dev.xyat.itemcontrol.item.client.gui.CurioSlotModifierEditorPage(com.google.gson.JsonParser.parseString("[{\"slot\":\"ring\",\"amount\":2},{\"slot\":\"necklace\",\"amount\":-1}]").getAsJsonArray(),rules->{}));
            case 31 -> KineticGui.open(new dev.xyat.itemcontrol.item.client.gui.ItemPropertyToolsPage(com.google.gson.JsonParser.parseString("{\"minecraft:brick\":{\"equipment_slot\":\"chest\",\"armor\":8,\"edible\":true,\"nutrition\":4}}").getAsJsonObject(),"minecraft:brick",rules->{},id->java.util.Map.of("armor","0","nutrition","0")));
            case 32 -> KineticGui.open(new dev.xyat.itemcontrol.item.client.gui.ItemPropertyComparisonPage("minecraft:brick",com.google.gson.JsonParser.parseString("{\"equipment_slot\":\"chest\",\"armor\":8,\"edible\":true,\"nutrition\":4}").getAsJsonObject(),java.util.Map.of("armor","0","nutrition","0")));
            case 0,1,2,3,4,5 -> {
                var p=new dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage("{}");KineticGui.open(p);
                var categories=Class.forName("dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage$EditorCategory").getEnumConstants();
                if(index>0)invoke(p,"changeCategory",categories[index]);
                invoke(p,"selectGridIndex",0);
                verifyPropertyGrid(p);
            }
            case 6,7,8 -> {
                // Protection rules live in the Core item list editor; 7 and 8 open the per-item dialog over it.
                dev.xyat.itemcontrol.item.client.gui.ProtectionRules.open(List.of("minecraft:diamond_sword;true;true;false;false"));
                if(index>=7)KineticGui.openChild(new dev.xyat.itemcontrol.item.client.gui.ProtectionRules.Dialog(new ItemStack(Items.DIAMOND_SWORD),
                        index==8?"minecraft:diamond_sword;true;true;false;false":null,rule->{}));
            }
            case 9 -> KineticGui.open(new dev.xyat.itemcontrol.item.client.gui.DirectEntityImmunityEditorPage(List.of("minecraft:zombie","#example:deliberately_long_entity_tag_for_rule_view")));
            case 10 -> KineticGui.open(new dev.xyat.itemcontrol.item.client.gui.DamageTypeEditorPage(List.of("minecraft:in_fire","#minecraft:is_fire","example:deliberately_long_damage_type_identifier_for_scrolling")));
            // The ban list is the Core item list editor; @mod and #tag rules are picked from its categories and search.
            case 11,12,13 -> {
                dev.xyat.itemcontrol.item.client.gui.BanRules.open();
                // banned-mods: the first @mod category, where a whole mod is banned with one click.
                if(index==12) {
                    var screen=Minecraft.getInstance().screen;var entries=(List<?>)field(screen,"categoryEntries");
                    for(int i=0;i<entries.size();i++)if(String.valueOf(invoke(entries.get(i),"type")).equals("MOD")){invoke(screen,"selectCategoryIndex",i);break;}
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
                var sources=Class.forName("dev.xyat.itemcontrol.item.client.gui.ItemTagEditorPage$TagSource").getEnumConstants();
                for(int row=0;row<40;row++)rows.add(construct("dev.xyat.itemcontrol.item.client.gui.ItemTagEditorPage$TagEntry","example:a_very_long_tag_identifier_to_exercise_the_entire_tag_row_"+row,sources[row%sources.length]));
                ((dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController)field(p,"tagScroll")).update(rows.size()*18,(int)field(p,"tagListH"));
                if(index==17){
                    var input=field(p,"tagInput");invoke(input,"setTextValue","example");invoke(p,"focus",input);
                    setField(p,"tagSuggestions",new ArrayList<>(List.of("example:a_very_long_tag_identifier_to_exercise_the_entire_suggestion_row".repeat(3))));
                }
            }
            case 18 -> {
                var container=new dev.xyat.itemcontrol.cleaner.CleanerSavedData.BigTrashContainer(54);
                int[] counts={2,64,999,1234,1_000_000,Integer.MAX_VALUE};
                for(int slot=0;slot<counts.length;slot++)container.setItem(slot,new ItemStack(slot==0?Items.DIAMOND_PICKAXE:Items.STONE,counts[slot]));
                container.getItem(0).setDamageValue(100);
                var menu=new dev.xyat.itemcontrol.cleaner.client.gui.CleanerMenu(0,mc.player.getInventory(),container);
                var p=new dev.xyat.itemcontrol.cleaner.client.gui.CleanerPage(menu,dev.xyat.kineticcore.api.text.KineticI18n.translatable("gui.itemcontrol.cleaner.cleaner.title"));
                mc.setScreen((net.minecraft.client.gui.screens.Screen)construct("dev.xyat.kineticcore.internal.client.gui.page.PageContainerScreen",p,mc.player.getInventory(),p.title()));
            }
            case 19,20,21 -> {
                var method=Class.forName("dev.xyat.itemcontrol.cleaner.config.CleanerConfigGui").getDeclaredMethod(
                        index==19?"openItemWhitelistEditor":index==20?"openTrashBinBlacklistEditor":"openProtectedAreaToolEditor");
                method.setAccessible(true);method.invoke(null);
            }
            case 33 -> {
                var p=new dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage("{}");KineticGui.open(p);
                var categories=Class.forName("dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage$EditorCategory").getEnumConstants();
                invoke(p,"changeCategory",categories[5]);
            }
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
                verifyPropertyGrid(p);
                if(!dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) {
                    var slots=(dev.xyat.kineticcore.api.client.gui.widget.KineticButton)field(p,"curioSlotButton");
                    if(slots.isEnabled())throw new AssertionError("Curios controls must be disabled without the dependency");
                    LOG.info("ITEM_CURIO_ABSENT_PASS optional startup and disabled controls");
                }
                if(index==26) {
                    if(!capturedPages.get(26) && dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available())verifyNativeSlotDraft(p);
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
        if (Boolean.getBoolean("itemcontrol.dropdownValidation") && (index == 28
                || index == 27 && dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available())) {
            ItemAttributeDropdownValidation.verifyAndLeaveOpen();
        }
    }
    private static void verifyPropertyGrid(Object page) throws Exception {
        var grid=(dev.xyat.kineticcore.api.client.gui.widget.list.KineticItemGrid)field(page,"itemGrid");
        // Grid from y 28: the first slot of the thirteenth row (y 318-340) is item 12 * 13.
        if(grid.columns()!=13 || grid.visibleRows()!=13 || grid.itemAt(10,324)!=156)
            throw new AssertionError("Property browser must show thirteen complete columns and thirteen complete rows");
    }
    private static void verifyUpdatedLayout() throws Exception {
        var screen=Minecraft.getInstance().screen;
        var pageObject=KineticGui.currentPage();
        if(pageObject!=null && pageObject.width()==640 && pageObject.height()==360)
            requireLayout((int)field(pageObject,"canvasSafeMargin")==4,"full-size editor uses the standard four-pixel safe margin");
        switch(page) {
            case 34 -> verifyTrashButtonHud(pageObject);
            case 0,5,33 -> {
                var search=control(pageObject,"searchBox");
                requireLayout(search.controlX()==48 && search.controlY()==4,"property search follows Back by four pixels in the header row");
                requireLayout((int)field(pageObject,"PANEL_X")==328,"property panel starts right after the flush item grid");
                if(page==33) {
                    int buttons=0;
                    for(var child:screen.children()) if(child instanceof dev.xyat.kineticcore.api.client.gui.widget.KineticButton button
                            && !button.isEnabled() && button.controlY()>=94 && button.controlY()<=142) {
                        requireLayout(!button.text().getString().isBlank(),"disabled protection control has a label");
                        requireLayout(button.text().getStyle().getColor()!=null,"disabled protection label has explicit gray color");
                        buttons++;
                    }
                    requireLayout(buttons==5,"all five empty protection buttons were checked");
                }
            }
            case 9 -> {
                var search=control(pageObject,"searchBox");
                var grid=(dev.xyat.kineticcore.api.client.gui.widget.list.KineticEntityGrid)field(pageObject,"entityGrid");
                requireLayout(search.controlX()==48 && search.controlY()==4,"entity search shares Back row with a four-pixel gap");
                requireLayout(grid.controlX()==4 && grid.controlY()==28 && grid.controlWidth()==632 && grid.controlHeight()==328,"entity grid fills the available canvas");
                requireLayout(grid.columns()==12 && grid.itemAt(615,299)==71,"twelve columns and six complete entity rows fit");
                requireLayout(grid.itemAt(54,29)==-1 && grid.itemAt(56,29)==1,"50-pixel cards retain two-pixel noninteractive gaps");
                var selected=(Set<String>)field(pageObject,"selectedEntries");
                String id=grid.items().get(0).entityId();boolean previous=selected.contains(id);
                var click=new dev.xyat.kineticcore.api.client.gui.input.MouseInput(5,29,dev.xyat.kineticcore.api.client.gui.input.MouseButton.LEFT,0,0);
                invoke(grid,"onMouseClick",click);
                requireLayout(selected.contains(id)!=previous,"Core grid click updates selection");
                invoke(pageObject,"toggleEntity",id);
                requireLayout(selected.contains(id)==previous,"grid selection check restores the draft");
            }
            case 10 -> {
                var search=control(pageObject,"searchBox");var add=control(pageObject,"addButton");var save=control(pageObject,"saveButton");
                requireLayout(search.controlX()==48 && search.controlY()==4 && search.controlWidth()==300,"damage search follows Back by four pixels and leaves room for the title (F012)");
                requireLayout(add.controlX()==552 && save.controlX()==596 && add.controlY()==4 && save.controlY()==4,"damage Add and Save share adjacent top positions");
                requireLayout(add.controlWidth()==40 && save.controlWidth()==40,"damage short action buttons are forty pixels");
                requireLayout((int)field(pageObject,"LIST_Y")==28 && (int)field(pageObject,"LIST_H")==328,"damage list uses the remaining canvas height");
            }
            case 14,15 -> {
                var search=control(pageObject,"leftSearchBox");var back=control(pageObject,"closeBtn");
                requireLayout(search.controlX()==back.controlX()+back.controlWidth()+4,"merge search follows Back by four pixels");
                requireLayout((int)field(pageObject,"rightY")==30,"merge grid moves up to the content row");
            }
            case 16,17 -> {
                requireLayout((int)field(pageObject,"leftY")==31,"tag item grid starts 3 px inside the content surface below the header divider");
                requireLayout(field(pageObject,"rightY").equals(field(pageObject,"tagListY")),"tag rows use the former item-ID space");
                var scroll=(dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController)field(pageObject,"tagScroll");
                scroll.update(((List<?>)field(pageObject,"displayedTags")).size()*18,(int)field(pageObject,"tagListH"));
                if(page==16)requireLayout(scroll.maxOffset()>0,"tag rows have a real scroll range");
            }
            case 18 -> verifyCleanerCountLayers((dev.xyat.itemcontrol.cleaner.client.gui.CleanerPage)pageObject);
            case 19,20 -> {
                requireLayout(screen.getClass().getName().equals("dev.xyat.kineticcore.internal.client.selector.ItemListEditorScreen"),"cleaner rule action opens the Core item-list editor directly");
                requireLayout(!((List<?>)field(screen,"displayList")).isEmpty(),"Core multi-selection starts with all item choices visible");
                requireLayout(field(screen,"rules") instanceof Set<?>,"Core selector keeps its multiple-selection draft");
            }
            case 21 -> requireLayout(screen.getClass().getName().equals("dev.xyat.kineticcore.internal.client.selector.ItemSelectorScreen"),"single area-tool action opens the Core item picker directly");
            default -> { return; }
        }
        layoutResults.add("PASS phase="+phase+" page="+page+" "+NAMES[page]);
    }
    private static void verifyTrashButtonHud(KineticPage hud) throws Exception {
        int savedX=dev.xyat.itemcontrol.cleaner.config.CleanerConfig.trashBinButtonX;
        int savedY=dev.xyat.itemcontrol.cleaner.config.CleanerConfig.trashBinButtonY;
        Object editor=field(hud,"editor"), snapshot=invoke(editor,"snapshot");
        var left=dev.xyat.kineticcore.api.client.gui.input.MouseButton.LEFT;
        try {
            requireLayout(hud.layout()==dev.xyat.kineticcore.api.client.gui.page.PageLayout.NATIVE,"trash-button HUD uses native coordinates");
            var initial=(dev.xyat.kineticcore.api.client.gui.selector.KineticHudEditorPage.HudLayout)invoke(hud,"currentLayout");
            requireLayout((int)invoke(hud,"scaledElementWidth")==16 && (int)invoke(hud,"scaledElementHeight")==16,"trash-button HUD keeps its sixteen-pixel icon");
            requireLayout(Boolean.TRUE.equals(invoke(hud,"onMouseClickCapture",new dev.xyat.kineticcore.api.client.gui.input.MouseInput(initial.x()+8,initial.y()+8,left,0,0))),"synthetic HUD drag starts on its icon");
            requireLayout(Boolean.TRUE.equals(invoke(hud,"onMouseDrag",new dev.xyat.kineticcore.api.client.gui.input.MouseDragInput(hud.width()+64,hud.height()+64,left,0,64,64))),"synthetic HUD drag is handled without moving the OS cursor");
            invoke(hud,"onMouseRelease",new dev.xyat.kineticcore.api.client.gui.input.MouseInput(hud.width()+64,hud.height()+64,left,0,0));
            var moved=(dev.xyat.kineticcore.api.client.gui.selector.KineticHudEditorPage.HudLayout)invoke(hud,"currentLayout");
            requireLayout(moved.x()==hud.width()-16 && moved.y()==hud.height()-16,"HUD drag clamps the complete icon to the screen");
            requireLayout(Boolean.FALSE.equals(invoke(hud,"onMouseScroll",new dev.xyat.kineticcore.api.client.gui.input.ScrollInput(moved.x()+8,moved.y()+8,0,1))),"trash-button HUD rejects scaling");
            invoke(hud,"resetLayout");
            var reset=(dev.xyat.kineticcore.api.client.gui.selector.KineticHudEditorPage.HudLayout)invoke(hud,"currentLayout");
            requireLayout(reset.x()==(hud.width()-176)/2+148 && reset.y()==(hud.height()-166)/2+61 && reset.scale()==1D,"reset uses the inventory-relative default position");
            requireLayout(savedX==dev.xyat.itemcontrol.cleaner.config.CleanerConfig.trashBinButtonX && savedY==dev.xyat.itemcontrol.cleaner.config.CleanerConfig.trashBinButtonY,"HUD preview actions never save configuration");
        } finally {
            invoke(editor,"restore",snapshot);
        }
    }
    @SuppressWarnings("unchecked")
    private static void verifyCleanerCountLayers(dev.xyat.itemcontrol.cleaner.client.gui.CleanerPage cleaner)throws Exception {
        var slots=cleaner.menu().slots;
        int[] counts={2,64,999,1234,1_000_000,Integer.MAX_VALUE};
        String[] labels={"2","64","999","1k","1m","2g"};
        var pendingCounts=(Map<net.minecraft.world.inventory.Slot,Integer>)field(cleaner,"originalCounts");
        requireLayout(pendingCounts.isEmpty(),"the real rendered frame completed its trash-count decoration pass");
        for(int slot=0;slot<counts.length;slot++)requireLayout(slots.get(slot).getItem().getCount()==counts[slot],"the real rendered frame retained the complete stored quantity");
        int left=(int)invoke(cleaner,"leftPos"),top=(int)invoke(cleaner,"topPos");
        int[] layer={0},decorations={0};
        var layers=new ArrayDeque<Integer>();
        var graphicsType=dev.xyat.kineticcore.api.client.gui.render.KineticGraphics.class;
        var graphics=(dev.xyat.kineticcore.api.client.gui.render.KineticGraphics)java.lang.reflect.Proxy.newProxyInstance(
                graphicsType.getClassLoader(),new Class<?>[]{graphicsType},(proxy,method,args)->{
                    switch(method.getName()) {
                        case "push" -> layers.push(layer[0]);
                        case "pop" -> layer[0]=layers.pop();
                        case "raise" -> layer[0]+=(int)args[0];
                        // 2026-10-24: the count is plain green text on a raised layer, so the durability bar the slot
                        // already drew can never cover it.
                        case "text" -> {
                            if(!(args[0] instanceof String text))return 0;
                            requireLayout(layer[0]>1,"trash quantities are drawn above native item icons and their durability bar");
                            int slotIndex=-1;
                            for(int slot=0;slot<counts.length;slot++)if(text.equals(labels[slot]) && args[2].equals(top+slots.get(slot).y+9))slotIndex=slot;
                            requireLayout(slotIndex>=0,"only trash slots receive custom count text");
                            requireLayout(args[3].equals(0xFF55FF55),"trash count preserves green abbreviated quantity");
                            var slot=slots.get(slotIndex);
                            requireLayout(slot.getItem().getCount()==counts[slotIndex],"real count is restored before decorations and tooltips");
                            decorations[0]++;
                        }
                    }
                    return method.getReturnType()==int.class?0:null;
                });
        var foreground=cleaner.getClass().getDeclaredMethod("renderForeground",graphicsType,int.class,int.class,float.class);
        foreground.setAccessible(true);
        try {
            // The normal screen frame exercises the background through the real native adapter. Only probe the
            // foreground's drawing commands: theme drawing in the background requires that native adapter.
            for(int slot=0;slot<counts.length;slot++) {
                pendingCounts.put(slots.get(slot),counts[slot]);
                slots.get(slot).getItem().setCount(1);
            }
            foreground.invoke(cleaner,graphics,0,0,0F);
            requireLayout(decorations[0]==counts.length,"all small and large trash quantities receive native decorations");
            requireLayout(layer[0]==0 && layers.isEmpty(),"count overlay restores the graphics transform for later tooltips");
            requireLayout(pendingCounts.isEmpty(),"count overlay clears its pending quantities after decoration");
            for(int slot=0;slot<counts.length;slot++)requireLayout(slots.get(slot).getItem().getCount()==counts[slot],"rendering retains the complete stored quantity");
        } finally {
            for(int slot=0;slot<counts.length;slot++)slots.get(slot).getItem().setCount(counts[slot]);
            pendingCounts.clear();
        }
    }
    private static dev.xyat.kineticcore.api.client.gui.widget.KineticControl control(Object target,String name)throws Exception {
        return (dev.xyat.kineticcore.api.client.gui.widget.KineticControl)field(target,name);
    }
    private static void requireLayout(boolean value,String message){if(!value)throw new AssertionError(message);}
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
        try {
            Files.createDirectories(Path.of(ROOT));
            Files.writeString(Path.of(ROOT,"result.txt"),(failures==0?"PASS":"FAIL")+" pages="+capturedPages.cardinality()+" captures="+captures+" failures="+failures+System.lineSeparator()+String.join(System.lineSeparator(),layoutResults));
        }catch(java.io.IOException error){failures++;LOG.error("ITEM_GUI_RESULT_FAIL",error);}
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
