package dev.xyat.itemcontrol.tabs.config;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.itemcontrol.tabs.network.TabNetwork;

public final class TabConfigGui {
    public static final String PAGE_ID = "itemcontrol:tabs";

    private TabConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        KineticI18n.translatable("cfg.itemcontrol.tabs.tabs.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.RELOAD_REQUIRED)
                .applyNotice(KineticI18n.translatable("cfg.itemcontrol.tabs.tabs.apply_notice"))
                .pageDescription(KineticI18n.translatable("cfg.itemcontrol.tabs.tabs.description"))
                .action(
                        "open_editor",
                        KineticI18n.translatable("cfg.itemcontrol.tabs.tabs.open_editor"),
                        TabNetwork::requestOpenEditor,
                        KineticI18n.translatable("cfg.itemcontrol.tabs.tabs.open_editor.tooltip")
                )
                .build());
    }

    // 原 create(Screen parent)：以当前界面为父打开本模组配置中心 / Former create(Screen parent): opens this mod's config hub as a child of the current screen.
    public static void open() {
        KTConfigApi.openOwner("itemcontrol");
    }
}
