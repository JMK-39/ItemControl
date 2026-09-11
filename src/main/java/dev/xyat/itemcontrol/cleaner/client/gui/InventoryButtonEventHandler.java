package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

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
        MinecraftForge.EVENT_BUS.addListener(InventoryButtonEventHandler::onScreenInit);
        registered = true;
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!CleanerConfig.enableTrashBin || !CleanerConfig.showTrashBinButton) return;
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;

        int x = screen.getGuiLeft() + CleanerConfig.trashBinButtonX;
        int y = screen.getGuiTop() + CleanerConfig.trashBinButtonY;

        TrashBinButton trashButton = new TrashBinButton(x, y);
        trashButton.setTooltip(Tooltip.create(Component.translatable("gui.itemcontrol.cleaner.cleaner.button.tooltip")));
        event.addListener(trashButton);
    }
}
