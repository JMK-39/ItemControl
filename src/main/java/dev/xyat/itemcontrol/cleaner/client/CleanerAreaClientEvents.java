package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import dev.xyat.itemcontrol.cleaner.area.CleanerArea;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;
import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.render.KineticWorldRender;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CleanerAreaClientEvents {
    private static boolean installed;

    private CleanerAreaClientEvents() {
    }

    public static void install() {
        if (installed) return;
        installed = true;
        KineticClientEvents.onMouseButtonBefore(CleanerAreaClientEvents::onMouseButton);
        KineticClientEvents.onLevelRender(KineticClientEvents.LevelRenderStage.AFTER_TRANSLUCENT_BLOCKS, CleanerAreaClientEvents::onRenderLevel);
    }

    private static void onMouseButton(KineticClientEvents.MouseButtonContext event) {
        if (!CleanerConfig.enableProtectedAreas) {
            return;
        }
        if (!event.pressed()) {
            return;
        }

        LocalPlayer player = KineticClientRuntime.localPlayer();
        ClientLevel level = KineticClientRuntime.currentLevel();

        if (player == null || level == null || KineticClientRuntime.currentScreen() != null) {
            return;
        }
        if (isHoldingTool(player)) {
            return;
        }

        int button = event.button();

        if (!KineticMouseButtons.isPrimary(button) && !KineticMouseButtons.isSecondary(button)) {
            return;
        }

        boolean actionKey = CleanerAreaKeyBindings.isDown();
        boolean alt = KineticKeyBindings.isKeyDown(KineticKeyBindings.Key.LEFT_ALT)
                || KineticKeyBindings.isKeyDown(KineticKeyBindings.Key.RIGHT_ALT);
        BlockPos pos = getLookBlockPos(player, level);

        if (KineticMouseButtons.isPrimary(button)) {
            if (actionKey && alt) {
                CleanerAreaClientState.clearSelection();
                CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.CLEAR_ALL, pos));
            } else if (actionKey) {
                if (isCurrentSelectionUnsavable(player, level)) {
                    notifyTooLarge(player, level);
                    event.cancel();
                    return;
                }
                CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.CONFIRM, pos));
            } else {
                handleSelectStart(player, level, pos);
            }

            event.cancel();
            return;
        }

        if (actionKey && alt) {
            CleanerAreaClientState.clearSelection();
            CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.CLEAR_SELF, pos));
        } else if (actionKey) {
            CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.CLEAR_TARGET, pos));
        } else {
            handleSelectEnd(player, level, pos);
        }

        event.cancel();
    }

    private static void handleSelectStart(LocalPlayer player, ClientLevel level, BlockPos pos) {
        if (player == null || level == null) {
            return;
        }

        if (pos != null) {
            CleanerArea area = findAreaAt(level, pos);

            if (area != null && !area.ownerId().equals(player.getUUID())) {
                CleanerAreaClientState.clearSelection();
                KineticOverlays.toast("cleaner_area", Component.translatable("msg.itemcontrol.cleaner.cleaner.area.owner", Component.literal(area.ownerName()).withStyle(ChatFormatting.GOLD)));
                return;
            }

            CleanerAreaClientState.setStart(pos);
            CleanerAreaClientState.setEnd(null);
        }

        CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.SELECT_START, pos));
    }

    private static void handleSelectEnd(LocalPlayer player, ClientLevel level, BlockPos pos) {
        if (player == null || level == null) {
            return;
        }

        if (pos != null) {
            CleanerArea area = findAreaAt(level, pos);

            if (area != null && !area.ownerId().equals(player.getUUID())) {
                CleanerAreaClientState.clearSelection();
                KineticOverlays.toast("cleaner_area", Component.translatable("msg.itemcontrol.cleaner.cleaner.area.overlap_other", Component.literal(area.ownerName()).withStyle(ChatFormatting.GOLD)));
                return;
            }

            BlockPos start = CleanerAreaClientState.getStart();

            if (start != null) {
                CleanerArea preview = new CleanerArea(player.getUUID(), player.getGameProfile().getName(), level.dimension().location().toString(), start, pos);
                CleanerArea other = findOverlappedOtherArea(preview, player.getUUID());

                if (other != null) {
                    CleanerAreaClientState.clearSelection();
                    KineticOverlays.toast("cleaner_area", Component.translatable("msg.itemcontrol.cleaner.cleaner.area.overlap_other", Component.literal(other.ownerName()).withStyle(ChatFormatting.GOLD)));
                    return;
                }

                CleanerAreaClientState.setEnd(pos);

                if (!isPreviewSaveable(player, level, preview)) {
                    notifyTooLarge(player, level);
                }
            } else {
                CleanerAreaClientState.setEnd(pos);
            }
        }

        CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.SELECT_END, pos));
    }

    private static void onRenderLevel(KineticClientEvents.LevelRenderContext event) {
        if (!CleanerConfig.enableProtectedAreas) {
            return;
        }

        LocalPlayer player = KineticClientRuntime.localPlayer();
        ClientLevel level = KineticClientRuntime.currentLevel();

        if (player == null || level == null || isHoldingTool(player)) {
            return;
        }

        String dim = level.dimension().location().toString();

        try (KineticWorldRender.LineBatch batch = KineticWorldRender.beginLineBatch(event)) {
            for (CleanerArea area : CleanerAreaClientState.getAreas()) {
                if (!area.dimension().equals(dim)) {
                    continue;
                }

                KineticWorldRender.Indicator indicator = CleanerAreaClientState.isOwn(area, player.getUUID())
                        ? KineticWorldRender.Indicator.SUCCESS
                        : KineticWorldRender.Indicator.WARNING;
                batch.box(areaBox(area), indicator);
            }

            BlockPos start = CleanerAreaClientState.getStart();
            BlockPos end = CleanerAreaClientState.getEnd();

            if (start != null) {
                BlockPos second = end == null ? start : end;
                CleanerArea preview = new CleanerArea(player.getUUID(), player.getGameProfile().getName(), dim, start, second);
                KineticWorldRender.Indicator indicator = isPreviewSaveable(player, level, preview)
                        ? KineticWorldRender.Indicator.SUCCESS
                        : KineticWorldRender.Indicator.DANGER;
                batch.box(areaBox(preview), indicator);
            }
        }
    }

    private static boolean isCurrentSelectionUnsavable(LocalPlayer player, ClientLevel level) {
        if (player == null || level == null) {
            return true;
        }

        BlockPos start = CleanerAreaClientState.getStart();
        BlockPos end = CleanerAreaClientState.getEnd();

        if (start == null || end == null) {
            return true;
        }

        CleanerArea preview = new CleanerArea(player.getUUID(), player.getGameProfile().getName(), level.dimension().location().toString(), start, end);
        return !isPreviewSaveable(player, level, preview);
    }

    private static boolean isPreviewSaveable(LocalPlayer player, ClientLevel level, CleanerArea preview) {
        if (player == null || level == null) {
            return false;
        }

        CleanerArea other = findOverlappedOtherArea(preview, player.getUUID());

        if (other != null) {
            return false;
        }

        int limit = Math.max(1, CleanerConfig.protectedAreaMaxGridCountPerPlayer);
        int used = getUsedGridCost(player.getUUID());
        List<CleanerArea> selfOverlaps = findOverlappedOwnAreas(preview, player.getUUID());
        int removedSelfCost = getTotalGridCost(selfOverlaps);
        CleanerArea finalArea = mergeWithOldSelfAreas(preview, selfOverlaps);

        long realUsedAfterRemove = Math.max(0L, (long) used - removedSelfCost);
        long finalCost = finalArea.gridCost();

        return realUsedAfterRemove + finalCost <= limit;
    }

    private static int getUsedGridCost(UUID ownerId) {
        int total = 0;

        for (CleanerArea area : CleanerAreaClientState.getAreas()) {
            if (area.ownerId().equals(ownerId)) {
                total = safeAdd(total, area.gridCost());
            }
        }

        return total;
    }

    private static int getTotalGridCost(List<CleanerArea> areas) {
        int total = 0;

        for (CleanerArea area : areas) {
            total = safeAdd(total, area.gridCost());
        }

        return total;
    }

    private static int safeAdd(int left, int right) {
        long result = (long) left + right;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static List<CleanerArea> findOverlappedOwnAreas(CleanerArea preview, UUID ownerId) {
        List<CleanerArea> result = new ArrayList<>();

        for (CleanerArea area : CleanerAreaClientState.getAreas()) {
            if (area.ownerId().equals(ownerId) && preview.overlaps(area)) {
                result.add(area);
            }
        }

        return result;
    }

    private static CleanerArea mergeWithOldSelfAreas(CleanerArea selectedArea, List<CleanerArea> selfOverlaps) {
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

        return new CleanerArea(selectedArea.ownerId(), selectedArea.ownerName(), selectedArea.dimension(), minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void notifyTooLarge(LocalPlayer player, ClientLevel level) {
        if (player == null || level == null) {
            return;
        }

        BlockPos start = CleanerAreaClientState.getStart();
        BlockPos end = CleanerAreaClientState.getEnd();

        if (start == null || end == null) {
            return;
        }

        CleanerArea preview = new CleanerArea(player.getUUID(), player.getGameProfile().getName(), level.dimension().location().toString(), start, end);
        int limit = Math.max(1, CleanerConfig.protectedAreaMaxGridCountPerPlayer);
        int used = getUsedGridCost(player.getUUID());
        List<CleanerArea> selfOverlaps = findOverlappedOwnAreas(preview, player.getUUID());
        int removedSelfCost = getTotalGridCost(selfOverlaps);
        CleanerArea finalArea = mergeWithOldSelfAreas(preview, selfOverlaps);
        int realUsedAfterRemove = Math.max(0, used - removedSelfCost);
        int cost = finalArea.gridCost();
        int remaining = Math.max(0, limit - realUsedAfterRemove);

        KineticOverlays.toast("cleaner_area", Component.translatable("msg.itemcontrol.cleaner.cleaner.area.too_large", Component.literal(String.valueOf(realUsedAfterRemove)).withStyle(ChatFormatting.YELLOW), Component.literal(String.valueOf(cost)).withStyle(ChatFormatting.RED), Component.literal(String.valueOf(limit)).withStyle(ChatFormatting.YELLOW), Component.literal(String.valueOf(remaining)).withStyle(ChatFormatting.GREEN)));
    }

    private static CleanerArea findAreaAt(ClientLevel level, BlockPos pos) {
        if (level == null) {
            return null;
        }

        String dim = level.dimension().location().toString();

        for (CleanerArea area : CleanerAreaClientState.getAreas()) {
            if (area.contains(dim, pos)) {
                return area;
            }
        }

        return null;
    }

    private static CleanerArea findOverlappedOtherArea(CleanerArea preview, UUID ownerId) {
        for (CleanerArea area : CleanerAreaClientState.getAreas()) {
            if (!area.ownerId().equals(ownerId) && preview.overlaps(area)) {
                return area;
            }
        }

        return null;
    }

    private static BlockPos getLookBlockPos(LocalPlayer player, ClientLevel level) {
        if (player == null || level == null) {
            return null;
        }

        double distance = Math.max(1.0D, CleanerConfig.protectedAreaRayTraceDistance);
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.x * distance, look.y * distance, look.z * distance);
        BlockHitResult result = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        if (result.getType() == HitResult.Type.BLOCK) {
            return result.getBlockPos();
        }

        return null;
    }

    private static AABB areaBox(CleanerArea area) {
        return new AABB(area.minX(), area.minY(), area.minZ(), area.maxX() + 1.0D, area.maxY() + 1.0D, area.maxZ() + 1.0D);
    }

    private static boolean isHoldingTool(LocalPlayer player) {
        if (player == null) {
            return true;
        }

        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);

        return !CleanerClientConfigState.isProtectedAreaTool(main) && !CleanerClientConfigState.isProtectedAreaTool(off);
    }

}
