package dev.xyat.itemcontrol.cleaner;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.CommandDispatcher;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.itemcontrol.cleaner.event.AutoCleanerEventHandler;
import dev.xyat.itemcontrol.cleaner.client.gui.CleanerMenu;
import dev.xyat.kineticcore.api.menu.KineticMenus;
import dev.xyat.kineticcore.api.command.CommandText;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public class CleanerCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> clean = Commands.literal("clean");

        clean.then(Commands.literal("help").executes(ctx -> sendHelp(ctx.getSource())));

        clean.then(Commands.literal("bin")
                .executes(ctx -> openTrashBin(ctx.getSource(), 1))
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .executes(ctx -> openTrashBin(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index")))
                ));

        clean.then(Commands.literal("trash")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> clearTrashBin(ctx.getSource())));

        clean.then(Commands.literal("auto")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> setAutoClean(ctx, BoolArgumentType.getBool(ctx, "enabled")))));

        clean.then(Commands.literal("toggle")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> setHardToggle(ctx, BoolArgumentType.getBool(ctx, "enabled")))));

        clean.executes(ctx -> sendHelp(ctx.getSource()));
        root.then(clean);
        root.then(createDelCommand());
    }

    /** The original /del bin command and its /kt del bin alias share the same implementation. */
    public static void registerTopLevelDel(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(createDelCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createDelCommand() {
        return Commands.literal("del")
                .then(Commands.literal("bin")
                        .executes(ctx -> openTrashBin(ctx.getSource(), 1))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> openTrashBin(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index")))));
    }

    private static int sendHelp(CommandSourceStack source) {
        MutableComponent msg = CommandText.header("cmd.itemcontrol.cleaner.clean.desc").append("\n");
        msg.append(CommandText.executable("/kt clean bin", "cmd.itemcontrol.cleaner.clean.bin.desc"));
        msg.append("\n").append(CommandText.executable("/del bin", "cmd.itemcontrol.cleaner.clean.bin.desc"));

        if (source.hasPermission(2)) {
            msg.append("\n").append(CommandText.executable("/kt clean trash", "cmd.itemcontrol.cleaner.clean.trash.desc"));
            msg.append("\n").append(CommandText.createSuggestCommand("/kt clean auto <true/false>", "/kt clean auto ", "cmd.itemcontrol.cleaner.clean.auto.desc"));
            msg.append("\n").append(CommandText.createSuggestCommand("/kt clean toggle <true/false>", "/kt clean toggle ", "cmd.itemcontrol.cleaner.clean.toggle.desc"));
        }

        source.sendSuccess(() -> msg, false);
        return 1;
    }

    public static int openTrashBin(CommandSourceStack source, int userIndex) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            CleanerSavedData data = CleanerSavedData.get(source.getLevel());
            int internalIndex = Math.max(0, userIndex - 1);

            if (internalIndex >= data.getHistorySize() && internalIndex != 0) {
                source.sendFailure(Component.translatable("cmd.itemcontrol.cleaner.clean.no_history"));
                return 0;
            }

            SimpleContainer storage = data.getRecord(internalIndex);
            KineticMenus.open(player,
                    Component.translatable("gui.itemcontrol.cleaner.cleaner.title"),
                    (id, inv, menuPlayer) -> new CleanerMenu(id, inv, storage));
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("cmd.itemcontrol.cleaner.clean.open_failed",
                    Component.literal(String.valueOf(e.getMessage()))));
            return 0;
        }
    }

    private static int clearTrashBin(CommandSourceStack source) {
        CleanerSavedData.get(source.getLevel()).clearAll();
        source.sendSuccess(() -> Component.translatable("cmd.itemcontrol.cleaner.bin.cleared").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setAutoClean(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        if (CleanerConfig.enableCleaner == enabled) {
            String key = enabled
                    ? "cmd.itemcontrol.cleaner.auto.already_enabled"
                    : "cmd.itemcontrol.cleaner.auto.already_disabled";
            ctx.getSource().sendSuccess(() -> Component.translatable(key), false);
            return 1;
        }

        CleanerConfig.enableCleaner = enabled;
        CleanerConfig.saveServerSettings();
        AutoCleanerEventHandler.resetTimer();
        Component status = Component.translatable(enabled ? "cmd.itemcontrol.cleaner.status.on" : "cmd.itemcontrol.cleaner.status.off")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        ctx.getSource().sendSuccess(() -> Component.translatable("cmd.itemcontrol.cleaner.auto.status", status), true);
        return 1;
    }

    private static int setHardToggle(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        boolean currentlyEnabled = !CleanerConfig.isCleanerHardDisabled;
        if (currentlyEnabled == enabled) {
            String key = enabled
                    ? "cmd.itemcontrol.cleaner.toggle.already_enabled"
                    : "cmd.itemcontrol.cleaner.toggle.already_disabled";
            ctx.getSource().sendSuccess(() -> Component.translatable(key), false);
            return 1;
        }

        CleanerConfig.isCleanerHardDisabled = !enabled;
        CleanerConfig.saveServerSettings();
        AutoCleanerEventHandler.resetTimer();
        String key = enabled ? "cmd.itemcontrol.cleaner.toggle.enabled" : "cmd.itemcontrol.cleaner.toggle.disabled";
        ctx.getSource().sendSuccess(() -> Component.translatable(key), true);
        return 1;
    }
}
