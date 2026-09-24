package dev.xyat.itemcontrol.item.network;

import dev.xyat.kineticcore.api.network.KineticCompression;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.itemcontrol.item.ItemModule;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.config.ItemProtectionConfig;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ItemNetwork {
    private static final Logger LOGGER = LogManager.getLogger("itemcontrol/ItemNetwork");
    private static final String PROTOCOL_VERSION = "5";

    private static final int MAX_COMPRESSED_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_BYTES = 8 * 1024 * 1024;
    public static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(ItemModule.MODID, "item_network"),
            PROTOCOL_VERSION,
            NetworkVersionPolicy.ANY
    );
    private static boolean registered;

    public static final int EDITOR_BAN_ITEM = 0;
    public static final int EDITOR_MERGE_ITEM = 1;
    public static final int EDITOR_PROTECTION_ITEM = 2;
    public static final int EDITOR_DAMAGE_IMMUNITY = 3;
    public static final int EDITOR_DIRECT_ENTITY_IMMUNITY = 4;
    public static final int EDITOR_ITEM_TAG = 5;
    public static final int EDITOR_ITEM_PROPERTIES = 6;

    private static final int MAX_PROTECTION_RULES = 4096;
    private static final int MAX_PROTECTION_RULE_LENGTH = 32767;
    private static final int MAX_RESOURCE_ENTRIES = 4096;
    private static final int MAX_RESOURCE_ENTRY_LENGTH = 1024;

    public static void register() {
        if (registered) return;
        registered = true;

        CHANNEL.registerClientbound(0, OpenBannedGuiPacket.class,
                NetworkCodec.of((buffer, packet) -> { }, buffer -> new OpenBannedGuiPacket()),
                OpenBannedGuiPacket::handleClient);
        CHANNEL.registerClientbound(1, OpenMergeGuiPacket.class,
                NetworkCodec.of((buffer, packet) -> { }, buffer -> new OpenMergeGuiPacket()),
                OpenMergeGuiPacket::handleClient);
        CHANNEL.registerClientbound(2, OpenProtectionEditorPacket.class,
                NetworkCodec.of(OpenProtectionEditorPacket::encode, OpenProtectionEditorPacket::decode),
                OpenProtectionEditorPacket::handleClient);
        CHANNEL.registerClientbound(3, OpenDamageTypeEditorPacket.class,
                NetworkCodec.of(OpenDamageTypeEditorPacket::encode, OpenDamageTypeEditorPacket::decode),
                OpenDamageTypeEditorPacket::handleClient);
        CHANNEL.registerClientbound(4, OpenDirectEntityImmunityEditorPacket.class,
                NetworkCodec.of(OpenDirectEntityImmunityEditorPacket::encode, OpenDirectEntityImmunityEditorPacket::decode),
                OpenDirectEntityImmunityEditorPacket::handleClient);
        CHANNEL.registerClientbound(5, OpenItemTagEditorPacket.class,
                NetworkCodec.of((buffer, packet) -> { }, buffer -> new OpenItemTagEditorPacket()),
                OpenItemTagEditorPacket::handleClient);
        CHANNEL.registerServerbound(6, SaveBanConfigPacket.class,
                NetworkCodec.of(SaveBanConfigPacket::encode, SaveBanConfigPacket::decode),
                SaveBanConfigPacket::handleServer);
        CHANNEL.registerClientbound(7, SyncBanConfigPacket.class,
                NetworkCodec.of(SyncBanConfigPacket::encode, SyncBanConfigPacket::decode),
                SyncBanConfigPacket::handleClient);
        CHANNEL.registerServerbound(8, RequestOpenEditorPacket.class,
                NetworkCodec.of((buffer, packet) -> buffer.writeVarInt(packet.editorType()),
                        buffer -> new RequestOpenEditorPacket(buffer.readVarInt())),
                RequestOpenEditorPacket::handleServer);
        CHANNEL.registerServerbound(9, SaveProtectionRulesPacket.class,
                NetworkCodec.of(SaveProtectionRulesPacket::encode, SaveProtectionRulesPacket::decode),
                SaveProtectionRulesPacket::handleServer);
        CHANNEL.registerServerbound(10, SaveDamageSourcesPacket.class,
                NetworkCodec.of(SaveDamageSourcesPacket::encode, SaveDamageSourcesPacket::decode),
                SaveDamageSourcesPacket::handleServer);
        CHANNEL.registerServerbound(11, SaveDirectEntitySourcesPacket.class,
                NetworkCodec.of(SaveDirectEntitySourcesPacket::encode, SaveDirectEntitySourcesPacket::decode),
                SaveDirectEntitySourcesPacket::handleServer);
        CHANNEL.registerClientbound(12, EditorSaveResultPacket.class,
                NetworkCodec.of(EditorSaveResultPacket::encode, EditorSaveResultPacket::decode),
                EditorSaveResultPacket::handleClient);
        CHANNEL.registerClientbound(13, OpenItemPropertyEditorPacket.class,
                NetworkCodec.of(OpenItemPropertyEditorPacket::encode, OpenItemPropertyEditorPacket::decode),
                OpenItemPropertyEditorPacket::handleClient);
        CHANNEL.registerClientbound(14, SyncItemPropertySnapshotPacket.class,
                NetworkCodec.of(SyncItemPropertySnapshotPacket::encode, SyncItemPropertySnapshotPacket::decode),
                SyncItemPropertySnapshotPacket::handleClient);
        CHANNEL.registerServerbound(15, SaveItemPropertyConfigPacket.class,
                NetworkCodec.of(SaveItemPropertyConfigPacket::encode, SaveItemPropertyConfigPacket::decode),
                SaveItemPropertyConfigPacket::handleServer);
        CHANNEL.registerClientbound(16, ItemPropertySaveResultPacket.class,
                NetworkCodec.of(ItemPropertySaveResultPacket::encode, ItemPropertySaveResultPacket::decode),
                ItemPropertySaveResultPacket::handleClient);

        KineticServerEvents.onPlayerLogin(KineticEventPriority.NORMAL, player -> sendServerConfigToPlayer(player, true));
        KineticServerEvents.onDatapackSync(KineticEventPriority.NORMAL, (server, player) -> {
            if (player != null) {
                sendServerConfigToPlayer(player, true);
                return;
            }
            BanItemConfig.load();
            CHANNEL.broadcast(new SyncBanConfigPacket(BanItemConfig.getNetworkJson()));
        });
    }

    public static void requestOpenEditor(int editorType) {
        CHANNEL.sendToServer(new RequestOpenEditorPacket(editorType));
    }

    public static void saveProtectionRules(List<String> rules) {
        CHANNEL.sendToServer(new SaveProtectionRulesPacket(rules));
    }

    public static void saveDamageSources(List<String> entries) {
        CHANNEL.sendToServer(new SaveDamageSourcesPacket(entries));
    }

    public static void saveDirectEntitySources(List<String> entries) {
        CHANNEL.sendToServer(new SaveDirectEntitySourcesPacket(entries));
    }

    public static void saveItemPropertyConfig(String json) {
        CHANNEL.sendToServer(new SaveItemPropertyConfigPacket(json));
    }

    private static void writeStringList(NetworkBuffer buf, List<String> values, int maxCount, int maxLength) {
        buf.writeStringList(values, maxCount, maxLength);
    }

    private static List<String> readStringList(NetworkBuffer buf, int maxCount, int maxLength) {
        return buf.readStringList(maxCount, maxLength);
    }

    public record RequestOpenEditorPacket(int editorType) {
        public static void encode(NetworkBuffer buf, RequestOpenEditorPacket msg) {
            buf.writeVarInt(msg.editorType);
        }

        public static RequestOpenEditorPacket decode(NetworkBuffer buf) {
            return new RequestOpenEditorPacket(buf.readVarInt());
        }

        public void handleServer(ServerPacketContext ctx) {
            ServerPlayer player = ctx.sender();
            if (player == null || !player.hasPermissions(2)) return;
            if (editorType == EDITOR_BAN_ITEM) {
                CHANNEL.sendToPlayer(player, new OpenBannedGuiPacket());
            } else if (editorType == EDITOR_MERGE_ITEM) {
                CHANNEL.sendToPlayer(player, new OpenMergeGuiPacket());
            } else if (editorType == EDITOR_PROTECTION_ITEM) {
                CHANNEL.sendToPlayer(player, new OpenProtectionEditorPacket(ItemProtectionConfig.indestructibleItemsRaw));
            } else if (editorType == EDITOR_DAMAGE_IMMUNITY) {
                CHANNEL.sendToPlayer(player, new OpenDamageTypeEditorPacket(ItemProtectionConfig.globalItemDamageImmunityRaw));
            } else if (editorType == EDITOR_DIRECT_ENTITY_IMMUNITY) {
                CHANNEL.sendToPlayer(player, new OpenDirectEntityImmunityEditorPacket(ItemProtectionConfig.globalDirectEntityImmunityRaw));
            } else if (editorType == EDITOR_ITEM_TAG) {
                CHANNEL.sendToPlayer(player, new OpenItemTagEditorPacket());
            } else if (editorType == EDITOR_ITEM_PROPERTIES) {
                CHANNEL.sendToPlayer(player, new OpenItemPropertyEditorPacket(
                        ItemPropertyConfig.pendingJson(), ItemPropertyConfig.activeJson()
                ));
            }
        
        }
    }

    public static void sendServerConfigToPlayer(ServerPlayer player, boolean reloadFromFile) {
        if (player == null) return;
        try {
            if (reloadFromFile) BanItemConfig.load();
            CHANNEL.sendToPlayer(player, new SyncBanConfigPacket(BanItemConfig.getNetworkJson()));
            CHANNEL.sendToPlayer(player, new SyncItemPropertySnapshotPacket(
                    ItemPropertyConfig.pendingJson(), ItemPropertyConfig.activeJson()
            ));
        } catch (Throwable e) {
            LOGGER.error("Failed to sync item configuration to player {}", player.getGameProfile().getName(), e);
        }
    }

    public static void syncServerConfigToAllPlayers() {
        try {
            CHANNEL.broadcast(new SyncBanConfigPacket(BanItemConfig.getNetworkJson()));
        } catch (Throwable e) {
            LOGGER.error("Failed to sync item configuration to all players", e);
        }
    }

    private static void syncItemPropertiesToAllPlayers() {
        try {
            CHANNEL.broadcast(new SyncItemPropertySnapshotPacket(
                    ItemPropertyConfig.pendingJson(), ItemPropertyConfig.activeJson()
            ));
        } catch (Throwable e) {
            LOGGER.error("Failed to sync item property overrides to players", e);
        }
    }

    public static class OpenBannedGuiPacket {
        public static void encode(NetworkBuffer buf, OpenBannedGuiPacket msg) {
        }

        public static OpenBannedGuiPacket decode(NetworkBuffer buf) {
            return new OpenBannedGuiPacket();
        }

        public void handleClient() {
            dev.xyat.itemcontrol.item.client.ItemClientProxy.openBannedGui();
        }
    }

    public static class OpenMergeGuiPacket {
        public static void encode(NetworkBuffer buf, OpenMergeGuiPacket msg) {
        }

        public static OpenMergeGuiPacket decode(NetworkBuffer buf) {
            return new OpenMergeGuiPacket();
        }

        public void handleClient() {
            dev.xyat.itemcontrol.item.client.ItemClientProxy.openMergeGui();
        }
    }

    public static final class OpenItemTagEditorPacket {
        public static void encode(NetworkBuffer buf, OpenItemTagEditorPacket msg) {
        }

        public static OpenItemTagEditorPacket decode(NetworkBuffer buf) {
            return new OpenItemTagEditorPacket();
        }

        public void handleClient() {
            dev.xyat.itemcontrol.item.client.ItemClientProxy.openItemTagEditor();
        }
    }

    public static final class OpenProtectionEditorPacket {
        private final List<String> rules;

        public OpenProtectionEditorPacket(List<String> rules) {
            this.rules = rules == null ? List.of() : new ArrayList<>(rules);
        }

        public static void encode(NetworkBuffer buf, OpenProtectionEditorPacket msg) {
            writeStringList(buf, msg.rules, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH);
        }

        public static OpenProtectionEditorPacket decode(NetworkBuffer buf) {
            return new OpenProtectionEditorPacket(readStringList(buf, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH));
        }

        public void handleClient() {
            dev.xyat.itemcontrol.item.client.ItemClientProxy.openProtectionEditor(new ArrayList<>(rules));
        }
    }

    public static final class OpenDamageTypeEditorPacket {
        private final List<String> entries;

        public OpenDamageTypeEditorPacket(List<String> entries) {
            this.entries = entries == null ? List.of() : new ArrayList<>(entries);
        }

        public static void encode(NetworkBuffer buf, OpenDamageTypeEditorPacket msg) {
            writeStringList(buf, msg.entries, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
        }

        public static OpenDamageTypeEditorPacket decode(NetworkBuffer buf) {
            return new OpenDamageTypeEditorPacket(readStringList(buf, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH));
        }

        public void handleClient() {
            dev.xyat.itemcontrol.item.client.ItemClientProxy.openDamageTypeEditor(new ArrayList<>(entries));
        }
    }

    public static final class OpenDirectEntityImmunityEditorPacket {
        private final List<String> entries;

        public OpenDirectEntityImmunityEditorPacket(List<String> entries) {
            this.entries = entries == null ? List.of() : new ArrayList<>(entries);
        }

        public static void encode(NetworkBuffer buf, OpenDirectEntityImmunityEditorPacket msg) {
            writeStringList(buf, msg.entries, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
        }

        public static OpenDirectEntityImmunityEditorPacket decode(NetworkBuffer buf) {
            return new OpenDirectEntityImmunityEditorPacket(readStringList(buf, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH));
        }

        public void handleClient() {
            dev.xyat.itemcontrol.item.client.ItemClientProxy.openDirectEntityImmunityEditor(new ArrayList<>(entries));
        }
    }

    public static class SaveBanConfigPacket {
        private final int editorType;
        private final String jsonData;

        public SaveBanConfigPacket(int editorType, String jsonData) {
            this.editorType = editorType;
            this.jsonData = jsonData;
        }

        public static void encode(NetworkBuffer buf, SaveBanConfigPacket msg) {
            buf.writeVarInt(msg.editorType);
            buf.writeByteArray(KineticCompression.compressUtf8(msg.jsonData, MAX_COMPRESSED_BYTES, MAX_DECOMPRESSED_BYTES), MAX_COMPRESSED_BYTES);
        }

        public static SaveBanConfigPacket decode(NetworkBuffer buf) {
            int editorType = buf.readVarInt();
            try {
                return new SaveBanConfigPacket(editorType, KineticCompression.decompressUtf8(buf.readByteArray(MAX_COMPRESSED_BYTES), MAX_DECOMPRESSED_BYTES));
            } catch (Throwable e) {
                LOGGER.error("Failed to decode item configuration save packet", e);
                return new SaveBanConfigPacket(editorType, "");
            }
        }

        public void handleServer(ServerPacketContext ctx) {
            ServerPlayer player = ctx.sender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                sendEditorSaveResult(player, editorType, false, List.of(), "msg.itemcontrol.item.editor.no_permission");
                return;
            }
            if (BanItemConfig.applyJson(jsonData, "server packet from " + player.getGameProfile().getName(), true)) {
                syncServerConfigToAllPlayers();
                sendEditorSaveResult(player, editorType, true, List.of(), "");
            } else {
                sendServerConfigToPlayer(player, false);
                sendEditorSaveResult(player, editorType, false, List.of(), "gui.kineticcore.config.save_failed");
            }
        
        }
    }

    public static final class SaveProtectionRulesPacket {
        private final List<String> rules;

        public SaveProtectionRulesPacket(List<String> rules) {
            this.rules = rules == null ? List.of() : new ArrayList<>(rules);
        }

        public static void encode(NetworkBuffer buf, SaveProtectionRulesPacket msg) {
            writeStringList(buf, msg.rules, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH);
        }

        public static SaveProtectionRulesPacket decode(NetworkBuffer buf) {
            return new SaveProtectionRulesPacket(readStringList(buf, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH));
        }

        public void handleServer(ServerPacketContext ctx) {
            ServerPlayer player = ctx.sender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                sendEditorSaveResult(
                        player,
                        EDITOR_PROTECTION_ITEM,
                        false,
                        ItemProtectionConfig.indestructibleItemsRaw,
                        "msg.itemcontrol.item.editor.no_permission"
                );
                return;
            }
            if (!ItemProtectionConfig.areValidProtectionRules(rules)) {
                sendEditorSaveResult(
                        player,
                        EDITOR_PROTECTION_ITEM,
                        false,
                        ItemProtectionConfig.indestructibleItemsRaw,
                        "msg.itemcontrol.item.protection_editor.save_invalid"
                );
                return;
            }

            List<String> previous = new ArrayList<>(ItemProtectionConfig.indestructibleItemsRaw);
            try {
                ItemProtectionConfig.setProtectionRules(rules);
                ItemProtectionConfig.save();
                sendEditorSaveResult(
                        player,
                        EDITOR_PROTECTION_ITEM,
                        true,
                        ItemProtectionConfig.indestructibleItemsRaw,
                        "msg.itemcontrol.item.protection_editor.save_success"
                );
            } catch (Throwable e) {
                ItemProtectionConfig.indestructibleItemsRaw = previous;
                ItemProtectionConfig.load();
                LOGGER.error("Failed to save item protection rules", e);
                sendEditorSaveResult(
                        player,
                        EDITOR_PROTECTION_ITEM,
                        false,
                        ItemProtectionConfig.indestructibleItemsRaw,
                        "msg.itemcontrol.item.protection_editor.save_failed"
                );
            }
        
        }
    }

    public static final class SaveDamageSourcesPacket {
        private final List<String> entries;

        public SaveDamageSourcesPacket(List<String> entries) {
            this.entries = entries == null ? List.of() : new ArrayList<>(entries);
        }

        public static void encode(NetworkBuffer buf, SaveDamageSourcesPacket msg) {
            writeStringList(buf, msg.entries, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
        }

        public static SaveDamageSourcesPacket decode(NetworkBuffer buf) {
            return new SaveDamageSourcesPacket(readStringList(buf, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH));
        }

        public void handleServer(ServerPacketContext ctx) {
            ServerPlayer player = ctx.sender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                sendEditorSaveResult(
                        player,
                        EDITOR_DAMAGE_IMMUNITY,
                        false,
                        ItemProtectionConfig.globalItemDamageImmunityRaw,
                        "msg.itemcontrol.item.editor.no_permission"
                );
                return;
            }
            if (!ItemProtectionConfig.areValidResourceEntries(entries)) {
                sendEditorSaveResult(
                        player,
                        EDITOR_DAMAGE_IMMUNITY,
                        false,
                        ItemProtectionConfig.globalItemDamageImmunityRaw,
                        "msg.itemcontrol.item.damage_type_editor.save_invalid"
                );
                return;
            }

            List<String> previous = new ArrayList<>(ItemProtectionConfig.globalItemDamageImmunityRaw);
            try {
                ItemProtectionConfig.setDamageSources(entries);
                ItemProtectionConfig.save();
                sendEditorSaveResult(
                        player,
                        EDITOR_DAMAGE_IMMUNITY,
                        true,
                        ItemProtectionConfig.globalItemDamageImmunityRaw,
                        "msg.itemcontrol.item.damage_type_editor.save_success"
                );
            } catch (Throwable e) {
                ItemProtectionConfig.globalItemDamageImmunityRaw = previous;
                ItemProtectionConfig.load();
                LOGGER.error("Failed to save global item damage immunity sources", e);
                sendEditorSaveResult(
                        player,
                        EDITOR_DAMAGE_IMMUNITY,
                        false,
                        ItemProtectionConfig.globalItemDamageImmunityRaw,
                        "msg.itemcontrol.item.damage_type_editor.save_failed"
                );
            }
        
        }
    }

    public static final class SaveDirectEntitySourcesPacket {
        private final List<String> entries;

        public SaveDirectEntitySourcesPacket(List<String> entries) {
            this.entries = entries == null ? List.of() : new ArrayList<>(entries);
        }

        public static void encode(NetworkBuffer buf, SaveDirectEntitySourcesPacket msg) {
            writeStringList(buf, msg.entries, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
        }

        public static SaveDirectEntitySourcesPacket decode(NetworkBuffer buf) {
            return new SaveDirectEntitySourcesPacket(readStringList(buf, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH));
        }

        public void handleServer(ServerPacketContext ctx) {
            ServerPlayer player = ctx.sender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                sendEditorSaveResult(
                        player,
                        EDITOR_DIRECT_ENTITY_IMMUNITY,
                        false,
                        ItemProtectionConfig.globalDirectEntityImmunityRaw,
                        "msg.itemcontrol.item.editor.no_permission"
                );
                return;
            }
            if (!ItemProtectionConfig.areValidResourceEntries(entries)) {
                sendEditorSaveResult(
                        player,
                        EDITOR_DIRECT_ENTITY_IMMUNITY,
                        false,
                        ItemProtectionConfig.globalDirectEntityImmunityRaw,
                        "msg.itemcontrol.item.direct_entity_editor.save_invalid"
                );
                return;
            }

            List<String> previous = new ArrayList<>(ItemProtectionConfig.globalDirectEntityImmunityRaw);
            try {
                ItemProtectionConfig.setDirectEntitySources(entries);
                ItemProtectionConfig.save();
                sendEditorSaveResult(
                        player,
                        EDITOR_DIRECT_ENTITY_IMMUNITY,
                        true,
                        ItemProtectionConfig.globalDirectEntityImmunityRaw,
                        "msg.itemcontrol.item.direct_entity_editor.save_success"
                );
            } catch (Throwable e) {
                ItemProtectionConfig.globalDirectEntityImmunityRaw = previous;
                ItemProtectionConfig.load();
                LOGGER.error("Failed to save global direct entity immunity sources", e);
                sendEditorSaveResult(
                        player,
                        EDITOR_DIRECT_ENTITY_IMMUNITY,
                        false,
                        ItemProtectionConfig.globalDirectEntityImmunityRaw,
                        "msg.itemcontrol.item.direct_entity_editor.save_failed"
                );
            }
        
        }
    }

    private static void sendEditorSaveResult(
            ServerPlayer player,
            int editorType,
            boolean success,
            List<String> entries,
            String messageKey
    ) {
        CHANNEL.sendToPlayer(player, new EditorSaveResultPacket(editorType, success, messageKey, entries));
    }

    public static final class EditorSaveResultPacket {
        private final int editorType;
        private final boolean success;
        private final String messageKey;
        private final List<String> entries;

        public EditorSaveResultPacket(int editorType, boolean success, String messageKey, List<String> entries) {
            this.editorType = editorType;
            this.success = success;
            this.messageKey = messageKey == null ? "" : messageKey;
            this.entries = entries == null ? List.of() : new ArrayList<>(entries);
        }

        public static void encode(NetworkBuffer buf, EditorSaveResultPacket msg) {
            buf.writeVarInt(msg.editorType);
            buf.writeBoolean(msg.success);
            buf.writeUtf(msg.messageKey, 256);
            if (msg.editorType == EDITOR_PROTECTION_ITEM) {
                writeStringList(buf, msg.entries, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH);
            } else {
                writeStringList(buf, msg.entries, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
            }
        }

        public static EditorSaveResultPacket decode(NetworkBuffer buf) {
            int editorType = buf.readVarInt();
            boolean success = buf.readBoolean();
            String messageKey = buf.readUtf(256);
            List<String> entries = editorType == EDITOR_PROTECTION_ITEM
                    ? readStringList(buf, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH)
                    : readStringList(buf, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
            return new EditorSaveResultPacket(editorType, success, messageKey, entries);
        }

        public void handleClient() {
            dev.xyat.itemcontrol.item.client.ItemClientProxy.applyEditorSaveResult(
                    editorType, success, new ArrayList<>(entries), messageKey
            );
        }
    }

    public static class SyncBanConfigPacket {
        private final String jsonData;

        public SyncBanConfigPacket(String jsonData) {
            this.jsonData = jsonData;
        }

        public static void encode(NetworkBuffer buf, SyncBanConfigPacket msg) {
            buf.writeByteArray(KineticCompression.compressUtf8(msg.jsonData, MAX_COMPRESSED_BYTES, MAX_DECOMPRESSED_BYTES), MAX_COMPRESSED_BYTES);
        }

        public static SyncBanConfigPacket decode(NetworkBuffer buf) {
            try {
                return new SyncBanConfigPacket(KineticCompression.decompressUtf8(buf.readByteArray(MAX_COMPRESSED_BYTES), MAX_DECOMPRESSED_BYTES));
            } catch (Throwable e) {
                LOGGER.error("Failed to decode item configuration sync packet", e);
                return new SyncBanConfigPacket("");
            }
        }

        public void handleClient() {
            dev.xyat.itemcontrol.item.client.ItemClientProxy.handleSyncBanConfig(jsonData);
        }
    }

    public static final class OpenItemPropertyEditorPacket {
        private final String pendingJson;
        private final String activeJson;

        public OpenItemPropertyEditorPacket(String pendingJson, String activeJson) {
            this.pendingJson = pendingJson == null ? "{}" : pendingJson;
            this.activeJson = activeJson == null ? "{}" : activeJson;
        }

        public static void encode(NetworkBuffer buf, OpenItemPropertyEditorPacket packet) {
            writeCompressedJson(buf, packet.pendingJson);
            writeCompressedJson(buf, packet.activeJson);
        }

        public static OpenItemPropertyEditorPacket decode(NetworkBuffer buf) {
            try {
                return new OpenItemPropertyEditorPacket(readCompressedJson(buf), readCompressedJson(buf));
            } catch (Throwable e) {
                LOGGER.error("Failed to decode item property editor snapshot", e);
                return new OpenItemPropertyEditorPacket("{}", "{}");
            }
        }

        public void handleClient() {
            dev.xyat.itemcontrol.item.client.ItemClientProxy.openItemPropertyEditor(pendingJson, activeJson);
        }
    }

    public static final class SyncItemPropertySnapshotPacket {
        private final String pendingJson;
        private final String activeJson;

        public SyncItemPropertySnapshotPacket(String pendingJson, String activeJson) {
            this.pendingJson = pendingJson == null ? "{}" : pendingJson;
            this.activeJson = activeJson == null ? "{}" : activeJson;
        }

        public static void encode(NetworkBuffer buf, SyncItemPropertySnapshotPacket packet) {
            writeCompressedJson(buf, packet.pendingJson);
            writeCompressedJson(buf, packet.activeJson);
        }

        public static SyncItemPropertySnapshotPacket decode(NetworkBuffer buf) {
            try {
                return new SyncItemPropertySnapshotPacket(readCompressedJson(buf), readCompressedJson(buf));
            } catch (Throwable e) {
                LOGGER.error("Failed to decode item property synchronization", e);
                return new SyncItemPropertySnapshotPacket("{}", "{}");
            }
        }

        public void handleClient() {
            ItemPropertyConfig.applyServerSnapshots(pendingJson, activeJson);
        }
    }

    public static final class SaveItemPropertyConfigPacket {
        private final String json;

        public SaveItemPropertyConfigPacket(String json) {
            this.json = json == null ? "{}" : json;
        }

        public static void encode(NetworkBuffer buf, SaveItemPropertyConfigPacket packet) {
            writeCompressedJson(buf, packet.json);
        }

        public static SaveItemPropertyConfigPacket decode(NetworkBuffer buf) {
            try {
                return new SaveItemPropertyConfigPacket(readCompressedJson(buf));
            } catch (Throwable e) {
                LOGGER.error("Failed to decode item property save packet", e);
                return new SaveItemPropertyConfigPacket("");
            }
        }

        public void handleServer(ServerPacketContext ctx) {
            ServerPlayer player = ctx.sender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                CHANNEL.sendToPlayer(player, new ItemPropertySaveResultPacket(
                        false, ItemPropertyConfig.pendingJson(), "msg.itemcontrol.item.editor.no_permission"
                ));
                return;
            }
            ItemPropertyConfig.SaveResult result = ItemPropertyConfig.savePending(json);
            if (!result.success()) {
                CHANNEL.sendToPlayer(player, new ItemPropertySaveResultPacket(
                        false, ItemPropertyConfig.pendingJson(), result.messageKey()
                ));
                return;
            }
            syncItemPropertiesToAllPlayers();
            CHANNEL.sendToPlayer(player, new ItemPropertySaveResultPacket(
                    true, ItemPropertyConfig.pendingJson(), "msg.itemcontrol.item_property.saved_restart"
            ));
        }
    }

    public static final class ItemPropertySaveResultPacket {
        private final boolean success;
        private final String pendingJson;
        private final String messageKey;

        public ItemPropertySaveResultPacket(boolean success, String pendingJson, String messageKey) {
            this.success = success;
            this.pendingJson = pendingJson == null ? "{}" : pendingJson;
            this.messageKey = messageKey == null ? "" : messageKey;
        }

        public static void encode(NetworkBuffer buf, ItemPropertySaveResultPacket packet) {
            buf.writeBoolean(packet.success);
            buf.writeUtf(packet.messageKey, 256);
            writeCompressedJson(buf, packet.pendingJson);
        }

        public static ItemPropertySaveResultPacket decode(NetworkBuffer buf) {
            boolean success = buf.readBoolean();
            String message = buf.readUtf(256);
            try {
                return new ItemPropertySaveResultPacket(success, readCompressedJson(buf), message);
            } catch (Throwable e) {
                LOGGER.error("Failed to decode item property save result", e);
                return new ItemPropertySaveResultPacket(false, "{}", "gui.kineticcore.config.save_failed");
            }
        }

        public void handleClient() {
            dev.xyat.itemcontrol.item.client.ItemClientProxy.applyItemPropertySaveResult(
                    success, pendingJson, messageKey
            );
        }
    }

    private static void writeCompressedJson(NetworkBuffer buf, String json) {
        buf.writeByteArray(
                KineticCompression.compressUtf8(json, MAX_COMPRESSED_BYTES, MAX_DECOMPRESSED_BYTES),
                MAX_COMPRESSED_BYTES
        );
    }

    private static String readCompressedJson(NetworkBuffer buf) {
        return KineticCompression.decompressUtf8(buf.readByteArray(MAX_COMPRESSED_BYTES), MAX_DECOMPRESSED_BYTES);
    }
}
