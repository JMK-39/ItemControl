package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfigGui;
import dev.xyat.kineticcore.api.config.client.KTServerConfigClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import dev.xyat.kineticcore.api.registry.KineticRegistries;

public final class CleanerClientConfigState {
    private CleanerClientConfigState() {
    }

    public static boolean isProtectedAreaTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String configured = KTServerConfigClient.getString(
                CleanerConfigGui.PAGE_ID,
                "protected_area_tool_item",
                CleanerConfig.protectedAreaToolItem
        );
        if (configured == null || configured.isBlank()) return false;
        ResourceLocation id = KineticRegistries.items().id(stack.getItem());
        return id != null && id.toString().equals(configured);
    }
}
