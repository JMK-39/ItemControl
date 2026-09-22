package dev.xyat.itemcontrol.cleaner.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.event.AutoCleanerEventHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;

import java.nio.file.Path;
import java.util.*;

public class CleanerConfig {
    private static final Path CONFIG_DIR = KineticPaths.configFile("kineticcore/cleaner.toml").getParent();
    private static final Path CONFIG_PATH = KineticPaths.configFile("kineticcore/cleaner.toml");
    private static CommentedFileConfig configData;

    public static boolean enableCleaner = true;
    public static boolean isCleanerHardDisabled = false;
    public static int cleanerIntervalSeconds = 600;
    public static boolean cleanExperienceOrbs = false;
    public static boolean allowManualClean = true;
    public static List<String> itemWhitelist = new ArrayList<>();
    public static boolean enableEntityCleaning = false;
    public static List<String> entityList = new ArrayList<>();

    public static boolean enableProtectedAreas = true;
    public static String protectedAreaToolItem = "minecraft:golden_hoe";
    public static int protectedAreaMaxGridCountPerPlayer = 6400;
    public static double protectedAreaRayTraceDistance = 32.0D;

    public static boolean enableTrashBin = true;
    public static int trashBinRows = 28;
    public static int trashBinHistorySize = 3;
    public static List<String> trashBinBlacklist = new ArrayList<>();

    public static boolean showTrashBinButton = true;
    public static int trashBinButtonX = 148;
    public static int trashBinButtonY = 61;

    private static final Set<String> CLEANER_EXACT_IDS = new HashSet<>();
    private static final Set<String> CLEANER_NAMESPACES = new HashSet<>();
    private static final Set<ResourceLocation> CLEANER_TAGS = new HashSet<>();
    private static final Set<String> CLEANER_ENTITY_IDS = new HashSet<>();

    private static final Set<String> TRASH_EXACT_IDS = new HashSet<>();
    private static final Set<String> TRASH_NAMESPACES = new HashSet<>();
    private static final Set<ResourceLocation> TRASH_TAGS = new HashSet<>();

    public static void load() {
        try {
            if (configData != null) {
                configData.close();
                configData = null;
            }
            KineticPaths.ensureConfigDirectory("kineticcore");

            com.electronwill.nightconfig.core.Config oldValues = com.electronwill.nightconfig.core.Config.inMemory();

            if (KineticPaths.configFileExists("kineticcore/cleaner.toml")) {
                try (FileConfig oldFile = FileConfig.of(CONFIG_PATH)) {
                    oldFile.load();
                    oldValues.putAll(oldFile);
                }
            }

            configData = CommentedFileConfig.builder(CONFIG_PATH)
                    .sync()
                    .preserveInsertionOrder()
                    .writingMode(WritingMode.REPLACE)
                    .build();

            setupConfigStructure(oldValues);
            configData.save();
            readValues();
        } catch (Exception e) {
            CleanerModule.LOGGER.error("Cleaner config failed to load", e);
        }
    }

