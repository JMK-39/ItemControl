package dev.xyat.itemcontrol.item.config;

import dev.xyat.itemcontrol.item.client.ItemClientProxy;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Server-authoritative entry point for the restart-required item property editor. */
public final class ItemPropertyConfigGui {
    public static final String PAGE_ID = "itemcontrol:item_properties";

    private ItemPropertyConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.itemcontrol.item_property.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.RESTART_GAME)
                .applyNotice(Component.translatable("cfg.itemcontrol.item_property.restart_notice"))
                .pageDescription(Component.translatable("cfg.itemcontrol.item_property.description"))
                .action(
                        "open_editor",
                        Component.translatable("cfg.itemcontrol.item_property.open_editor"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_ITEM_PROPERTIES),
                        Component.translatable("cfg.itemcontrol.item_property.open_editor.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreenForOwner(parent, "itemcontrol");
    }
}
