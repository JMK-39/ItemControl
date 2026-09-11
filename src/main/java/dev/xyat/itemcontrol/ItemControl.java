package dev.xyat.itemcontrol;

import dev.xyat.itemcontrol.item.ItemModule;
import dev.xyat.itemcontrol.tabs.TabsModule;
import dev.xyat.itemcontrol.cleaner.CleanerModule;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ItemControl.MODID)
public final class ItemControl {
    public static final String MODID = "itemcontrol";

    public ItemControl(FMLJavaModLoadingContext context) {
        new ItemModule(context);
        new TabsModule(context);
        new CleanerModule(context);
    }
}
