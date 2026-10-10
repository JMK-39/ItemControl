package dev.xyat.itemcontrol.item.compat;

import dev.xyat.itemcontrol.item.config.ItemCurioSettings;
import dev.xyat.itemcontrol.item.property.ItemPropertyOverrides;
import dev.xyat.itemcontrol.item.property.ItemEquipmentRefresh;
import dev.xyat.kineticcore.api.event.KineticExternalEvents;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** The nested adapter is loaded only with Curios installed; no replacement item capabilities. */
public final class ItemCuriosCompat {
    private ItemCuriosCompat() {}
    public static boolean available() { return KineticPlatform.isModLoaded("curios"); }
    public static void register() { if (available()) Loaded.register(); }
    public static List<String> slots(LivingEntity wearer) {
        return available() && wearer != null ? Loaded.slots(wearer) : List.of();
    }
    public static void refreshSlotModifiers(LivingEntity wearer,List<ItemEquipmentRefresh.ModifierEntry> previous,List<ItemEquipmentRefresh.ModifierEntry> current){
        if(available())Loaded.refreshSlotModifiers(wearer,previous,current);
    }
    public static List<String> originalSlots(ItemStack stack, LivingEntity wearer) {
        return available() && wearer != null && !stack.isEmpty()
                ? dev.xyat.itemcontrol.item.config.ItemPropertyConfig.previewOriginal(() -> Loaded.originalSlots(stack, wearer)) : List.of();
    }
    public static void collect(LivingEntity wearer, List<ItemEquipmentRefresh.ModifierEntry> target) {
        if (available()) Loaded.collect(wearer, target);
    }
    public record AttributePreview(String attribute, String operation, double amount) {}
    public static List<AttributePreview> originalAttributes(ItemStack stack, LivingEntity wearer) {
        return available() && wearer != null ? dev.xyat.itemcontrol.item.config.ItemPropertyConfig.previewOriginal(() -> Loaded.preview(stack, wearer)) : List.of();
    }
    private static ItemCurioSettings settings(ItemStack stack) {
        var rule = ItemPropertyOverrides.active(stack);
        return rule == null ? null : rule.curio();
    }

    private static final class Loaded {
        private static List<String> originalSlots(ItemStack stack, LivingEntity wearer) {
            return top.theillusivec4.curios.api.CuriosApi.getItemStackSlots(stack, wearer).keySet().stream().sorted().toList();
        }
        private static List<AttributePreview> preview(ItemStack stack, LivingEntity wearer) {
            var names = top.theillusivec4.curios.api.CuriosApi.getItemStackSlots(stack, wearer).keySet().stream().sorted().toList();
            if (names.isEmpty()) names = slots(wearer);
            var context = new top.theillusivec4.curios.api.SlotContext(names.isEmpty() ? "curio" : names.get(0), wearer, 0, false, true);
            List<AttributePreview> result = new java.util.ArrayList<>();
            //? if >=1.21 {
            /*var modifiers = top.theillusivec4.curios.api.CuriosApi.getAttributeModifiers(context,
                    top.theillusivec4.curios.api.CuriosApi.getSlotId(context), stack);
            modifiers.forEach((attribute, modifier) -> addPreview(result, attribute.value(), modifier.operation().name(), modifier.amount()));
            *///?} else {
            var modifiers = top.theillusivec4.curios.api.CuriosApi.getAttributeModifiers(context, top.theillusivec4.curios.api.CuriosApi.getSlotUuid(context), stack);
            modifiers.forEach((attribute, modifier) -> addPreview(result, attribute, modifier.getOperation().name(), modifier.getAmount()));
            //?}
            return List.copyOf(result);
        }
        private static void addPreview(List<AttributePreview> result, net.minecraft.world.entity.ai.attributes.Attribute attribute, String operation, double amount) {
            var id = dev.xyat.kineticcore.api.registry.KineticRegistries.attributes().id(attribute);
            // Curios' synthetic slot-count attributes are not ordinary registered living-entity attributes.
            if (id != null) result.add(new AttributePreview(id.toString(), operation, amount));
        }
        private static List<String> slots(LivingEntity wearer) {
            return top.theillusivec4.curios.api.CuriosApi.getSlots(wearer.level()).keySet().stream().sorted().toList();
        }
        private static void register() {
            //? if >=1.21 {
            /*KineticExternalEvents.subscribe(top.theillusivec4.curios.api.event.CurioCanEquipEvent.class, event -> {
                var rule = settings(event.getStack());
                if (rule == null || !rule.enabled() || !rule.overridesSlots()) return;
                //? if >=26.1 {
                event.setEquipResult(rule.allows(event.getSlotContext().identifier()));
                //?} else {
                /^event.setEquipResult(rule.allows(event.getSlotContext().identifier())
                        ? net.neoforged.neoforge.common.util.TriState.TRUE : net.neoforged.neoforge.common.util.TriState.FALSE);^/
                //?}
            });
            KineticExternalEvents.subscribe(top.theillusivec4.curios.api.event.CurioCanUnequipEvent.class, event -> {
                var rule = settings(event.getStack());
                if (rule == null || !rule.enabled() || !rule.overridesUnequip() || rule.overridesSlots() && !rule.allows(event.getSlotContext().identifier())) return;
                //? if >=26.1 {
                event.setUnequipResult(rule.canUnequip());
                //?} else {
                /^event.setUnequipResult(rule.canUnequip() ? net.neoforged.neoforge.common.util.TriState.TRUE
                        : net.neoforged.neoforge.common.util.TriState.FALSE);^/
                //?}
            });
            *///?} else {
            KineticExternalEvents.subscribe(top.theillusivec4.curios.api.event.CurioEquipEvent.class, event -> {
                var rule = settings(event.getStack());
                if (rule != null && rule.enabled() && rule.overridesSlots()) event.setResult(rule.allows(event.getSlotContext().identifier())
                        ? net.minecraftforge.eventbus.api.Event.Result.ALLOW : net.minecraftforge.eventbus.api.Event.Result.DENY);
            });
            KineticExternalEvents.subscribe(top.theillusivec4.curios.api.event.CurioUnequipEvent.class, event -> {
                var rule = settings(event.getStack());
                if (rule != null && rule.enabled() && rule.overridesUnequip() && (!rule.overridesSlots() || rule.allows(event.getSlotContext().identifier()))) event.setResult(rule.canUnequip()
                        ? net.minecraftforge.eventbus.api.Event.Result.ALLOW : net.minecraftforge.eventbus.api.Event.Result.DENY);
            });
            //?}
            KineticExternalEvents.subscribe(top.theillusivec4.curios.api.event.CurioAttributeModifierEvent.class,
                    dev.xyat.kineticcore.api.event.KineticEventPriority.LOWEST, false, Loaded::attributes);
        }

