package dev.xyat.itemcontrol.item;

import dev.xyat.kineticcore.api.registry.KineticItems;
import dev.xyat.kineticcore.api.registry.KineticRegistryHandle;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.world.item.Item;

public final class InitItems {
    public static final KineticRegistryHandle<Item> VOID_PLACEHOLDER = KineticItems.register(
            KineticResourceIds.of(ItemModule.MODID, "void_placeholder"),
            VoidPlaceholderItem::new
    );

    private InitItems() {
    }

    public static void register() {
    }
}
