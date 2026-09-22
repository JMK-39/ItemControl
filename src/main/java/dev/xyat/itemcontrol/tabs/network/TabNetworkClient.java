package dev.xyat.itemcontrol.tabs.network;

import dev.xyat.itemcontrol.tabs.TabConfig;
import dev.xyat.itemcontrol.tabs.TabModule;
import dev.xyat.itemcontrol.tabs.gui.TabUnifiedScreen;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.gui.screens.Screen;

public final class TabNetworkClient {
    private TabNetworkClient() {
    }

    public static void handleSync() {
        TabModule.refreshTabs();
    }

    public static void handleOpenEditor(TabNetwork.OpenTabEditorPacket packet) {
        Screen parent = KineticClientRuntime.currentScreen();
        if (TabConfig.beginEdit(packet.json())) {
            KineticClientRuntime.openScreen(new TabUnifiedScreen(parent));
        }
    }
}