    private static void setupConfigStructure(com.electronwill.nightconfig.core.Config oldValues) {
        configData.setComment("cleaner", """
             扫地姬自动清理设置
             用来定时清理地面掉落物、经验球和指定实体。
             Cleaner Settings
             Used to automatically clean ground drops, experience orbs, and selected entities.""");

        define(oldValues, "cleaner.enable", true, """
             是否启用扫地姬自动清理功能。
             Whether to enable the automatic cleaner feature.""");

        define(oldValues, "cleaner.hard_disabled", false, """
             是否彻底禁用扫地姬。
             如果这里是 true，扫地姬所有清理逻辑都会停止，包括自动清理和手动清理。
             Whether to completely disable the cleaner.
             If true, all cleaner logic will stop, including automatic and manual cleaning.""");

        define(oldValues, "cleaner.interval", 600, """
             自动清理间隔时间，单位是秒。
             例如 600 就是每 10 分钟清理一次。
             Automatic cleaning interval, in seconds.
             For example, 600 means cleaning once every 10 minutes.""");

        define(oldValues, "cleaner.cleanExperienceOrbs", false, """
             是否清理地面上的经验球。
             Whether to clean experience orbs on the ground.""");

        define(oldValues, "cleaner.allowManualClean", true, """
             是否允许普通玩家使用快捷键手动触发清理。
             OP 2 级玩家不受这个开关限制。
             Whether normal players can trigger manual cleaning with the hotkey.
             OP level 2 players are not limited by this option.""");

        define(oldValues, "cleaner.itemWhitelist", Arrays.asList("minecraft:ancient_debris", "@tacz", "#forge:ingots"), """
             扫地姬物品白名单。
             在这个列表里的物品不会被扫地姬清理。
             Cleaner item whitelist.
             Items in this list will not be cleaned by the cleaner.
             
             支持 3 种写法:
             Supported formats:
             
             1. 精确物品 ID:
             1. Exact item ID:
             "minecraft:diamond"
             
             2. 整个模组 ID，使用 @ 开头:
             2. Whole mod ID, starts with @:
             "@tacz"
             
             3. 物品标签，使用 # 开头:
             3. Item tag, starts with #:
             "#forge:ingots\"""");

        define(oldValues, "cleaner.enableEntityCleaning", false, """
             是否启用非物品实体清理。
             开启后，会按照下面的实体 ID 列表清理指定实体。
             Whether to enable non-item entity cleaning.
             When enabled, entities in the list below will be cleaned.""");

        define(oldValues, "cleaner.entityList", Arrays.asList("minecraft:arrow", "minecraft:spectral_arrow"), """
             需要被扫地姬清理的实体 ID 列表。
             例如箭、光灵箭、药水云、三叉戟等。
             Entity ID list to be cleaned by the cleaner.
             For example, arrows, spectral arrows, area effect clouds, tridents, etc.
             
             示例:
             Example:
             ["minecraft:arrow", "minecraft:spectral_arrow", "minecraft:area_effect_cloud"]""");

        configData.setComment("protected_areas", """
             扫地姬保护区域设置
             保护区域内的掉落物不会被扫地姬清理。
             Cleaner Protected Area Settings
             Dropped items inside protected areas will not be cleaned by the cleaner.""");

        define(oldValues, "protected_areas.enable", true, """
             是否启用扫地姬保护区域功能。
             Whether to enable cleaner protected areas.""");

        define(oldValues, "protected_areas.tool_item", "minecraft:golden_hoe", """
             用来框选保护区域的工具物品 ID。
             默认是 minecraft:golden_hoe，也就是金锄头。
             你可以改成其他物品 ID，例如 minecraft:stick。
             Tool item ID used to select protected areas.
             Default is minecraft:golden_hoe, which is the golden hoe.
             You can change it to another item ID, such as minecraft:stick.""");

        defineGridLimit(oldValues);

        define(oldValues, "protected_areas.ray_trace_distance", 32.0D, """
             框选保护区域时，视线可以选中多远的方块，单位是格。
             默认 32，意思是玩家看向 32 格以内的方块都可以选中。
             Ray trace distance used when selecting protected areas, in blocks.
             Default is 32, meaning blocks within 32 blocks in the player's view can be selected.""");

        configData.setComment("trash_bin", """
             垃圾桶设置
             垃圾桶会暂存被扫地姬清理掉的物品，防止重要物品被误删。
             Trash Bin Settings
             The trash bin temporarily stores items cleaned by the cleaner to prevent important items from being lost.""");

        define(oldValues, "trash_bin.enable", true, """
             是否启用垃圾桶功能。
             关闭后，被扫地姬清理的物品不会进入垃圾桶。
             Whether to enable the trash bin.
             If disabled, items cleaned by the cleaner will not enter the trash bin.""");

        define(oldValues, "trash_bin.rows", 28, """
             垃圾桶容量，单位是行。
             每行有 9 个槽位。
             Trash bin capacity, in rows.
             Each row has 9 slots.""");

        define(oldValues, "trash_bin.history_count", 3, """
             垃圾桶保存几次历史清理记录。
             建议设置为 3 到 5。
             Number of cleanup history records stored in the trash bin.
             Recommended value is 3 to 5.""");

        define(oldValues, "trash_bin.blacklist", new ArrayList<>(), """
             垃圾桶黑名单。
             在这个列表里的物品被清理后会直接销毁，不会进入垃圾桶。
             Trash bin blacklist.
             Items in this list will be destroyed directly after cleaning and will not enter the trash bin.
             
             提示:
             如果圆石、泥土、树叶这类垃圾太多，可以加入这里，避免垃圾桶爆满。
             Tip:
             If items like cobblestone, dirt, or leaves are too common, add them here to prevent the trash bin from filling up.
             
             支持的写法和扫地姬物品白名单一样。
             The supported formats are the same as the cleaner item whitelist.
             
             示例:
             Example:
             ["minecraft:cobblestone", "minecraft:dirt", "#minecraft:leaves"]""");

        define(oldValues, "trash_bin.show_button", true, """
             是否在玩家背包界面显示垃圾桶按钮。
             Whether to show the trash bin button in the player inventory screen.""");

        define(oldValues, "trash_bin.button_x", 148, """
             垃圾桶按钮相对于玩家背包界面左上角的 X 轴位置偏移。
             可以在按键设置中绑定垃圾桶位置编辑按键，然后在玩家背包界面中直接拖动调整。
             X offset of the trash bin button relative to the top-left corner of the player inventory screen.
             Bind the trash bin position editor key in Controls, then drag the button directly in the player inventory screen.""");

        define(oldValues, "trash_bin.button_y", 61, """
             垃圾桶按钮相对于玩家背包界面左上角的 Y 轴位置偏移。
             可以在按键设置中绑定垃圾桶位置编辑按键，然后在玩家背包界面中直接拖动调整。
             Y offset of the trash bin button relative to the top-left corner of the player inventory screen.
             Bind the trash bin position editor key in Controls, then drag the button directly in the player inventory screen.""");

    }

