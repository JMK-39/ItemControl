package dev.xyat.itemcontrol.item.network;

import dev.xyat.kineticcore.api.KTNetworkProtocol;
import dev.xyat.kineticcore.api.NetworkCompressUtil;
import dev.xyat.itemcontrol.item.ItemModule;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.config.ItemProtectionConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = ItemModule.MODID)
public class ItemNetwork {
    private static final Logger LOGGER = LogManager.getLogger("itemcontrol/ItemNetwork");
    private static final String PROTOCOL_VERSION = "4";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ItemModule.MODID, "item_network"),
            () -> PROTOCOL_VERSION,
            KTNetworkProtocol::acceptsAnyVersion,
            KTNetworkProtocol::acceptsAnyVersion
    );

    public static final int EDITOR_BAN_ITEM = 0;
    public static final int EDITOR_MERGE_ITEM = 1;
    public static final int EDITOR_PROTECTION_ITEM = 2;
    public static final int EDITOR_DAMAGE_IMMUNITY = 3;
    public static final int EDITOR_DIRECT_ENTITY_IMMUNITY = 4;
    public static final int EDITOR_ITEM_TAG = 5;

    private static final int MAX_PROTECTION_RULES = 4096;
    private static final int MAX_PROTECTION_RULE_LENGTH = 32767;
    private static final int MAX_RESOURCE_ENTRIES = 4096;
    private static final int MAX_RESOURCE_ENTRY_LENGTH = 1024;

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenBannedGuiPacket.class, OpenBannedGuiPacket::encode, OpenBannedGuiPacket::decode, OpenBannedGuiPacket::handle);
        CHANNEL.registerMessage(id++, OpenMergeGuiPacket.class, OpenMergeGuiPacket::encode, OpenMergeGuiPacket::decode, OpenMergeGuiPacket::handle);
        CHANNEL.registerMessage(id++, OpenProtectionEditorPacket.class, OpenProtectionEditorPacket::encode, OpenProtectionEditorPacket::decode, OpenProtectionEditorPacket::handle);
        CHANNEL.registerMessage(id++, OpenDamageTypeEditorPacket.class, OpenDamageTypeEditorPacket::encode, OpenDamageTypeEditorPacket::decode, OpenDamageTypeEditorPacket::handle);
        CHANNEL.registerMessage(id++, OpenDirectEntityImmunityEditorPacket.class, OpenDirectEntityImmunityEditorPacket::encode, OpenDirectEntityImmunityEditorPacket::decode, OpenDirectEntityImmunityEditorPacket::handle);
        CHANNEL.registerMessage(id++, OpenItemTagEditorPacket.class, OpenItemTagEditorPacket::encode, OpenItemTagEditorPacket::decode, OpenItemTagEditorPacket::handle);
        CHANNEL.registerMessage(id++, SaveBanConfigPacket.class, SaveBanConfigPacket::encode, SaveBanConfigPacket::decode, SaveBanConfigPacket::handle);
        CHANNEL.registerMessage(id++, SyncBanConfigPacket.class, SyncBanConfigPacket::encode, SyncBanConfigPacket::decode, SyncBanConfigPacket::handle);
        CHANNEL.registerMessage(id++, RequestOpenEditorPacket.class, RequestOpenEditorPacket::encode, RequestOpenEditorPacket::decode, RequestOpenEditorPacket::handle);
        CHANNEL.registerMessage(id++, SaveProtectionRulesPacket.class, SaveProtectionRulesPacket::encode, SaveProtectionRulesPacket::decode, SaveProtectionRulesPacket::handle);
        CHANNEL.registerMessage(id++, SaveDamageSourcesPacket.class, SaveDamageSourcesPacket::encode, SaveDamageSourcesPacket::decode, SaveDamageSourcesPacket::handle);
        CHANNEL.registerMessage(id++, SaveDirectEntitySourcesPacket.class, SaveDirectEntitySourcesPacket::encode, SaveDirectEntitySourcesPacket::decode, SaveDirectEntitySourcesPacket::handle);
        CHANNEL.registerMessage(id, EditorSaveResultPacket.class, EditorSaveResultPacket::encode, EditorSaveResultPacket::decode, EditorSaveResultPacket::handle);
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

    private static void writeStringList(FriendlyByteBuf buf, List<String> values, int maxCount, int maxLength) {
        if (values.size() > maxCount) throw new IllegalArgumentException("Too many list entries");
        buf.writeVarInt(values.size());
        for (String value : values) {
            String safeValue = value == null ? "" : value;
            if (safeValue.length() > maxLength) throw new IllegalArgumentException("List entry is too long");
            buf.writeUtf(safeValue, maxLength);
        }
    }

    private static List<String> readStringList(FriendlyByteBuf buf, int maxCount, int maxLength) {
        int size = buf.readVarInt();
        if (size < 0 || size > maxCount) throw new IllegalArgumentException("Invalid list entry count");
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(buf.readUtf(maxLength));
        return values;
    }

    public record RequestOpenEditorPacket(int editorType) {
        public static void encode(RequestOpenEditorPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.editorType);
        }

        public static RequestOpenEditorPacket decode(FriendlyByteBuf buf) {
            return new RequestOpenEditorPacket(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                if (editorType == EDITOR_BAN_ITEM) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenBannedGuiPacket());
                } else if (editorType == EDITOR_MERGE_ITEM) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenMergeGuiPacket());
                } else if (editorType == EDITOR_PROTECTION_ITEM) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenProtectionEditorPacket(ItemProtectionConfig.indestructibleItemsRaw));
                } else if (editorType == EDITOR_DAMAGE_IMMUNITY) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenDamageTypeEditorPacket(ItemProtectionConfig.globalItemDamageImmunityRaw));
                } else if (editorType == EDITOR_DIRECT_ENTITY_IMMUNITY) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenDirectEntityImmunityEditorPacket(ItemProtectionConfig.globalDirectEntityImmunityRaw));
                } else if (editorType == EDITOR_ITEM_TAG) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenItemTagEditorPacket());
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendServerConfigToPlayer(player, true);
        }
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null) {
            sendServerConfigToPlayer(player, true);
            return;
        }
        BanItemConfig.load();
        String json = BanItemConfig.getNetworkJson();
        for (ServerPlayer target : event.getPlayerList().getPlayers()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new SyncBanConfigPacket(json));
        }
    }

    public static void sendServerConfigToPlayer(ServerPlayer player, boolean reloadFromFile) {
        if (player == null) return;
        try {
            if (reloadFromFile) BanItemConfig.load();
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncBanConfigPacket(BanItemConfig.getNetworkJson()));
        } catch (Throwable e) {
            LOGGER.error("Failed to sync item configuration to player {}", player.getGameProfile().getName(), e);
        }
    }

    public static void syncServerConfigToAllPlayers() {
        try {
            CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncBanConfigPacket(BanItemConfig.getNetworkJson()));
        } catch (Throwable e) {
            LOGGER.error("Failed to sync item configuration to all players", e);
        }
    }

    public static class OpenBannedGuiPacket {
        public static void encode(OpenBannedGuiPacket msg, FriendlyByteBuf buf) {
        }

        public static OpenBannedGuiPacket decode(FriendlyByteBuf buf) {
            return new OpenBannedGuiPacket();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> dev.xyat.itemcontrol.item.client.ItemClientProxy::openBannedGui));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class OpenMergeGuiPacket {
        public static void encode(OpenMergeGuiPacket msg, FriendlyByteBuf buf) {
        }

        public static OpenMergeGuiPacket decode(FriendlyByteBuf buf) {
            return new OpenMergeGuiPacket();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> dev.xyat.itemcontrol.item.client.ItemClientProxy::openMergeGui));
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class OpenItemTagEditorPacket {
        public static void encode(OpenItemTagEditorPacket msg, FriendlyByteBuf buf) {
        }

        public static OpenItemTagEditorPacket decode(FriendlyByteBuf buf) {
            return new OpenItemTagEditorPacket();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> dev.xyat.itemcontrol.item.client.ItemClientProxy::openItemTagEditor));
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class OpenProtectionEditorPacket {
        private final List<String> rules;

        public OpenProtectionEditorPacket(List<String> rules) {
            this.rules = rules == null ? List.of() : new ArrayList<>(rules);
        }

        public static void encode(OpenProtectionEditorPacket msg, FriendlyByteBuf buf) {
            writeStringList(buf, msg.rules, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH);
        }

        public static OpenProtectionEditorPacket decode(FriendlyByteBuf buf) {
            return new OpenProtectionEditorPacket(readStringList(buf, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            List<String> copy = new ArrayList<>(rules);
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.xyat.itemcontrol.item.client.ItemClientProxy.openProtectionEditor(copy)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class OpenDamageTypeEditorPacket {
        private final List<String> entries;

        public OpenDamageTypeEditorPacket(List<String> entries) {
            this.entries = entries == null ? List.of() : new ArrayList<>(entries);
        }

        public static void encode(OpenDamageTypeEditorPacket msg, FriendlyByteBuf buf) {
            writeStringList(buf, msg.entries, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
        }

        public static OpenDamageTypeEditorPacket decode(FriendlyByteBuf buf) {
            return new OpenDamageTypeEditorPacket(readStringList(buf, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            List<String> copy = new ArrayList<>(entries);
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.xyat.itemcontrol.item.client.ItemClientProxy.openDamageTypeEditor(copy)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class OpenDirectEntityImmunityEditorPacket {
        private final List<String> entries;

        public OpenDirectEntityImmunityEditorPacket(List<String> entries) {
            this.entries = entries == null ? List.of() : new ArrayList<>(entries);
        }

        public static void encode(OpenDirectEntityImmunityEditorPacket msg, FriendlyByteBuf buf) {
            writeStringList(buf, msg.entries, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
        }

        public static OpenDirectEntityImmunityEditorPacket decode(FriendlyByteBuf buf) {
            return new OpenDirectEntityImmunityEditorPacket(readStringList(buf, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            List<String> copy = new ArrayList<>(entries);
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.xyat.itemcontrol.item.client.ItemClientProxy.openDirectEntityImmunityEditor(copy)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SaveBanConfigPacket {
        private final int editorType;
        private final String jsonData;

        public SaveBanConfigPacket(int editorType, String jsonData) {
            this.editorType = editorType;
            this.jsonData = jsonData;
        }

        public static void encode(SaveBanConfigPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.editorType);
            buf.writeByteArray(NetworkCompressUtil.compress(msg.jsonData));
        }

        public static SaveBanConfigPacket decode(FriendlyByteBuf buf) {
            int editorType = buf.readVarInt();
            try {
                return new SaveBanConfigPacket(editorType, NetworkCompressUtil.decompress(buf.readByteArray()));
            } catch (Throwable e) {
                LOGGER.error("Failed to decode item configuration save packet", e);
                return new SaveBanConfigPacket(editorType, "");
            }
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
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
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class SaveProtectionRulesPacket {
        private final List<String> rules;

        public SaveProtectionRulesPacket(List<String> rules) {
            this.rules = rules == null ? List.of() : new ArrayList<>(rules);
        }

        public static void encode(SaveProtectionRulesPacket msg, FriendlyByteBuf buf) {
            writeStringList(buf, msg.rules, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH);
        }

        public static SaveProtectionRulesPacket decode(FriendlyByteBuf buf) {
            return new SaveProtectionRulesPacket(readStringList(buf, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
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
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class SaveDamageSourcesPacket {
        private final List<String> entries;

        public SaveDamageSourcesPacket(List<String> entries) {
            this.entries = entries == null ? List.of() : new ArrayList<>(entries);
        }

        public static void encode(SaveDamageSourcesPacket msg, FriendlyByteBuf buf) {
            writeStringList(buf, msg.entries, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
        }

        public static SaveDamageSourcesPacket decode(FriendlyByteBuf buf) {
            return new SaveDamageSourcesPacket(readStringList(buf, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
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
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class SaveDirectEntitySourcesPacket {
        private final List<String> entries;

        public SaveDirectEntitySourcesPacket(List<String> entries) {
            this.entries = entries == null ? List.of() : new ArrayList<>(entries);
        }

        public static void encode(SaveDirectEntitySourcesPacket msg, FriendlyByteBuf buf) {
            writeStringList(buf, msg.entries, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
        }

        public static SaveDirectEntitySourcesPacket decode(FriendlyByteBuf buf) {
            return new SaveDirectEntitySourcesPacket(readStringList(buf, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
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
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private static void sendEditorSaveResult(
            ServerPlayer player,
            int editorType,
            boolean success,
            List<String> entries,
            String messageKey
    ) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new EditorSaveResultPacket(editorType, success, messageKey, entries)
        );
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

        public static void encode(EditorSaveResultPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.editorType);
            buf.writeBoolean(msg.success);
            buf.writeUtf(msg.messageKey, 256);
            if (msg.editorType == EDITOR_PROTECTION_ITEM) {
                writeStringList(buf, msg.entries, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH);
            } else {
                writeStringList(buf, msg.entries, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
            }
        }

        public static EditorSaveResultPacket decode(FriendlyByteBuf buf) {
            int editorType = buf.readVarInt();
            boolean success = buf.readBoolean();
            String messageKey = buf.readUtf(256);
            List<String> entries = editorType == EDITOR_PROTECTION_ITEM
                    ? readStringList(buf, MAX_PROTECTION_RULES, MAX_PROTECTION_RULE_LENGTH)
                    : readStringList(buf, MAX_RESOURCE_ENTRIES, MAX_RESOURCE_ENTRY_LENGTH);
            return new EditorSaveResultPacket(editorType, success, messageKey, entries);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            List<String> copy = new ArrayList<>(entries);
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> dev.xyat.itemcontrol.item.client.ItemClientProxy.applyEditorSaveResult(
                            editorType,
                            success,
                            copy,
                            messageKey
                    )
            ));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SyncBanConfigPacket {
        private final String jsonData;

        public SyncBanConfigPacket(String jsonData) {
            this.jsonData = jsonData;
        }

        public static void encode(SyncBanConfigPacket msg, FriendlyByteBuf buf) {
            buf.writeByteArray(NetworkCompressUtil.compress(msg.jsonData));
        }

        public static SyncBanConfigPacket decode(FriendlyByteBuf buf) {
            try {
                return new SyncBanConfigPacket(NetworkCompressUtil.decompress(buf.readByteArray()));
            } catch (Throwable e) {
                LOGGER.error("Failed to decode item configuration sync packet", e);
                return new SyncBanConfigPacket("");
            }
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.xyat.itemcontrol.item.client.ItemClientProxy.handleSyncBanConfig(jsonData)));
            ctx.get().setPacketHandled(true);
        }
    }
}
