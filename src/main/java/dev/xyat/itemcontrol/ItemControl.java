package dev.xyat.itemcontrol;

import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.item.ItemModule;
import dev.xyat.itemcontrol.tabs.TabsModule;
import net.minecraftforge.fml.common.Mod;

@Mod(ItemControl.MODID)
public final class ItemControl {
    public static final String MODID = "itemcontrol";

    public ItemControl() {
        new ItemModule();
        new TabsModule();
        new CleanerModule();
    }
}
