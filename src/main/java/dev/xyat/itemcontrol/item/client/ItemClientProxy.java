package dev.xyat.itemcontrol.item.client;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.itemcontrol.item.ItemModule;
import dev.xyat.itemcontrol.item.client.gui.BannedItemScreen;
import dev.xyat.itemcontrol.item.client.gui.DamageTypeEditorScreen;
import dev.xyat.itemcontrol.item.client.gui.DirectEntityImmunityEditorScreen;
import dev.xyat.itemcontrol.item.client.gui.ItemSearchCache;
import dev.xyat.itemcontrol.item.client.gui.ItemTagEditorScreen;
import dev.xyat.itemcontrol.item.client.gui.MergeItemScreen;
import dev.xyat.itemcontrol.item.client.gui.ProtectionItemEditorScreen;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;


@Mod.EventBusSubscriber(modid = ItemModule.MODID, value = Dist.CLIENT)
public class ItemClientProxy {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Screen editorReturnScreen;

    public static void requestOpenEditorFromCurrentScreen(int editorType) {
        Minecraft minecraft = Minecraft.getInstance();
        editorReturnScreen = minecraft.screen;
        ItemNetwork.requestOpenEditor(editorType);
    }

    private static Screen takeEditorReturnScreen(Minecraft minecraft) {
        Screen parent = editorReturnScreen != null ? editorReturnScreen : minecraft.screen;
        editorReturnScreen = null;
        return parent;
    }

    public static void openBannedGui() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = takeEditorReturnScreen(minecraft);
        ItemSearchCache.prepareCache(() -> minecraft.setScreen(new BannedItemScreen(parent)));
    }

    public static void openMergeGui() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = takeEditorReturnScreen(minecraft);
        ItemSearchCache.prepareCache(() -> minecraft.setScreen(new MergeItemScreen(parent)));
    }

    public static void openItemTagEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = takeEditorReturnScreen(minecraft);
        ItemSearchCache.prepareCache(() -> minecraft.setScreen(new ItemTagEditorScreen(parent)));
    }

    public static void openProtectionEditor(java.util.List<String> rules) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = takeEditorReturnScreen(minecraft);
        minecraft.setScreen(new ProtectionItemEditorScreen(parent, rules));
    }

    public static void openDamageTypeEditor(java.util.List<String> entries) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = takeEditorReturnScreen(minecraft);
        minecraft.setScreen(new DamageTypeEditorScreen(parent, entries));
    }

    public static void openDirectEntityImmunityEditor(java.util.List<String> entries) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = takeEditorReturnScreen(minecraft);
        minecraft.setScreen(new DirectEntityImmunityEditorScreen(parent, entries));
    }


    public static void applyEditorSaveResult(int editorType, boolean success, java.util.List<String> entries, String messageKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (editorType == ItemNetwork.EDITOR_BAN_ITEM && minecraft.screen instanceof BannedItemScreen screen) {
            screen.applySaveResult(success);
        } else if (editorType == ItemNetwork.EDITOR_MERGE_ITEM && minecraft.screen instanceof MergeItemScreen screen) {
            screen.applySaveResult(success);
        } else if (editorType == ItemNetwork.EDITOR_ITEM_TAG && minecraft.screen instanceof ItemTagEditorScreen screen) {
            screen.applySaveResult(success);
        } else if (editorType == ItemNetwork.EDITOR_PROTECTION_ITEM && minecraft.screen instanceof ProtectionItemEditorScreen screen) {
            screen.applySaveResult(success, entries);
        } else if (editorType == ItemNetwork.EDITOR_DAMAGE_IMMUNITY && minecraft.screen instanceof DamageTypeEditorScreen screen) {
            screen.applySaveResult(success, entries);
        } else if (editorType == ItemNetwork.EDITOR_DIRECT_ENTITY_IMMUNITY && minecraft.screen instanceof DirectEntityImmunityEditorScreen screen) {
            screen.applySaveResult(success, entries);
        }
        if (success) {
            String successKey = getEditorSaveSuccessKey(editorType);
            if (!successKey.isBlank()) {
                GuiOverlay.toast(
                        "itemcontrol_editor_save_" + editorType,
                        Component.translatable(successKey)
                );
            }
        } else if (messageKey != null && !messageKey.isBlank()) {
            GuiOverlay.toast(
                    "itemcontrol_editor_save_" + editorType,
                    Component.translatable(messageKey)
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

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        try {
            BanItemConfig.load();
            ItemSearchCache.clear();
        } catch (Throwable e) {
            LOGGER.error("离开服务器后重载本地物品封禁配置失败", e);
        }
    }

}
