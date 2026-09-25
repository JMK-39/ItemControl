package dev.xyat.itemcontrol.item.property;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;

/** Forge event adapters for per-item combat and mining overrides. */
public final class ItemPropertyEventHandler {
    private static boolean registered;

    private ItemPropertyEventHandler() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(ItemPropertyEventHandler::onAttributeModifiers);
        MinecraftForge.EVENT_BUS.addListener(ItemPropertyEventHandler::onItemUseFinish);
    }

    private static void onAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemPropertyOverrides.applyAttributeOverrides(event);
    }

    private static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        var rule = ItemPropertyOverrides.active(event.getItem());
        if (rule != null && Boolean.TRUE.equals(rule.nonConsumable()) && event.getItem().isEdible()) {
            event.setResultStack(event.getItem().copy());
        }
    }
}
