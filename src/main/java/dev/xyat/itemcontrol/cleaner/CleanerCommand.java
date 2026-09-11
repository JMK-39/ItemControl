package dev.xyat.itemcontrol.cleaner;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.itemcontrol.cleaner.event.AutoCleanerEventHandler;
import dev.xyat.itemcontrol.cleaner.client.gui.CleanerMenu;
import dev.xyat.kineticcore.command.CommandUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import javax.annotation.Nonnull;

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
    }

    private static int sendHelp(CommandSourceStack source) {
        MutableComponent msg = CommandUtils.createHeader("cmd.itemcontrol.cleaner.clean.desc").append("\n");
        msg.append(CommandUtils.createExecutableCommand("/kt clean bin", "cmd.itemcontrol.cleaner.clean.bin.desc"));

        if (source.hasPermission(2)) {
            msg.append("\n").append(CommandUtils.createExecutableCommand("/kt clean trash", "cmd.itemcontrol.cleaner.clean.trash.desc"));
            msg.append("\n").append(CommandUtils.createSuggestCommand("/kt clean auto <true/false>", "/kt clean auto ", "cmd.itemcontrol.cleaner.clean.auto.desc"));
            msg.append("\n").append(CommandUtils.createSuggestCommand("/kt clean toggle <true/false>", "/kt clean toggle ", "cmd.itemcontrol.cleaner.clean.toggle.desc"));
        }

        source.sendSuccess(() -> msg, false);
        return 1;
    }

    private static int openTrashBin(CommandSourceStack source, int userIndex) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            CleanerSavedData data = CleanerSavedData.get(source.getLevel());
            int internalIndex = Math.max(0, userIndex - 1);

            if (internalIndex >= data.getHistorySize() && internalIndex != 0) {
                source.sendFailure(Component.translatable("cmd.itemcontrol.cleaner.clean.no_history").withStyle(ChatFormatting.RED));
                return 0;
            }

            SimpleContainer storage = data.getRecord(internalIndex);
            player.openMenu(new MenuProvider() {
                @Override @Nonnull public Component getDisplayName() { return Component.translatable("gui.itemcontrol.cleaner.cleaner.title"); }
                @Override @Nonnull public AbstractContainerMenu createMenu(int id, @Nonnull Inventory inv, @Nonnull Player p) {
                    return new CleanerMenu(id, inv, storage);
                }
            });
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("cmd.itemcontrol.cleaner.clean.open_failed",
                    Component.literal(String.valueOf(e.getMessage())).withStyle(ChatFormatting.YELLOW)));
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
