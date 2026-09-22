package dev.xyat.itemcontrol.item.event;

import dev.xyat.itemcontrol.item.InitItems;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.world.event.KineticWorldEvents;

public final class VoidItemEventHandler {
    private static boolean registered;

    private VoidItemEventHandler() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        KineticWorldEvents.onItemPickup(KineticEventPriority.NORMAL, context -> {
            if (context.stack().getItem() == InitItems.VOID_PLACEHOLDER.get() && !context.player().isCreative()) {
                context.cancel();
                context.item().discard();
            }
        });
    }
}
