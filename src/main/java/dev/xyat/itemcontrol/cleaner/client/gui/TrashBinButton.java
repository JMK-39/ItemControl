package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class TrashBinButton extends AbstractButton {
    private static final int SIZE = 16;

    public TrashBinButton(int x, int y) {
        super(x, y, SIZE, SIZE, Component.translatable("gui.itemcontrol.cleaner.cleaner.button.tooltip"));
    }

    @Override
    public void onPress() {
        CleanerNetwork.sendToServer(new CleanerNetwork.OpenTrashBinRequest());
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderIcon(graphics, getX(), getY(), isHoveredOrFocused());
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    public static void renderIcon(GuiGraphics graphics, int x, int y, boolean hovered) {
        graphics.blit(
                InventoryButtonEventHandler.TRASH_BIN_TEXTURE,
                x,
                y,
                0,
                hovered ? SIZE : 0,
                SIZE,
                SIZE,
                SIZE,
                SIZE * 2
        );
    }
}
