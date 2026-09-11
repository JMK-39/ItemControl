package dev.xyat.itemcontrol.item.event;

import dev.xyat.itemcontrol.item.ItemModule;
import dev.xyat.itemcontrol.item.InitItems;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ItemModule.MODID)
public class VoidItemEventHandler {

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        // 核心逻辑 4：禁止生存模式拾取
        if (event.getItem().getItem().getItem() == InitItems.VOID_PLACEHOLDER.get()) {
            if (!event.getEntity().isCreative()) {
                event.setCanceled(true); // 取消拾取动作
                event.getItem().discard(); // 实体直接消失
            }
        }
    }
}
