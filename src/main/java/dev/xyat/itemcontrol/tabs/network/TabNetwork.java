package dev.xyat.itemcontrol.tabs.network;

import dev.xyat.itemcontrol.tabs.TabConfig;
import dev.xyat.itemcontrol.tabs.TabsModule;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.network.KineticCompression;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.server.level.ServerPlayer;

public final class TabNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final int MAX_COMPRESSED_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_BYTES = 8 * 1024 * 1024;
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(TabsModule.MODID, "tabs_sync"),
            PROTOCOL_VERSION,
            NetworkVersionPolicy.ANY
    );
    private static boolean registered;

    private TabNetwork() {
    }

    public static void register() {
        if (registered) return;
        registered = true;

        CHANNEL.registerClientbound(0, SyncTabPacket.class,
                NetworkCodec.of(
                        (buffer, packet) -> buffer.writeByteArray(
                                KineticCompression.compressUtf8(packet.json(), MAX_COMPRESSED_BYTES, MAX_DECOMPRESSED_BYTES),
                                MAX_COMPRESSED_BYTES
                        ),
                        buffer -> new SyncTabPacket(KineticCompression.decompressUtf8(
                                buffer.readByteArray(MAX_COMPRESSED_BYTES), MAX_DECOMPRESSED_BYTES
                        ))
                ),
                TabNetwork::handleSync);

        CHANNEL.registerServerbound(1, SaveTabPacket.class,
                NetworkCodec.of(
                        (buffer, packet) -> buffer.writeByteArray(
                                KineticCompression.compressUtf8(packet.json(), MAX_COMPRESSED_BYTES, MAX_DECOMPRESSED_BYTES),
                                MAX_COMPRESSED_BYTES
                        ),
                        buffer -> new SaveTabPacket(KineticCompression.decompressUtf8(
                                buffer.readByteArray(MAX_COMPRESSED_BYTES), MAX_DECOMPRESSED_BYTES
                        ))
                ),
                TabNetwork::handleSave);

        CHANNEL.registerClientbound(2, NotifyPacket.class,
                NetworkCodec.of(
                        (buffer, packet) -> buffer.writeUtf(packet.langKey(), 1024),
                        buffer -> new NotifyPacket(buffer.readUtf(1024))
                ),
                TabNetwork::handleNotify);

        CHANNEL.registerServerbound(3, RequestNotifyPacket.class,
                NetworkCodec.of(
                        (buffer, packet) -> buffer.writeUtf(packet.langKey(), 1024),
                        buffer -> new RequestNotifyPacket(buffer.readUtf(1024))
                ),
                TabNetwork::handleRequestNotify);

        CHANNEL.registerClientbound(4, ClearNotifyPacket.class,
                NetworkCodec.of((buffer, packet) -> { }, buffer -> new ClearNotifyPacket()),
                packet -> KineticOverlays.clearToasts());

        CHANNEL.registerClientbound(5, OpenTabEditorPacket.class,
                NetworkCodec.of(
                        (buffer, packet) -> buffer.writeByteArray(
                                KineticCompression.compressUtf8(packet.json(), MAX_COMPRESSED_BYTES, MAX_DECOMPRESSED_BYTES),
                                MAX_COMPRESSED_BYTES
                        ),
                        buffer -> new OpenTabEditorPacket(KineticCompression.decompressUtf8(
                                buffer.readByteArray(MAX_COMPRESSED_BYTES), MAX_DECOMPRESSED_BYTES
                        ))
                ),
                message -> TabNetworkClient.handleOpenEditor(message));

        CHANNEL.registerServerbound(6, RequestOpenEditorPacket.class,
                NetworkCodec.of((buffer, packet) -> { }, buffer -> new RequestOpenEditorPacket()),
                TabNetwork::handleRequestOpenEditor);
    }

    public static void requestOpenEditor() {
        CHANNEL.sendToServer(new RequestOpenEditorPacket());
    }

    public static void saveTabs(String json) {
        CHANNEL.sendToServer(new SaveTabPacket(json));
    }

    public static void requestNotification(String langKey) {
        CHANNEL.sendToServer(new RequestNotifyPacket(langKey));
    }

    public static boolean sendEditorSnapshot(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return false;
        if (!TabConfig.loadForEditor()) {
            CHANNEL.sendToPlayer(player, new NotifyPacket("gui.itemcontrol.tabs.tabs.notify.load_failed"));
            return false;
        }
        String json = TabConfig.GSON.toJson(TabConfig.data);
        CHANNEL.sendToPlayer(player, new OpenTabEditorPacket(json));
        return true;
    }

    private static void handleSync(SyncTabPacket packet) {
        try {
            TabConfig.Data next = TabConfig.GSON.fromJson(packet.json(), TabConfig.Data.class);
            if (!TabConfig.isValidForServer(next)) return;
            TabConfig.data = next;
            TabNetworkClient.handleSync();
        } catch (RuntimeException exception) {
            TabsModule.LOGGER.error("Rejected invalid tabs sync payload", exception);
        }
    }

    private static void handleSave(SaveTabPacket packet, ServerPacketContext context) {
        ServerPlayer sender = context.sender();
        if (!sender.hasPermissions(2)) {
            CHANNEL.sendToPlayer(sender, new NotifyPacket("gui.itemcontrol.tabs.tabs.notify.save_failed"));
            return;
        }

        boolean success = false;
        String savedJson = null;
        try {
            TabConfig.Data next = TabConfig.GSON.fromJson(packet.json(), TabConfig.Data.class);
            if (TabConfig.isValidForServer(next) && TabConfig.save(next)) {
                savedJson = TabConfig.GSON.toJson(TabConfig.data);
                success = true;
            }
        } catch (RuntimeException exception) {
            TabsModule.LOGGER.error("Rejected invalid tabs save payload", exception);
        }

        if (success) {
            CHANNEL.broadcast(new SyncTabPacket(savedJson));
            CHANNEL.sendToPlayer(sender, new NotifyPacket("gui.itemcontrol.tabs.tabs.notify.saved"));
        } else {
            CHANNEL.sendToPlayer(sender, new NotifyPacket("gui.itemcontrol.tabs.tabs.notify.save_failed"));
        }
    }

    private static void handleNotify(NotifyPacket packet) {
        if ("gui.itemcontrol.tabs.tabs.notify.saved".equals(packet.langKey())) {
            KTConfigApi.notifySaved(dev.xyat.itemcontrol.tabs.config.TabConfigGui.PAGE_ID);
        } else {
            KineticOverlays.toast(KineticI18n.translatable(packet.langKey()));
        }
    }

    private static void handleRequestNotify(RequestNotifyPacket packet, ServerPacketContext context) {
        CHANNEL.sendToPlayer(context.sender(), new NotifyPacket(packet.langKey()));
    }

    private static void handleRequestOpenEditor(RequestOpenEditorPacket packet, ServerPacketContext context) {
        ServerPlayer sender = context.sender();
        if (sender.hasPermissions(2)) {
            sendEditorSnapshot(sender);
        }
    }

    public record SyncTabPacket(String json) {
    }

    public record SaveTabPacket(String json) {
    }

    public record NotifyPacket(String langKey) {
    }

    public record RequestNotifyPacket(String langKey) {
    }

    public record ClearNotifyPacket() {
    }

    public record OpenTabEditorPacket(String json) {
    }

    public record RequestOpenEditorPacket() {
    }
}
