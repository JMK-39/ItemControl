package dev.xyat.itemcontrol.item.config;

import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.itemcontrol.item.client.ItemClientProxy;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import net.minecraft.client.gui.screens.Screen;
import dev.xyat.kineticcore.api.text.KineticI18n;


public class ItemProtectionConfigGui {
    public static final String PAGE_ID = "itemcontrol:item_protection";

    private ItemProtectionConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        KineticI18n.translatable("cfg.itemcontrol.item.protection")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.MIXED)
                .applyNotice(KineticI18n.translatable("cfg.itemcontrol.item.protection.apply_notice"))
                .divider()
                .description(KineticI18n.translatable("cfg.itemcontrol.item.protection.description"))
                .booleanValue(
                        "enable_item_protection",
                        KineticI18n.translatable("cfg.itemcontrol.item.prot.enable"),
                        () -> ItemProtectionConfig.enableItemProtection,
                        value -> ItemProtectionConfig.enableItemProtection = value,
                        true,
                        KineticI18n.translatable("cfg.itemcontrol.item.prot.enable.tooltip")
                )
                .booleanValue(
                        "enable_void_salvage",
                        KineticI18n.translatable("cfg.itemcontrol.item.prot.void_salvage"),
                        () -> ItemProtectionConfig.enableVoidSalvage,
                        value -> ItemProtectionConfig.enableVoidSalvage = value,
                        true,
                        KineticI18n.translatable("cfg.itemcontrol.item.prot.void_salvage.tooltip")
                )
                .booleanValue(
                        "global_damage_immunity_enabled",
                        KineticI18n.translatable("cfg.itemcontrol.item.prot.global.damage.immunity.enable"),
                        () -> ItemProtectionConfig.enableGlobalItemDamageImmunity,
                        value -> ItemProtectionConfig.enableGlobalItemDamageImmunity = value,
                        true,
                        KineticI18n.translatable("cfg.itemcontrol.item.prot.global.damage.immunity.enable.tip")
                )
                .action(
                        "open_damage_immunity_editor",
                        KineticI18n.translatable("cfg.itemcontrol.item.editor.damage_immunity"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_DAMAGE_IMMUNITY),
                        KineticI18n.translatable("cfg.itemcontrol.item.editor.damage_immunity.tooltip")
                )
                .booleanValue(
                        "global_direct_entity_immunity_enabled",
                        KineticI18n.translatable("cfg.itemcontrol.item.prot.global.direct.entity.immunity.enable"),
                        () -> ItemProtectionConfig.enableGlobalDirectEntityImmunity,
                        value -> ItemProtectionConfig.enableGlobalDirectEntityImmunity = value,
                        true,
                        KineticI18n.translatable("cfg.itemcontrol.item.prot.global.direct.entity.immunity.enable.tip")
                )
                .action(
                        "open_direct_entity_immunity_editor",
                        KineticI18n.translatable("cfg.itemcontrol.item.prot.global.direct.entity.immunity.list"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_DIRECT_ENTITY_IMMUNITY),
                        KineticI18n.translatable("cfg.itemcontrol.item.prot.global.direct.entity.immunity.list.tip")
                )
                .divider()
                .description(KineticI18n.translatable("cfg.itemcontrol.item.editors.description"))
                .action(
                        "open_banitem_editor",
                        KineticI18n.translatable("cfg.itemcontrol.item.editor.banitem"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_BAN_ITEM),
                        KineticI18n.translatable("cfg.itemcontrol.item.editor.banitem.tooltip")
                )
                .action(
                        "open_mergeitem_editor",
                        KineticI18n.translatable("cfg.itemcontrol.item.editor.mergeitem"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_MERGE_ITEM),
                        KineticI18n.translatable("cfg.itemcontrol.item.editor.mergeitem.tooltip")
                )
                .action(
                        "open_item_tag_editor",
                        KineticI18n.translatable("cfg.itemcontrol.item.editor.item_tag"),
                        () -> ItemClientProxy.requestOpenEditorFromCurrentScreen(ItemNetwork.EDITOR_ITEM_TAG),
                        KineticI18n.translatable("cfg.itemcontrol.item.editor.item_tag.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreen(parent, PAGE_ID);
    }

}
