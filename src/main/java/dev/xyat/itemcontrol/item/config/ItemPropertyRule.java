package dev.xyat.itemcontrol.item.config;

import java.util.List;

/** Immutable runtime view of the properties ItemControl can override for one registered item. */
public record ItemPropertyRule(
        Double attackDamage,
        Double attackSpeed,
        Double armor,
        Double armorToughness,
        Double knockbackResistance,
        List<AttributeModifier> attributes,
        Double miningSpeed,
        Integer miningLevel,
        Integer nutrition,
        Double saturation,
        Double eatSeconds,
        Boolean alwaysEat,
        Integer maxStackSize,
        Integer maxDamage,
        Integer enchantability,
        String rarity,
        Boolean fireResistant,
        Double blockHardness,
        Double blockExplosionResistance,
        Boolean explosionImmune
) {
    public ItemPropertyRule {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    public boolean isEmpty() {
        return attackDamage == null
                && attackSpeed == null
                && armor == null
                && armorToughness == null
                && knockbackResistance == null
                && attributes.isEmpty()
                && miningSpeed == null
                && miningLevel == null
                && nutrition == null
                && saturation == null
                && eatSeconds == null
                && alwaysEat == null
                && maxStackSize == null
                && maxDamage == null
                && enchantability == null
                && rarity == null
                && fireResistant == null
                && blockHardness == null
                && blockExplosionResistance == null
                && explosionImmune == null;
    }

    /** A generic replacement modifier for one registered attribute and equipment slot. */
    public record AttributeModifier(String attribute, String slot, String operation, double amount) {
    }
}
