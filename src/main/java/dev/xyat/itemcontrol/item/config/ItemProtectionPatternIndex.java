package dev.xyat.itemcontrol.item.config;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Startup-only protection lookup for item NBT, item tags, and mod namespaces. */
final class ItemProtectionPatternIndex {
    private final Map<ResourceLocation, List<Entry>> nbtRules;
    private final List<Entry> scopeRules;

    private ItemProtectionPatternIndex(Map<ResourceLocation, List<Entry>> nbtRules, List<Entry> scopeRules) {
        this.nbtRules = nbtRules;
        this.scopeRules = scopeRules;
    }

    static ItemProtectionPatternIndex empty() {
        return new ItemProtectionPatternIndex(Map.of(), List.of());
    }

    static boolean isPatternKey(String key) {
        return key != null && (key.startsWith("@") || key.startsWith("#") || key.contains("{"));
    }

    static boolean isValidPatternKey(String key) {
        return parse(key, null) != null;
    }

    static ItemProtectionPatternIndex build(Map<String, ItemPropertyRule> rules) {
        Map<ResourceLocation, List<Entry>> nbt = new LinkedHashMap<>();
        List<Entry> scopes = new ArrayList<>();
        rules.forEach((key, rule) -> {
            if (rule == null || !rule.hasProtectionFields()) return;
            Entry entry = parse(key, rule);
            if (entry == null) return;
            if (entry.nbt != null) nbt.computeIfAbsent(entry.itemId, ignored -> new ArrayList<>()).add(entry);
            else scopes.add(entry);
        });
        Map<ResourceLocation, List<Entry>> immutableNbt = new LinkedHashMap<>();
        nbt.forEach((id, entries) -> immutableNbt.put(id, List.copyOf(entries)));
        return new ItemProtectionPatternIndex(Map.copyOf(immutableNbt), List.copyOf(scopes));
    }

    ItemPropertyRule matchingNbt(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return null;
        for (Entry entry : nbtRules.getOrDefault(id, List.of())) {
            if (stack.hasTag() && NbtUtils.compareNbt(entry.nbt, stack.getTag(), true)) return entry.rule;
        }
        return null;
    }

    ItemPropertyRule matchingScope(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return null;
        for (Entry entry : scopeRules) {
            if (entry.namespace != null && id.getNamespace().equals(entry.namespace)
                    || entry.tag != null && stack.is(entry.tag)) return entry.rule;
        }
        return null;
    }

    private static Entry parse(String key, ItemPropertyRule rule) {
        if (!isPatternKey(key)) return null;
        if (key.startsWith("@")) {
            String namespace = key.substring(1);
            return namespace.matches("[a-z0-9_.-]+") ? new Entry(null, null, namespace, null, rule) : null;
        }
        if (key.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(key.substring(1));
            return id == null ? null : new Entry(null, TagKey.create(Registries.ITEM, id), null, null, rule);
        }
        int nbtStart = key.indexOf('{');
        ResourceLocation itemId = ResourceLocation.tryParse(key.substring(0, nbtStart));
        if (itemId == null) return null;
        try {
            return new Entry(itemId, null, null, TagParser.parseTag(key.substring(nbtStart)), rule);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record Entry(ResourceLocation itemId, TagKey<Item> tag, String namespace,
                         CompoundTag nbt, ItemPropertyRule rule) {
    }
}
