package dev.xyat.itemcontrol.cleaner.event;

import dev.xyat.itemcontrol.cleaner.area.CleanerAreaSavedData;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.player.KineticPlayerMessages;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.world.event.KineticWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

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
    private static Component manualInitiatorName = Component.empty();
    private static boolean trackingRulesDirty = false;
    private static boolean registered = false;
    private static CleanupSession activeSession;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        KineticWorldEvents.onLevelLoad(KineticEventPriority.NORMAL, AutoCleanerEventHandler::onLevelLoad);
        KineticWorldEvents.onLevelUnload(KineticEventPriority.NORMAL, AutoCleanerEventHandler::onLevelUnload);
        KineticWorldEvents.onEntityJoin(KineticEventPriority.NORMAL, context -> onEntityJoin(context.entity()));
        KineticWorldEvents.onEntityLeave(KineticEventPriority.NORMAL, (entity, level) -> onEntityLeave(entity));
        KineticServerEvents.onStopping(KineticEventPriority.NORMAL, AutoCleanerEventHandler::onServerStopping);
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END, AutoCleanerEventHandler::onServerTick);
    }

    private static void onLevelLoad(net.minecraft.world.level.LevelAccessor levelAccess) {
        if (levelAccess instanceof ServerLevel level) {
            getTrackedEntities(level);
            if (autoTicksUntilNextClean == -1) {
                resetTimer();
            }
        }
    }

    private static void onLevelUnload(net.minecraft.world.level.LevelAccessor levelAccess) {
        if (levelAccess instanceof ServerLevel level) {
            TRACKED_ENTITIES.remove(level);
        }
    }

    private static void onEntityJoin(Entity entity) {
        if (entity.level() instanceof ServerLevel level && shouldTrack(entity)) {
            getTrackedEntities(level).add(entity);
        }
    }

    private static void onEntityLeave(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            Set<Entity> tracked = TRACKED_ENTITIES.get(level);
            if (tracked != null) {
                tracked.remove(entity);
            }
        }
    }

    private static void onServerStopping(MinecraftServer server) {
        TRACKED_ENTITIES.keySet().removeIf(level -> level.getServer() == server);
        if (activeSession != null && activeSession.server == server) {
            activeSession = null;
        }
        trackingRulesDirty = false;
        autoTicksUntilNextClean = -1;
        manualTickCounter = -1;
        manualInitiatorName = Component.empty();
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
            KineticPlayerMessages.system(player, KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.hard_disabled_moe"));
            return;
        }

        if (!CleanerConfig.allowManualClean && !player.hasPermissions(2)) {
            KineticPlayerMessages.system(player, KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.manual.locked"));
            return;
        }

        if (manualTickCounter > 0 || isAutoCleanImminent()) {
            KineticPlayerMessages.system(player, KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.manual.busy"));
            return;
        }

        manualTickCounter = MANUAL_DELAY_SECONDS * 20;
        manualInitiatorName = player.getDisplayName().copy();
        broadcastActionBar(player.server, manualInitiatorName, MANUAL_DELAY_SECONDS, false);

        KineticPlayerMessages.system(player, KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.manual.confirmed"));
    }

    private static void onServerTick(MinecraftServer server) {
        if (CleanerConfig.isCleanerHardDisabled) {
            activeSession = null;
            manualTickCounter = -1;
            return;
        }

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
                broadcastActionBar(server, Component.empty(), autoTicksUntilNextClean / 20, true);
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
        ResourceLocation id = KineticRegistries.entityTypes().id(entity.getType());
        return CleanerConfig.shouldCleanEntity(id);
    }

    private static boolean shouldTrack(Entity entity) {
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            return true;
        }
        ResourceLocation id = KineticRegistries.entityTypes().id(entity.getType());
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

    private static void broadcastActionBar(MinecraftServer server, Component initiator, int seconds, boolean isAuto) {
        String key;
        Component message;
        // Give the numeral its own fully localized color, without relying on
        // styling propagation from the surrounding translatable message.
        Component coloredNumber = KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.countdown.number." + seconds);
        if (isAuto) {
            key = seconds <= 3
                    ? "msg.itemcontrol.cleaner.cleaner.actionbar.auto.urgent"
                    : seconds <= 6
                            ? "msg.itemcontrol.cleaner.cleaner.actionbar.auto.yellow"
                            : "msg.itemcontrol.cleaner.cleaner.actionbar.auto.normal";
            message = KineticI18n.translatable(key, coloredNumber);
        } else {
            key = seconds <= 3
                    ? "msg.itemcontrol.cleaner.cleaner.actionbar.manual.urgent"
                    : seconds <= 6
                            ? "msg.itemcontrol.cleaner.cleaner.actionbar.manual.yellow"
                            : "msg.itemcontrol.cleaner.cleaner.actionbar.manual.normal";
            message = KineticI18n.translatable(key, initiator, coloredNumber);
        }
        server.getPlayerList().getPlayers().forEach(player -> KineticPlayerMessages.display(player, message, true));
    }

    private static void clearActionBar(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(player -> KineticPlayerMessages.display(player, Component.empty(), true));
    }

    private static void broadcastSystemMessage(MinecraftServer server) {
        Component message = KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.warning", KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.countdown.number.10"));
        server.getPlayerList().getPlayers().forEach(player -> KineticPlayerMessages.system(player, message));
    }

    private static void broadcastResult(MinecraftServer server, CleanResult result) {
        if (result.itemCount <= 0 && result.entityCount <= 0) return;

        Component resultLine;
        if (result.itemCount > 0 && result.entityCount > 0) {
            resultLine = KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.result.both", result.itemCount, result.entityCount);
        } else if (result.itemCount > 0) {
            resultLine = KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.result.items", result.itemCount);
        } else {
            resultLine = KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.result.entities", result.entityCount);
        }

        net.minecraft.network.chat.MutableComponent message = KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.result.header").copy()
                .append(resultLine);
        if (CleanerConfig.enableTrashBin && result.itemCount > 0) {
            Component link = KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.cleaner.link").copy()
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kt clean bin"))
                            .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    KineticI18n.translatable("msg.itemcontrol.cleaner.cleaner.cleaner.hover")
                            )));
            message.append("\n").append(link);
        }
        server.getPlayerList().getPlayers().forEach(player -> KineticPlayerMessages.system(player, message));
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
                    ResourceLocation id = KineticRegistries.entityTypes().id(entity.getType());
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
