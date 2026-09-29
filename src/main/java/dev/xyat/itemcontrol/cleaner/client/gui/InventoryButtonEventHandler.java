package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public final class InventoryButtonEventHandler {
    private static boolean registered;

    private InventoryButtonEventHandler() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        KineticClientEvents.onScreenInitAfter(InventoryButtonEventHandler::onScreenInit);
    }

    private static void onScreenInit(KineticClientEvents.ScreenInitContext event) {
        if (!CleanerConfig.enableTrashBin || !CleanerConfig.showTrashBinButton) return;
        if (!(event.screen() instanceof InventoryScreen screen)) return;

        int x = screen.getGuiLeft() + CleanerConfig.trashBinButtonX;
        int y = screen.getGuiTop() + CleanerConfig.trashBinButtonY;

        Component tooltip = KineticI18n.translatable("gui.itemcontrol.cleaner.cleaner.button.tooltip");
        event.addControl(new TrashBinButton(
                x,
                y,
                tooltip,
                () -> CleanerNetwork.sendToServer(new CleanerNetwork.OpenTrashBinRequest())
        ));
    }
}
