package dev.xyat.itemcontrol.item.client;

import com.mojang.logging.LogUtils;
import dev.xyat.itemcontrol.item.client.gui.BannedItemScreen;
import dev.xyat.itemcontrol.item.client.gui.DamageTypeEditorScreen;
import dev.xyat.itemcontrol.item.client.gui.DirectEntityImmunityEditorScreen;
import dev.xyat.itemcontrol.item.client.gui.ItemSearchCache;
import dev.xyat.itemcontrol.item.client.gui.ItemCacheHudRenderer;
import dev.xyat.itemcontrol.item.client.gui.ItemTagEditorScreen;
import dev.xyat.itemcontrol.item.client.gui.MergeItemScreen;
import dev.xyat.itemcontrol.item.client.gui.ProtectionItemEditorScreen;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;

public final class ItemClientProxy {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Screen editorReturnScreen;
    private static boolean installed;

    private ItemClientProxy() {
    }

    public static void install() {
        if (installed) return;
        installed = true;
        ItemCacheHudRenderer.install();
        KineticClientEvents.onLogout(ItemClientProxy::onClientLogout);
    }

    public static void requestOpenEditorFromCurrentScreen(int editorType) {
        editorReturnScreen = KineticClientRuntime.currentScreen();
        ItemNetwork.requestOpenEditor(editorType);
    }

    private static Screen takeEditorReturnScreen() {
        Screen current = KineticClientRuntime.currentScreen();
        Screen parent = editorReturnScreen != null ? editorReturnScreen : current;
        editorReturnScreen = null;
        return parent;
    }

    public static void openBannedGui() {
        Screen parent = takeEditorReturnScreen();
        ItemSearchCache.prepareCache(() -> KineticClientRuntime.openScreen(new BannedItemScreen(parent)));
    }

    public static void openMergeGui() {
        Screen parent = takeEditorReturnScreen();
        ItemSearchCache.prepareCache(() -> KineticClientRuntime.openScreen(new MergeItemScreen(parent)));
    }

    public static void openItemTagEditor() {
        Screen parent = takeEditorReturnScreen();
        ItemSearchCache.prepareCache(() -> KineticClientRuntime.openScreen(new ItemTagEditorScreen(parent)));
    }

    public static void openProtectionEditor(java.util.List<String> rules) {
        Screen parent = takeEditorReturnScreen();
        ItemSearchCache.prepareCache(() ->
                KineticClientRuntime.openScreen(new ProtectionItemEditorScreen(parent, rules))
        );
    }

    public static void openDamageTypeEditor(java.util.List<String> entries) {
        Screen parent = takeEditorReturnScreen();
        KineticClientRuntime.openScreen(new DamageTypeEditorScreen(parent, entries));
    }

    public static void openDirectEntityImmunityEditor(java.util.List<String> entries) {
        Screen parent = takeEditorReturnScreen();
        KineticClientRuntime.openScreen(new DirectEntityImmunityEditorScreen(parent, entries));
    }

    public static void applyEditorSaveResult(int editorType, boolean success, java.util.List<String> entries, String messageKey) {
        Screen current = KineticClientRuntime.currentScreen();
        if (editorType == ItemNetwork.EDITOR_BAN_ITEM && current instanceof BannedItemScreen screen) {
            screen.applySaveResult(success);
        } else if (editorType == ItemNetwork.EDITOR_MERGE_ITEM && current instanceof MergeItemScreen screen) {
            screen.applySaveResult(success);
        } else if (editorType == ItemNetwork.EDITOR_ITEM_TAG && current instanceof ItemTagEditorScreen screen) {
            screen.applySaveResult(success);
        } else if (editorType == ItemNetwork.EDITOR_PROTECTION_ITEM && current instanceof ProtectionItemEditorScreen screen) {
            screen.applySaveResult(success, entries);
        } else if (editorType == ItemNetwork.EDITOR_DAMAGE_IMMUNITY && current instanceof DamageTypeEditorScreen screen) {
            screen.applySaveResult(success, entries);
        } else if (editorType == ItemNetwork.EDITOR_DIRECT_ENTITY_IMMUNITY && current instanceof DirectEntityImmunityEditorScreen screen) {
            screen.applySaveResult(success, entries);
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
            ItemSearchCache.clear();
        } catch (Throwable e) {
            LOGGER.error("离开服务器后重载本地物品封禁配置失败", e);
        }
    }
}
