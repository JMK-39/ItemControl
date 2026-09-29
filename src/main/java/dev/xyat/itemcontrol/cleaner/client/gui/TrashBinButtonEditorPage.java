package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.selector.KineticHudEditorPage;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;

/** Position editor for the trash-bin button on the inventory screen (native coordinates, fixed scale). */
public final class TrashBinButtonEditorPage extends KineticHudEditorPage {
    private static final int BUTTON_SIZE = 16;
    private static final int DEFAULT_BUTTON_X = 148;
    private static final int DEFAULT_BUTTON_Y = 61;
    private static final int INVENTORY_WIDTH = 176;
    private static final int INVENTORY_HEIGHT = 166;

    public TrashBinButtonEditorPage() {
        super(KineticI18n.translatable("screen.itemcontrol.cleaner.trash_bin_button_editor.title"));
    }

    @Override
    protected int elementWidth() {
        return BUTTON_SIZE;
    }

    @Override
    protected int elementHeight() {
        return BUTTON_SIZE;
    }

    @Override
    protected HudLayout initialLayout(int screenWidth, int screenHeight) {
        return new HudLayout(inventoryLeft(screenWidth) + CleanerConfig.trashBinButtonX,
                inventoryTop(screenHeight) + CleanerConfig.trashBinButtonY, 1.0D);
    }

    @Override
    protected HudLayout defaultLayout(int screenWidth, int screenHeight) {
        return new HudLayout(inventoryLeft(screenWidth) + DEFAULT_BUTTON_X,
                inventoryTop(screenHeight) + DEFAULT_BUTTON_Y, 1.0D);
    }

    @Override
    protected boolean scalable() {
        return false;
    }

    @Override
    protected void renderBackdrop(KineticGraphics graphics, int mouseX, int mouseY) {
        renderInventoryReference(graphics, width(), height(), mouseX, mouseY);
    }

    @Override
    protected void renderElement(KineticGraphics graphics, int x, int y, int mouseX, int mouseY) {
        TrashBinButton.renderIcon(graphics, x, y, false);
    }

    @Override
    protected Component instruction() {
        return KineticI18n.translatable("screen.itemcontrol.cleaner.trash_bin_button_editor.instruction_scale");
    }

    @Override
    protected Component positionText(HudLayout layout) {
        return KineticI18n.translatable(
                "screen.itemcontrol.cleaner.trash_bin_button_editor.position_scale",
                Component.literal(String.valueOf(layout.x() - inventoryLeft(width()))),
                Component.literal(String.valueOf(layout.y() - inventoryTop(height()))),
                Component.literal("100")
        );
    }

    @Override
    protected void save(HudLayout layout) {
        CleanerConfig.setTrashBinButtonPosition(layout.x() - inventoryLeft(width()), layout.y() - inventoryTop(height()));
    }

    private static int inventoryLeft(int screenWidth) {
        return (screenWidth - INVENTORY_WIDTH) / 2;
    }

    private static int inventoryTop(int screenHeight) {
        return (screenHeight - INVENTORY_HEIGHT) / 2;
    }
}
