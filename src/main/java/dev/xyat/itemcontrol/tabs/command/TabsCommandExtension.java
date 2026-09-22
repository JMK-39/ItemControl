package dev.xyat.itemcontrol.tabs.command;

import dev.xyat.kineticcore.api.command.KineticCommands;
import dev.xyat.kineticcore.api.command.CommandExtension;
import dev.xyat.itemcontrol.tabs.TabsModule;
import dev.xyat.itemcontrol.tabs.TabConfig;
import net.minecraft.commands.CommandSourceStack;

public final class TabsCommandExtension implements CommandExtension {
    private TabsCommandExtension() {}

    public static void install() {
        KineticCommands.registerExtension(TabsModule.MODID, new TabsCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        TabConfig.load();
    }
}
