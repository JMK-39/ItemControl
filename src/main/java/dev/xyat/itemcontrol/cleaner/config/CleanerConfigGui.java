package dev.xyat.itemcontrol.cleaner.config;

import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.kineticcore.config.client.KTConfigPage;
import dev.xyat.kineticcore.config.client.KTConfigScope;
import dev.xyat.kineticcore.config.client.KTServerConfigClient;
import dev.xyat.itemcontrol.cleaner.client.gui.CleanerItemRuleEditorScreen;
import dev.xyat.itemcontrol.cleaner.client.gui.TrashBinButtonEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CleanerConfigGui {
    public static final String PAGE_ID = "itemcontrol:cleaner";
    public static final String CLIENT_PAGE_ID = "itemcontrol:client";

    public static void load() {
        KTConfigApi.register(buildServerPage());
        KTConfigApi.register(buildClientPage());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreen(parent, PAGE_ID);
    }

    private static KTConfigPage buildServerPage() {
        return KTConfigPage.builder(PAGE_ID, Component.translatable("cfg.itemcontrol.cleaner.cleaner"))
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.MIXED)
                .applyNotice(Component.translatable("cfg.itemcontrol.cleaner.cleaner.apply_notice"))
                .section(Component.translatable("cfg.itemcontrol.cleaner.cleaner"))
                .booleanValue(
                        "enable_cleaner",
                        Component.translatable("cfg.itemcontrol.cleaner.cleaner.enable"),
                        () -> CleanerConfig.enableCleaner,
                        value -> CleanerConfig.enableCleaner = value,
                        true,
                        null
                )
                .booleanValue(
                        "hard_disable_cleaner",
                        Component.translatable("cfg.itemcontrol.cleaner.cleaner.hard_disabled"),
                        () -> CleanerConfig.isCleanerHardDisabled,
                        value -> CleanerConfig.isCleanerHardDisabled = value,
                        false,
                        Component.translatable("cfg.itemcontrol.cleaner.cleaner.hard_disabled.tooltip")
                )
                .booleanValue(
                        "clean_experience_orbs",
                        Component.translatable("cfg.itemcontrol.cleaner.cleaner.clean_experience_orbs"),
                        () -> CleanerConfig.cleanExperienceOrbs,
                        value -> CleanerConfig.cleanExperienceOrbs = value,
                        false,
                        null
                )
                .booleanValue(
                        "allow_manual_clean",
                        Component.translatable("cfg.itemcontrol.cleaner.cleaner.allow_manual"),
                        () -> CleanerConfig.allowManualClean,
                        value -> CleanerConfig.allowManualClean = value,
                        true,
                        null
                )
                .intValue(
                        "cleaner_interval_seconds",
                        Component.translatable("cfg.itemcontrol.cleaner.cleaner.interval"),
                        () -> CleanerConfig.cleanerIntervalSeconds,
                        value -> CleanerConfig.cleanerIntervalSeconds = value,
                        600,
                        null
                )
                .action(
                        "open_item_whitelist_editor",
                        Component.translatable("cfg.itemcontrol.cleaner.cleaner.whitelist"),
                        CleanerConfigGui::openItemWhitelistEditor,
                        Component.translatable("cfg.itemcontrol.cleaner.cleaner.whitelist.tooltip")
                )
                .booleanValue(
                        "enable_entity_cleaning",
                        Component.translatable("cfg.itemcontrol.cleaner.cleaner.entity_cleaning"),
                        () -> CleanerConfig.enableEntityCleaning,
                        value -> CleanerConfig.enableEntityCleaning = value,
                        false,
                        null
                )
                .entityList(
                        "entity_list",
                        Component.translatable("cfg.itemcontrol.cleaner.cleaner.entity_list"),
                        () -> CleanerConfig.entityList,
                        value -> CleanerConfig.entityList = value,
                        List.of("minecraft:arrow", "minecraft:spectral_arrow"),
                        Component.translatable("cfg.itemcontrol.cleaner.cleaner.entity_list.tooltip")
                )
                .section(Component.translatable("cfg.itemcontrol.cleaner.trashbin"))
                .booleanValue(
                        "enable_trash_bin",
                        Component.translatable("cfg.itemcontrol.cleaner.bin.enable"),
                        () -> CleanerConfig.enableTrashBin,
                        value -> CleanerConfig.enableTrashBin = value,
                        true,
                        null
                )
                .intValue(
                        "trash_bin_rows",
                        Component.translatable("cfg.itemcontrol.cleaner.bin.rows"),
                        () -> CleanerConfig.trashBinRows,
                        value -> CleanerConfig.trashBinRows = value,
                        28,
                        1,
                        Integer.MAX_VALUE,
                        null
                )
                .intValue(
                        "trash_bin_history_size",
                        Component.translatable("cfg.itemcontrol.cleaner.bin.history"),
                        () -> CleanerConfig.trashBinHistorySize,
                        value -> CleanerConfig.trashBinHistorySize = value,
                        3,
                        1,
                        Integer.MAX_VALUE,
                        null
                )
                .action(
                        "open_trash_bin_blacklist_editor",
                        Component.translatable("cfg.itemcontrol.cleaner.bin.blacklist"),
                        CleanerConfigGui::openTrashBinBlacklistEditor,
                        Component.translatable("cfg.itemcontrol.cleaner.bin.blacklist.tooltip")
                )
                .section(Component.translatable("cfg.itemcontrol.cleaner.area"))
                .booleanValue(
                        "enable_protected_areas",
                        Component.translatable("cfg.itemcontrol.cleaner.area.enable"),
                        () -> CleanerConfig.enableProtectedAreas,
                        value -> CleanerConfig.enableProtectedAreas = value,
                        true,
                        null
                )
                .action(
                        "open_protected_area_tool_editor",
                        Component.translatable("cfg.itemcontrol.cleaner.area.tool"),
                        CleanerConfigGui::openProtectedAreaToolEditor,
                        Component.translatable("cfg.itemcontrol.cleaner.area.tool.tooltip")
                )
                .intValue(
                        "protected_area_max_grid_count_per_player",
                        Component.translatable("cfg.itemcontrol.cleaner.area.max_grids"),
                        () -> CleanerConfig.protectedAreaMaxGridCountPerPlayer,
                        value -> CleanerConfig.protectedAreaMaxGridCountPerPlayer = value,
                        6400,
                        1,
                        Integer.MAX_VALUE,
                        null
                )
                .doubleValue(
                        "protected_area_ray_trace_distance",
                        Component.translatable("cfg.itemcontrol.cleaner.area.ray_trace_distance"),
                        () -> CleanerConfig.protectedAreaRayTraceDistance,
                        value -> CleanerConfig.protectedAreaRayTraceDistance = value,
                        32.0D,
                        null
                )
                .build();
    }

    private static KTConfigPage buildClientPage() {
        return KTConfigPage.builder(CLIENT_PAGE_ID, Component.translatable("cfg.itemcontrol.cleaner.client"))
                .scope(KTConfigScope.CLIENT_LOCAL)
                .pageDescription(Component.translatable("cfg.itemcontrol.cleaner.client.description"))
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .section(Component.translatable("cfg.itemcontrol.cleaner.trashbin"))
                .booleanValue(
                        "show_trash_bin_button",
                        Component.translatable("cfg.itemcontrol.cleaner.bin.show_button"),
                        () -> CleanerConfig.showTrashBinButton,
                        value -> CleanerConfig.showTrashBinButton = value,
                        true,
                        null
                )
                .action(
                        "edit_trash_bin_button_position",
                        Component.translatable("cfg.itemcontrol.cleaner.bin.edit_button_position"),
                        CleanerConfigGui::openTrashBinButtonEditor,
                        Component.translatable("cfg.itemcontrol.cleaner.bin.edit_button_position.tooltip")
                )
                .onSave(CleanerConfig::saveClientSettings)
                .build();
    }

    private static void openItemWhitelistEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        List<String> values = KTServerConfigClient.getStringList(
                PAGE_ID,
                "item_whitelist",
                CleanerConfig.itemWhitelist
        );
        minecraft.setScreen(new CleanerItemRuleEditorScreen(
                parent,
                CleanerItemRuleEditorScreen.Mode.CLEANER_WHITELIST,
                values,
                updated -> KTServerConfigClient.savePartial(
                        PAGE_ID,
                        Map.of("item_whitelist", new ArrayList<>(updated))
                )
        ));
    }

    private static void openTrashBinBlacklistEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        List<String> values = KTServerConfigClient.getStringList(
                PAGE_ID,
                "trash_bin_blacklist",
                CleanerConfig.trashBinBlacklist
        );
        minecraft.setScreen(new CleanerItemRuleEditorScreen(
                parent,
                CleanerItemRuleEditorScreen.Mode.TRASH_BLACKLIST,
                values,
                updated -> KTServerConfigClient.savePartial(
                        PAGE_ID,
                        Map.of("trash_bin_blacklist", new ArrayList<>(updated))
                )
        ));
    }

    private static void openProtectedAreaToolEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        String current = KTServerConfigClient.getString(
                PAGE_ID,
                "protected_area_tool_item",
                CleanerConfig.protectedAreaToolItem
        );
        minecraft.setScreen(new CleanerItemRuleEditorScreen(
                parent,
                CleanerItemRuleEditorScreen.Mode.AREA_TOOL,
                List.of(current),
                updated -> !updated.isEmpty() && KTServerConfigClient.savePartial(
                        PAGE_ID,
                        Map.of("protected_area_tool_item", updated.get(0))
                )
        ));
    }

    private static void openTrashBinButtonEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new TrashBinButtonEditorScreen(minecraft.screen));
    }
}
