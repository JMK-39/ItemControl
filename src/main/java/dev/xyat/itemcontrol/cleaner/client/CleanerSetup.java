package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.itemcontrol.cleaner.CleanerInit;
import dev.xyat.itemcontrol.cleaner.client.gui.CleanerScreen;
import dev.xyat.kineticcore.api.client.registry.KineticClientMenus;

public final class CleanerSetup {
    private static boolean registered;

    private CleanerSetup() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        CleanerKeyBindings.register();
        CleanerAreaKeyBindings.register();
        CleanerKeyHandler.install();
        CleanerAreaTooltips.install();
        CleanerAreaClientEvents.install();
        KineticClientMenus.register(CleanerInit.TRASH_BIN, CleanerScreen::new);
    }
}
