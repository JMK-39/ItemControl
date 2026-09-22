package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class CleanerAreaTooltips {
    private static boolean installed;

    private CleanerAreaTooltips() {
    }

    public static void install() {
        if (installed) return;
        installed = true;
        KineticClientEvents.onItemTooltip(context -> {
            if (!CleanerConfig.enableProtectedAreas || !CleanerClientConfigState.isProtectedAreaTool(context.itemStack())) {
                return;
            }
            Component actionKey = CleanerAreaKeyBindings.translatedKeyMessage().copy().withStyle(ChatFormatting.GOLD);
            context.tooltip().add(Component.translatable("tip.itemcontrol.cleaner.cleaner.area_tool.line1"));
            context.tooltip().add(Component.translatable("tip.itemcontrol.cleaner.cleaner.area_tool.line2"));
            context.tooltip().add(Component.translatable("tip.itemcontrol.cleaner.cleaner.area_tool.line3", actionKey));
            context.tooltip().add(Component.translatable("tip.itemcontrol.cleaner.cleaner.area_tool.line4", actionKey));
            context.tooltip().add(Component.translatable("tip.itemcontrol.cleaner.cleaner.area_tool.line5", actionKey));
        });
    }
}
