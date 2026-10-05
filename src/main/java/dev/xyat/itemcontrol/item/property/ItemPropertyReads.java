package dev.xyat.itemcontrol.item.property;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Vanilla item values that ItemControl overrides, read the way each Minecraft version stores them: item classes up
 * to 1.20.1, item methods and food components on 1.21.1, and item components only on 26.1.
 */
public final class ItemPropertyReads {
    private ItemPropertyReads() {
    }

    /** The armor slot of armor items, otherwise null. */
    public static EquipmentSlot armorSlot(ItemStack stack) {
        //? if >=26.1 {
        /*net.minecraft.world.item.equipment.Equippable equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? equippable.slot() : null;
        *///?} else {
        return stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor ? armor.getEquipmentSlot() : null;
        //?}
    }

    public static boolean isFood(ItemStack stack) {
        //? if >=26.1 {
        /*return stack.has(net.minecraft.core.component.DataComponents.FOOD);
        *///?} else {
        return stack.getFoodProperties(null) != null;
        //?}
    }

    /** Hunger restored, or null for non-food. */
    public static Integer nutrition(ItemStack stack) {
        //? if >=26.1 {
        /*net.minecraft.world.food.FoodProperties food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        return food == null ? null : food.nutrition();
        *///?} else if >=1.21 {
        /*net.minecraft.world.food.FoodProperties food = stack.getFoodProperties(null);
        return food == null ? null : food.nutrition();
        *///?} else {
        net.minecraft.world.food.FoodProperties food = stack.getFoodProperties(null);
        return food == null ? null : food.getNutrition();
        //?}
    }

    /** The saturation modifier (saturation per two hunger points), or null for non-food. */
    public static Double saturationModifier(ItemStack stack) {
        //? if >=1.21 {
        /*net.minecraft.world.food.FoodProperties food = food(stack);
        return food == null ? null : food.nutrition() == 0 ? 0 : food.saturation() / (2.0 * food.nutrition());
        *///?} else {
        net.minecraft.world.food.FoodProperties food = stack.getFoodProperties(null);
        return food == null ? null : (double) food.getSaturationModifier();
        //?}
    }

    public static boolean canAlwaysEat(ItemStack stack) {
        //? if >=1.21 {
        /*net.minecraft.world.food.FoodProperties food = food(stack);
        *///?} else {
        net.minecraft.world.food.FoodProperties food = stack.getFoodProperties(null);
        //?}
        return food != null && food.canAlwaysEat();
    }

    /** Seconds it takes to eat, or null for non-food. */
    public static Double eatSeconds(ItemStack stack) {
        //? if >=26.1 {
        /*if (!isFood(stack)) return null;
        net.minecraft.world.item.component.Consumable consumable = stack.get(net.minecraft.core.component.DataComponents.CONSUMABLE);
        return (double) (consumable == null ? net.minecraft.world.item.component.Consumable.DEFAULT_CONSUME_SECONDS : consumable.consumeSeconds());
        *///?} else if >=1.21 {
        /*net.minecraft.world.food.FoodProperties food = stack.getFoodProperties(null);
        return food == null ? null : (double) food.eatSeconds();
        *///?} else {
        return stack.getFoodProperties(null) == null ? null : stack.getUseDuration() / 20.0;
        //?}
    }

    public static int enchantability(ItemStack stack) {
        //? if >=26.1 {
        /*net.minecraft.world.item.enchantment.Enchantable enchantable = stack.get(net.minecraft.core.component.DataComponents.ENCHANTABLE);
        return enchantable == null ? 0 : enchantable.value();
        *///?} else {
        return stack.getEnchantmentValue();
        //?}
    }

    public static boolean fireResistant(ItemStack stack) {
        //? if >=26.1 {
        /*net.minecraft.world.item.component.DamageResistant resistant = stack.get(net.minecraft.core.component.DataComponents.DAMAGE_RESISTANT);
        return resistant != null && resistant.types().unwrapKey().filter(net.minecraft.tags.DamageTypeTags.IS_FIRE::equals).isPresent();
        *///?} else if >=1.21 {
        /*return stack.has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT);
        *///?} else {
        return stack.getItem().isFireResistant();
        //?}
    }

    /**
     * The vanilla mining level of tools (0 wood/gold, 1 stone, 2 iron, 3 diamond, 4 netherite up to 1.21.1), or null
     * for items that are not tiered tools. 26.1 tools have no tier; their level is what they can harvest.
     */
    public static Integer miningLevel(ItemStack stack) {
        //? if >=26.1 {
        /*if (!stack.has(net.minecraft.core.component.DataComponents.TOOL)) return null;
        if (stack.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) return 3;
        if (stack.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())) return 2;
        if (stack.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState())) return 1;
        return 0;
        *///?} else if >=1.21 {
        /*if (!(stack.getItem() instanceof net.minecraft.world.item.TieredItem tiered)) return null;
        var tier = tiered.getTier();
        return tier == net.minecraft.world.item.Tiers.NETHERITE ? 4 : tier == net.minecraft.world.item.Tiers.DIAMOND ? 3
                : tier == net.minecraft.world.item.Tiers.IRON ? 2 : tier == net.minecraft.world.item.Tiers.STONE ? 1 : 0;
        *///?} else {
        return stack.getItem() instanceof net.minecraft.world.item.TieredItem tiered ? tiered.getTier().getLevel() : null;
        //?}
    }

    /** The mining speed of tools on the blocks they are made for, or of other items on common blocks. */
    public static double miningSpeed(ItemStack stack) {
        //? if >=26.1 {
        /*net.minecraft.world.item.component.Tool tool = stack.get(net.minecraft.core.component.DataComponents.TOOL);
        if (tool != null) {
            return tool.rules().stream().flatMap(rule -> rule.speed().stream()).max(Float::compare).orElse(tool.defaultMiningSpeed());
        }
        *///?} else {
        if (stack.getItem() instanceof net.minecraft.world.item.TieredItem tiered) return tiered.getTier().getSpeed();
        //?}
        return Math.max(stack.getDestroySpeed(Blocks.STONE.defaultBlockState()),
                Math.max(stack.getDestroySpeed(Blocks.DIRT.defaultBlockState()),
                        stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState())));
    }

    //? if >=26.1 {
    /*private static net.minecraft.world.food.FoodProperties food(ItemStack stack) {
        return stack.get(net.minecraft.core.component.DataComponents.FOOD);
    }
    *///?} else if >=1.21 {
    /*private static net.minecraft.world.food.FoodProperties food(ItemStack stack) {
        return stack.getFoodProperties(null);
    }
    *///?}
}
