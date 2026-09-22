package dev.xyat.itemcontrol.cleaner;

import dev.xyat.itemcontrol.cleaner.area.CleanerAreaManager;
import dev.xyat.itemcontrol.cleaner.client.gui.CleanerMenu;
import dev.xyat.itemcontrol.cleaner.event.AutoCleanerEventHandler;
import dev.xyat.kineticcore.api.registry.KineticMenuTypes;
import dev.xyat.kineticcore.api.registry.KineticRegistryHandle;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.world.inventory.MenuType;

public final class CleanerInit {
    public static final KineticRegistryHandle<MenuType<CleanerMenu>> TRASH_BIN = KineticMenuTypes.register(
            KineticResourceIds.of(CleanerModule.MODID, "trash_bin"),
            (windowId, inventory, data) -> new CleanerMenu(windowId, inventory)
    );

    private CleanerInit() {
    }

    public static void register() {
        AutoCleanerEventHandler.register();
        CleanerAreaManager.register();
    }
}