    private static void defineGridLimit(com.electronwill.nightconfig.core.Config oldValues) {
        int value = oldValues.getOrElse("protected_areas.max_grid_count_per_player", 6400);

        if (value == 25) {
            value = 6400;
        }

        configData.set("protected_areas.max_grid_count_per_player", value);
        configData.setComment("protected_areas.max_grid_count_per_player", " " + """
             每个玩家最多可以保存多少个三维方块格子。
             保护区域会按照整个盒子的体积来扣额度，也就是 X 长度 × Y 高度 × Z 宽度。
             默认 6400。
             说明: 如果只框选 1 格高，6400 大约等于 5x5 区块的平面范围。
             如果框选高度更高，就会消耗更多额度。
             Maximum 3D block grid quota per player.
             Protected areas use the full box volume as cost: X length × Y height × Z width.
             Default is 6400.
             Note: If the selected area is only 1 block tall, 6400 is roughly equal to a 5x5 chunk flat area.
             Taller selections will cost more quota.""".trim());
    }

    private static void define(com.electronwill.nightconfig.core.Config oldValues, String path, Object def, String comment) {
        configData.set(path, oldValues.getOrElse(path, def));
        if (comment != null) {
            configData.setComment(path, " " + comment.trim());
        }
    }

    private static void readValues() {
        enableCleaner = configData.getOrElse("cleaner.enable", true);
        isCleanerHardDisabled = configData.getOrElse("cleaner.hard_disabled", false);
        cleanerIntervalSeconds = configData.getOrElse("cleaner.interval", 600);
        cleanExperienceOrbs = configData.getOrElse("cleaner.cleanExperienceOrbs", false);
        allowManualClean = configData.getOrElse("cleaner.allowManualClean", true);
        itemWhitelist = configData.getOrElse("cleaner.itemWhitelist", new ArrayList<>());
        enableEntityCleaning = configData.getOrElse("cleaner.enableEntityCleaning", false);
        entityList = configData.getOrElse("cleaner.entityList", new ArrayList<>());

        enableProtectedAreas = configData.getOrElse("protected_areas.enable", true);
        protectedAreaToolItem = configData.getOrElse("protected_areas.tool_item", "minecraft:golden_hoe");
        protectedAreaMaxGridCountPerPlayer = Math.max(1, configData.getOrElse("protected_areas.max_grid_count_per_player", 6400));
        protectedAreaRayTraceDistance = configData.getOrElse("protected_areas.ray_trace_distance", 32.0D);

        enableTrashBin = configData.getOrElse("trash_bin.enable", true);
        trashBinRows = configData.getOrElse("trash_bin.rows", 28);
        trashBinHistorySize = configData.getOrElse("trash_bin.history_count", 3);
        trashBinBlacklist = configData.getOrElse("trash_bin.blacklist", new ArrayList<>());
        showTrashBinButton = configData.getOrElse("trash_bin.show_button", true);
        trashBinButtonX = configData.getOrElse("trash_bin.button_x", 148);
        trashBinButtonY = configData.getOrElse("trash_bin.button_y", 61);

        rebuildCaches();
    }

