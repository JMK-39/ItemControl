package dev.xyat.itemcontrol.cleaner.event;

import dev.xyat.itemcontrol.cleaner.area.CleanerAreaSavedData;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AutoCleanerEventHandler {

    private static final int MANUAL_DELAY_SECONDS = 10;
    private static final int ENTITIES_PER_TICK = 500;
    private static final Map<ServerLevel, Set<Entity>> TRACKED_ENTITIES = new HashMap<>();

    private static int autoTicksUntilNextClean = -1;
    private static int manualTickCounter = -1;
    private static String manualInitiatorName = "";
    private static boolean trackingRulesDirty = false;
    private static boolean registered = false;
    private static CleanupSession activeSession;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(AutoCleanerEventHandler::onLevelLoad);
        MinecraftForge.EVENT_BUS.addListener(AutoCleanerEventHandler::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(AutoCleanerEventHandler::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(AutoCleanerEventHandler::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(AutoCleanerEventHandler::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(AutoCleanerEventHandler::onServerTick);
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            getTrackedEntities(level);
            if (autoTicksUntilNextClean == -1) {
                resetTimer();
            }
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            TRACKED_ENTITIES.remove(level);
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && shouldTrack(event.getEntity())) {
            getTrackedEntities(level).add(event.getEntity());
        }
    }

    private static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Set<Entity> tracked = TRACKED_ENTITIES.get(level);
            if (tracked != null) {
                tracked.remove(event.getEntity());
            }
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        TRACKED_ENTITIES.keySet().removeIf(level -> level.getServer() == server);
        if (activeSession != null && activeSession.server == server) {
            activeSession = null;
        }
        trackingRulesDirty = false;
        autoTicksUntilNextClean = -1;
        manualTickCounter = -1;
        manualInitiatorName = "";
    }

    public static void onCleanerRulesChanged() {
        if (!TRACKED_ENTITIES.isEmpty()) {
            trackingRulesDirty = true;
        }
    }

    public static void resetTimer() {
        if (CleanerConfig.isCleanerHardDisabled || !CleanerConfig.enableCleaner) {
            autoTicksUntilNextClean = -1;
        } else {
            autoTicksUntilNextClean = CleanerConfig.cleanerIntervalSeconds * 20;
        }
        manualTickCounter = -1;
    }

    public static boolean isAutoCleanImminent() {
        return activeSession != null
                || CleanerConfig.enableCleaner
                && autoTicksUntilNextClean > 0
                && autoTicksUntilNextClean <= 200;
    }

    public static void triggerManualClean(ServerPlayer player) {
        if (CleanerConfig.isCleanerHardDisabled) {
            player.sendSystemMessage(Component.translatable("msg.itemcontrol.cleaner.cleaner.hard_disabled_moe").withStyle(ChatFormatting.RED));
            return;
        }

        if (!CleanerConfig.allowManualClean && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable("msg.itemcontrol.cleaner.cleaner.manual.locked").withStyle(ChatFormatting.RED));
            return;
        }

        if (manualTickCounter > 0 || isAutoCleanImminent()) {
            player.sendSystemMessage(Component.translatable("msg.itemcontrol.cleaner.cleaner.manual.busy").withStyle(ChatFormatting.YELLOW));
            return;
        }

        manualTickCounter = MANUAL_DELAY_SECONDS * 20;
        manualInitiatorName = player.getName().getString();

        player.sendSystemMessage(Component.translatable("msg.itemcontrol.cleaner.cleaner.manual.confirmed").withStyle(ChatFormatting.GREEN));
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (CleanerConfig.isCleanerHardDisabled) {
            activeSession = null;
            manualTickCounter = -1;
            return;
        }

        MinecraftServer server = event.getServer();
        rebuildTrackingIfNeeded(server);

        if (activeSession != null) {
            processActiveCleanup(server);
            if (activeSession != null) {
                return;
            }
        }

        if (manualTickCounter > 0) {
            manualTickCounter--;
            if (manualTickCounter > 0 && manualTickCounter % 20 == 0) {
                broadcastActionBar(server, manualInitiatorName, manualTickCounter / 20, false);
            }
            if (manualTickCounter == 0) {
                clearActionBar(server);
                manualTickCounter = -1;
                beginCleanup(server);
            }
        }

        if (activeSession == null && CleanerConfig.enableCleaner && autoTicksUntilNextClean > 0) {
            autoTicksUntilNextClean--;
            if (autoTicksUntilNextClean == 200) {
                broadcastSystemMessage(server);
            }
            if (autoTicksUntilNextClean > 0
                    && autoTicksUntilNextClean <= 200
                    && autoTicksUntilNextClean % 20 == 0) {
                broadcastActionBar(server, "Auto", autoTicksUntilNextClean / 20, true);
            }
            if (autoTicksUntilNextClean == 0) {
                clearActionBar(server);
                beginCleanup(server);
                autoTicksUntilNextClean = CleanerConfig.cleanerIntervalSeconds * 20;
            }
        }
    }

    private static void processActiveCleanup(MinecraftServer server) {
        if (activeSession == null) {
            return;
        }
        if (activeSession.process()) {
            CleanResult result = activeSession.finish();
            activeSession = null;
            broadcastResult(server, result);
        }
    }

    private static void beginCleanup(MinecraftServer server) {
        if (activeSession == null) {
            activeSession = createSession(server);
        }
    }

    private static CleanupSession createSession(MinecraftServer server) {
        List<LevelCleanupJob> jobs = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            ArrayDeque<Entity> candidates = new ArrayDeque<>();
            for (Entity entity : getTrackedEntities(level)) {
                if (isCleanupCandidate(entity)) {
                    candidates.addLast(entity);
                }
            }
            jobs.add(new LevelCleanupJob(level, candidates));
        }
        return new CleanupSession(server, jobs);
    }

    private static boolean isCleanupCandidate(Entity entity) {
        if (entity instanceof ItemEntity) {
            return true;
        }
        if (entity instanceof ExperienceOrb) {
            return CleanerConfig.cleanExperienceOrbs;
        }
        if (!CleanerConfig.enableEntityCleaning) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return CleanerConfig.shouldCleanEntity(id);
    }

    private static boolean shouldTrack(Entity entity) {
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return CleanerConfig.shouldCleanEntity(id);
    }

    private static Set<Entity> getTrackedEntities(ServerLevel level) {
        Set<Entity> existing = TRACKED_ENTITIES.get(level);
        if (existing != null) {
            return existing;
        }

        Set<Entity> tracked = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entity entity : level.getAllEntities()) {
            if (shouldTrack(entity)) {
                tracked.add(entity);
            }
        }
        TRACKED_ENTITIES.put(level, tracked);
        return tracked;
    }

    private static void rebuildTrackingIfNeeded(MinecraftServer server) {
        if (!trackingRulesDirty) {
            return;
        }

        TRACKED_ENTITIES.clear();
        for (ServerLevel level : server.getAllLevels()) {
            getTrackedEntities(level);
        }
        trackingRulesDirty = false;
    }

    private static void broadcastActionBar(MinecraftServer server, String initiator, int seconds, boolean isAuto) {
        ChatFormatting timeColor = seconds <= 3 ? ChatFormatting.RED : ChatFormatting.YELLOW;
        Component secondsText = Component.literal(String.valueOf(seconds)).withStyle(timeColor, ChatFormatting.BOLD);

        String key;
        Component msg;
        if (isAuto) {
            key = seconds <= 3
                    ? "msg.itemcontrol.cleaner.cleaner.actionbar.auto.urgent"
                    : "msg.itemcontrol.cleaner.cleaner.actionbar.auto.normal";
            msg = Component.translatable(key, secondsText);
        } else {
            key = seconds <= 3
                    ? "msg.itemcontrol.cleaner.cleaner.actionbar.manual.urgent"
                    : "msg.itemcontrol.cleaner.cleaner.actionbar.manual.normal";
            Component initiatorText = Component.literal(initiator).withStyle(ChatFormatting.GOLD);
            msg = Component.translatable(key, initiatorText, secondsText);
        }
        server.getPlayerList().getPlayers().forEach(player -> player.displayClientMessage(msg, true));
    }

    private static void clearActionBar(MinecraftServer server) {
        server.getPlayerList().getPlayers()
                .forEach(player -> player.displayClientMessage(Component.empty(), true));
    }

    private static void broadcastSystemMessage(MinecraftServer server) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "msg.itemcontrol.cleaner.cleaner.warning",
                        Component.literal("10").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                ).withStyle(ChatFormatting.YELLOW),
                false);
    }

    private static void broadcastResult(MinecraftServer server, CleanResult result) {
        if (result.itemCount <= 0 && result.entityCount <= 0) {
            return;
        }

        MutableComponent msg = Component.translatable("msg.itemcontrol.cleaner.cleaner.result.header")
                .withStyle(ChatFormatting.GREEN);

        if (result.itemCount > 0) {
            msg.append(Component.translatable(
                    "msg.itemcontrol.cleaner.cleaner.result.items",
                    Component.literal(String.valueOf(result.itemCount))
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        }
        if (result.itemCount > 0 && result.entityCount > 0) {
            msg.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
        }
        if (result.entityCount > 0) {
            msg.append(Component.translatable(
                    "msg.itemcontrol.cleaner.cleaner.result.entities",
                    Component.literal(String.valueOf(result.entityCount))
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        }

        if (CleanerConfig.enableTrashBin && result.itemCount > 0) {
            msg.append("\n").append(Component.translatable("msg.itemcontrol.cleaner.cleaner.cleaner.link")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.GOLD)
                            .withBold(true)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kt clean bin"))
                            .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("msg.itemcontrol.cleaner.cleaner.cleaner.hover")
                                            .withStyle(ChatFormatting.GOLD)))));
        }
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    public record CleanResult(int itemCount, int entityCount) {
    }

    private static final class CleanupSession {
        private final MinecraftServer server;
        private final List<LevelCleanupJob> jobs;
        private final TrashBinCollectorEventHandler.TrashAccumulator trashAccumulator =
                new TrashBinCollectorEventHandler.TrashAccumulator();
        private int jobIndex;
        private long itemCount;
        private long entityCount;
        private boolean finished;

        private CleanupSession(MinecraftServer server, List<LevelCleanupJob> jobs) {
            this.server = server;
            this.jobs = jobs;
        }

        private boolean process() {
            int remainingBudget = Math.max(1, AutoCleanerEventHandler.ENTITIES_PER_TICK);
            while (remainingBudget > 0 && jobIndex < jobs.size()) {
                LevelCleanupJob job = jobs.get(jobIndex);
                int processed = job.process(remainingBudget, this);
                remainingBudget -= processed;
                if (job.isDone()) {
                    jobIndex++;
                } else if (processed == 0) {
                    break;
                }
            }
            return jobIndex >= jobs.size();
        }

        private void addItems(int count) {
            if (count > 0) {
                if (itemCount > Long.MAX_VALUE - count) {
                    itemCount = Long.MAX_VALUE;
                } else {
                    itemCount += count;
                }
            }
        }

        private void addEntity() {
            if (entityCount < Long.MAX_VALUE) {
                entityCount++;
            }
        }

        private CleanResult finish() {
            if (!finished) {
                trashAccumulator.commit(server);
                finished = true;
            }
            return new CleanResult(toIntCount(itemCount), toIntCount(entityCount));
        }

        private static int toIntCount(long value) {
            return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
        }
    }

    private static final class LevelCleanupJob {
        private final ServerLevel level;
        private final String dimension;
        private final CleanerAreaSavedData areaData;
        private final ArrayDeque<Entity> candidates;

        private LevelCleanupJob(ServerLevel level, ArrayDeque<Entity> candidates) {
            this.level = level;
            this.dimension = level.dimension().location().toString();
            this.areaData = CleanerAreaSavedData.get(level);
            this.candidates = candidates;
        }

        private int process(int budget, CleanupSession session) {
            int processed = 0;
            while (processed < budget && !candidates.isEmpty()) {
                Entity entity = candidates.removeFirst();
                processed++;

                if (entity.isRemoved() || entity.level() != level) {
                    continue;
                }

                if (entity instanceof ItemEntity itemEntity) {
                    ItemStack stack = itemEntity.getItem();
                    if (stack.isEmpty()
                            || CleanerConfig.isItemForbiddenInTrash(stack)
                            || CleanerConfig.enableProtectedAreas
                            && areaData.isProtected(dimension, itemEntity.blockPosition())) {
                        continue;
                    }

                    session.trashAccumulator.add(stack);
                    session.addItems(stack.getCount());
                    itemEntity.discard();
                    continue;
                }

                if (entity instanceof ExperienceOrb) {
                    if (CleanerConfig.cleanExperienceOrbs) {
                        entity.discard();
                        session.addEntity();
                    }
                    continue;
                }

                if (CleanerConfig.enableEntityCleaning) {
                    ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
                    if (CleanerConfig.shouldCleanEntity(id)) {
                        entity.discard();
                        session.addEntity();
                    }
                }
            }
            return processed;
        }

        private boolean isDone() {
            return candidates.isEmpty();
        }
    }
}
