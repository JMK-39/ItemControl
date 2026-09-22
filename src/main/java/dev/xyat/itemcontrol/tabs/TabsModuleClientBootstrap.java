package dev.xyat.itemcontrol.tabs;

import dev.xyat.itemcontrol.tabs.config.TabConfigGui;

public final class TabsModuleClientBootstrap {
    private TabsModuleClientBootstrap() {
    }

    public static void init() {
        TabModule.register();
        TabClientEvents.install();
        TabConfigGui.load();
    }
}
