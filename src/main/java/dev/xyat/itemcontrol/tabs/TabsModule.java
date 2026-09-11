package dev.xyat.itemcontrol.tabs;

import com.mojang.logging.LogUtils;
import dev.xyat.itemcontrol.tabs.TabConfig;
import dev.xyat.itemcontrol.tabs.command.TabsCommandExtension;
import dev.xyat.itemcontrol.tabs.network.TabNetwork;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

public final class TabsModule {
    public static final String MODID = "itemcontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TabsModule(FMLJavaModLoadingContext context) {
        TabConfig.load();
        KTServerConfigApi.registerActionPage("itemcontrol:tabs");
        TabNetwork.register();
        TabsCommandExtension.install();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TabsModuleClientBootstrap.init(context));
    }
}
