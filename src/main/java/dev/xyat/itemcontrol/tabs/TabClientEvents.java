package dev.xyat.itemcontrol.tabs;

import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class TabClientEvents {
    private static Component message = null;
    private static long expireTime = 0;
    private static boolean installed;

    private TabClientEvents() {
    }

    public static void install() {
        if (installed) return;
        installed = true;
        KineticClientEvents.onHudRender(KineticClientEvents.HudStage.HOTBAR, (graphics, partialTick) ->
                renderNotificationScaled(graphics, KineticClientRuntime.guiScaledWidth(), KineticClientRuntime.font()));
    }

    public static void showNotification(String langKey) {
        message = Component.translatable(langKey);
        expireTime = System.currentTimeMillis() + 4000;
    }

    public static void clearNotification() {
        message = null;
        expireTime = 0;
    }

    public static void renderNotificationScaled(GuiGraphics g, int vWidth, Font font) {
        if (message != null && System.currentTimeMillis() < expireTime) {
            int textWidth = font.width(message);
            int boxWidth = textWidth + 30;
            int x = (vWidth - boxWidth) / 2;
            int y = 5;

            g.pose().pushPose();
            g.pose().translate(0, 0, 1000);
            GuiTheme.panelAlt(g, x, y, boxWidth, 20);
            g.drawCenteredString(font, message, vWidth / 2, y + 6, 0xFFFFFF);
            g.pose().popPose();
        }
    }
}