        private static void attributes(top.theillusivec4.curios.api.event.CurioAttributeModifierEvent event) {
            var rule = settings(event.getItemStack());
            if (rule == null || !rule.enabled()) return;
            //? if <26.1 {
            if (rule.overridesSlots() && !rule.allows(event.getSlotContext().identifier())) return;
            //?}
            for (int i = 0; i < rule.slotModifiers().size(); i++) {
                var row = rule.slotModifiers().get(i);
                //? if >=1.21 {
                /*com.google.common.collect.Multimap<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, AttributeModifier> modifiers = com.google.common.collect.HashMultimap.create();
                //? if >=26.1 {
                var id = KineticResourceIds.of("itemcontrol", "curio_slots/" + dev.xyat.kineticcore.api.registry.KineticRegistries.items().id(event.getItemStack().getItem()).getNamespace() + "/" + dev.xyat.kineticcore.api.registry.KineticRegistries.items().id(event.getItemStack().getItem()).getPath() + "/" + i);
                //?} else {
                /^var context = event.getSlotContext();
                var id = KineticResourceIds.of("itemcontrol", "curio_slots/" + context.identifier() + "/" + context.index() + "/" + i);^/
                //?}
                top.theillusivec4.curios.api.CuriosApi.addSlotModifier(modifiers, row.slot(), id, row.amount(), AttributeModifier.Operation.ADD_VALUE);
                //? if >=26.1 {
                modifiers.forEach((attribute, modifier) -> {
                    addScopedModifier(event, rule, attribute, modifier);
                });
                //?} else {
                /^modifiers.forEach(event::addModifier);^/
                //?}
                */
                //?} else {
                com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier> modifiers = com.google.common.collect.HashMultimap.create();
                var context = event.getSlotContext();
                UUID id = UUID.nameUUIDFromBytes(("itemcontrol/curio_slots/" + context.identifier() + "/" + context.index() + "/" + i).getBytes(StandardCharsets.UTF_8));
                top.theillusivec4.curios.api.CuriosApi.addSlotModifier(modifiers, row.slot(), id, row.amount(), AttributeModifier.Operation.ADDITION);
                modifiers.forEach(event::addModifier);
                //?}
            }
            // Remove originals first so multiple replacement rows can coexist for one attribute.
            for (var row : rule.attributes()) {
                if (row.mode().equals("add")) continue;
                var attribute = ItemPropertyOverrides.attribute(ResourceLocation.tryParse(row.attribute()));
                if (attribute == null) continue;
                //? if >=1.21 {
                /*event.removeAttribute(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));*/
                //?} else {
                event.removeAttribute(attribute);
                //?}
            }
            for (int i = 0; i < rule.attributes().size(); i++) {
                var row = rule.attributes().get(i);
                if (row.mode().equals("remove")) continue;
                var attribute = ItemPropertyOverrides.attribute(ResourceLocation.tryParse(row.attribute()));
                var operation = ItemPropertyOverrides.parseOperation(row.operation());
                if (attribute == null || operation == null) continue;
                //? if >=26.1 {
                /*var modifier = new AttributeModifier(KineticResourceIds.of("itemcontrol", "curio/" + i), row.amount(), operation);
                var holder = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute);
                addScopedModifier(event, rule, holder, modifier);
                *///?} else if >=1.21 {
                /*var context = event.getSlotContext();
                event.addModifier(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute),
                        new AttributeModifier(KineticResourceIds.of("itemcontrol", "curio/" + context.identifier() + "/" + context.index() + "/" + i), row.amount(), operation));
                *///?} else {
                var context = event.getSlotContext();
                UUID id = UUID.nameUUIDFromBytes(("itemcontrol/curio/" + context.identifier() + "/" + context.index() + "/" + i).getBytes(StandardCharsets.UTF_8));
                event.addModifier(attribute, new AttributeModifier(id, "ItemControl accessory", row.amount(), operation));
                //?}
            }
        }

