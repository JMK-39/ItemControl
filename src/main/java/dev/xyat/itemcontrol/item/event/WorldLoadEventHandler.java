package dev.xyat.itemcontrol.item.event;

import dev.xyat.itemcontrol.item.ItemModule;
import dev.xyat.itemcontrol.item.util.ItemBanControl;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ItemModule.MODID)
public class WorldLoadEventHandler {

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!ItemBanControl.isReplacementEnabled()) {
            ItemBanControl.setReplacementEnabled(true);
        }
    }
}
