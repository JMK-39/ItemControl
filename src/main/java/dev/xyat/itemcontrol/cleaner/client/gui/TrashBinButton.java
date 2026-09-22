package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import net.minecraft.client.gui.GuiGraphics;

public final class TrashBinButton {
    private static final int SIZE = 16;

    private TrashBinButton() {
    }

    public static void renderIcon(GuiGraphics graphics, int x, int y, boolean hovered) {
        KineticWidgets.renderTextureButtonIcon(
                graphics,
                x,
                y,
                SIZE,
                SIZE,
                InventoryButtonEventHandler.TRASH_BIN_TEXTURE,
                0,
                0,
                SIZE,
                SIZE,
                SIZE * 2,
                hovered
        );
    }
}
