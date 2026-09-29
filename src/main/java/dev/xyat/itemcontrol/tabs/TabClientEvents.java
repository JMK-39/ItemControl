package dev.xyat.itemcontrol.tabs;

import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
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
                renderNotificationScaled(graphics, KineticClientRuntime.guiScaledWidth()));
    }

    public static void showNotification(String langKey) {
        message = KineticI18n.translatable(langKey);
        expireTime = System.currentTimeMillis() + 4000;
    }

    public static void clearNotification() {
        message = null;
        expireTime = 0;
    }

    public static void renderNotificationScaled(KineticGraphics g, int vWidth) {
        if (message != null && System.currentTimeMillis() < expireTime) {
            int textWidth = g.textWidth(message);
            int boxWidth = textWidth + 30;
            int x = (vWidth - boxWidth) / 2;
            int y = 5;

            g.isolated(() -> {
                // 原 translate(0, 0, 1000)：4 层 × 250 / Former translate(0, 0, 1000): 4 layers x 250.
                g.raise(4);
                KineticTheme.panelAlt(g, x, y, boxWidth, 20);
                g.centeredText(message, vWidth / 2, y + 6, 0xFFFFFF, true);
            });
        }
    }
}
