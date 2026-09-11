package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.client.gui.CleanerScreen;
import dev.xyat.itemcontrol.cleaner.CleanerInit;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CleanerModule.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CleanerSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 告诉 Minecraft：当收到 TRASH_BIN 的菜单数据时，使用 CleanerScreen 来渲染界面
            MenuScreens.register(CleanerInit.TRASH_BIN.get(), CleanerScreen::new);

        });
    }
}
