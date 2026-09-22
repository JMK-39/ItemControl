package dev.xyat.itemcontrol.item.command;

import dev.xyat.kineticcore.api.command.KineticCommands;
import dev.xyat.kineticcore.api.command.CommandExtension;
import dev.xyat.itemcontrol.item.ItemModule;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.config.ItemProtectionConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import net.minecraft.commands.CommandSourceStack;

public final class ItemCommandExtension implements CommandExtension {
    private ItemCommandExtension() {
    }

    public static void install() {
        KineticCommands.registerExtension(ItemModule.MODID, new ItemCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        BanItemConfig.load();
        ItemProtectionConfig.load();
        ItemNetwork.syncServerConfigToAllPlayers();
    }
}
