package dev.xyat.itemcontrol.tabs.network;

import dev.xyat.itemcontrol.tabs.TabConfig;
import dev.xyat.itemcontrol.tabs.TabModule;
import dev.xyat.itemcontrol.tabs.gui.TabUnifiedPage;
import dev.xyat.kineticcore.api.client.gui.KineticGui;

public final class TabNetworkClient {
    private TabNetworkClient() {
    }

    public static void handleSync() {
        TabModule.refreshTabs();
    }

    public static void handleOpenEditor(TabNetwork.OpenTabEditorPacket packet) {
        if (TabConfig.beginEdit(packet.json())) {
            // 返回当前界面（无界面时回到游戏）/ Back returns to the current screen (or the game when none is open).
            KineticGui.openChild(new TabUnifiedPage());
        }
    }
}
