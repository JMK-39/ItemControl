package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.render.KineticTexture;
import dev.xyat.kineticcore.api.client.gui.widget.KineticCustomControl;
import net.minecraft.network.chat.Component;

/**
 * 背包界面中的垃圾桶贴图按钮（原 KineticWidgets.createTextureButton）。
 * Trash-bin texture button injected into the inventory screen (formerly KineticWidgets.createTextureButton).
 */
public final class TrashBinButton extends KineticCustomControl {
    private static final int SIZE = 16;
    // 贴图 16x32：上半为常态，下半为悬停态 / 16x32 texture: normal state on top, hovered state below.
    private static final KineticTexture TEXTURE = KineticTexture.of(
            CleanerModule.MODID,
            "textures/gui/trash_bin_button.png",
            SIZE,
            SIZE * 2
    );

    private final Component tooltip;
    private final Runnable action;

    public TrashBinButton(int x, int y, Component tooltip, Runnable action) {
        super(x, y, SIZE, SIZE);
        this.tooltip = tooltip;
        this.action = action;
        setTooltip(tooltip);
    }

    public static void renderIcon(KineticGraphics graphics, int x, int y, boolean hovered) {
        graphics.texture(TEXTURE, x, y, 0, hovered ? SIZE : 0, SIZE, SIZE);
    }

    @Override
    protected void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = controlHovered();
        renderIcon(graphics, controlX(), controlY(), hovered);
        // 注入原版界面的控件不经页面宿主绘制提示，这里按帧请求 / Injected controls have no page host for tooltips; request it per frame.
        if (hovered && tooltip != null) {
            KineticOverlays.requestTooltip(tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        // 原版 Button 仅响应左键 / Vanilla Button only reacted to the left button.
        if (!input.isLeft()) return false;
        // 与原版 Button 一致：先播放点击音效 / Same as vanilla Button: play the click sound first.
        playClickSound();
        if (action != null) action.run();
        return true;
    }

    @Override
    protected Component narration() {
        return tooltip;
    }
}
