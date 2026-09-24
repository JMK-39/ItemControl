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
            ItemPropertyRule rule = ItemPropertyConfig.active((Item) (Object) this);
            if (rule == null || (rule.nutrition() == null && rule.saturation() == null && rule.alwaysEat() == null)) return;
            FoodProperties original = cir.getReturnValue();
            int nutrition = rule.nutrition() != null ? rule.nutrition() : original == null ? 0 : original.getNutrition();
            float saturation = rule.saturation() != null
                    ? rule.saturation().floatValue()
                    : original == null ? 0.0F : original.getSaturationModifier();
            FoodProperties.Builder builder = new FoodProperties.Builder()
                    .nutrition(nutrition)
                    .saturationMod(saturation);
            if (rule.alwaysEat() != null ? rule.alwaysEat() : original != null && original.canAlwaysEat()) {
                builder.alwaysEat();
            }
            if (original != null) {
                if (original.isMeat()) builder.meat();
                if (original.isFastFood()) builder.fast();
                for (Pair<MobEffectInstance, Float> effect : original.getEffects()) {
                    builder.effect(() -> new MobEffectInstance(effect.getFirst()), effect.getSecond());
                }
            }
            cir.setReturnValue(builder.build());
        }

        @Inject(method = "getUseDuration(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"), cancellable = true)
        private void itemcontrol$useDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
            ItemPropertyRule rule = ItemPropertyOverrides.active(stack);
            if (rule != null && rule.eatSeconds() != null && stack.getFoodProperties(null) != null) {
                cir.setReturnValue(Math.max(1, Math.min(72_000, Math.round(rule.eatSeconds().floatValue() * 20.0F))));
            }
        }
    }

    @Mixin(ItemStack.class)
    public abstract static class ItemStackProperties {
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
