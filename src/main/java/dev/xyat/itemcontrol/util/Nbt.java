package dev.xyat.itemcontrol.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.UUID;

/**
 * NBT access that reads the same on every Minecraft version. 26.1 getters return Optional, UUIDs go through a codec
 * and item stacks are written with the item stack codec; the stored data keeps its layout.
 */
public final class Nbt {
    private Nbt() {
    }

    //? if >=26.1 {
    /*public static CompoundTag compound(ListTag list, int index) { return list.getCompoundOrEmpty(index); }
    public static int intValue(CompoundTag tag, String key) { return tag.getIntOr(key, 0); }
    public static boolean hasUuid(CompoundTag tag, String key) { return tag.read(key, net.minecraft.core.UUIDUtil.CODEC).isPresent(); }
    public static UUID uuid(CompoundTag tag, String key) { return tag.read(key, net.minecraft.core.UUIDUtil.CODEC).orElse(null); }
    public static void putUuid(CompoundTag tag, String key, UUID value) { tag.store(key, net.minecraft.core.UUIDUtil.CODEC, value); }
    *///?} else {
    public static CompoundTag compound(ListTag list, int index) { return list.getCompound(index); }
    public static int intValue(CompoundTag tag, String key) { return tag.getInt(key); }
    public static boolean hasUuid(CompoundTag tag, String key) { return tag.hasUUID(key); }
    public static UUID uuid(CompoundTag tag, String key) { return tag.hasUUID(key) ? tag.getUUID(key) : null; }
    public static void putUuid(CompoundTag tag, String key, UUID value) { tag.putUUID(key, value); }
    //?}

    //? if >=26.1 {
    /*public static CompoundTag saveItem(net.minecraft.world.item.ItemStack stack, net.minecraft.core.HolderLookup.Provider lookup) {
        return (CompoundTag) net.minecraft.world.item.ItemStack.CODEC.encodeStart(lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack).getOrThrow();
    }

    public static net.minecraft.world.item.ItemStack loadItem(CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookup) {
        return net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.parse(lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag)
                .result().orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }
    *///?} else if >=1.21 {
    /*public static CompoundTag saveItem(net.minecraft.world.item.ItemStack stack, net.minecraft.core.HolderLookup.Provider lookup) {
        return (CompoundTag) stack.save(lookup);
    }

    public static net.minecraft.world.item.ItemStack loadItem(CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookup) {
        return net.minecraft.world.item.ItemStack.parseOptional(lookup, tag);
    }
    *///?}
}
