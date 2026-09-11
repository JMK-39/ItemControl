package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CleanerModule.MODID, value = Dist.CLIENT)
public class CleanerAreaTooltips {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!CleanerConfig.enableProtectedAreas || !CleanerClientConfigState.isProtectedAreaTool(event.getItemStack())) {
            return;
        }
        Component actionKey = CleanerAreaKeyBindings.AREA_ACTION_KEY.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GOLD);
        event.getToolTip().add(Component.translatable("tip.itemcontrol.cleaner.cleaner.area_tool.line1"));
        event.getToolTip().add(Component.translatable("tip.itemcontrol.cleaner.cleaner.area_tool.line2"));
        event.getToolTip().add(Component.translatable("tip.itemcontrol.cleaner.cleaner.area_tool.line3", actionKey));
        event.getToolTip().add(Component.translatable("tip.itemcontrol.cleaner.cleaner.area_tool.line4", actionKey));
        event.getToolTip().add(Component.translatable("tip.itemcontrol.cleaner.cleaner.area_tool.line5", actionKey));
    }
}
