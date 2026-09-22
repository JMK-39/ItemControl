package dev.xyat.itemcontrol.tabs;

import dev.xyat.itemcontrol.tabs.jei.TabJeiPlugin;
import dev.xyat.itemcontrol.tabs.mixin.client.Access;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.runtime.KineticCreativeTabs;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TabModule {
    public static boolean bypassAllModifications = false;
    public static final Set<String> INJECTED_ITEMS = new HashSet<>();
    public static final Set<String> INJECTED_RULES = new HashSet<>();
    private static boolean registered;

    private TabModule() {
    }

    public static void load() {
        TabConfig.load();
    }

    public static void register() {
        if (registered) return;
        registered = true;
        KineticCreativeTabs.onBuildContents(TabModule::onBuildContents);
    }

    public static void onBuildContents(KineticCreativeTabs.Context context) {
        if (TabConfig.data == null) return;
        String currentTab = context.tabKey().location().toString();
        if (currentTab.equals("minecraft:search")) return;

        for (TabConfig.TabAddition add : TabConfig.data.additions) {
            if (!currentTab.equals(add.tabId)) continue;
            for (TabConfig.TabItem tabItem : add.items) {
                if (tabItem == null) continue;
                ItemStack stack = tabItem.getStack();
                if (stack.isEmpty()) continue;
                context.accept(stack);

                String rule = buildRule(stack);
                INJECTED_ITEMS.add(currentTab + "|" + rule);
                INJECTED_RULES.add(rule);
            }
        }
    }

    public static void refreshTabs() {
        if (KineticClientRuntime.currentLevel() == null || KineticClientRuntime.localPlayer() == null) return;

        INJECTED_ITEMS.clear();
        INJECTED_RULES.clear();

        KineticClientRuntime.execute(() -> {
            List<ItemStack> allSearchableItems = new ArrayList<>();

            for (KineticCreativeTabs.TabEntry entry : KineticCreativeTabs.entries()) {
                CreativeModeTab tab = entry.tab();
                if (!(tab instanceof Access accessor)) continue;
                String tabId = entry.key().location().toString();

                Collection<ItemStack> currentItems = tab.getDisplayItems();
                Collection<ItemStack> nextDisplayItems = ItemStackLinkedSet.createTypeAndTagSet();
                Set<ItemStack> nextSearchItems = ItemStackLinkedSet.createTypeAndTagSet();

                for (ItemStack stack : currentItems) {
                    if (!isRemoved(stack)) {
                        nextDisplayItems.add(stack);
                        nextSearchItems.add(stack);
                    }
                }

                for (TabConfig.TabAddition add : TabConfig.data.additions) {
                    if (add.tabId.equals(tabId)) {
                        for (TabConfig.TabItem item : add.items) {
                            ItemStack addStack = item.getStack();
                            if (!addStack.isEmpty()) {
                                nextDisplayItems.add(addStack);
                                nextSearchItems.add(addStack);
                                String rule = buildRule(addStack);
                                INJECTED_ITEMS.add(tabId + "|" + rule);
                                INJECTED_RULES.add(rule);
                            }
                        }
                    }
                }

                accessor.setDisplayItems(nextDisplayItems);
                accessor.setDisplayItemsSearchTab(nextSearchItems);

                if (tab.getType() != CreativeModeTab.Type.SEARCH && !TabConfig.data.hiddenTabs.contains(tabId)) {
                    allSearchableItems.addAll(nextSearchItems);
                }
            }

            KineticCreativeTabs.refreshSearch(allSearchableItems);

            if (KineticPlatform.isModLoaded("jei")) {
                TabJeiPlugin.refreshJei();
            }
        });
    }

    public static boolean isRemoved(ItemStack stack) {
        if (TabConfig.data == null) return false;
        return matchesAnyRule(stack, TabConfig.data.removals);
    }

    public static boolean matchesAnyRule(ItemStack stack, List<String> rules) {
        if (rules == null || rules.isEmpty() || stack == null || stack.isEmpty()) return false;
        ResourceLocation id = KineticRegistries.items().id(stack.getItem());
        if (id == null) return false;
        String idStr = id.toString();

        for (String rule : rules) {
            if (rule == null || rule.isEmpty()) continue;
            if (rule.startsWith("@")) {
                if (id.getNamespace().equals(rule.substring(1))) return true;
            } else if (rule.startsWith("#")) {
                String tag = rule.substring(1);
                if (stack.getTags().anyMatch(t -> t.location().toString().equals(tag))) return true;
            } else if (rule.contains("{")) {
                ItemStack ruleStack = parseItemStr(rule);
                if (!ruleStack.isEmpty() && ItemStack.isSameItemSameTags(stack, ruleStack)) return true;
            } else if (idStr.equals(rule)) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack parseItemStr(String str) {
        if (str == null || str.isEmpty()) return ItemStack.EMPTY;
        try {
            int brace = str.indexOf('{');
            String idPart = brace == -1 ? str : str.substring(0, brace);
            ResourceLocation id = KineticResourceIds.tryParse(idPart);
            if (id == null) return ItemStack.EMPTY;
            net.minecraft.world.item.Item item = KineticRegistries.items().get(id);
            if (item == null) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(item);
            if (brace != -1) {
                String nbt = str.substring(brace);
                if (!nbt.equals("{}")) stack.setTag(net.minecraft.nbt.TagParser.parseTag(nbt));
            }
            return stack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static String buildRule(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = KineticRegistries.items().id(stack.getItem());
        if (id == null) return "";
        String nbt = (stack.hasTag() && stack.getTag() != null) ? stack.getTag().toString() : "{}";
        return nbt.equals("{}") ? id.toString() : id + nbt;
    }
}