        //? if >=26.1 {
        /*private static void addScopedModifier(top.theillusivec4.curios.api.event.CurioAttributeModifierEvent event, ItemCurioSettings rule,
                net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, AttributeModifier modifier) {
            if (!rule.overridesSlots() || rule.slots().contains("curio")) event.addModifier(attribute, modifier);
            else for (String slot : rule.slots()) {
                // Curios 15's list predicate requires every ID to match. Use a separate entry per permitted slot.
                event.addModifier(attribute, new AttributeModifier(modifier.id().withSuffix("/" + slot), modifier.amount(), modifier.operation()), slot);
            }
        }*/
        //?}

        private static void refreshSlotModifiers(LivingEntity wearer,List<ItemEquipmentRefresh.ModifierEntry> previous,List<ItemEquipmentRefresh.ModifierEntry> current){
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(wearer).ifPresent(inventory->{
                com.google.common.collect.Multimap<String,AttributeModifier> removed=com.google.common.collect.HashMultimap.create(),added=com.google.common.collect.HashMultimap.create();
                for(var row:previous)if(row.attribute() instanceof top.theillusivec4.curios.api.SlotAttribute slot && current.stream().noneMatch(next->sameSlotModifier(row,next)))removed.put(slot.getIdentifier(),row.modifier());
                for(var row:current)if(row.attribute() instanceof top.theillusivec4.curios.api.SlotAttribute slot && previous.stream().noneMatch(before->sameSlotModifier(before,row)))added.put(slot.getIdentifier(),row.modifier());
                if(!removed.isEmpty())inventory.removeSlotModifiers(removed);
                if(!added.isEmpty())inventory.addTransientSlotModifiers(added);
            });
        }
        private static boolean sameSlotModifier(ItemEquipmentRefresh.ModifierEntry left,ItemEquipmentRefresh.ModifierEntry right){
            if(!(left.attribute() instanceof top.theillusivec4.curios.api.SlotAttribute first)||!(right.attribute() instanceof top.theillusivec4.curios.api.SlotAttribute second)||!first.getIdentifier().equals(second.getIdentifier()))return false;
            //? if >=1.21 {
            /*return left.modifier().id().equals(right.modifier().id()) && left.modifier().amount()==right.modifier().amount() && left.modifier().operation()==right.modifier().operation();*/
            //?} else {
            return left.modifier().getId().equals(right.modifier().getId()) && left.modifier().getAmount()==right.modifier().getAmount() && left.modifier().getOperation()==right.modifier().getOperation();
            //?}
        }
        private static void collect(LivingEntity wearer, List<ItemEquipmentRefresh.ModifierEntry> target) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(wearer).ifPresent(inventory -> {
                inventory.getCurios().forEach((name, handler) -> {
                    for (int index = 0; index < handler.getStacks().getSlots(); index++) {
                        //? if >=1.21 {
                        /*if (!inventory.isSlotActive(name, index)) continue;*/
                        //?}
                        ItemStack stack = handler.getStacks().getStackInSlot(index);
                        if (stack.isEmpty()) continue;
                        var context = new top.theillusivec4.curios.api.SlotContext(name, wearer, index, false, handler.getRenders().get(index));
                        //? if >=1.21 {
                        /*var modifiers = top.theillusivec4.curios.api.CuriosApi.getAttributeModifiers(context,
                                top.theillusivec4.curios.api.CuriosApi.getSlotId(context), stack);
                        modifiers.forEach((attribute, modifier) -> target.add(new ItemEquipmentRefresh.ModifierEntry(attribute.value(), modifier)));
                        *///?} else {
                        var id = top.theillusivec4.curios.api.CuriosApi.getSlotUuid(context);
                        var modifiers = top.theillusivec4.curios.api.CuriosApi.getAttributeModifiers(context, id, stack);
                        modifiers.forEach((attribute, modifier) -> target.add(new ItemEquipmentRefresh.ModifierEntry(attribute, modifier)));
                        //?}
                    }
                });
            });
        }
    }
}
