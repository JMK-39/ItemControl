package dev.xyat.itemcontrol.item.util;

import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes the configured item tag changes into the item tags as they load, so every tag check sees them (Curios slots,
 * recipes, enchanting): added tags gain the item, removed tags lose it, and "*" takes it out of every tag it was in.
 */
public final class ItemTagEdits {
    private ItemTagEdits() {
    }

    /** Returns the loaded tags with the edits applied; untouched when nothing is configured. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Map apply(Map tags) {
        BanItemConfig.Data data = BanItemConfig.data;
        if (tags == null || data == null || (data.addedItemTags.isEmpty() && data.removedItemTags.isEmpty())) return tags;
        Map result = new HashMap(tags);
        // Removals: the item leaves the named tags, or every tag for "*".
        for (Map.Entry<String, List<String>> entry : data.removedItemTags.entrySet()) {
            Item item = item(entry.getKey());
            if (item == null) continue;
            Set<String> removed = Set.copyOf(entry.getValue());
            boolean all = removed.contains(BanItemConfig.ALL_TAGS);
            for (Object key : List.copyOf(result.keySet())) {
                if (!all && !removed.contains(key.toString())) continue;
                Collection<Object> values = (Collection<Object>) result.get(key);
                if (values == null || values.stream().noneMatch(value -> isItem(value, item))) continue;
                List<Object> kept = new ArrayList<>(values);
                kept.removeIf(value -> isItem(value, item));
                result.put(key, kept);
            }
        }
        // Additions: the item joins the tag, which is created when no pack defines it.
        for (Map.Entry<String, List<String>> entry : data.addedItemTags.entrySet()) {
            Item item = item(entry.getKey());
            if (item == null) continue;
            Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(item);
            for (String tag : entry.getValue()) {
                Object key = KineticResourceIds.tryParse(tag);
                if (key == null) continue;
                Collection<Object> values = (Collection<Object>) result.get(key);
                if (values != null && values.stream().anyMatch(value -> isItem(value, item))) continue;
                List<Object> grown = values == null ? new ArrayList<>() : new ArrayList<>(values);
                grown.add(holder);
                result.put(key, grown);
            }
        }
        return result;
    }

    private static Item item(String id) {
        var location = KineticResourceIds.tryParse(id);
        return location == null ? null : KineticRegistries.items().get(location);
    }

    private static boolean isItem(Object value, Item item) {
        return value instanceof Holder<?> holder && holder.value() == item;
    }
}
