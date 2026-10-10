package dev.xyat.itemcontrol.item.property;

import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.itemcontrol.item.config.ItemPropertyRule;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

/** Independent use capabilities, without mutating persisted item data. */
public final class ItemCapabilityRuntime {
    private ItemCapabilityRuntime() {}
    public static EquipmentSlot equipmentSlot(ItemStack stack) {
        var rule = ItemPropertyOverrides.active(stack);
        String slot = rule == null ? null : rule.capabilities().equipmentSlot();
        if(slot==null)return ItemPropertyConfig.previewOriginal(()->ItemPropertyReads.armorSlot(stack));
        return slot.equals("none") ? null : EquipmentSlot.valueOf(slot.toUpperCase(java.util.Locale.ROOT));
    }
    public static boolean overridesEquipment(ItemStack stack) {
        var rule = ItemPropertyOverrides.active(stack);
        return rule != null && rule.capabilities().equipmentSlot() != null;
    }
    public static boolean foodChanged(ItemPropertyRule rule) {
        return rule != null && (rule.capabilities().edible() != null || rule.capabilities().overridesFoodEffects()
                || rule.nutrition() != null || rule.saturation() != null || rule.alwaysEat() != null || rule.eatSeconds() != null);
    }
    public static boolean customUse(ItemStack stack) {
        var rule = ItemPropertyOverrides.active(stack);
        return rule != null && (overridesEquipment(stack) || foodChanged(rule) || rule.capabilities().foodRemainder() != null);
    }
    public static Boolean edibleOverride(ItemStack stack) {
        var rule = ItemPropertyOverrides.active(stack);
        if (rule == null) return null;
        if (rule.capabilities().edible() != null) return rule.capabilities().edible();
        return foodChanged(rule) ? true : null;
    }
    public static MobEffectInstance effect(dev.xyat.itemcontrol.item.config.ItemCapabilitySettings.FoodEffect row) {
        var type = KineticRegistries.mobEffects().get(ResourceLocation.tryParse(row.effect()));
        if (type == null) return null;
        //? if >=1.21 {
        /*return new MobEffectInstance(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(type), row.duration(), row.amplifier());*/
        //?} else {
        return new MobEffectInstance(type, row.duration(), row.amplifier());
        //?}
    }
    //? if >=26.1 {
    /*public static FoodProperties food(ItemStack stack, FoodProperties original) {
        var rule = ItemPropertyOverrides.active(stack);
        if (!foodChanged(rule)) return original;
        if (Boolean.FALSE.equals(rule.capabilities().edible())) return null;
        int nutrition = rule.nutrition() != null ? rule.nutrition() : original == null ? 0 : original.nutrition();
        float coefficient = rule.saturation() != null ? rule.saturation().floatValue() : original == null || original.nutrition() == 0 ? 0 : original.saturation() / (2 * original.nutrition());
        return new FoodProperties(nutrition, 2 * nutrition * coefficient, rule.alwaysEat() != null ? rule.alwaysEat() : original != null && original.canAlwaysEat());
    }*/
    //?} else if >=1.21 {
    /*public static FoodProperties food(ItemStack stack, FoodProperties original) {
        var rule = ItemPropertyOverrides.active(stack);
        if (!foodChanged(rule)) return original;
        if (Boolean.FALSE.equals(rule.capabilities().edible())) return null;
        int nutrition = rule.nutrition() != null ? rule.nutrition() : original == null ? 0 : original.nutrition();
        float coefficient = rule.saturation() != null ? rule.saturation().floatValue() : original == null || original.nutrition() == 0 ? 0 : original.saturation() / (2 * original.nutrition());
        float seconds = rule.eatSeconds() != null ? rule.eatSeconds().floatValue() : original == null ? 1.6F : original.eatSeconds();
        var effects = new ArrayList<FoodProperties.PossibleEffect>();
        if (original != null && !rule.capabilities().foodEffectsMode().equals("replace")) effects.addAll(original.effects());
        for (var row : rule.capabilities().foodEffects()) { var effect = effect(row); if (effect != null) effects.add(new FoodProperties.PossibleEffect(() -> new MobEffectInstance(effect), (float)row.probability())); }
        return new FoodProperties(nutrition, 2 * nutrition * coefficient, rule.alwaysEat() != null ? rule.alwaysEat() : original != null && original.canAlwaysEat(), seconds,
                original == null ? java.util.Optional.empty() : original.usingConvertsTo(), List.copyOf(effects));
    }*/
    //?} else {
    public static FoodProperties food(ItemStack stack, FoodProperties original) {
        var rule = ItemPropertyOverrides.active(stack);
        if (!foodChanged(rule)) return original;
        if (Boolean.FALSE.equals(rule.capabilities().edible())) return null;
        var builder = new FoodProperties.Builder()
                .nutrition(rule.nutrition() != null ? rule.nutrition() : original == null ? 0 : original.getNutrition())
                .saturationMod(rule.saturation() != null ? rule.saturation().floatValue() : original == null ? 0 : original.getSaturationModifier());
        if (rule.alwaysEat() != null ? rule.alwaysEat() : original != null && original.canAlwaysEat()) builder.alwaysEat();
        if (original != null) {
            if (original.isMeat()) builder.meat();
            if (original.isFastFood()) builder.fast();
            if (!rule.capabilities().foodEffectsMode().equals("replace")) for (var pair : original.getEffects()) builder.effect(() -> new MobEffectInstance(pair.getFirst()), pair.getSecond());
        }
        for (var row : rule.capabilities().foodEffects()) { var effect = effect(row); if (effect != null) builder.effect(() -> new MobEffectInstance(effect), (float)row.probability()); }
        return builder.build();
    }
    //?}
    public static boolean edible(ItemStack stack) {
        Boolean override = edibleOverride(stack);
        if (override != null) return override;
        //? if >=26.1 {
        /*return stack.has(net.minecraft.core.component.DataComponents.FOOD);*/
        //?} else {
        return stack.getFoodProperties(null) != null;
        //?}
    }
    /** null preserves the item's original use behavior. */
    //? if >=26.1 {
    /*public static net.minecraft.world.InteractionResult use(ItemStack stack, Level level, Player player, InteractionHand hand) {
        Boolean success = useAction(stack, level, player, hand);
        return success == null ? null : success ? net.minecraft.world.InteractionResult.CONSUME : net.minecraft.world.InteractionResult.FAIL;
    }*/
    //?} else {
    public static net.minecraft.world.InteractionResultHolder<ItemStack> use(ItemStack stack, Level level, Player player, InteractionHand hand) {
        Boolean success = useAction(stack, level, player, hand);
        return success == null ? null : success ? net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand)) : net.minecraft.world.InteractionResultHolder.fail(stack);
    }
    //?}
    private static Boolean useAction(ItemStack stack, Level level, Player player, InteractionHand hand) {
        if (!customUse(stack)) return null;
        EquipmentSlot slot = equipmentSlot(stack);
        if (slot != null && (player.isShiftKeyDown() || !edible(stack))) {
            if (!player.getItemBySlot(slot).isEmpty()) return false;
            if (!level.isClientSide()) {
                ItemStack equipped = stack.copy(); equipped.setCount(1);
                player.setItemSlot(slot, equipped);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return true;
        }
        if (edible(stack)) {
            if (!player.canEat(ItemPropertyReads.canAlwaysEat(stack))) return false;
            player.startUsingItem(hand); return true;
        }
        return overridesEquipment(stack)||Boolean.FALSE.equals(edibleOverride(stack)) ? false : null;
    }
    public static int duration(ItemStack stack) {
        var rule = ItemPropertyOverrides.active(stack);
        if (rule != null && rule.eatSeconds() != null) return Math.max(1, Math.min(72_000, Math.round(rule.eatSeconds().floatValue() * 20)));
        Double original = ItemPropertyConfig.previewOriginal(() -> ItemPropertyReads.eatSeconds(stack));
        return original == null ? 32 : Math.max(1, (int)Math.round(original * 20));
    }
    public static boolean convertedWeapon(ItemStack stack){var rule=ItemPropertyOverrides.active(stack);return rule!=null&&rule.attackDamage()!=null&&rule.maxDamage()!=null&&rule.maxDamage()>0&&ItemPropertyConfig.previewOriginal(stack::getMaxDamage)==0;}
    public static void hurtWeapon(ItemStack stack,LivingEntity wearer){
        //? if >=1.21 {
        /*stack.hurtAndBreak(1,wearer,EquipmentSlot.MAINHAND);*/
        //?} else {
        stack.hurtAndBreak(1,wearer,entity->entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        //?}
        if(wearer instanceof Player player)player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(stack.getItem()));
    }
    public static void hurtEquipment(LivingEntity wearer,EquipmentSlot slot,net.minecraft.world.damagesource.DamageSource source,float amount,int previous){
        ItemStack stack=wearer.getItemBySlot(slot);
        if(amount<=0||stack.isEmpty()||equipmentSlot(stack)!=slot||!stack.isDamageableItem()||stack.getDamageValue()!=previous||ItemPropertyConfig.previewOriginal(()->ItemPropertyReads.armorSlot(stack))!=null)return;
        if(source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)&&ItemPropertyReads.fireResistant(stack))return;
        int damage=Math.max(1,(int)(amount/4));
        //? if >=1.21 {
        /*if(stack.canBeHurtBy(source))stack.hurtAndBreak(damage,wearer,slot);*/
        //?} else {
        stack.hurtAndBreak(damage,wearer,entity->entity.broadcastBreakEvent(slot));
        //?}
    }
    public static boolean manualFinish(ItemStack stack) {
        var rule = ItemPropertyOverrides.active(stack);
        if (rule == null || !edible(stack)) return false;
        //? if >=26.1 {
        /*return foodChanged(rule) || rule.capabilities().foodRemainder() != null || Boolean.TRUE.equals(rule.nonConsumable());*/
        //?} else {
        return !ItemPropertyConfig.previewOriginal(() -> ItemPropertyReads.isFood(stack)) || rule.capabilities().foodRemainder() != null || Boolean.TRUE.equals(rule.nonConsumable());
        //?}
    }
    public static ItemStack finish(ItemStack stack, Level level, LivingEntity entity) {
        var rule = ItemPropertyOverrides.active(stack);
        int count = stack.getCount();
        boolean retained = Boolean.TRUE.equals(rule.nonConsumable()) || entity instanceof Player player && player.getAbilities().instabuild;
        //? if >=26.1 {
        /*var nativeRemainder = stack.get(net.minecraft.core.component.DataComponents.USE_REMAINDER);
        FoodProperties properties = food(stack, stack.get(net.minecraft.core.component.DataComponents.FOOD));
        var original = stack.get(net.minecraft.core.component.DataComponents.CONSUMABLE);
        if (original == null) original = net.minecraft.world.item.component.Consumables.DEFAULT_FOOD;
        var effects = new ArrayList<>(original.onConsumeEffects());
        if (rule.capabilities().foodEffectsMode().equals("replace")) effects.removeIf(effect -> effect instanceof net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect);
        for (var row : rule.capabilities().foodEffects()) { var effect = effect(row); if (effect != null) effects.add(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(effect, (float)row.probability())); }
        var consumable = new net.minecraft.world.item.component.Consumable(duration(stack)/20.0F, net.minecraft.world.item.ItemUseAnimation.EAT, original.sound(), original.hasConsumeParticles(), effects);
        ItemStack working = stack.copy(); working.remove(net.minecraft.core.component.DataComponents.FOOD);
        consumable.onConsume(level, entity, working);
        if (entity instanceof Player player && properties != null) player.getFoodData().eat(properties);
        stack.setCount(working.getCount());*/
        //?} else {
        entity.eat(level, stack);
        //?}
        if (retained) { stack.setCount(count); return stack; }
        String remainder = rule.capabilities().foodRemainder();
        //? if >=26.1 {
        /*if (remainder == null) {
            if (nativeRemainder != null) return nativeRemainder.convertIntoRemainder(stack, count, false, extra -> {
                if (!level.isClientSide() && entity instanceof Player player && !player.getInventory().add(extra)) player.drop(extra, false);
            });
        }*/
        //?}
        if (remainder == null || remainder.equals("minecraft:air")) return stack;
        var item = KineticRegistries.items().get(ResourceLocation.tryParse(remainder));
        if (item == null || item == Items.AIR) return stack;
        ItemStack returned = new ItemStack(item);
        if (stack.isEmpty()) return returned;
        if (!level.isClientSide() && entity instanceof Player player && !player.getInventory().add(returned)) player.drop(returned, false);
        return stack;
    }
}
