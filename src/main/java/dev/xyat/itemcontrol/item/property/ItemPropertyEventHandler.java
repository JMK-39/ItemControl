package dev.xyat.itemcontrol.item.property;

import dev.xyat.kineticcore.api.entity.event.KineticLivingEvents;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.event.KineticExternalEvents;
import dev.xyat.kineticcore.api.runtime.KineticModLifecycle;
import net.minecraftforge.event.ItemAttributeModifierEvent;

/** Forge event adapters for per-item combat and mining overrides. */
public final class ItemPropertyEventHandler {
    private static boolean registered;

    private ItemPropertyEventHandler() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        KineticExternalEvents.subscribe(ItemAttributeModifierEvent.class, ItemPropertyEventHandler::onAttributeModifiers);
        KineticLivingEvents.onUseItemFinish(KineticEventPriority.NORMAL, ItemPropertyEventHandler::onItemUseFinish);
        KineticModLifecycle.onCommonSetup(ItemPropertyOverrides::onCommonSetup);
    }

    private static void onAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemPropertyOverrides.applyAttributeOverrides(event);
    }

    private static void onItemUseFinish(KineticLivingEvents.UseItemFinishContext context) {
        var rule = ItemPropertyOverrides.active(context.item());
        if (rule != null && Boolean.TRUE.equals(rule.nonConsumable()) && context.item().isEdible()) {
            context.setResultStack(context.item().copy());
        }
    }
}
