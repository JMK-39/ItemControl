package dev.xyat.itemcontrol.cleaner.config;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.kineticcore.api.config.client.KTServerConfigClient;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.itemcontrol.cleaner.client.gui.CleanerItemRuleEditorPage;
import dev.xyat.itemcontrol.cleaner.client.gui.TrashBinButtonEditorPage;

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

    // 原 create(Screen parent)：以当前界面为父打开配置页 / Former create(Screen parent): opens the config page as a child of the current screen.
    public static void open() {
        KTConfigApi.openPage(PAGE_ID);
    }

    private static KTConfigPage buildServerPage() {
        return KTConfigPage.builder(PAGE_ID, KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner"))
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.MIXED)
                .applyNotice(KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.apply_notice"))
                .divider()
                .booleanValue(
                        "enable_cleaner",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.enable"),
                        () -> CleanerConfig.enableCleaner,
                        value -> CleanerConfig.enableCleaner = value,
                        true,
                        null
                )
                .booleanValue(
                        "hard_disable_cleaner",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.hard_disabled"),
                        () -> CleanerConfig.isCleanerHardDisabled,
                        value -> CleanerConfig.isCleanerHardDisabled = value,
                        false,
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.hard_disabled.tooltip")
                )
                .booleanValue(
                        "clean_experience_orbs",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.clean_experience_orbs"),
                        () -> CleanerConfig.cleanExperienceOrbs,
                        value -> CleanerConfig.cleanExperienceOrbs = value,
                        false,
                        null
                )
                .booleanValue(
                        "allow_manual_clean",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.allow_manual"),
                        () -> CleanerConfig.allowManualClean,
                        value -> CleanerConfig.allowManualClean = value,
                        true,
                        null
                )
                .intValueUnbounded(
                        "cleaner_interval_seconds",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.interval"),
                        () -> CleanerConfig.cleanerIntervalSeconds,
                        value -> CleanerConfig.cleanerIntervalSeconds = value,
                        600,
                        null
                )
                .action(
                        "open_item_whitelist_editor",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.whitelist"),
                        CleanerConfigGui::openItemWhitelistEditor,
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.whitelist.tooltip")
                )
                .booleanValue(
                        "enable_entity_cleaning",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.entity_cleaning"),
                        () -> CleanerConfig.enableEntityCleaning,
                        value -> CleanerConfig.enableEntityCleaning = value,
                        false,
                        null
                )
                .entityList(
                        "entity_list",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.entity_list"),
                        () -> CleanerConfig.entityList,
                        value -> CleanerConfig.entityList = value,
                        List.of("minecraft:arrow", "minecraft:spectral_arrow"),
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.cleaner.entity_list.tooltip")
                )
                .divider()
                .booleanValue(
                        "enable_trash_bin",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.bin.enable"),
                        () -> CleanerConfig.enableTrashBin,
                        value -> CleanerConfig.enableTrashBin = value,
                        true,
                        null
                )
                .intValue(
                        "trash_bin_rows",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.bin.rows"),
                        () -> CleanerConfig.trashBinRows,
                        value -> CleanerConfig.trashBinRows = value,
                        28,
                        1,
                        Integer.MAX_VALUE,
                        null
                )
                .intValue(
                        "trash_bin_history_size",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.bin.history"),
                        () -> CleanerConfig.trashBinHistorySize,
                        value -> CleanerConfig.trashBinHistorySize = value,
                        3,
                        1,
                        Integer.MAX_VALUE,
                        null
                )
                .action(
                        "open_trash_bin_blacklist_editor",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.bin.blacklist"),
                        CleanerConfigGui::openTrashBinBlacklistEditor,
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.bin.blacklist.tooltip")
                )
                .divider()
                .booleanValue(
                        "enable_protected_areas",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.area.enable"),
                        () -> CleanerConfig.enableProtectedAreas,
                        value -> CleanerConfig.enableProtectedAreas = value,
                        true,
                        null
                )
                .action(
                        "open_protected_area_tool_editor",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.area.tool"),
                        CleanerConfigGui::openProtectedAreaToolEditor,
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.area.tool.tooltip")
                )
                .intValue(
                        "protected_area_max_grid_count_per_player",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.area.max_grids"),
                        () -> CleanerConfig.protectedAreaMaxGridCountPerPlayer,
                        value -> CleanerConfig.protectedAreaMaxGridCountPerPlayer = value,
                        6400,
                        1,
                        Integer.MAX_VALUE,
                        null
                )
                .doubleValueUnbounded(
                        "protected_area_ray_trace_distance",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.area.ray_trace_distance"),
                        () -> CleanerConfig.protectedAreaRayTraceDistance,
                        value -> CleanerConfig.protectedAreaRayTraceDistance = value,
                        32.0D,
                        null
                )
                .build();
    }

    private static KTConfigPage buildClientPage() {
        return KTConfigPage.builder(CLIENT_PAGE_ID, KineticI18n.translatable("cfg.itemcontrol.cleaner.client"))
                .scope(KTConfigScope.CLIENT_LOCAL)
                .pageDescription(KineticI18n.translatable("cfg.itemcontrol.cleaner.client.description"))
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .divider()
                .booleanValue(
                        "show_trash_bin_button",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.bin.show_button"),
                        () -> CleanerConfig.showTrashBinButton,
                        value -> CleanerConfig.showTrashBinButton = value,
                        true,
                        null
                )
                .action(
                        "edit_trash_bin_button_position",
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.bin.edit_button_position"),
                        CleanerConfigGui::openTrashBinButtonEditor,
                        KineticI18n.translatable("cfg.itemcontrol.cleaner.bin.edit_button_position.tooltip")
                )
                .onSave(CleanerConfig::saveClientSettings)
                .build();
    }

    private static void openItemWhitelistEditor() {
        List<String> values = KTServerConfigClient.getStringList(
                PAGE_ID,
                "item_whitelist",
                CleanerConfig.itemWhitelist
        );
        KineticGui.openChild(new CleanerItemRuleEditorPage(
                CleanerItemRuleEditorPage.Mode.CLEANER_WHITELIST,
                values,
                updated -> KTServerConfigClient.savePartial(
                        PAGE_ID,
                        Map.of("item_whitelist", new ArrayList<>(updated))
                )
        ));
    }

    private static void openTrashBinBlacklistEditor() {
        List<String> values = KTServerConfigClient.getStringList(
                PAGE_ID,
                "trash_bin_blacklist",
                CleanerConfig.trashBinBlacklist
        );
        KineticGui.openChild(new CleanerItemRuleEditorPage(
                CleanerItemRuleEditorPage.Mode.TRASH_BLACKLIST,
                values,
                updated -> KTServerConfigClient.savePartial(
                        PAGE_ID,
                        Map.of("trash_bin_blacklist", new ArrayList<>(updated))
                )
        ));
    }

    private static void openProtectedAreaToolEditor() {
        String current = KTServerConfigClient.getString(
                PAGE_ID,
                "protected_area_tool_item",
                CleanerConfig.protectedAreaToolItem
        );
        KineticGui.openChild(new CleanerItemRuleEditorPage(
                CleanerItemRuleEditorPage.Mode.AREA_TOOL,
                List.of(current),
                updated -> !updated.isEmpty() && KTServerConfigClient.savePartial(
                        PAGE_ID,
                        Map.of("protected_area_tool_item", updated.get(0))
                )
        ));
    }

    private static void openTrashBinButtonEditor() {
        KineticGui.openChild(new TrashBinButtonEditorPage());
    }
}
