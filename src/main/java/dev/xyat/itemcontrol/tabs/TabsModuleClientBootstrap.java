package dev.xyat.itemcontrol.tabs;

import dev.xyat.itemcontrol.tabs.TabModule;
import dev.xyat.itemcontrol.tabs.config.TabConfigGui;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@OnlyIn(Dist.CLIENT)
public final class TabsModuleClientBootstrap {
    private TabsModuleClientBootstrap() {}

    public static void init(FMLJavaModLoadingContext context) {
        TabModule.register(context.getModEventBus());
        TabConfigGui.load();
    }
}
