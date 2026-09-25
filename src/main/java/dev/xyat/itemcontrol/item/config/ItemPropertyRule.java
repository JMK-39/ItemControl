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
        Boolean nonConsumable,
        Integer maxStackSize,
        Integer maxDamage,
        Integer enchantability,
        String rarity,
        Boolean fireResistant,
        Double blockHardness,
        Double blockExplosionResistance,
        Boolean explosionImmune,
        Boolean glowing,
        Boolean noGravity,
        Boolean persistent
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
                && nonConsumable == null
                && maxStackSize == null
                && maxDamage == null
                && enchantability == null
                && rarity == null
                && fireResistant == null
                && blockHardness == null
                && blockExplosionResistance == null
                && explosionImmune == null
                && glowing == null
                && noGravity == null
                && persistent == null;

    }

    public boolean hasProtectionFields() {
        return fireResistant != null || explosionImmune != null || glowing != null
                || noGravity != null || persistent != null;
    }

    public boolean hasEnabledProtection() {
        return Boolean.TRUE.equals(fireResistant) || Boolean.TRUE.equals(explosionImmune)
                || Boolean.TRUE.equals(glowing) || Boolean.TRUE.equals(noGravity)
                || Boolean.TRUE.equals(persistent);
    }

    /** A generic replacement modifier for one registered attribute and equipment slot. */
    public record AttributeModifier(String attribute, String slot, String operation, double amount) {
    }
}
