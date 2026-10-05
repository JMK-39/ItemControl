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
        // 26.1 item components are built by default-component initializers each time the game binds them.
        //? if >=26.1 {
        /*net.neoforged.fml.ModList.get().getModContainerById("itemcontrol").ifPresent(container -> container.getEventBus().addListener(
                (net.neoforged.neoforge.event.ModifyDefaultComponentsEvent event) -> event.modifyMatching((item, components) -> true,
                        (components, context, item) -> ItemPropertyOverrides.applyDefaultComponents(item, components))));
        *///?}
    }

    private static void onAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemPropertyOverrides.applyAttributeOverrides(event);
    }

    private static void onItemUseFinish(KineticLivingEvents.UseItemFinishContext context) {
        var rule = ItemPropertyOverrides.active(context.item());
        if (rule != null && Boolean.TRUE.equals(rule.nonConsumable()) && ItemPropertyReads.isFood(context.item())) {

            context.setResultStack(context.item().copy());
        }
    }
}
