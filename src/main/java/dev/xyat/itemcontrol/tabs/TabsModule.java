package dev.xyat.itemcontrol.tabs;

import com.mojang.logging.LogUtils;
import dev.xyat.itemcontrol.tabs.command.TabsCommandExtension;
import dev.xyat.itemcontrol.tabs.network.TabNetwork;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import org.slf4j.Logger;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;

public final class TabsModule {
    public static final String MODID = "itemcontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TabsModule() {
        TabConfig.load();
        KTServerConfigApi.registerActionPage("itemcontrol:tabs");
        TabNetwork.register();
        TabsCommandExtension.install();
        KineticPlatform.runOnClient(() -> TabsModuleClientBootstrap::init);
    }
}
