package dev.xyat.itemcontrol.item.event;

import dev.xyat.itemcontrol.item.InitItems;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
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
        // Inventories saved before an item was banned are cleaned when the player joins.
        KineticServerEvents.onPlayerLogin(KineticEventPriority.NORMAL, player -> {
            if (!BanItemConfig.hasBanRules()) return;
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (BanItemConfig.isBanned(inventory.getItem(i))) inventory.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
        });
    }
}
