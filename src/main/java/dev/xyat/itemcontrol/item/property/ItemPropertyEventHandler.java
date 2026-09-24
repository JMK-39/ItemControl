package dev.xyat.itemcontrol.item.property;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemAttributeModifierEvent;

/** Forge event adapters for per-item combat and mining overrides. */
public final class ItemPropertyEventHandler {
    private static boolean registered;

    private ItemPropertyEventHandler() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(ItemPropertyEventHandler::onAttributeModifiers);
    }

    private static void onAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemPropertyOverrides.applyAttributeOverrides(event);
    }
}
