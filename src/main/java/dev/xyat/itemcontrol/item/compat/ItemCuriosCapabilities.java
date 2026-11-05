package dev.xyat.itemcontrol.item.compat;

import dev.xyat.itemcontrol.ItemControl;
import net.minecraft.world.item.ItemStack;

//? if >=26.1 {
/*@net.neoforged.fml.common.EventBusSubscriber(modid = ItemControl.MODID)*/
//?} else if neoforge {
/*@net.neoforged.fml.common.EventBusSubscriber(modid = ItemControl.MODID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)*/
//?} else {
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ItemControl.MODID)
//?}
public final class ItemCuriosCapabilities {
    private ItemCuriosCapabilities() {}

    //? if neoforge {
    /*@net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void register(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        if (ItemCuriosCompat.available()) Loaded.register(event);
    }*/
    //?} else {
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void attach(net.minecraftforge.event.AttachCapabilitiesEvent<ItemStack> event) {
        if (ItemCuriosCompat.available()) Loaded.attach(event);
    }
    //?}

    private static final class Loaded {
        //? if neoforge {
        /*private static void register(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
            // Curios and other mods register first. NeoForge tries this provider only when those return null.
            event.registerItem(top.theillusivec4.curios.api.CuriosCapability.ITEM,
                    (stack, context) -> ItemCuriosCompat.configuredAccessory(stack) ? new ConvertedCurio(stack) : null,
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().toArray(net.minecraft.world.item.Item[]::new));
        }*/
        //?} else {
        private static void attach(net.minecraftforge.event.AttachCapabilitiesEvent<ItemStack> event) {
            ItemStack stack = event.getObject();
            if (stack.isEmpty()) return;
            // Attach even before a rule exists. The provider reads the current snapshot on every query.
            var capability = net.minecraftforge.common.util.LazyOptional.<top.theillusivec4.curios.api.type.capability.ICurio>of(() -> new ConvertedCurio(stack));
            event.addCapability(dev.xyat.kineticcore.api.resource.KineticResourceIds.of("itemcontrol", "curio"),
                    new net.minecraftforge.common.capabilities.ICapabilityProvider() {
                        @Override
                        public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                                net.minecraftforge.common.capabilities.Capability<T> requested, net.minecraft.core.Direction side) {
                            return requested == top.theillusivec4.curios.api.CuriosCapability.ITEM && ItemCuriosCompat.configuredAccessory(stack)
                                    ? capability.cast() : net.minecraftforge.common.util.LazyOptional.empty();
                        }
                    });
            event.addListener(capability::invalidate);
        }
        //?}
    }

    private record ConvertedCurio(ItemStack stack) implements top.theillusivec4.curios.api.type.capability.ICurio {
        @Override public ItemStack getStack() { return stack; }
    }
}
