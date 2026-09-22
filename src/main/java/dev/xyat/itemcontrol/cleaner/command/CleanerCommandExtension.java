package dev.xyat.itemcontrol.cleaner.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.CleanerCommand;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.itemcontrol.cleaner.event.AutoCleanerEventHandler;
import dev.xyat.kineticcore.api.command.CommandText;
import dev.xyat.kineticcore.api.command.KineticCommands;
import dev.xyat.kineticcore.api.command.CommandExtension;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class CleanerCommandExtension implements CommandExtension {
    private CleanerCommandExtension() {
    }

    public static void install() {
        KineticCommands.registerExtension(CleanerModule.MODID, new CleanerCommandExtension());
        KineticCommands.registerTopLevel("itemcontrol:del", CleanerCommand::registerTopLevelDel);
    }

    @Override
    public void registerCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        CleanerCommand.register(root);
    }

    @Override
    public void appendHelpItems(CommandSourceStack source, List<MutableComponent> items) {
        items.add(CommandText.executable(
                "/kt clean",
                "cmd.itemcontrol.cleaner.clean.desc"
        ));
    }

    @Override
    public void reload(CommandSourceStack source) {
        CleanerConfig.load();
        AutoCleanerEventHandler.resetTimer();
    }
}
