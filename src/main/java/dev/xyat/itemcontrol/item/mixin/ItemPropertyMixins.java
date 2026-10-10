package dev.xyat.itemcontrol.item.mixin;

import com.mojang.datafixers.util.Pair;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.itemcontrol.item.config.ItemPropertyRule;
import dev.xyat.itemcontrol.item.property.ItemPropertyOverrides;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class ItemPropertyMixins {
    private ItemPropertyMixins() {
    }

// 26.1 keeps enchantability and eating time in item components (ItemPropertyOverrides.applyDefaultComponents), so this
// Mixin is left out of its config there (unavailable_mixins).
//? if >=26.1 {
/*    @Mixin(Item.class)
    public abstract static class ItemProperties {
    }*/
//?} else if >=1.21 {
/*    @Mixin(Item.class)
    public abstract static class ItemProperties {
        @Inject(method = "getEnchantmentValue()I", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$enchantability(CallbackInfoReturnable<Integer> cir) {
            cir.setReturnValue(ItemPropertyOverrides.enchantability((Item) (Object) this, cir.getReturnValue()));
        }
        @Inject(method = "getUseDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$useDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
            ItemPropertyRule rule = ItemPropertyOverrides.active(stack);
            if (rule != null && rule.eatSeconds() != null && stack.getFoodProperties(entity) != null)
                cir.setReturnValue(Math.max(1, Math.min(72_000, Math.round(rule.eatSeconds().floatValue() * 20.0F))));
        }
    }*/
//?} else {
    @Mixin(Item.class)
    public abstract static class ItemProperties {
        @Inject(method = "getMaxStackSize()I", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$maxStackSize(CallbackInfoReturnable<Integer> cir) {
            cir.setReturnValue(ItemPropertyOverrides.maxStackSize((Item) (Object) this, cir.getReturnValue()));
        }

        @Inject(method = "getMaxDamage()I", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$maxDamage(CallbackInfoReturnable<Integer> cir) {
            cir.setReturnValue(ItemPropertyOverrides.maxDamage((Item) (Object) this, cir.getReturnValue()));
        }

        @Inject(method = "getEnchantmentValue()I", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$enchantability(CallbackInfoReturnable<Integer> cir) {
            cir.setReturnValue(ItemPropertyOverrides.enchantability((Item) (Object) this, cir.getReturnValue()));
        }

        @Inject(method = "getRarity(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/Rarity;", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$rarity(ItemStack stack, CallbackInfoReturnable<net.minecraft.world.item.Rarity> cir) {
            cir.setReturnValue(ItemPropertyOverrides.rarity((Item) (Object) this, stack, cir.getReturnValue()));
        }

        @Inject(method = "getFoodProperties()Lnet/minecraft/world/food/FoodProperties;", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$foodProperties(CallbackInfoReturnable<FoodProperties> cir) {
            cir.setReturnValue(dev.xyat.itemcontrol.item.property.ItemCapabilityRuntime.food(new ItemStack((Item)(Object)this), cir.getReturnValue()));
        }

        @Inject(method = "isEdible", at = @At("HEAD"), cancellable = true)
        private void itemcontrol$edible(CallbackInfoReturnable<Boolean> cir) {
            Boolean value = dev.xyat.itemcontrol.item.property.ItemCapabilityRuntime.edibleOverride(new ItemStack((Item)(Object)this));
            if (value != null) cir.setReturnValue(value);
        }
        @Inject(method = "getUseDuration(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$useDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
            ItemPropertyRule rule = ItemPropertyOverrides.active(stack);
            if (rule != null && rule.eatSeconds() != null && stack.getFoodProperties(null) != null) {
                cir.setReturnValue(Math.max(1, Math.min(72_000, Math.round(rule.eatSeconds().floatValue() * 20.0F))));
            }
        }
    }
//?}


    @Mixin(ItemStack.class)
    public abstract static class ItemStackProperties {
//? if >=1.21 {
/*        @Inject(method = "getMaxStackSize()I", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$maxStackSize(CallbackInfoReturnable<Integer> cir) {
            cir.setReturnValue(ItemPropertyOverrides.maxStackSize(((ItemStack)(Object)this).getItem(), cir.getReturnValue()));
        }
        @Inject(method = "getMaxDamage()I", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$maxDamage(CallbackInfoReturnable<Integer> cir) {
            cir.setReturnValue(ItemPropertyOverrides.maxDamage(((ItemStack)(Object)this).getItem(), cir.getReturnValue()));
        }
        @Inject(method = "getRarity()Lnet/minecraft/world/item/Rarity;", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$rarity(CallbackInfoReturnable<net.minecraft.world.item.Rarity> cir) {
            ItemStack stack = (ItemStack)(Object)this;
            cir.setReturnValue(ItemPropertyOverrides.rarity(stack.getItem(), stack, cir.getReturnValue()));
        }
*/
//?}
        // 26.1 food is the item's default FOOD component (ItemPropertyOverrides.applyDefaultComponents).
//? if >=1.21 && <26.1 {
/*        // ItemStack inherits this default method from IItemStackExtension. A merged override gives
        // existing stacks the active rule without mutating their persisted food component.
        public FoodProperties getFoodProperties(net.minecraft.world.entity.LivingEntity entity) {
            ItemStack stack = (ItemStack)(Object)this;
            FoodProperties original = stack.getItem().getFoodProperties(stack, entity);
            return dev.xyat.itemcontrol.item.property.ItemCapabilityRuntime.food(stack, original);
        }
*/
//?}
        @Inject(method = "isDamageableItem()Z", at = @At("HEAD"), cancellable = true)
        private void itemcontrol$unbreakable(CallbackInfoReturnable<Boolean> cir) {
            ItemStack stack=(ItemStack)(Object)this;
            if (ItemPropertyOverrides.isUnbreakable(stack)) {cir.setReturnValue(false);return;}
            var rule=ItemPropertyOverrides.active(stack);
            if(rule!=null && Integer.valueOf(0).equals(rule.maxDamage())){cir.setReturnValue(false);return;}
            if(rule!=null && rule.maxDamage()!=null && rule.maxDamage()>0){
                //? if >=1.21 {
                /*if(!stack.has(net.minecraft.core.component.DataComponents.UNBREAKABLE))cir.setReturnValue(true);*/
                //?} else {
                if(stack.getTag()==null||!stack.getTag().getBoolean("Unbreakable"))cir.setReturnValue(true);
                //?}
            }
        }

        @Inject(method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$miningSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
            cir.setReturnValue(ItemPropertyOverrides.miningSpeed((ItemStack) (Object) this, cir.getReturnValue()));
        }

        @Inject(method = "isCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"), cancellable = true)
        private void itemcontrol$miningLevel(BlockState state, CallbackInfoReturnable<Boolean> cir) {
            Boolean override = ItemPropertyOverrides.correctToolForDrops((ItemStack) (Object) this, state);
            if (override != null) cir.setReturnValue(override);
        }
    }

    @Mixin(BlockBehaviour.BlockStateBase.class)
    public abstract static class BlockStateProperties {
        @Inject(method = "getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$blockHardness(
                net.minecraft.world.level.BlockGetter level,
                net.minecraft.core.BlockPos pos,
                CallbackInfoReturnable<Float> cir
        ) {
            cir.setReturnValue(ItemPropertyOverrides.blockHardness((BlockState) (Object) this, cir.getReturnValue()));
        }
    }

    @Mixin(BlockBehaviour.class)
    public interface BlockPropertyAccess {
        @Accessor("explosionResistance")
        float itemcontrol$getExplosionResistance();

        @Mutable
        @Accessor("explosionResistance")
        void itemcontrol$setExplosionResistance(float explosionResistance);
    }
}
