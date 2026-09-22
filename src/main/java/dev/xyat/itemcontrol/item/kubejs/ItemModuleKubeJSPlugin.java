package dev.xyat.itemcontrol.item.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.xyat.itemcontrol.item.event.ItemEntityDamageEvent;
import dev.xyat.itemcontrol.item.util.ItemProtectionList;
import dev.xyat.kineticcore.api.event.KineticExternalEvents;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.world.event.KineticWorldEvents;
import net.minecraft.world.entity.item.ItemEntity;

public final class ItemModuleKubeJSPlugin extends dev.latvian.mods.kubejs.KubeJSPlugin {
    public static final EventGroup GROUP = EventGroup.of("itemcontrolEvents");

    private static EventHandler itemHurt;
    private static EventHandler itemSpawn;
    private static EventHandler itemRemoved;

    @Override
    public void init() {
        KineticExternalEvents.subscribe(ItemEntityDamageEvent.class, this::onItemHurt);
        KineticWorldEvents.onEntityJoin(KineticEventPriority.NORMAL, context -> {
            if (context.entity() instanceof ItemEntity item
                    && itemSpawn != null
                    && itemSpawn.hasListeners()
                    && itemSpawn.post(new ItemEntityDamageEventJS(item)).override()) {
                context.cancel();
            }
        });
    }

    @Override
    public void registerEvents() {
        itemHurt = GROUP.server("itemHurt", () -> ItemEntityDamageEventJS.class).hasResult();
        itemSpawn = GROUP.server("itemSpawn", () -> ItemEntityDamageEventJS.class).hasResult();
        itemRemoved = GROUP.server("itemRemoved", () -> ItemEntityDamageEventJS.class).hasResult();
        GROUP.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("ItemProtection", ItemProtectionList.class);
    }

    private void onItemHurt(ItemEntityDamageEvent event) {
        if (itemHurt != null && itemHurt.hasListeners()) {
            if (itemHurt.post(new ItemEntityDamageEventJS(event)).override()) {
                event.setCanceled(true);
            }
        }
    }

    public static boolean postItemRemoved(ItemEntity entity) {
        return itemRemoved != null
                && itemRemoved.hasListeners()
                && itemRemoved.post(new ItemEntityDamageEventJS(entity)).override();
    }
}
