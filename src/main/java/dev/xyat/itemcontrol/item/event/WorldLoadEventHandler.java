package dev.xyat.itemcontrol.item.event;

import dev.xyat.itemcontrol.item.util.ItemBanControl;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.world.event.KineticWorldEvents;

public final class WorldLoadEventHandler {
    private static boolean registered;

    private WorldLoadEventHandler() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        KineticWorldEvents.onLevelLoad(KineticEventPriority.NORMAL, level -> {
            if (level instanceof net.minecraft.server.level.ServerLevel) ItemBanControl.setServerThread(Thread.currentThread());
            if (!ItemBanControl.isReplacementEnabled()) {
                ItemBanControl.setReplacementEnabled(true);
            }
        });
    }
}