    public static void rebuildCaches() {
        parseRules(itemWhitelist, CLEANER_EXACT_IDS, CLEANER_NAMESPACES, CLEANER_TAGS);
        parseRules(trashBinBlacklist, TRASH_EXACT_IDS, TRASH_NAMESPACES, TRASH_TAGS);
        CLEANER_ENTITY_IDS.clear();
        for (String entityId : entityList) {
            if (entityId != null && !entityId.isBlank()) {
                CLEANER_ENTITY_IDS.add(entityId);
            }
        }
        AutoCleanerEventHandler.onCleanerRulesChanged();
    }

    private static void parseRules(List<String> rules, Set<String> ids, Set<String> namespaces, Set<ResourceLocation> tags) {
        ids.clear();
        namespaces.clear();
        tags.clear();

        for (String rule : rules) {
            if (rule == null || rule.isBlank()) {
                continue;
            }

            if (rule.startsWith("@")) {
                namespaces.add(rule.substring(1));
            } else if (rule.startsWith("#")) {
                ResourceLocation tagLoc = KineticResourceIds.tryParse(rule.substring(1));
                if (tagLoc != null) {
                    tags.add(tagLoc);
                }
            } else {
                ids.add(rule);
            }
        }
    }

    public static boolean isItemForbiddenInTrash(ItemStack stack) {
        return matchesRule(stack, CLEANER_EXACT_IDS, CLEANER_NAMESPACES, CLEANER_TAGS);
    }

    public static boolean isItemTrashBlacklisted(ItemStack stack) {
        return matchesRule(stack, TRASH_EXACT_IDS, TRASH_NAMESPACES, TRASH_TAGS);
    }

    private static boolean matchesRule(ItemStack stack, Set<String> ids, Set<String> namespaces, Set<ResourceLocation> tags) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation rl = KineticRegistries.items().id(stack.getItem());

        if (rl == null) {
            return false;
        }

        if (ids.contains(rl.toString())) {
            return true;
        }

        if (namespaces.contains(rl.getNamespace())) {
            return true;
        }

        for (ResourceLocation tagKey : tags) {
            if (stack.is(ItemTags.create(tagKey))) {
                return true;
            }
        }

        return false;
    }

    public static boolean shouldCleanEntity(ResourceLocation rl) {
        return rl != null && CLEANER_ENTITY_IDS.contains(rl.toString());
    }

    public static int getTrashBinSlots() {
        return trashBinRows * 9;
    }

    public static boolean isProtectedAreaTool(ItemStack stack) {
        if (stack.isEmpty() || protectedAreaToolItem == null || protectedAreaToolItem.isBlank()) {
            return false;
        }

        ResourceLocation id = KineticRegistries.items().id(stack.getItem());
        return id != null && id.toString().equals(protectedAreaToolItem);
    }


    public static void setTrashBinButtonPosition(int x, int y) {
        trashBinButtonX = x;
        trashBinButtonY = y;
        saveClientSettings();
    }

    public static void save() {
        saveServerSettings();
    }

    public static void saveServerSettings() {
        if (configData == null) {
            throw new IllegalStateException("Cleaner server config is not loaded");
        }

        configData.set("cleaner.enable", enableCleaner);
        configData.set("cleaner.hard_disabled", isCleanerHardDisabled);
        configData.set("cleaner.interval", cleanerIntervalSeconds);
        configData.set("cleaner.cleanExperienceOrbs", cleanExperienceOrbs);
        configData.set("cleaner.allowManualClean", allowManualClean);
        configData.set("cleaner.itemWhitelist", itemWhitelist);
        configData.set("cleaner.enableEntityCleaning", enableEntityCleaning);
        configData.set("cleaner.entityList", entityList);

        configData.set("protected_areas.enable", enableProtectedAreas);
        configData.set("protected_areas.tool_item", protectedAreaToolItem);
        configData.set("protected_areas.max_grid_count_per_player", protectedAreaMaxGridCountPerPlayer);
        configData.set("protected_areas.ray_trace_distance", protectedAreaRayTraceDistance);

        configData.set("trash_bin.enable", enableTrashBin);
        configData.set("trash_bin.rows", trashBinRows);
        configData.set("trash_bin.history_count", trashBinHistorySize);
        configData.set("trash_bin.blacklist", trashBinBlacklist);

        configData.save();
        readValues();
        AutoCleanerEventHandler.resetTimer();
    }

    public static void saveClientSettings() {
        if (configData == null) {
            return;
        }
        configData.set("trash_bin.show_button", showTrashBinButton);
        configData.set("trash_bin.button_x", trashBinButtonX);
        configData.set("trash_bin.button_y", trashBinButtonY);
        configData.save();
    }
}
