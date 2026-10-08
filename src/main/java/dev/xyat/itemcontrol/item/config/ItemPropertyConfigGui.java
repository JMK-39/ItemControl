package dev.xyat.itemcontrol.item.config;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.itemcontrol.item.client.ItemClientProxy;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;

/** Server-authoritative entry point for item and optional accessory properties. */
public final class ItemPropertyConfigGui {
    public static final String PAGE_ID = "itemcontrol:item_properties";

    private ItemPropertyConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        KineticI18n.translatable("cfg.itemcontrol.item_property.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .applyNotice(KineticI18n.translatable("cfg.itemcontrol.item_property.restart_notice"))
                .pageDescription(KineticI18n.translatable("cfg.itemcontrol.item_property.description"))
                .action(
                        "open_editor",
                        KineticI18n.translatable("cfg.itemcontrol.item_property.open_editor"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_ITEM_PROPERTIES),
                        KineticI18n.translatable("cfg.itemcontrol.item_property.open_editor.tooltip")
                )
                .build());
    }

    // 原 create(Screen parent)：以当前界面为父打开本模组配置中心 / Former create(Screen parent): opens this mod's config hub as a child of the current screen.
    public static void open() {
        KTConfigApi.openOwner("itemcontrol");
    }
}
