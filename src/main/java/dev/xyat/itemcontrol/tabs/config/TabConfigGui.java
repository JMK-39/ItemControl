package dev.xyat.itemcontrol.tabs.config;

import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.kineticcore.config.client.KTConfigPage;
import dev.xyat.kineticcore.config.client.KTConfigScope;
import dev.xyat.itemcontrol.tabs.network.TabNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TabConfigGui {
    public static final String PAGE_ID = "itemcontrol:tabs";

    private TabConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.itemcontrol.tabs.tabs.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.RELOAD_REQUIRED)
                .applyNotice(Component.translatable("cfg.itemcontrol.tabs.tabs.apply_notice"))
                .pageDescription(Component.translatable("cfg.itemcontrol.tabs.tabs.description"))
                .action(
                        "open_editor",
                        Component.translatable("cfg.itemcontrol.tabs.tabs.open_editor"),
                        TabNetwork::requestOpenEditor,
                        Component.translatable("cfg.itemcontrol.tabs.tabs.open_editor.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreenForOwner(parent, "itemcontrol");
    }
}
