package dev.xyat.itemcontrol.cleaner.Network;

import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.CleanerCommand;
import dev.xyat.itemcontrol.cleaner.CleanerSavedData;
import dev.xyat.itemcontrol.cleaner.area.CleanerArea;
import dev.xyat.itemcontrol.cleaner.area.CleanerAreaManager;
import dev.xyat.itemcontrol.cleaner.client.gui.CleanerMenu;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.itemcontrol.cleaner.event.AutoCleanerEventHandler;
import dev.xyat.kineticcore.api.menu.KineticMenus;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CleanerNetwork {
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(CleanerModule.MODID, "cleaner"),
            "1",
            NetworkVersionPolicy.ANY
    );
    private static boolean registered;

    private CleanerNetwork() {
    }

    public static void register() {
        if (registered) return;
        registered = true;

        CHANNEL.registerServerbound(0, CleanerRequest.class,
                NetworkCodec.of((buffer, packet) -> { }, buffer -> new CleanerRequest()),
                CleanerNetwork::handleCleanerRequest);
        CHANNEL.registerServerbound(1, SyncTrashBin.class,
                NetworkCodec.of(
                        (buffer, packet) -> {
                            buffer.writeInt(packet.rowOffset());
                            buffer.writeInt(packet.historyIndex());
                        },
                        buffer -> new SyncTrashBin(buffer.readInt(), buffer.readInt())
                ),
                CleanerNetwork::handleSyncTrashBin);
        CHANNEL.registerClientbound(2, SyncTrashBinCounts.class,
                NetworkCodec.of(
                        (buffer, packet) -> {
                            buffer.writeInt(packet.containerId());
                            buffer.writeVarIntArray(packet.counts());
                        },
                        buffer -> new SyncTrashBinCounts(buffer.readInt(), buffer.readVarIntArray())
                ),
                message -> CleanerNetworkClient.handleSyncCounts(message));
        CHANNEL.registerServerbound(3, OpenTrashBinRequest.class,
                NetworkCodec.of((buffer, packet) -> { }, buffer -> new OpenTrashBinRequest()),
                CleanerNetwork::handleOpenTrashBin);
        CHANNEL.registerServerbound(4, AreaToolAction.class,
                NetworkCodec.of(CleanerNetwork::writeAreaToolAction, CleanerNetwork::readAreaToolAction),
                CleanerNetwork::handleAreaToolAction);
        CHANNEL.registerClientbound(5, SyncCleanerAreas.class,
                NetworkCodec.of(CleanerNetwork::writeCleanerAreas, CleanerNetwork::readCleanerAreas),
                message -> CleanerNetworkClient.handleSyncAreas(message));
        CHANNEL.registerClientbound(6, NotifyToast.class,
                NetworkCodec.of(
                        (buffer, packet) -> buffer.writeComponent(packet.message()),
                        buffer -> new NotifyToast(buffer.readComponent())
                ),
                message -> CleanerNetworkClient.handleNotifyToast(message));
        CHANNEL.registerClientbound(7, ClearAreaSelection.class,
                NetworkCodec.of((buffer, packet) -> { }, buffer -> new ClearAreaSelection()),
                packet -> CleanerNetworkClient.handleClearAreaSelection());
    }

    private static void handleCleanerRequest(CleanerRequest packet, ServerPacketContext context) {
        AutoCleanerEventHandler.triggerManualClean(context.sender());
    }

    private static void handleOpenTrashBin(OpenTrashBinRequest packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        CleanerCommand.openTrashBin(player.createCommandSourceStack(), 1);
    }

    private static void handleSyncTrashBin(SyncTrashBin packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (player.containerMenu instanceof CleanerMenu cleanerMenu) {
            cleanerMenu.updateState(packet.historyIndex(), packet.rowOffset());
        }
    }

    private static void writeAreaToolAction(NetworkBuffer buffer, AreaToolAction packet) {
        buffer.writeVarInt(packet.action());
        buffer.writeBoolean(packet.pos() != null);
        if (packet.pos() != null) buffer.writeBlockPos(packet.pos());
    }

    private static AreaToolAction readAreaToolAction(NetworkBuffer buffer) {
        return new AreaToolAction(buffer.readVarInt(), buffer.readBoolean() ? buffer.readBlockPos() : null);
    }

    private static void handleAreaToolAction(AreaToolAction packet, ServerPacketContext context) {
        CleanerAreaManager.handleToolAction(context.sender(), packet.action(), packet.pos());
    }

    private static void writeCleanerAreas(NetworkBuffer buffer, SyncCleanerAreas packet) {
        buffer.writeVarInt(packet.areas().size());
        for (CleanerArea area : packet.areas()) {
            buffer.writeUuid(area.ownerId());
            buffer.writeUtf(area.ownerName(), 64);
            buffer.writeUtf(area.dimension(), 128);
            buffer.writeInt(area.minX());
            buffer.writeInt(area.minY());
            buffer.writeInt(area.minZ());
            buffer.writeInt(area.maxX());
            buffer.writeInt(area.maxY());
            buffer.writeInt(area.maxZ());
        }
    }

    private static SyncCleanerAreas readCleanerAreas(NetworkBuffer buffer) {
        int size = buffer.readVarInt();
        List<CleanerArea> areas = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            UUID ownerId = buffer.readUuid();
            String ownerName = buffer.readUtf(64);
            String dimension = buffer.readUtf(128);
            int minX = buffer.readInt();
            int minY = buffer.readInt();
            int minZ = buffer.readInt();
            int maxX = buffer.readInt();
            int maxY = buffer.readInt();
            int maxZ = buffer.readInt();
            areas.add(new CleanerArea(ownerId, ownerName, dimension, minX, minY, minZ, maxX, maxY, maxZ));
        }
        return new SyncCleanerAreas(areas);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(Object message, ServerPlayer player) {
        CHANNEL.sendToPlayer(player, message);
    }

    public record CleanerRequest() {
    }

    public record OpenTrashBinRequest() {
    }

    public record SyncTrashBin(int rowOffset, int historyIndex) {
    }

    public record SyncTrashBinCounts(int containerId, int[] counts) {
    }

    public record AreaToolAction(int action, BlockPos pos) {
        public static final int SELECT_START = 1;
        public static final int SELECT_END = 2;
        public static final int CONFIRM = 3;
        public static final int CLEAR_TARGET = 4;
        public static final int CLEAR_SELF = 5;
        public static final int CLEAR_ALL = 6;
    }

    public record SyncCleanerAreas(List<CleanerArea> areas) {
        public SyncCleanerAreas {
            areas = areas == null ? List.of() : List.copyOf(areas);
        }
    }

    public record NotifyToast(Component message) {
    }

    public record ClearAreaSelection() {
    }
}
