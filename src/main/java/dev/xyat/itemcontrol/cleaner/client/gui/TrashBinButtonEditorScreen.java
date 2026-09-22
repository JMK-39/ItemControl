package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.kineticcore.api.client.screen.KineticNativeScreen;
import dev.xyat.kineticcore.api.client.selector.HudPositionEditor;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
        setParentScreen(parent);
        reserveStandaloneDraft();
    }

    @Override
    protected void buildUi() {
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
                inventoryTop + DEFAULT_BUTTON_Y,
                1.0D,
                1.0D,
                1.0D
        );
        if (!draftConfigured) {
            configureStandaloneDraft(editor::snapshot, editor::restore);
            draftConfigured = true;
        }

        editor.addControlButtons(
                button -> addControl(button, null),
                Component.translatable("gui.itemcontrol.cleaner.trash_bin_button_editor.save"),
                Component.translatable("gui.itemcontrol.cleaner.trash_bin_button_editor.reset"),
                Component.translatable("gui.itemcontrol.cleaner.trash_bin_button_editor.cancel"),
                this::saveAndClose,
                this::closeWithoutSaving
        );
    }

    @Override
    protected void renderNativeBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var player = KineticClientRuntime.localPlayer();
        if (player != null) {
            HudPositionEditor.renderInventoryReference(
                    graphics,
                    font,
                    player,
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
                        Component.literal(String.valueOf(currentRelativeY())).withStyle(ChatFormatting.AQUA),
                        Component.literal("100").withStyle(ChatFormatting.AQUA)
                ),
                (g, x, y, mx, my) -> TrashBinButton.renderIcon(g, x, y, false)
        );
    }

    @Override
    protected boolean nativeMouseClicked(double mouseX, double mouseY, int button) {
        return editor.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean nativeMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return editor.mouseDragged(mouseX, mouseY, button);
    }

    @Override
    protected boolean nativeMouseReleased(double mouseX, double mouseY, int button) {
        return editor.mouseReleased(button);
    }

    @Override
    protected boolean nativeKeyPressed(int keyCode, int scanCode, int modifiers) {
        return editor.keyPressed(keyCode, KineticClientRuntime.shiftModifierDown());
    }

    @Override
    protected boolean handleCloseRequest() {
        closeWithoutSaving();
        return true;
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
        navigateBack();
    }

    private int currentRelativeX() {
        return editor.getX() - HudPositionEditor.getInventoryLeft(width);
    }

    private int currentRelativeY() {
        return editor.getY() - HudPositionEditor.getInventoryTop(height);
    }
}
