package dev.xyat.itemcontrol.tabs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticCreativeTabs;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TabConfig {
    private static final int MAX_RULES = 16384;
    private static final int MAX_ADDITIONS = 4096;
    private static final int MAX_ITEMS_PER_TAB = 4096;
    private static final int MAX_HIDDEN_TABS = 4096;
    private static final String CONFIG_FILE = "kineticcore/creative_tabs.json";

    public static Data data = new Data();
    public static Data currentEditing = null;
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TabConfig() {
    }

    public static void load() {
        loadInternal();
    }

    public static boolean loadForEditor() {
        return loadInternal();
    }

    private static boolean loadInternal() {
        if (KineticPaths.configFileExists(CONFIG_FILE)) {
            try {
                Data loaded = GSON.fromJson(KineticPaths.readConfigText(CONFIG_FILE), Data.class);
                if (!isStructurallyValid(loaded)) {
                    TabsModule.LOGGER.error("Invalid tabs config snapshot in {}", CONFIG_FILE);
                    return false;
                }
                data = loaded;
            } catch (Exception e) {
                TabsModule.LOGGER.error("Failed to load tabs config.", e);
                return false;
            }
        }
        if (!isStructurallyValid(data)) data = new Data();
        return true;
    }

    public static boolean save() {
        return save(data);
    }

    public static boolean save(Data next) {
        if (!isValidForServer(next)) {
            TabsModule.LOGGER.error("Refusing to save invalid tabs config");
            return false;
        }

        try {
            KineticPaths.writeConfigTextsAtomic(Map.of(CONFIG_FILE, GSON.toJson(next)));
            data = next;
            return true;
        } catch (Exception e) {
            TabsModule.LOGGER.error("Failed to save tabs config.", e);
            return false;
        }
    }

    public static boolean beginEdit(String serverSnapshotJson) {
        try {
            Data snapshot = GSON.fromJson(serverSnapshotJson, Data.class);
            if (!isStructurallyValid(snapshot)) {
                TabsModule.LOGGER.error("Rejected invalid server tabs snapshot");
                currentEditing = null;
                return false;
            }
            currentEditing = snapshot;
            return true;
        } catch (RuntimeException exception) {
            TabsModule.LOGGER.error("Failed to parse server tabs snapshot", exception);
            currentEditing = null;
            return false;
        }
    }

    public static boolean isValidForServer(Data value) {
        return isStructurallyValid(value);
    }

    private static boolean isStructurallyValid(Data value) {
        if (value == null || value.removals == null || value.additions == null || value.hiddenTabs == null) return false;
        if (value.removals.size() > MAX_RULES || value.additions.size() > MAX_ADDITIONS || value.hiddenTabs.size() > MAX_HIDDEN_TABS) return false;
        if (value.removals.stream().anyMatch(rule -> !isValidRemovalRuleSyntax(rule))) return false;
        if (value.hiddenTabs.stream().anyMatch(tab -> hasInvalidTabIdSyntax(tab))) return false;
        for (TabAddition addition : value.additions) {
            if (addition == null || hasInvalidTabIdSyntax(addition.tabId) || addition.items == null || addition.items.size() > MAX_ITEMS_PER_TAB) return false;
            for (TabItem item : addition.items) {
                if (hasInvalidTabItemStructure(item)) return false;
            }
        }
        return true;
    }

    private static boolean hasInvalidTabIdSyntax(String value) {
        return value == null || value.isBlank() || KineticResourceIds.tryParse(value.trim()) == null;
    }

    private static boolean isValidRemovalRuleSyntax(String value) {
        if (value == null) return false;
        String rule = value.trim();
        if (rule.isEmpty() || rule.length() > 32767) return false;
        if (rule.startsWith("@")) {
            String namespace = rule.substring(1);
            return !namespace.isBlank() && KineticResourceIds.tryParse(namespace + ":placeholder") != null;
        }
        if (rule.startsWith("#")) return KineticResourceIds.tryParse(rule.substring(1)) != null;
        int brace = rule.indexOf('{');
        String idPart = brace < 0 ? rule : rule.substring(0, brace);
        if (KineticResourceIds.tryParse(idPart) == null) return false;
        if (brace >= 0) {
            try {
                TagParser.parseTag(rule.substring(brace));
            } catch (Exception exception) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasInvalidTabItemStructure(TabItem item) {
        if (item == null || item.id == null || item.nbt == null || item.nbt.length() > 32767) return true;
        if (KineticResourceIds.tryParse(item.id.trim()) == null) return true;
        if (!item.nbt.isBlank() && !"{}".equals(item.nbt)) {
            try {
                TagParser.parseTag(item.nbt);
            } catch (Exception exception) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidTabId(String value) {
        if (value == null || value.isBlank()) return false;
        ResourceLocation id = KineticResourceIds.tryParse(value.trim());
        return id != null && KineticCreativeTabs.contains(id);
    }

    private static boolean isValidRemovalRule(String value) {
        if (value == null) return false;
        String rule = value.trim();
        if (rule.isEmpty() || rule.length() > 32767) return false;
        if (rule.startsWith("@")) {
            String namespace = rule.substring(1);
            return !namespace.isBlank() && KineticResourceIds.tryParse(namespace + ":placeholder") != null;
        }
        if (rule.startsWith("#")) return KineticResourceIds.tryParse(rule.substring(1)) != null;
        return !TabModule.parseItemStr(rule).isEmpty();
    }

    private static boolean isValidTabItem(TabItem item) {
        if (item == null || item.id == null || item.nbt == null) return false;
        ResourceLocation id = KineticResourceIds.tryParse(item.id.trim());
        if (id == null || !KineticRegistries.items().contains(id)) return false;
        Item registered = KineticRegistries.items().get(id);
        if (registered == null || registered == Items.AIR) return false;
        if (item.nbt.length() > 32767) return false;
        if (!item.nbt.isBlank() && !"{}".equals(item.nbt)) {
            try {
                TagParser.parseTag(item.nbt);
            } catch (Exception exception) {
                return false;
            }
        }
        return true;
    }

    public static class Data {
        public List<String> removals = new ArrayList<>();
        public List<TabAddition> additions = new ArrayList<>();
        public List<String> hiddenTabs = new ArrayList<>();
    }

    public static class TabItem {
        public String id = "minecraft:air";
        public String nbt = "{}";
        public boolean matchNbt = true;

        public TabItem() {
        }

        public TabItem(String id, String nbt) {
            this.id = id;
            this.nbt = (nbt == null || nbt.isEmpty()) ? "{}" : nbt;
        }

        public boolean isAir() {
            return "minecraft:air".equals(id);
        }

        public ItemStack getStack() {
            if (isAir()) return ItemStack.EMPTY;
            try {
                ResourceLocation resourceId = KineticResourceIds.tryParse(id);
                if (resourceId == null) return ItemStack.EMPTY;
                Item item = KineticRegistries.items().get(resourceId);
                if (item == null) return ItemStack.EMPTY;
                ItemStack stack = new ItemStack(item);
                if (matchNbt && nbt != null && !nbt.equals("{}")) {
                    stack.setTag(TagParser.parseTag(nbt));
                }
                return stack;
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }
    }

    public static class TabAddition {
        public String tabId = "minecraft:ingredients";
        public List<TabItem> items = new ArrayList<>();
    }
}
