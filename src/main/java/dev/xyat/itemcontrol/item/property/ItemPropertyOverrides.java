package dev.xyat.itemcontrol.item.property;

import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.itemcontrol.item.config.ItemPropertyRule;
import dev.xyat.itemcontrol.item.mixin.ItemPropertyMixins.BlockPropertyAccess;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runtime lookups and vanilla-facing adapters for the active per-item property snapshot. */
public final class ItemPropertyOverrides {
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
            Item item = ForgeRegistries.ITEMS.getValue(entry.getKey());
            if (!(item instanceof BlockItem blockItem)) continue;
            Block block = blockItem.getBlock();
            if (block == Blocks.AIR || !(block instanceof BlockPropertyAccess access)) continue;
            ORIGINAL_EXPLOSION_RESISTANCE.putIfAbsent(block, access.itemcontrol$getExplosionResistance());
            access.itemcontrol$setExplosionResistance(rule.blockExplosionResistance().floatValue());
        }
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ItemPropertyConfig.activatePendingSnapshot();
            applyBlockOverrides();
        });
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
        return rule == null || rule.maxStackSize() == null ? original : rule.maxStackSize();
    }

    public static int maxDamage(Item item, int original) {
        ItemPropertyRule rule = ItemPropertyConfig.active(item);
        return rule == null || rule.maxDamage() == null ? original : rule.maxDamage();
    }

    public static int enchantability(Item item, int original) {
        ItemPropertyRule rule = ItemPropertyConfig.active(item);
        return rule == null || rule.enchantability() == null ? original : rule.enchantability();
    }

    public static boolean fireResistant(Item item, boolean original) {
        ItemPropertyRule rule = ItemPropertyConfig.active(item);
        return rule == null || rule.fireResistant() == null ? original : rule.fireResistant();
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

    public static void applyAttributeOverrides(net.minecraftforge.event.ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        ItemPropertyRule rule = active(stack);
        if (rule == null) return;
        EquipmentSlot eventSlot = event.getSlotType();

        LinkedHashMap<Attribute, List<ModifierSpec>> replacements = new LinkedHashMap<>();
        if (eventSlot == EquipmentSlot.MAINHAND) {
            addConvenience(replacements, Attributes.ATTACK_DAMAGE, rule.attackDamage(), eventSlot);
            addConvenience(replacements, Attributes.ATTACK_SPEED, rule.attackSpeed(), eventSlot);
        }
        if (stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == eventSlot) {
            addConvenience(replacements, Attributes.ARMOR, rule.armor(), eventSlot);
            addConvenience(replacements, Attributes.ARMOR_TOUGHNESS, rule.armorToughness(), eventSlot);
            addConvenience(replacements, Attributes.KNOCKBACK_RESISTANCE, rule.knockbackResistance(), eventSlot);
        }

        for (int index = 0; index < rule.attributes().size(); index++) {
            ItemPropertyRule.AttributeModifier row = rule.attributes().get(index);
            EquipmentSlot slot = parseSlot(row.slot());
            if (slot == null || slot != eventSlot) continue;
            ResourceLocation id = ResourceLocation.tryParse(row.attribute());
            Attribute attribute = id == null ? null : ForgeRegistries.ATTRIBUTES.getValue(id);
            AttributeModifier.Operation operation = parseOperation(row.operation());
            if (attribute == null || operation == null || !Double.isFinite(row.amount())) continue;
            replacements.computeIfAbsent(attribute, ignored -> new ArrayList<>())
                    .add(new ModifierSpec(row.amount(), operation, "itemcontrol.attribute." + index));
        }

        replacements.forEach((attribute, modifiers) -> {
            event.removeAttribute(attribute);
            for (ModifierSpec modifier : modifiers) {
                UUID id = UUID.nameUUIDFromBytes((stack.getItem() + "/" + eventSlot + "/" + modifier.name)
                        .getBytes(StandardCharsets.UTF_8));
                event.addModifier(attribute, new AttributeModifier(id, modifier.name, modifier.amount, modifier.operation));
            }
        });
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

    private static AttributeModifier.Operation parseOperation(String operation) {
        if (operation == null) return null;
        try {
            return AttributeModifier.Operation.valueOf(operation.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record ModifierSpec(double amount, AttributeModifier.Operation operation, String name) {
    }
}
