package dev.xyat.itemcontrol.item.client;

import com.mojang.logging.LogUtils;
import dev.xyat.itemcontrol.item.client.gui.BannedItemPage;
import dev.xyat.itemcontrol.item.client.gui.DamageTypeEditorPage;
import dev.xyat.itemcontrol.item.client.gui.DirectEntityImmunityEditorPage;
import dev.xyat.itemcontrol.item.client.gui.ItemSearchCache;
import dev.xyat.itemcontrol.item.client.gui.ItemCacheHudRenderer;
import dev.xyat.itemcontrol.item.client.gui.ItemTagEditorPage;
import dev.xyat.itemcontrol.item.client.gui.ItemPropertyEditorPage;
import dev.xyat.itemcontrol.item.client.gui.MergeItemPage;
import dev.xyat.itemcontrol.item.client.gui.ProtectionItemEditorPage;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.text.KineticI18n;
import org.slf4j.Logger;

public final class ItemClientProxy {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean installed;

    private ItemClientProxy() {
    }

    public static void install() {
        if (installed) return;
        installed = true;
        ItemCacheHudRenderer.install();
        KineticClientEvents.onLogout(ItemClientProxy::onClientLogout);
    }

    // 编辑器在服务端回包后以 KineticGui.openChild 打开，返回到打开时的当前界面（无界面时回到游戏）
    // Editors open with KineticGui.openChild after the server reply; back returns to the screen current at that
    // moment (or the game when none is open).
    public static void requestOpenEditorFromCurrentScreen(int editorType) {
        ItemNetwork.requestOpenEditor(editorType);
    }

    public static void openBannedGui() {
        ItemSearchCache.prepareCache(() -> KineticGui.openChild(new BannedItemPage()));
    }

    public static void openMergeGui() {
        ItemSearchCache.prepareCache(() -> KineticGui.openChild(new MergeItemPage()));
    }

    public static void openItemTagEditor() {
        ItemSearchCache.prepareCache(() -> KineticGui.openChild(new ItemTagEditorPage()));
    }

    public static void openProtectionEditor(java.util.List<String> rules) {
        ItemSearchCache.prepareCache(() ->
                KineticGui.openChild(new ProtectionItemEditorPage(rules))
        );
    }

    public static void openItemPropertyEditor(String pendingJson, String activeJson) {
        dev.xyat.itemcontrol.item.config.ItemPropertyConfig.applyServerSnapshots(pendingJson, activeJson);
        ItemSearchCache.prepareCache(() -> KineticGui.openChild(new ItemPropertyEditorPage(pendingJson)));
    }

    public static void openDamageTypeEditor(java.util.List<String> entries) {
        KineticGui.openChild(new DamageTypeEditorPage(entries));
    }

    public static void openDirectEntityImmunityEditor(java.util.List<String> entries) {
        KineticGui.openChild(new DirectEntityImmunityEditorPage(entries));
    }

    public static void applyEditorSaveResult(int editorType, boolean success, java.util.List<String> entries, String messageKey) {
        KineticPage current = KineticGui.currentPage();
        if (editorType == ItemNetwork.EDITOR_BAN_ITEM && current instanceof BannedItemPage page) {
            page.applySaveResult(success);
        } else if (editorType == ItemNetwork.EDITOR_MERGE_ITEM && current instanceof MergeItemPage page) {
            page.applySaveResult(success);
        } else if (editorType == ItemNetwork.EDITOR_ITEM_TAG && current instanceof ItemTagEditorPage page) {
            page.applySaveResult(success);
        } else if (editorType == ItemNetwork.EDITOR_PROTECTION_ITEM && current instanceof ProtectionItemEditorPage page) {
            page.applySaveResult(success, entries);
        } else if (editorType == ItemNetwork.EDITOR_DAMAGE_IMMUNITY && current instanceof DamageTypeEditorPage page) {
            page.applySaveResult(success, entries);
        } else if (editorType == ItemNetwork.EDITOR_DIRECT_ENTITY_IMMUNITY && current instanceof DirectEntityImmunityEditorPage page) {
            page.applySaveResult(success, entries);
        }

        if (success) {
            String successKey = getEditorSaveSuccessKey(editorType);
            if (!successKey.isBlank()) {
                KineticOverlays.toast(
                        "itemcontrol_editor_save_" + editorType,
                        KineticI18n.translatable(successKey)
                );
            }
        } else if (messageKey != null && !messageKey.isBlank()) {
            KineticOverlays.toast(
                    "itemcontrol_editor_save_" + editorType,
                    KineticI18n.translatable(messageKey)
            );
        }
    }

    public static void applyItemPropertySaveResult(boolean success, String pendingJson, String messageKey) {
        ItemPropertyEditorPage page = KineticGui.currentPage(ItemPropertyEditorPage.class);
        if (page != null) {
            page.applySaveResult(success, pendingJson, messageKey);
        }
        if (success) {
            KineticOverlays.toast(
                    "itemcontrol_item_property_save",
                    KineticI18n.translatable("msg.itemcontrol.item_property.saved_restart")
            );
        } else if (messageKey != null && !messageKey.isBlank()) {
            KineticOverlays.toast("itemcontrol_item_property_save", KineticI18n.translatable(messageKey));
        }
    }

    private static String getEditorSaveSuccessKey(int editorType) {
        return switch (editorType) {
            case ItemNetwork.EDITOR_BAN_ITEM -> "msg.itemcontrol.item.banitem.save_success";
            case ItemNetwork.EDITOR_MERGE_ITEM -> "msg.itemcontrol.item.mergeitem.save_success";
            case ItemNetwork.EDITOR_PROTECTION_ITEM -> "msg.itemcontrol.item.protection_editor.save_success";
            case ItemNetwork.EDITOR_DAMAGE_IMMUNITY -> "msg.itemcontrol.item.damage_type_editor.save_success";
            case ItemNetwork.EDITOR_DIRECT_ENTITY_IMMUNITY -> "msg.itemcontrol.item.direct_entity_editor.save_success";
            case ItemNetwork.EDITOR_ITEM_TAG -> "msg.itemcontrol.item.item_tag.save_success";
            default -> "";
        };
    }

    public static void handleSyncBanConfig(String jsonData) {
        try {
            boolean ok = BanItemConfig.applyJson(jsonData, "client sync packet", false);
            if (!ok) {
                LOGGER.warn("客户端同步配置被拒绝，保留当前内存配置");
                return;
            }
            ItemSearchCache.clear();
        } catch (Throwable e) {
            LOGGER.error("同步物品封禁数据时发生异常", e);
        }
    }

    private static void onClientLogout() {
        try {
            BanItemConfig.load();
            dev.xyat.itemcontrol.item.config.ItemPropertyConfig.load();
            dev.xyat.itemcontrol.item.property.ItemPropertyOverrides.applyBlockOverrides();
            ItemSearchCache.clear();
        } catch (Throwable e) {
            LOGGER.error("离开服务器后重载本地物品封禁配置失败", e);
        }
    }
}
