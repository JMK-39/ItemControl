package dev.xyat.itemcontrol.cleaner.Network;

import dev.xyat.kineticcore.api.KTNetworkProtocol;
import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.CleanerSavedData;
import dev.xyat.itemcontrol.cleaner.area.CleanerArea;
import dev.xyat.itemcontrol.cleaner.area.CleanerAreaManager;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.itemcontrol.cleaner.event.AutoCleanerEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class CleanerNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;
    private static int id() { return packetId++; }

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CleanerModule.MODID, "cleaner"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION).clientAcceptedVersions(KTNetworkProtocol::acceptsAnyVersion).serverAcceptedVersions(KTNetworkProtocol::acceptsAnyVersion).simpleChannel();

    public static void register() {
        CHANNEL.messageBuilder(CleanerRequest.class, id(), NetworkDirection.PLAY_TO_SERVER).decoder(CleanerRequest::new).encoder(CleanerRequest::toBytes).consumerMainThread(CleanerRequest::handle).add();
        CHANNEL.messageBuilder(SyncTrashBin.class, id(), NetworkDirection.PLAY_TO_SERVER).decoder(SyncTrashBin::new).encoder(SyncTrashBin::toBytes).consumerMainThread(SyncTrashBin::handle).add();
        CHANNEL.messageBuilder(SyncTrashBinCounts.class, id(), NetworkDirection.PLAY_TO_CLIENT).decoder(SyncTrashBinCounts::new).encoder(SyncTrashBinCounts::toBytes).consumerMainThread(SyncTrashBinCounts::handle).add();
        CHANNEL.messageBuilder(OpenTrashBinRequest.class, id(), NetworkDirection.PLAY_TO_SERVER).decoder(OpenTrashBinRequest::new).encoder(OpenTrashBinRequest::toBytes).consumerMainThread(OpenTrashBinRequest::handle).add();
        CHANNEL.messageBuilder(AreaToolAction.class, id(), NetworkDirection.PLAY_TO_SERVER).decoder(AreaToolAction::new).encoder(AreaToolAction::toBytes).consumerMainThread(AreaToolAction::handle).add();
        CHANNEL.messageBuilder(SyncCleanerAreas.class, id(), NetworkDirection.PLAY_TO_CLIENT).decoder(SyncCleanerAreas::new).encoder(SyncCleanerAreas::toBytes).consumerMainThread(SyncCleanerAreas::handle).add();
        CHANNEL.messageBuilder(NotifyToast.class, id(), NetworkDirection.PLAY_TO_CLIENT).decoder(NotifyToast::new).encoder(NotifyToast::toBytes).consumerMainThread(NotifyToast::handle).add();
        CHANNEL.messageBuilder(ClearAreaSelection.class, id(), NetworkDirection.PLAY_TO_CLIENT).decoder(ClearAreaSelection::new).encoder(ClearAreaSelection::toBytes).consumerMainThread(ClearAreaSelection::handle).add();
    }

    public static class CleanerRequest {
        public CleanerRequest() {} public CleanerRequest(FriendlyByteBuf buf) {} public void toBytes(FriendlyByteBuf buf) {}
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> { ServerPlayer player = ctx.get().getSender(); if (player != null) AutoCleanerEventHandler.triggerManualClean(player); });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class OpenTrashBinRequest {
        public OpenTrashBinRequest() {} public OpenTrashBinRequest(FriendlyByteBuf buf) {} public void toBytes(FriendlyByteBuf buf) {}
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
                        @Override public @NotNull Component getDisplayName() { return net.minecraft.network.chat.Component.translatable("gui.itemcontrol.cleaner.cleaner.title"); }
                        @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.@NotNull Inventory inv, net.minecraft.world.entity.player.@NotNull Player p) {
                            net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) p.level();
                            CleanerSavedData data = CleanerSavedData.get(sl);
                            net.minecraft.world.Container realData = null;
                            try { realData = data.getRecord(0); } catch (Exception ignored) {}
                            if (realData == null) { realData = new CleanerSavedData.BigTrashContainer(CleanerConfig.getTrashBinSlots()); }
                            return new dev.xyat.itemcontrol.cleaner.client.gui.CleanerMenu(id, inv, realData);
                        }
                    });
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SyncTrashBin(int rowOffset, int historyIndex) {
        public SyncTrashBin(FriendlyByteBuf buf) {
            this(buf.readInt(), buf.readInt());
        }
        public void toBytes(FriendlyByteBuf buf) { buf.writeInt(this.rowOffset); buf.writeInt(this.historyIndex); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.containerMenu instanceof dev.xyat.itemcontrol.cleaner.client.gui.CleanerMenu cleanerMenu) {
                    cleanerMenu.updateState(this.historyIndex, this.rowOffset);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SyncTrashBinCounts(int containerId, int[] counts) {
        public SyncTrashBinCounts(FriendlyByteBuf buf) {
            this(buf.readInt(), buf.readVarIntArray());
        }
        public void toBytes(FriendlyByteBuf buf) { buf.writeInt(this.containerId); buf.writeVarIntArray(this.counts); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CleanerNetworkClient.handleSyncCounts(this)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record AreaToolAction(int action, BlockPos pos) {
        public static final int SELECT_START = 1;
        public static final int SELECT_END = 2;
        public static final int CONFIRM = 3;
        public static final int CLEAR_TARGET = 4;
        public static final int CLEAR_SELF = 5;
        public static final int CLEAR_ALL = 6;

        public AreaToolAction(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readBoolean() ? buf.readBlockPos() : null);
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeVarInt(action);
            buf.writeBoolean(pos != null);
            if (pos != null) {
                buf.writeBlockPos(pos);
            }
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    CleanerAreaManager.handleToolAction(player, action, pos);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SyncCleanerAreas(List<CleanerArea> areas) {
        public SyncCleanerAreas {
            areas = new ArrayList<>(areas); // 保持防御性复制
        }

        public SyncCleanerAreas(FriendlyByteBuf buf) {
            this(readAreasFromBuf(buf));
        }

        private static List<CleanerArea> readAreasFromBuf(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<CleanerArea> parsedAreas = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                UUID ownerId = buf.readUUID();
                String ownerName = buf.readUtf(64);
                String dimension = buf.readUtf(128);
                int minX = buf.readInt();
                int minY = buf.readInt();
                int minZ = buf.readInt();
                int maxX = buf.readInt();
                int maxY = buf.readInt();
                int maxZ = buf.readInt();
                parsedAreas.add(new CleanerArea(ownerId, ownerName, dimension, minX, minY, minZ, maxX, maxY, maxZ));
            }
            return parsedAreas;
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeVarInt(areas.size());
            for (CleanerArea area : areas) {
                buf.writeUUID(area.ownerId());
                buf.writeUtf(area.ownerName(), 64);
                buf.writeUtf(area.dimension(), 128);
                buf.writeInt(area.minX());
                buf.writeInt(area.minY());
                buf.writeInt(area.minZ());
                buf.writeInt(area.maxX());
                buf.writeInt(area.maxY());
                buf.writeInt(area.maxZ());
            }
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CleanerNetworkClient.handleSyncAreas(this)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record NotifyToast(Component message) {
        public NotifyToast(FriendlyByteBuf buf) {
            this(buf.readComponent());
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeComponent(message);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CleanerNetworkClient.handleNotifyToast(this)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ClearAreaSelection {
        public ClearAreaSelection() {}

        public ClearAreaSelection(FriendlyByteBuf buf) {}

        public void toBytes(FriendlyByteBuf buf) {}

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> CleanerNetworkClient::handleClearAreaSelection));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void sendToServer(Object msg) { CHANNEL.send(PacketDistributor.SERVER.noArg(), msg); }
    public static void sendToPlayer(Object msg, ServerPlayer player) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg); }
}
