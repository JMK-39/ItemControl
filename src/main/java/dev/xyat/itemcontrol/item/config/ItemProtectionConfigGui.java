package dev.xyat.itemcontrol.item.config;

import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.kineticcore.config.client.KTConfigPage;
import dev.xyat.kineticcore.config.client.KTConfigScope;
import dev.xyat.itemcontrol.item.client.ItemClientProxy;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class ItemProtectionConfigGui {
    public static final String PAGE_ID = "itemcontrol:item_protection";

    private ItemProtectionConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.itemcontrol.item.protection")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.MIXED)
                .applyNotice(Component.translatable("cfg.itemcontrol.item.protection.apply_notice"))
                .section(Component.translatable("cfg.itemcontrol.item.protection"))
                .description(Component.translatable("cfg.itemcontrol.item.protection.description"))
                .booleanValue(
                        "enable_item_protection",
                        Component.translatable("cfg.itemcontrol.item.prot.enable"),
                        () -> ItemProtectionConfig.enableItemProtection,
                        value -> ItemProtectionConfig.enableItemProtection = value,
                        true,
                        Component.translatable("cfg.itemcontrol.item.prot.enable.tooltip")
                )
                .booleanValue(
                        "enable_void_salvage",
                        Component.translatable("cfg.itemcontrol.item.prot.void_salvage"),
                        () -> ItemProtectionConfig.enableVoidSalvage,
                        value -> ItemProtectionConfig.enableVoidSalvage = value,
                        true,
                        Component.translatable("cfg.itemcontrol.item.prot.void_salvage.tooltip")
                )
                .action(
                        "open_protection_editor",
                        Component.translatable("cfg.itemcontrol.item.editor.protection"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_PROTECTION_ITEM),
                        Component.translatable("cfg.itemcontrol.item.editor.protection.tooltip")
                )
                .booleanValue(
                        "global_damage_immunity_enabled",
                        Component.translatable("cfg.itemcontrol.item.prot.global.damage.immunity.enable"),
                        () -> ItemProtectionConfig.enableGlobalItemDamageImmunity,
                        value -> ItemProtectionConfig.enableGlobalItemDamageImmunity = value,
                        true,
                        Component.translatable("cfg.itemcontrol.item.prot.global.damage.immunity.enable.tip")
                )
                .action(
                        "open_damage_immunity_editor",
                        Component.translatable("cfg.itemcontrol.item.editor.damage_immunity"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_DAMAGE_IMMUNITY),
                        Component.translatable("cfg.itemcontrol.item.editor.damage_immunity.tooltip")
                )
                .booleanValue(
                        "global_direct_entity_immunity_enabled",
                        Component.translatable("cfg.itemcontrol.item.prot.global.direct.entity.immunity.enable"),
                        () -> ItemProtectionConfig.enableGlobalDirectEntityImmunity,
                        value -> ItemProtectionConfig.enableGlobalDirectEntityImmunity = value,
                        true,
                        Component.translatable("cfg.itemcontrol.item.prot.global.direct.entity.immunity.enable.tip")
                )
                .action(
                        "open_direct_entity_immunity_editor",
                        Component.translatable("cfg.itemcontrol.item.prot.global.direct.entity.immunity.list"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_DIRECT_ENTITY_IMMUNITY),
                        Component.translatable("cfg.itemcontrol.item.prot.global.direct.entity.immunity.list.tip")
                )
                .section(Component.translatable("cfg.itemcontrol.item.editors.title"))
                .description(Component.translatable("cfg.itemcontrol.item.editors.description"))
                .action(
                        "open_banitem_editor",
                        Component.translatable("cfg.itemcontrol.item.editor.banitem"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_BAN_ITEM),
                        Component.translatable("cfg.itemcontrol.item.editor.banitem.tooltip")
                )
                .action(
                        "open_mergeitem_editor",
                        Component.translatable("cfg.itemcontrol.item.editor.mergeitem"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_MERGE_ITEM),
                        Component.translatable("cfg.itemcontrol.item.editor.mergeitem.tooltip")
                )
                .action(
                        "open_item_tag_editor",
                        Component.translatable("cfg.itemcontrol.item.editor.item_tag"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_ITEM_TAG),
                        Component.translatable("cfg.itemcontrol.item.editor.item_tag.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreen(parent, PAGE_ID);
    }

}
