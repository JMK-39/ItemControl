package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.kineticcore.api.client.selector.HudPositionEditor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import dev.xyat.kineticcore.api.client.screen.KineticNativeScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class TrashBinButtonEditorScreen extends KineticNativeScreen {
    private static final int BUTTON_SIZE = 16;
    private static final int DEFAULT_BUTTON_X = 148;
    private static final int DEFAULT_BUTTON_Y = 61;

    private final Screen parent;
    private final HudPositionEditor editor = new HudPositionEditor();
    private boolean draftConfigured;

    public TrashBinButtonEditorScreen() {
        this(null);
    }

    public TrashBinButtonEditorScreen(Screen parent) {
        super(Component.translatable("screen.itemcontrol.cleaner.trash_bin_button_editor.title"));
        this.parent = parent;
        reserveStandaloneDraft();
    }

    @Override
    protected void init() {
        int inventoryLeft = HudPositionEditor.getInventoryLeft(width);
        int inventoryTop = HudPositionEditor.getInventoryTop(height);

        editor.initialize(
                width,
                height,
                BUTTON_SIZE,
                BUTTON_SIZE,
                inventoryLeft + CleanerConfig.trashBinButtonX,
                inventoryTop + CleanerConfig.trashBinButtonY,
                inventoryLeft + DEFAULT_BUTTON_X,
                inventoryTop + DEFAULT_BUTTON_Y
        );
        if (!draftConfigured) {
            configureStandaloneDraft(editor::snapshot, editor::restore);
            draftConfigured = true;
        }

        editor.addControlButtons(
                this::addRenderableWidget,
                Component.translatable("gui.itemcontrol.cleaner.cleaner.trash_bin_button_editor.save"),
                Component.translatable("gui.itemcontrol.cleaner.cleaner.trash_bin_button_editor.reset"),
                Component.translatable("gui.itemcontrol.cleaner.cleaner.trash_bin_button_editor.cancel"),
                this::saveAndClose,
                this::closeWithoutSaving
        );
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            HudPositionEditor.renderInventoryReference(
                    graphics,
                    font,
                    minecraft.player,
                    width,
                    height,
                    mouseX,
                    mouseY
            );
        }

        editor.render(
                graphics,
                font,
                mouseX,
                mouseY,
                title,
                Component.translatable("screen.itemcontrol.cleaner.trash_bin_button_editor.instruction_scale"),
                Component.translatable(
                        "screen.itemcontrol.cleaner.trash_bin_button_editor.position_scale",
                        Component.literal(String.valueOf(currentRelativeX())).withStyle(ChatFormatting.AQUA),
                        Component.literal(String.valueOf(currentRelativeY())).withStyle(ChatFormatting.AQUA)
                ),
                (g, x, y, mx, my) -> TrashBinButton.renderIcon(g, x, y, false)
        );

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editor.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (editor.mouseDragged(mouseX, mouseY, button)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (editor.mouseReleased(button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editor.keyPressed(keyCode, hasShiftDown())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        closeWithoutSaving();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void saveAndClose() {
        CleanerConfig.setTrashBinButtonPosition(currentRelativeX(), currentRelativeY());
        commitDraft();
        closeScreen();
    }

    private void closeWithoutSaving() {
        closeScreen();
    }

    private void closeScreen() {
        Minecraft.getInstance().setScreen(parent);
    }

    private int currentRelativeX() {
        return editor.getX() - HudPositionEditor.getInventoryLeft(width);
    }

    private int currentRelativeY() {
        return editor.getY() - HudPositionEditor.getInventoryTop(height);
    }
}
