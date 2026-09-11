package dev.xyat.itemcontrol.tabs.command;

import dev.xyat.kineticcore.command.KTCommandApi;
import dev.xyat.kineticcore.command.KTCommandExtension;
import dev.xyat.itemcontrol.tabs.TabsModule;
import dev.xyat.itemcontrol.tabs.TabConfig;
import net.minecraft.commands.CommandSourceStack;

public final class TabsCommandExtension implements KTCommandExtension {
    private TabsCommandExtension() {}

    public static void install() {
        KTCommandApi.register(TabsModule.MODID, new TabsCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        TabConfig.load();
    }
}
