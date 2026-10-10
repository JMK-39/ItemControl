package dev.xyat.itemcontrol.item.property;

import dev.xyat.itemcontrol.item.compat.ItemCuriosCompat;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Refreshes equipped attribute caches once, on the server thread, after a successful save. */
public final class ItemEquipmentRefresh {
    private ItemEquipmentRefresh() {}
    public record ModifierEntry(Attribute attribute, AttributeModifier modifier) {}

    public static void applyPending(MinecraftServer server) {
        Map<LivingEntity, List<ModifierEntry>> previous = new IdentityHashMap<>();
        for (var level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living) previous.put(living, collect(living));
        }
        ItemPropertyConfig.activatePendingSnapshot();
        ItemPropertyOverrides.applyBlockOverrides();
        previous.forEach((entity, old) -> refresh(entity, old));
    }

    public static List<ModifierEntry> collect(LivingEntity entity) {
        List<ModifierEntry> result = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            //? if >=1.21 {
            /*stack.forEachModifier(slot, (attribute, modifier) -> result.add(new ModifierEntry(attribute.value(), modifier)));
            *///?} else {
            stack.getAttributeModifiers(slot).forEach((attribute, modifier) -> result.add(new ModifierEntry(attribute, modifier)));
            //?}
        }
        ItemCuriosCompat.collect(entity, result);
        return result;
    }

    public static void refresh(LivingEntity entity, List<ModifierEntry> previous) {
        List<ModifierEntry> current=collect(entity);
        ItemCuriosCompat.refreshSlotModifiers(entity,previous,current);
        for (var entry : previous) {
            //? if >=1.21 {
            /*var instance = entity.getAttribute(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(entry.attribute()));
            if (instance != null) instance.removeModifier(entry.modifier().id());
            *///?} else {
            var instance = entity.getAttribute(entry.attribute());
            if (instance != null) instance.removeModifier(entry.modifier().getId());
            //?}
        }
        for (var entry : current) {
            //? if >=1.21 {
            /*var instance = entity.getAttribute(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(entry.attribute()));
            if (instance != null && !instance.hasModifier(entry.modifier().id())) instance.addTransientModifier(entry.modifier());
            *///?} else {
            var instance = entity.getAttribute(entry.attribute());
            if (instance != null && !instance.hasModifier(entry.modifier())) instance.addTransientModifier(entry.modifier());
            //?}
        }
    }
}
