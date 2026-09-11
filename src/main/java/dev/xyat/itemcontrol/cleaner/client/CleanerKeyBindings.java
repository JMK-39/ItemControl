package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.itemcontrol.cleaner.CleanerModule;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = CleanerModule.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CleanerKeyBindings {

    public static final KeyMapping CLEANER_KEY = new KeyMapping(
            "key.itemcontrol.cleaner",
            GLFW.GLFW_KEY_DELETE,
            "key.itemcontrol.category"
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CLEANER_KEY);
    }
}
