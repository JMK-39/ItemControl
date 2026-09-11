package dev.xyat.itemcontrol.cleaner.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.CleanerCommand;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.itemcontrol.cleaner.event.AutoCleanerEventHandler;
import dev.xyat.kineticcore.command.CommandUtils;
import dev.xyat.kineticcore.command.KTCommandApi;
import dev.xyat.kineticcore.command.KTCommandExtension;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class CleanerCommandExtension implements KTCommandExtension {
    private CleanerCommandExtension() {
    }

    public static void install() {
        KTCommandApi.register(CleanerModule.MODID, new CleanerCommandExtension());
    }

    @Override
    public void registerCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        CleanerCommand.register(root);
    }

    @Override
    public void appendHelpItems(CommandSourceStack source, List<MutableComponent> items) {
        items.add(CommandUtils.createExecutableCommand(
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
