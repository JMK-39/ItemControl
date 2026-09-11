package dev.xyat.itemcontrol.cleaner.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import dev.xyat.itemcontrol.cleaner.area.CleanerArea;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = CleanerModule.MODID, value = Dist.CLIENT)
public class CleanerAreaClientEvents {
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!CleanerConfig.enableProtectedAreas) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (isHoldingTool()) {
            return;
        }

        int button = event.getButton();

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }

        boolean actionKey = CleanerAreaKeyBindings.AREA_ACTION_KEY.isDown();
        boolean alt = isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT);
        BlockPos pos = getLookBlockPos();

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (actionKey && alt) {
                CleanerAreaClientState.clearSelection();
                CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.CLEAR_ALL, pos));
            } else if (actionKey) {
                if (!isCurrentSelectionSaveable(mc)) {
                    notifyTooLarge(mc);
                    event.setCanceled(true);
                    return;
                }
                CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.CONFIRM, pos));
            } else {
                handleSelectStart(mc, pos);
            }

            event.setCanceled(true);
            return;
        }

        if (actionKey && alt) {
            CleanerAreaClientState.clearSelection();
            CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.CLEAR_SELF, pos));
        } else if (actionKey) {
            CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.CLEAR_TARGET, pos));
        } else {
            handleSelectEnd(mc, pos);
        }

        event.setCanceled(true);
    }

    private static void handleSelectStart(Minecraft mc, BlockPos pos) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (pos != null) {
            CleanerArea area = findAreaAt(pos);

            if (area != null && !area.ownerId().equals(mc.player.getUUID())) {
                CleanerAreaClientState.clearSelection();
                GuiOverlay.toast("cleaner_area", Component.translatable("msg.itemcontrol.cleaner.cleaner.area.owner", Component.literal(area.ownerName()).withStyle(ChatFormatting.GOLD)));
                return;
            }

            CleanerAreaClientState.setStart(pos);
            CleanerAreaClientState.setEnd(null);
        }

        CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.SELECT_START, pos));
    }

    private static void handleSelectEnd(Minecraft mc, BlockPos pos) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (pos != null) {
            CleanerArea area = findAreaAt(pos);

            if (area != null && !area.ownerId().equals(mc.player.getUUID())) {
                CleanerAreaClientState.clearSelection();
                GuiOverlay.toast("cleaner_area", Component.translatable("msg.itemcontrol.cleaner.cleaner.area.overlap_other", Component.literal(area.ownerName()).withStyle(ChatFormatting.GOLD)));
                return;
            }

            BlockPos start = CleanerAreaClientState.getStart();

            if (start != null) {
                CleanerArea preview = new CleanerArea(mc.player.getUUID(), mc.player.getGameProfile().getName(), mc.level.dimension().location().toString(), start, pos);
                CleanerArea other = findOverlappedOtherArea(preview, mc.player.getUUID());

                if (other != null) {
                    CleanerAreaClientState.clearSelection();
                    GuiOverlay.toast("cleaner_area", Component.translatable("msg.itemcontrol.cleaner.cleaner.area.overlap_other", Component.literal(other.ownerName()).withStyle(ChatFormatting.GOLD)));
                    return;
                }

                CleanerAreaClientState.setEnd(pos);

                if (!isPreviewSaveable(mc, preview)) {
                    notifyTooLarge(mc);
                }
            } else {
                CleanerAreaClientState.setEnd(pos);
            }
        }

        CleanerNetwork.sendToServer(new CleanerNetwork.AreaToolAction(CleanerNetwork.AreaToolAction.SELECT_END, pos));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!CleanerConfig.enableProtectedAreas) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null || isHoldingTool()) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        Camera camera = mc.gameRenderer.getMainCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.5F);

        String dim = mc.level.dimension().location().toString();

        for (CleanerArea area : CleanerAreaClientState.getAreas()) {
            if (!area.dimension().equals(dim)) {
                continue;
            }

            boolean own = CleanerAreaClientState.isOwn(area, mc.player.getUUID());
            float red = own ? 0.1F : 1.0F;
            float green = own ? 1.0F : 0.85F;
            float blue = own ? 0.1F : 0.05F;

            renderAreaBox(pose, buffer, area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ(), camX, camY, camZ, red, green, blue);
        }

        BlockPos start = CleanerAreaClientState.getStart();
        BlockPos end = CleanerAreaClientState.getEnd();

        if (start != null) {
            BlockPos second = end == null ? start : end;
            CleanerArea preview = new CleanerArea(mc.player.getUUID(), mc.player.getGameProfile().getName(), dim, start, second);
            boolean saveable = isPreviewSaveable(mc, preview);

            float red = saveable ? 0.1F : 1.0F;
            float green = saveable ? 1.0F : 0.05F;
            float blue = saveable ? 0.1F : 0.05F;

            renderAreaBox(pose, buffer,
                    preview.minX(),
                    preview.minY(),
                    preview.minZ(),
                    preview.maxX(),
                    preview.maxY(),
                    preview.maxZ(),
                    camX, camY, camZ, red, green, blue);
        }

        buffer.endBatch(RenderType.lines());
        RenderSystem.lineWidth(1.0F);
    }

    private static boolean isCurrentSelectionSaveable(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        BlockPos start = CleanerAreaClientState.getStart();
        BlockPos end = CleanerAreaClientState.getEnd();

        if (start == null || end == null) {
            return false;
        }

        CleanerArea preview = new CleanerArea(mc.player.getUUID(), mc.player.getGameProfile().getName(), mc.level.dimension().location().toString(), start, end);
        return isPreviewSaveable(mc, preview);
    }

    private static boolean isPreviewSaveable(Minecraft mc, CleanerArea preview) {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        CleanerArea other = findOverlappedOtherArea(preview, mc.player.getUUID());

        if (other != null) {
            return false;
        }

        int limit = Math.max(1, CleanerConfig.protectedAreaMaxGridCountPerPlayer);
        int used = getUsedGridCost(mc.player.getUUID());
        List<CleanerArea> selfOverlaps = findOverlappedOwnAreas(preview, mc.player.getUUID());
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

    private static void notifyTooLarge(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        BlockPos start = CleanerAreaClientState.getStart();
        BlockPos end = CleanerAreaClientState.getEnd();

        if (start == null || end == null) {
            return;
        }

        CleanerArea preview = new CleanerArea(mc.player.getUUID(), mc.player.getGameProfile().getName(), mc.level.dimension().location().toString(), start, end);
        int limit = Math.max(1, CleanerConfig.protectedAreaMaxGridCountPerPlayer);
        int used = getUsedGridCost(mc.player.getUUID());
        List<CleanerArea> selfOverlaps = findOverlappedOwnAreas(preview, mc.player.getUUID());
        int removedSelfCost = getTotalGridCost(selfOverlaps);
        CleanerArea finalArea = mergeWithOldSelfAreas(preview, selfOverlaps);
        int realUsedAfterRemove = Math.max(0, used - removedSelfCost);
        int cost = finalArea.gridCost();
        int remaining = Math.max(0, limit - realUsedAfterRemove);

        GuiOverlay.toast("cleaner_area", Component.translatable("msg.itemcontrol.cleaner.cleaner.area.too_large", Component.literal(String.valueOf(realUsedAfterRemove)).withStyle(ChatFormatting.YELLOW), Component.literal(String.valueOf(cost)).withStyle(ChatFormatting.RED), Component.literal(String.valueOf(limit)).withStyle(ChatFormatting.YELLOW), Component.literal(String.valueOf(remaining)).withStyle(ChatFormatting.GREEN)));
    }

    private static CleanerArea findAreaAt(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return null;
        }

        String dim = mc.level.dimension().location().toString();

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

    private static BlockPos getLookBlockPos() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            return null;
        }

        double distance = Math.max(1.0D, CleanerConfig.protectedAreaRayTraceDistance);
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 look = mc.player.getViewVector(1.0F);
        Vec3 end = eye.add(look.x * distance, look.y * distance, look.z * distance);
        BlockHitResult result = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));

        if (result.getType() == HitResult.Type.BLOCK) {
            return result.getBlockPos();
        }

        return null;
    }

    private static void renderAreaBox(PoseStack pose, MultiBufferSource.BufferSource buffer, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, double camX, double camY, double camZ, float red, float green, float blue) {
        AABB box = new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D).move(-camX, -camY, -camZ);
        LevelRenderer.renderLineBox(pose, buffer.getBuffer(RenderType.lines()), box, red, green, blue, 1.0F);
    }

    private static boolean isHoldingTool() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return true;
        }

        ItemStack main = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = mc.player.getItemInHand(InteractionHand.OFF_HAND);

        return !CleanerClientConfigState.isProtectedAreaTool(main) && !CleanerClientConfigState.isProtectedAreaTool(off);
    }

    private static boolean isKeyDown(int key) {
        Minecraft mc = Minecraft.getInstance();
        return GLFW.glfwGetKey(mc.getWindow().getWindow(), key) == GLFW.GLFW_PRESS;
    }
}
