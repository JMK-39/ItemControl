package dev.xyat.itemcontrol.cleaner;

import dev.xyat.itemcontrol.cleaner.CleanerModule;
import dev.xyat.itemcontrol.cleaner.area.CleanerAreaManager;
import dev.xyat.itemcontrol.cleaner.client.gui.CleanerMenu;
import dev.xyat.itemcontrol.cleaner.event.AutoCleanerEventHandler;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CleanerInit {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CleanerModule.MODID);

    public static final RegistryObject<MenuType<CleanerMenu>> TRASH_BIN =
            MENUS.register("trash_bin", () -> IForgeMenuType.create((windowId, inv, data) -> new CleanerMenu(windowId, inv)));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
        AutoCleanerEventHandler.register();
        CleanerAreaManager.register();
    }
}
