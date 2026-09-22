package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.TextureButton;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class InventoryButtonEventHandler {
    public static final ResourceLocation TRASH_BIN_TEXTURE = new ResourceLocation(
            CleanerModule.MODID,
            "textures/gui/trash_bin_button.png"
    );

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

        Component tooltip = Component.translatable("gui.itemcontrol.cleaner.cleaner.button.tooltip");
        TextureButton trashButton = KineticWidgets.createTextureButton(
                x, y, 16, 16,
                TRASH_BIN_TEXTURE,
                0, 0, 16, 16, 32,
                tooltip,
                tooltip,
                () -> CleanerNetwork.sendToServer(new CleanerNetwork.OpenTrashBinRequest())
        );
        event.addControl(trashButton);
    }
}
