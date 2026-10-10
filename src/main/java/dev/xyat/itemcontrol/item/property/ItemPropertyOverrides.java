package dev.xyat.itemcontrol.item.property;

import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.itemcontrol.item.config.ItemPropertyRule;
import dev.xyat.itemcontrol.item.mixin.ItemPropertyMixins.BlockPropertyAccess;
import dev.xyat.kineticcore.api.minecraft.MinecraftAttributes;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runtime lookups and vanilla-facing adapters for the active per-item property snapshot. */
public final class ItemPropertyOverrides {
    // Keep the float passed through vanilla combat finite, including critical hits.
    private static final double INFINITE_ATTACK_DAMAGE = Float.MAX_VALUE / 16.0D;
    private static final Map<Block, Float> ORIGINAL_EXPLOSION_RESISTANCE = new IdentityHashMap<>();

    private ItemPropertyOverrides() {
    }

    public static ItemPropertyRule active(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : ItemPropertyConfig.active(stack.getItem());
    }

    public static ItemPropertyRule active(Block block) {
        if (block == null || block == Blocks.AIR) return null;
        return ItemPropertyConfig.active(block.asItem());
    }

    public static ItemPropertyRule active(BlockState state) {
        return state == null ? null : active(state.getBlock());
    }

    /** Applies block-level fields after registries are ready; these changes affect all block states. */
    public static void applyBlockOverrides() {
        for (Map.Entry<Block, Float> original : ORIGINAL_EXPLOSION_RESISTANCE.entrySet()) {
            if (original.getKey() instanceof BlockPropertyAccess access) {
                access.itemcontrol$setExplosionResistance(original.getValue());
            }
        }
        for (Map.Entry<ResourceLocation, ItemPropertyRule> entry : ItemPropertyConfig.activeSnapshot().entrySet()) {
            ItemPropertyRule rule = entry.getValue();
            if (rule.blockExplosionResistance() == null) continue;
            Item item = KineticRegistries.items().get(entry.getKey());
            if (!(item instanceof BlockItem blockItem)) continue;
            Block block = blockItem.getBlock();
            if (block == Blocks.AIR || !(block instanceof BlockPropertyAccess access)) continue;
            ORIGINAL_EXPLOSION_RESISTANCE.putIfAbsent(block, access.itemcontrol$getExplosionResistance());
            access.itemcontrol$setExplosionResistance(rule.blockExplosionResistance().floatValue());
        }
    }

    /** Returns the resistance captured before ItemControl applied its block override. */
    public static float originalBlockExplosionResistance(Block block) {
        if (!(block instanceof BlockPropertyAccess access)) return 0.0F;
        return ORIGINAL_EXPLOSION_RESISTANCE.getOrDefault(block, access.itemcontrol$getExplosionResistance());
    }

    public static void onCommonSetup() {
        ItemPropertyConfig.activatePendingSnapshot();
        applyBlockOverrides();
    }

    /** Returns null when the block has no level override, otherwise the override decision. */
    public static Boolean correctToolForDrops(ItemStack stack, BlockState state) {
        ItemPropertyRule rule = active(stack);
        if (rule == null || rule.miningLevel() == null || state == null || !state.requiresCorrectToolForDrops()) return null;
        int requiredLevel = state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? 3
                : state.is(BlockTags.NEEDS_IRON_TOOL) ? 2
                : state.is(BlockTags.NEEDS_STONE_TOOL) ? 1
                : 0;
        return rule.miningLevel() >= requiredLevel;
    }

    public static float miningSpeed(ItemStack stack, float original) {
        ItemPropertyRule rule = active(stack);
        return rule == null || rule.miningSpeed() == null ? original : rule.miningSpeed().floatValue();
    }

    public static float blockHardness(BlockState state, float original) {
        ItemPropertyRule rule = active(state);
        return rule == null || rule.blockHardness() == null ? original : rule.blockHardness().floatValue();
    }

    public static int maxStackSize(Item item, int original) {
        ItemPropertyRule rule = ItemPropertyConfig.active(item);
        if(rule==null)return original;
        return rule.maxStackSize()!=null?rule.maxStackSize():rule.maxDamage()!=null&&rule.maxDamage()>0?1:original;
    }

