package dev.xyat.itemcontrol.item.data;

import net.minecraft.world.item.ItemStack;

/** Uses the item data syntax native to each Minecraft version. */
public final class ItemData {
    private ItemData() {}

    public static String emptyData() {
        //? if >=1.21 {
        /*return "[]";
        *///?} else {
        return "{}";
        //?}
    }

    public static String format(ItemStack stack) {
        //? if >=1.21 {
        /*if (stack.isEmpty()) return emptyData();
        String text = dev.xyat.kineticcore.api.inventory.KineticItemText.format(stack);
        int start = text.indexOf('[');
        return start < 0 ? emptyData() : text.substring(start);
        *///?} else {
        return stack.hasTag() && stack.getTag() != null ? stack.getTag().toString() : emptyData();
        //?}
    }

    //? if >=1.21 {
    /*public static String formatStack(ItemStack stack) {
        return dev.xyat.kineticcore.api.inventory.KineticItemText.format(stack);
    }

    public static ItemStack parse(String text) {
        if (text == null || text.isBlank()) return ItemStack.EMPTY;
        int start = text.indexOf('[');
        String id = start < 0 ? text.trim() : text.substring(0, start).trim();
        try { return compile(id, start < 0 ? "[]" : text.substring(start)).display(); }
        catch (RuntimeException invalid) { return ItemStack.EMPTY; }
    }

    public static boolean matches(String text, ItemStack actual) {
        int start = text.indexOf('[');
        if (start < 0) return false;
        try { return compile(text.substring(0, start), text.substring(start)).matches(actual, false); }
        catch (RuntimeException invalid) { return false; }
    }

    public record Rule(net.minecraft.core.component.DataComponentPatch patch, ItemStack display) {
        public boolean matches(ItemStack actual, boolean strong) {
            if (strong) return display.getComponentsPatch().equals(actual.getComponentsPatch());
            for (var entry : patch.entrySet()) {
                Object value = actual.get(entry.getKey());
                if (entry.getValue().isEmpty()) {
                    if (value != null) return false;
                } else if (entry.getValue().get() instanceof net.minecraft.world.item.component.CustomData expected) {
                    if (!(value instanceof net.minecraft.world.item.component.CustomData found)
                            || !net.minecraft.nbt.NbtUtils.compareNbt(expected.copyTag(), found.copyTag(), true)) return false;
                } else if (!entry.getValue().get().equals(value)) return false;
            }
            return true;
        }
    }

    public static Rule compile(String id, String data) {
        String components = data == null || data.isBlank() ? "[]" : data.trim();
        if (!components.startsWith("[")) throw new IllegalArgumentException("Expected [components]");
        var reader = new com.mojang.brigadier.StringReader(id + components);
        try {
            var parsed = new net.minecraft.commands.arguments.item.ItemParser(registries()).parse(reader);
            if (reader.canRead()) throw new IllegalArgumentException("Trailing item constraint text");
            final Rule[] result = { null };
            dev.xyat.itemcontrol.item.util.ItemBanControl.withSkip(() -> {
                result[0] = new Rule(parsed.components(), new ItemStack(parsed.item(), 1, parsed.components()));
                return null;
            });
            return result[0];
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw new IllegalArgumentException(invalid.getMessage(), invalid);
        }
    }

    // Dynamic components (for example enchantments) need a world registry lookup.
    // Keep raw constraints during startup; matching validates them once that lookup exists.
    public static boolean hasWorldContext() {
        return registryContext() != net.minecraft.core.registries.BuiltInRegistries.REGISTRY;
    }

    public static boolean validConstraint(String id, String data) {
        if (data == null || !data.startsWith("[") || !data.endsWith("]")) return false;
        if (!hasWorldContext()) {
            var location = net.minecraft.resources.ResourceLocation.tryParse(id);
            // Mod item registries may not be populated yet during addon construction.
            return location != null;
        }
        try { compile(id, data); return true; }
        catch (RuntimeException invalid) { return false; }
    }

    public static Object registryContext() {
        var server = dev.xyat.kineticcore.api.runtime.KineticServerRuntime.currentServer();
        if (server != null) return server.registryAccess();
        var lookup = dev.xyat.kineticcore.api.runtime.KineticPlatform.callOnClient(() -> () -> {
            var level = dev.xyat.kineticcore.api.runtime.KineticClientRuntime.currentLevel();
            return level == null ? null : level.registryAccess();
        }, null);
        return lookup != null ? lookup : net.minecraft.core.registries.BuiltInRegistries.REGISTRY;
    }

    private static net.minecraft.core.HolderLookup.Provider registries() {
        Object context = registryContext();
        return context instanceof net.minecraft.core.HolderLookup.Provider lookup ? lookup
                : net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
    }
    *///?}
}