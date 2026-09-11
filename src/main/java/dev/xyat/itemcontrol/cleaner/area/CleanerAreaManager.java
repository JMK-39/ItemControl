package dev.xyat.itemcontrol.cleaner.area;

import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CleanerAreaManager {
    private static final Map<UUID, BlockPos> START_POINTS = new HashMap<>();
    private static final Map<UUID, BlockPos> END_POINTS = new HashMap<>();
    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(CleanerAreaManager::onPlayerLogin);
    }

    public static void handleToolAction(ServerPlayer player, int action, BlockPos pos) {
        if (!CleanerConfig.enableProtectedAreas) {
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.disabled");
            return;
        }
        boolean holdingAreaTool = CleanerConfig.isProtectedAreaTool(player.getMainHandItem())
                || CleanerConfig.isProtectedAreaTool(player.getOffhandItem());
        if (!holdingAreaTool) {
            return;
        }
        if (action == CleanerNetwork.AreaToolAction.SELECT_START) {
            selectStart(player, pos);
            return;
        }
        if (action == CleanerNetwork.AreaToolAction.SELECT_END) {
            selectEnd(player, pos);
            return;
        }
        if (action == CleanerNetwork.AreaToolAction.CONFIRM) {
            saveSelection(player);
            return;
        }
        if (action == CleanerNetwork.AreaToolAction.CLEAR_TARGET) {
            clearTarget(player, pos);
            return;
        }
        if (action == CleanerNetwork.AreaToolAction.CLEAR_SELF) {
            clearSelf(player);
            return;
        }
        if (action == CleanerNetwork.AreaToolAction.CLEAR_ALL) {
            clearAll(player);
        }
    }

    private static void selectStart(ServerPlayer player, BlockPos pos) {
        if (pos == null) {
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.no_target");
            return;
        }
        CleanerArea found = findArea(player.serverLevel(), pos);
        if (found != null && !found.ownerId().equals(player.getUUID())) {
            START_POINTS.remove(player.getUUID());
            END_POINTS.remove(player.getUUID());
            CleanerNetwork.sendToPlayer(new CleanerNetwork.ClearAreaSelection(), player);
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.owner", gold(found.ownerName()));
            return;
        }
        START_POINTS.put(player.getUUID(), pos);
        END_POINTS.remove(player.getUUID());
        notify(player, "msg.itemcontrol.cleaner.cleaner.area.start", aqua(pos.getX()), aqua(pos.getY()), aqua(pos.getZ()), green(getRemainingGridCount(player)));
    }

    private static void selectEnd(ServerPlayer player, BlockPos pos) {
        if (pos == null) {
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.no_target");
            return;
        }
        CleanerArea found = findArea(player.serverLevel(), pos);
        if (found != null && !found.ownerId().equals(player.getUUID())) {
            clearServerAndClientSelection(player);
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.overlap_other", gold(found.ownerName()));
            return;
        }
        BlockPos start = START_POINTS.get(player.getUUID());
        if (start != null) {
            CleanerArea preview = new CleanerArea(player.getUUID(), player.getGameProfile().getName(), player.serverLevel().dimension().location().toString(), start, pos);
            CleanerArea other = findOverlappedOtherArea(player.serverLevel(), preview, player.getUUID());
            if (other != null) {
                clearServerAndClientSelection(player);
                notify(player, "msg.itemcontrol.cleaner.cleaner.area.overlap_other", gold(other.ownerName()));
                return;
            }
        }
        END_POINTS.put(player.getUUID(), pos);
        notify(player, "msg.itemcontrol.cleaner.cleaner.area.end", aqua(pos.getX()), aqua(pos.getY()), aqua(pos.getZ()), green(getRemainingGridCountAfterPreview(player, pos)));
    }

    private static void saveSelection(ServerPlayer player) {
        BlockPos first = START_POINTS.get(player.getUUID());
        BlockPos second = END_POINTS.get(player.getUUID());
        if (first == null || second == null) {
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.missing");
            return;
        }

        ServerLevel level = player.serverLevel();
        String dim = level.dimension().location().toString();
        CleanerArea selectedArea = new CleanerArea(player.getUUID(), player.getGameProfile().getName(), dim, first, second);
        CleanerAreaSavedData data = CleanerAreaSavedData.get(level);

        for (CleanerArea old : data.getAreas()) {
            if (selectedArea.overlaps(old) && !old.ownerId().equals(player.getUUID())) {
                clearServerAndClientSelection(player);
                notify(player, "msg.itemcontrol.cleaner.cleaner.area.overlap_other", gold(old.ownerName()));
                return;
            }
        }

        List<CleanerArea> selfOverlaps = findOverlappedOwnAreas(data, selectedArea, player.getUUID());
        CleanerArea finalArea = mergeWithOldSelfAreas(selectedArea, selfOverlaps, player);

        int used = getUsedGridCost(data, player.getUUID());
        int removedSelfCost = getTotalGridCost(selfOverlaps);
        int cost = finalArea.gridCost();
        int realUsedAfterRemove = Math.max(0, used - removedSelfCost);
        int limit = Math.max(1, CleanerConfig.protectedAreaMaxGridCountPerPlayer);

        if ((long) realUsedAfterRemove + cost > limit) {
            clearServerAndClientSelection(player);
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.too_large", yellow(realUsedAfterRemove), red(cost), yellow(limit), green(Math.max(0, limit - realUsedAfterRemove)));
            return;
        }

        for (CleanerArea old : selfOverlaps) {
            data.removeArea(old);
        }
        data.addArea(finalArea);
        clearServerAndClientSelection(player);
        notify(player, "msg.itemcontrol.cleaner.cleaner.area.saved", yellow(cost), green(Math.max(0, limit - realUsedAfterRemove - cost)));
        syncAll(player.server);
    }

    private static int getUsedGridCost(CleanerAreaSavedData data, UUID ownerId) {
        int total = 0;
        for (CleanerArea area : data.getAreas()) {
            if (area.ownerId().equals(ownerId)) {
                total += area.gridCost();
            }
        }
        return total;
    }

    private static int getTotalGridCost(List<CleanerArea> areas) {
        int total = 0;
        for (CleanerArea area : areas) {
            total += area.gridCost();
        }
        return total;
    }

    private static int getRemainingGridCount(ServerPlayer player) {
        CleanerAreaSavedData data = CleanerAreaSavedData.get(player.serverLevel());
        int limit = Math.max(1, CleanerConfig.protectedAreaMaxGridCountPerPlayer);
        int used = getUsedGridCost(data, player.getUUID());
        return Math.max(0, limit - used);
    }

    private static int getRemainingGridCountAfterPreview(ServerPlayer player, BlockPos end) {
        BlockPos start = START_POINTS.get(player.getUUID());
        if (start == null || end == null) {
            return getRemainingGridCount(player);
        }
        CleanerAreaSavedData data = CleanerAreaSavedData.get(player.serverLevel());
        int limit = Math.max(1, CleanerConfig.protectedAreaMaxGridCountPerPlayer);
        CleanerArea preview = new CleanerArea(player.getUUID(), player.getGameProfile().getName(), player.serverLevel().dimension().location().toString(), start, end);
        List<CleanerArea> selfOverlaps = findOverlappedOwnAreas(data, preview, player.getUUID());
        CleanerArea finalArea = mergeWithOldSelfAreas(preview, selfOverlaps, player);
        int used = getUsedGridCost(data, player.getUUID());
        int realUsedAfterRemove = Math.max(0, used - getTotalGridCost(selfOverlaps));
        return Math.max(0, limit - realUsedAfterRemove - finalArea.gridCost());
    }

    private static void clearTarget(ServerPlayer player, BlockPos pos) {
        if (pos == null) {
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.no_target_area");
            return;
        }
        ServerLevel level = player.serverLevel();
        CleanerAreaSavedData data = CleanerAreaSavedData.get(level);
        CleanerArea target = findArea(level, pos);
        if (target == null) {
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.no_target_area");
            return;
        }
        if (target.ownerId().equals(player.getUUID()) || isAdmin(player)) {
            data.removeArea(target);
            clearServerAndClientSelection(player);
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.removed_one", gold(target.ownerName()), green(getRemainingGridCount(player)));
            syncAll(player.server);
        } else {
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.not_owner", gold(target.ownerName()));
        }
    }

    private static void clearSelf(ServerPlayer player) {
        CleanerAreaSavedData data = CleanerAreaSavedData.get(player.serverLevel());
        int removed = data.removeByOwner(player.getUUID());
        clearServerAndClientSelection(player);
        notify(player, "msg.itemcontrol.cleaner.cleaner.area.removed_self", yellow(removed), green(getRemainingGridCount(player)));
        syncAll(player.server);
    }

    private static void clearAll(ServerPlayer player) {
        if (!isAdmin(player)) {
            notify(player, "msg.itemcontrol.cleaner.cleaner.area.no_permission");
            return;
        }
        CleanerAreaSavedData data = CleanerAreaSavedData.get(player.server.overworld());
        int removed = data.clearAllAreas();
        START_POINTS.clear();
        END_POINTS.clear();
        notify(player, "msg.itemcontrol.cleaner.cleaner.area.removed_all", yellow(removed));
        syncAll(player.server);
    }

    private static void clearServerAndClientSelection(ServerPlayer player) {
        START_POINTS.remove(player.getUUID());
        END_POINTS.remove(player.getUUID());
        CleanerNetwork.sendToPlayer(new CleanerNetwork.ClearAreaSelection(), player);
    }

    private static CleanerArea mergeWithOldSelfAreas(CleanerArea selectedArea, List<CleanerArea> selfOverlaps, ServerPlayer player) {
        int minX = selectedArea.minX();
        int minY = selectedArea.minY();
        int minZ = selectedArea.minZ();
        int maxX = selectedArea.maxX();
        int maxY = selectedArea.maxY();
        int maxZ = selectedArea.maxZ();

        for (CleanerArea area : selfOverlaps) {
            minX = Math.min(minX, area.minX());
            minY = Math.min(minY, area.minY());
            minZ = Math.min(minZ, area.minZ());
            maxX = Math.max(maxX, area.maxX());
            maxY = Math.max(maxY, area.maxY());
            maxZ = Math.max(maxZ, area.maxZ());
        }

        return new CleanerArea(player.getUUID(), player.getGameProfile().getName(), selectedArea.dimension(), minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<CleanerArea> findOverlappedOwnAreas(CleanerAreaSavedData data, CleanerArea preview, UUID ownerId) {
        List<CleanerArea> result = new ArrayList<>();
        for (CleanerArea area : data.getAreas()) {
            if (area.ownerId().equals(ownerId) && preview.overlaps(area)) {
                result.add(area);
            }
        }
        return result;
    }

    private static CleanerArea findOverlappedOtherArea(ServerLevel level, CleanerArea preview, UUID ownerId) {
        CleanerAreaSavedData data = CleanerAreaSavedData.get(level);
        for (CleanerArea area : data.getAreas()) {
            if (!area.ownerId().equals(ownerId) && preview.overlaps(area)) {
                return area;
            }
        }
        return null;
    }

    private static CleanerArea findArea(ServerLevel level, BlockPos pos) {
        String dim = level.dimension().location().toString();
        CleanerAreaSavedData data = CleanerAreaSavedData.get(level);
        return data.findContaining(dim, pos);
    }

    private static boolean isAdmin(ServerPlayer player) {
        return player.hasPermissions(2);
    }


    private static Component gold(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.GOLD);
    }

    private static Component yellow(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.YELLOW);
    }

    private static Component green(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.GREEN);
    }

    private static Component red(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.RED);
    }

    private static Component aqua(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.AQUA);
    }

    private static void notify(ServerPlayer player, String key, Object... args) {
        CleanerNetwork.sendToPlayer(new CleanerNetwork.NotifyToast(Component.translatable(key, args)), player);
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    public static void syncTo(ServerPlayer player) {
        CleanerAreaSavedData data = CleanerAreaSavedData.get(player.server.overworld());
        CleanerNetwork.sendToPlayer(new CleanerNetwork.SyncCleanerAreas(data.getAreas()), player);
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }
}