    public static int maxDamage(Item item, int original) {
        ItemPropertyRule rule = ItemPropertyConfig.active(item);
        return rule == null || rule.maxDamage() == null || rule.maxDamage() == -1
                ? original : rule.maxDamage();
    }

    public static boolean isUnbreakable(ItemStack stack) {
        ItemPropertyRule rule = active(stack);
        return rule != null && rule.maxDamage() != null && rule.maxDamage() == -1;
    }

    public static int enchantability(Item item, int original) {
        ItemPropertyRule rule = ItemPropertyConfig.active(item);
        return rule == null || rule.enchantability() == null ? original : rule.enchantability();
    }

    public static net.minecraft.world.item.Rarity rarity(Item item, ItemStack stack, net.minecraft.world.item.Rarity original) {
        ItemPropertyRule rule = active(stack);
        if (rule == null || rule.rarity() == null) return original;
        try {
            return net.minecraft.world.item.Rarity.valueOf(rule.rarity().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return original;
        }
    }

    //? if >=1.21 {
/*public static void applyAttributeOverrides(net.neoforged.neoforge.event.ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        ItemPropertyRule rule = active(stack);
        if (rule == null) return;
        java.util.Map<SlotAttribute, List<ModifierSpec>> replacements = new LinkedHashMap<>();
        if (rule.attackDamage() != null) {
            double amount = rule.attackDamage() == -2.0D ? INFINITE_ATTACK_DAMAGE : rule.attackDamage();
            widenAttackDamageRange(amount);
            addNeo(replacements, Attributes.ATTACK_DAMAGE, amount, EquipmentSlot.MAINHAND, "attack_damage");
        }
        addNeo(replacements, Attributes.ATTACK_SPEED, rule.attackSpeed(), EquipmentSlot.MAINHAND, "attack_speed");
        EquipmentSlot armorSlot = ItemPropertyReads.armorSlot(stack);
        if (armorSlot != null) {
            EquipmentSlot slot = armorSlot;
            addNeo(replacements, Attributes.ARMOR, rule.armor(), slot, "armor");
            addNeo(replacements, Attributes.ARMOR_TOUGHNESS, rule.armorToughness(), slot, "armor_toughness");
            addNeo(replacements, Attributes.KNOCKBACK_RESISTANCE, rule.knockbackResistance(), slot, "knockback_resistance");
        }
        var overwritten = new java.util.HashSet<>(replacements.keySet());
        for (int index = 0; index < rule.attributes().size(); index++) {
            var row = rule.attributes().get(index);
            EquipmentSlot slot = parseSlot(row.slot());
            ResourceLocation id = ResourceLocation.tryParse(row.attribute());
            Attribute attribute = attribute(id);
            AttributeModifier.Operation operation = parseOperation(row.operation());
            if (slot == null || attribute == null || operation == null || !Double.isFinite(row.amount())) continue;
            var key = new SlotAttribute(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute), slot);
            if (!row.mode().equals("add")) overwritten.add(key);
            if (row.mode().equals("remove")) continue;
            replacements.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new ModifierSpec(row.amount(), operation, "attribute_" + index));
        }
        // Neo computes all slots in one event. Preserve the unaffected slots of grouped modifiers.
        for (var entry : List.copyOf(event.getModifiers())) {
            boolean affected = overwritten.stream().anyMatch(key -> key.attribute.equals(entry.attribute()) && entry.slot().test(key.slot));
            if (!affected) continue;
            event.removeModifier(entry.attribute(), entry.modifier().id());
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (entry.slot().test(slot) && !overwritten.contains(new SlotAttribute(entry.attribute(), slot))) {
                    var modifier = entry.modifier();
                    event.addModifier(entry.attribute(), new AttributeModifier(dev.xyat.kineticcore.api.resource.KineticResourceIds.of("itemcontrol", "preserved/" + modifier.id().getNamespace() + "/" + modifier.id().getPath() + "/" + slot.getName()), modifier.amount(), modifier.operation()), net.minecraft.world.entity.EquipmentSlotGroup.bySlot(slot));
                }
            }
        }
        replacements.forEach((key, modifiers) -> modifiers.forEach(modifier -> event.addModifier(key.attribute,
            new AttributeModifier(dev.xyat.kineticcore.api.resource.KineticResourceIds.of("itemcontrol", modifier.name + "/" + key.slot.getName()), modifier.amount, modifier.operation),
            net.minecraft.world.entity.EquipmentSlotGroup.bySlot(key.slot))));
    }

    private record SlotAttribute(net.minecraft.core.Holder<Attribute> attribute, EquipmentSlot slot) {}

    private static void addNeo(Map<SlotAttribute, List<ModifierSpec>> target, net.minecraft.core.Holder<Attribute> attribute, Double value, EquipmentSlot slot, String name) {
        if (value != null && Double.isFinite(value)) target.computeIfAbsent(new SlotAttribute(attribute, slot), ignored -> new ArrayList<>()).add(new ModifierSpec(value, AttributeModifier.Operation.ADD_VALUE, name));
    }*/
//?} else {
public static void applyAttributeOverrides(net.minecraftforge.event.ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        ItemPropertyRule rule = active(stack);
        if (rule == null) return;
        EquipmentSlot eventSlot = event.getSlotType();

        LinkedHashMap<Attribute, List<ModifierSpec>> replacements = new LinkedHashMap<>();
        if (eventSlot == EquipmentSlot.MAINHAND) {
            Double damage = rule.attackDamage();
            if (damage != null) {
                double amount = damage == -2.0D ? INFINITE_ATTACK_DAMAGE : damage;
                widenAttackDamageRange(amount);
                addConvenience(replacements, Attributes.ATTACK_DAMAGE, amount, eventSlot);
            }
            addConvenience(replacements, Attributes.ATTACK_SPEED, rule.attackSpeed(), eventSlot);
        }
        if (ItemPropertyReads.armorSlot(stack) == eventSlot) {
            addConvenience(replacements, Attributes.ARMOR, rule.armor(), eventSlot);
            addConvenience(replacements, Attributes.ARMOR_TOUGHNESS, rule.armorToughness(), eventSlot);
            addConvenience(replacements, Attributes.KNOCKBACK_RESISTANCE, rule.knockbackResistance(), eventSlot);
        }

        var overwritten = new java.util.HashSet<>(replacements.keySet());
        for (int index = 0; index < rule.attributes().size(); index++) {
            ItemPropertyRule.AttributeModifier row = rule.attributes().get(index);
            EquipmentSlot slot = parseSlot(row.slot());
            if (slot == null || slot != eventSlot) continue;
            ResourceLocation id = ResourceLocation.tryParse(row.attribute());
            Attribute attribute = attribute(id);
            AttributeModifier.Operation operation = parseOperation(row.operation());
            if (attribute == null || operation == null || !Double.isFinite(row.amount())) continue;
            if (!row.mode().equals("add")) overwritten.add(attribute);
            if (row.mode().equals("remove")) continue;
            replacements.computeIfAbsent(attribute, ignored -> new ArrayList<>())
                    .add(new ModifierSpec(row.amount(), operation, "itemcontrol.attribute." + index));
        }

        overwritten.forEach(event::removeAttribute);
        replacements.forEach((attribute, modifiers) -> {
            for (ModifierSpec modifier : modifiers) {
                UUID id = UUID.nameUUIDFromBytes((stack.getItem() + "/" + eventSlot + "/" + modifier.name)
                        .getBytes(StandardCharsets.UTF_8));
                event.addModifier(attribute, new AttributeModifier(id, modifier.name, modifier.amount, modifier.operation));
            }
        });
    }
//?}


    /** The attribute a rule names, or null. */
    public static Attribute attribute(ResourceLocation id) {
        if (id == null) return null;
        Attribute attribute = KineticRegistries.attributes().get(id);
        // 26.1 dropped the "generic." style prefixes from attribute ids; rules written for older versions keep matching.
        int prefix = id.getPath().indexOf('.');
        if (attribute == null && prefix > 0) {
            attribute = KineticRegistries.attributes().get(dev.xyat.kineticcore.api.resource.KineticResourceIds.of(id.getNamespace(), id.getPath().substring(prefix + 1)));
        }
        return attribute;
    }

    private static void widenAttackDamageRange(double amount) {
        //? if >=1.21 {
/*if (!(Attributes.ATTACK_DAMAGE.value() instanceof RangedAttribute ranged)) return;*/
//?} else {
if (!(Attributes.ATTACK_DAMAGE instanceof RangedAttribute ranged)) return;
//?}

        double requiredMaximum = Math.max(0, amount + 1);
        if (requiredMaximum > ranged.getMaxValue()) {
            MinecraftAttributes.setRange(ranged, ranged.getMinValue(), requiredMaximum);
        }
    }

    private static void addConvenience(
            Map<Attribute, List<ModifierSpec>> target,
            Attribute attribute,
            Double value,
            EquipmentSlot slot
    ) {
        if (value == null || !Double.isFinite(value)) return;
        String name = "itemcontrol.override." + attribute.getDescriptionId() + "." + slot.getName();
        target.computeIfAbsent(attribute, ignored -> new ArrayList<>())
                .add(new ModifierSpec(value, AttributeModifier.Operation.ADDITION, name));
    }

    private static EquipmentSlot parseSlot(String slot) {
        if (slot == null) return null;
        try {
            return EquipmentSlot.valueOf(slot.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    //? if >=1.21 {
/*public static AttributeModifier.Operation parseOperation(String operation) {
        if (operation == null) return null;
        try {
            return AttributeModifier.Operation.valueOf(switch (operation.toUpperCase(java.util.Locale.ROOT)) { case "ADDITION" -> "ADD_VALUE"; case "MULTIPLY_BASE" -> "ADD_MULTIPLIED_BASE"; case "MULTIPLY_TOTAL" -> "ADD_MULTIPLIED_TOTAL"; default -> operation.toUpperCase(java.util.Locale.ROOT); });
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }*/
//?} else {
public static AttributeModifier.Operation parseOperation(String operation) {
        if (operation == null) return null;
        try {
            return AttributeModifier.Operation.valueOf(operation.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
//?}


    //? if >=26.1 {
    /*/^*
     * Enchantability still binds through item defaults. Food is read dynamically from the original stack components.
     ^/
    public static void applyDefaultComponents(Item item, net.minecraft.core.component.DataComponentMap.Builder components) {
        var enchantable = components.get(net.minecraft.core.component.DataComponents.ENCHANTABLE);
        ORIGINAL_ENCHANTABILITY.putIfAbsent(item, enchantable == null ? 0 : enchantable.value());
        ItemPropertyRule rule = ItemPropertyConfig.active(item);
        if (rule == null) return;
        if (rule.enchantability() != null) {
            components.set(net.minecraft.core.component.DataComponents.ENCHANTABLE,
                    rule.enchantability() > 0 ? new net.minecraft.world.item.enchantment.Enchantable(rule.enchantability()) : null);
        }
    }
    private static final java.util.Map<Item,Integer> ORIGINAL_ENCHANTABILITY = new java.util.concurrent.ConcurrentHashMap<>();
    public static Integer originalEnchantability(Item item) { return ORIGINAL_ENCHANTABILITY.get(item); }
    *///?} else if >=1.21 {
    /*public static net.minecraft.world.food.FoodProperties food(ItemStack stack, net.minecraft.world.food.FoodProperties original) {
        ItemPropertyRule rule = active(stack);
        if (rule == null || (rule.nutrition() == null && rule.saturation() == null && rule.alwaysEat() == null && rule.eatSeconds() == null)) return original;
        int nutrition = rule.nutrition() != null ? rule.nutrition() : original == null ? 0 : original.nutrition();
        float coefficient = rule.saturation() != null ? rule.saturation().floatValue()
                : original == null || original.nutrition() == 0 ? 0.0F : original.saturation() / (2.0F * original.nutrition());
        float saturation = 2.0F * nutrition * coefficient;
        boolean always = rule.alwaysEat() != null ? rule.alwaysEat() : original != null && original.canAlwaysEat();
        float seconds = rule.eatSeconds() != null ? rule.eatSeconds().floatValue() : original == null ? 1.6F : original.eatSeconds();
        return new net.minecraft.world.food.FoodProperties(nutrition, saturation, always, seconds,
            original == null ? java.util.Optional.empty() : original.usingConvertsTo(), original == null ? List.of() : original.effects());
    }
    *///?}

    private record ModifierSpec(double amount, AttributeModifier.Operation operation, String name) {
    }
}
