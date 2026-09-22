package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.world.event.KineticWorldEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class ItemCacheHudRenderer {
    private static boolean installed;

    private ItemCacheHudRenderer() {
    }

    public static void install() {
        if (installed) return;
        installed = true;
        KineticWorldEvents.onEntityJoin(KineticEventPriority.NORMAL, context -> {
            var player = KineticClientRuntime.localPlayer();
            if (context.level().isClientSide() && player != null && context.entity() == player) {
                KineticItemSearch.clear();
                ItemSearchCache.clear();
            }
        });
    }

    public static Component getDisplayNameCustom(ItemStack stack) {
        if (stack.getItem() == net.minecraft.world.item.Items.ENCHANTED_BOOK) {
            try {
                List<Component> lines = stack.getTooltipLines(KineticClientRuntime.localPlayer(), TooltipFlag.Default.NORMAL);
                if (lines.size() > 1) return Component.translatable("gui.itemcontrol.item.common.tooltip_pair", lines.get(0), lines.get(1));
            } catch (Exception ignored) {
            }
        }
        return stack.getHoverName();
    }
}
