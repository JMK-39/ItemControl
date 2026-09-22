package dev.xyat.itemcontrol.cleaner;

import com.mojang.logging.LogUtils;
import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import dev.xyat.itemcontrol.cleaner.client.gui.InventoryButtonEventHandler;
import dev.xyat.itemcontrol.cleaner.command.CleanerCommandExtension;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfigGui;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.config.server.KTServerConfigSpec;
import org.slf4j.Logger;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import dev.xyat.itemcontrol.cleaner.client.CleanerSetup;

public final class CleanerModule {
    public static final String MODID = "itemcontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CleanerModule() {
        CleanerConfig.load();
        KTServerConfigApi.register(KTServerConfigSpec.builder("itemcontrol:cleaner")
                .booleanValue("enable_cleaner", () -> CleanerConfig.enableCleaner, value -> CleanerConfig.enableCleaner = value)
                .booleanValue("hard_disable_cleaner", () -> CleanerConfig.isCleanerHardDisabled, value -> CleanerConfig.isCleanerHardDisabled = value)
                .booleanValue("clean_experience_orbs", () -> CleanerConfig.cleanExperienceOrbs, value -> CleanerConfig.cleanExperienceOrbs = value)
                .booleanValue("allow_manual_clean", () -> CleanerConfig.allowManualClean, value -> CleanerConfig.allowManualClean = value)
                .intValue("cleaner_interval_seconds", () -> CleanerConfig.cleanerIntervalSeconds, value -> CleanerConfig.cleanerIntervalSeconds = value, Integer.MIN_VALUE, Integer.MAX_VALUE)
                .stringList("item_whitelist", () -> CleanerConfig.itemWhitelist, value -> CleanerConfig.itemWhitelist = value)
                .booleanValue("enable_entity_cleaning", () -> CleanerConfig.enableEntityCleaning, value -> CleanerConfig.enableEntityCleaning = value)
                .stringList("entity_list", () -> CleanerConfig.entityList, value -> CleanerConfig.entityList = value)
                .booleanValue("enable_trash_bin", () -> CleanerConfig.enableTrashBin, value -> CleanerConfig.enableTrashBin = value)
                .intValue("trash_bin_rows", () -> CleanerConfig.trashBinRows, value -> CleanerConfig.trashBinRows = value, 1, Integer.MAX_VALUE)
                .intValue("trash_bin_history_size", () -> CleanerConfig.trashBinHistorySize, value -> CleanerConfig.trashBinHistorySize = value, 1, Integer.MAX_VALUE)
                .stringList("trash_bin_blacklist", () -> CleanerConfig.trashBinBlacklist, value -> CleanerConfig.trashBinBlacklist = value)
                .booleanValue("enable_protected_areas", () -> CleanerConfig.enableProtectedAreas, value -> CleanerConfig.enableProtectedAreas = value)
                .stringValue("protected_area_tool_item", () -> CleanerConfig.protectedAreaToolItem, value -> CleanerConfig.protectedAreaToolItem = value)
                .intValue("protected_area_max_grid_count_per_player", () -> CleanerConfig.protectedAreaMaxGridCountPerPlayer, value -> CleanerConfig.protectedAreaMaxGridCountPerPlayer = value, 1, Integer.MAX_VALUE)
                .doubleValue("protected_area_ray_trace_distance", () -> CleanerConfig.protectedAreaRayTraceDistance, value -> CleanerConfig.protectedAreaRayTraceDistance = value, -Double.MAX_VALUE, Double.MAX_VALUE)
                .onSave(CleanerConfig::saveServerSettings)
                .build());
        CleanerInit.register();
        CleanerNetwork.register();
        CleanerCommandExtension.install();

        KineticPlatform.runOnClient(() -> () -> {
            CleanerSetup.register();
            CleanerConfigGui.load();
            InventoryButtonEventHandler.register();
        });
    }
}
