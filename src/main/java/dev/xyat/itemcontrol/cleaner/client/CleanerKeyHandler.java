package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CleanerModule.MODID, value = Dist.CLIENT)
public class CleanerKeyHandler {

    private static int cooldown = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (cooldown > 0) {
            cooldown--;
        }

        while (CleanerKeyBindings.CLEANER_KEY.consumeClick()) {
            if (cooldown == 0 && Minecraft.getInstance().player != null) {
                CleanerNetwork.sendToServer(new CleanerNetwork.CleanerRequest());
                cooldown = 20; // 1秒冷却
            }
        }
    }
}
