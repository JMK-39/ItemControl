package dev.xyat.itemcontrol.item.mixin;

import dev.xyat.itemcontrol.item.property.ItemCapabilityRuntime;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class ItemCapabilityMixins {
    private ItemCapabilityMixins() {}
    @Mixin(ItemStack.class)
    public abstract static class StackUse {
        //? if >=26.1 {
        /*@org.spongepowered.asm.mixin.Shadow public abstract net.minecraft.core.component.DataComponentMap getComponents();
        @SuppressWarnings("unchecked")
        public <T> T get(net.minecraft.core.component.DataComponentType<? extends T> type) {
            T original = getComponents().get(type);
            if (type == net.minecraft.core.component.DataComponents.FOOD) {
                return (T) ItemCapabilityRuntime.food((ItemStack)(Object)this, (net.minecraft.world.food.FoodProperties) original);
            }
            return original;
        }*/
        //?}
        @Inject(method="use", at=@At("HEAD"), cancellable=true)
        //? if >=26.1 {
        /*private void itemcontrol$use(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {*/
        //?} else {
        private void itemcontrol$use(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<net.minecraft.world.InteractionResultHolder<ItemStack>> cir) {
        //?}
            var result = ItemCapabilityRuntime.use((ItemStack)(Object)this, level, player, hand);
            if (result != null) cir.setReturnValue(result);
        }
        @Inject(method="useOn", at=@At("HEAD"), cancellable=true)
        private void itemcontrol$useOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
            if (ItemCapabilityRuntime.customUse((ItemStack)(Object)this)) cir.setReturnValue(InteractionResult.PASS);
        }
        @Inject(method="finishUsingItem", at=@At("HEAD"), cancellable=true)
        private void itemcontrol$finish(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
            ItemStack stack = (ItemStack)(Object)this;
            if(Boolean.FALSE.equals(ItemCapabilityRuntime.edibleOverride(stack))){cir.setReturnValue(stack);return;}
            if (ItemCapabilityRuntime.manualFinish(stack)) cir.setReturnValue(ItemCapabilityRuntime.finish(stack, level, entity));
        }
        @Inject(method="hurtEnemy",at=@At("RETURN"),cancellable=true)
        //? if >=26.1 {
        /*private void itemcontrol$hit(LivingEntity target,LivingEntity wearer,CallbackInfoReturnable<Boolean> cir){
            if(!cir.getReturnValue()&&ItemCapabilityRuntime.convertedWeapon((ItemStack)(Object)this)){ItemCapabilityRuntime.hurtWeapon((ItemStack)(Object)this,wearer);cir.setReturnValue(true);}
        }*/
        //?} else if >=1.21 {
        /*private void itemcontrol$hit(LivingEntity target,Player wearer,CallbackInfoReturnable<Boolean> cir){
            if(!cir.getReturnValue()&&ItemCapabilityRuntime.convertedWeapon((ItemStack)(Object)this)){ItemCapabilityRuntime.hurtWeapon((ItemStack)(Object)this,wearer);cir.setReturnValue(true);}
        }*/
        //?} else {
        private void itemcontrol$hit(LivingEntity target,Player wearer,CallbackInfo ci){
            if(ItemCapabilityRuntime.convertedWeapon((ItemStack)(Object)this))ItemCapabilityRuntime.hurtWeapon((ItemStack)(Object)this,wearer);
        }
        //?}
        //? if >=26.1 {
        /*@Inject(method="getUseAnimation", at=@At("HEAD"), cancellable=true)
        private void itemcontrol$animation(CallbackInfoReturnable<net.minecraft.world.item.ItemUseAnimation> cir) {
            if (ItemCapabilityRuntime.customUse((ItemStack)(Object)this) && ItemCapabilityRuntime.edible((ItemStack)(Object)this)) cir.setReturnValue(net.minecraft.world.item.ItemUseAnimation.EAT);
        }*/
        //?} else {
        @Inject(method="getUseAnimation", at=@At("HEAD"), cancellable=true)
        private void itemcontrol$animation(CallbackInfoReturnable<net.minecraft.world.item.UseAnim> cir) {
            if (ItemCapabilityRuntime.customUse((ItemStack)(Object)this) && ItemCapabilityRuntime.edible((ItemStack)(Object)this)) cir.setReturnValue(net.minecraft.world.item.UseAnim.EAT);
        }
        //?}
        @Inject(method="getUseDuration", at=@At("HEAD"), cancellable=true)
        //? if >=1.21 {
        /*private void itemcontrol$duration(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {*/
        //?} else {
        private void itemcontrol$duration(CallbackInfoReturnable<Integer> cir) {
        //?}
            ItemStack stack = (ItemStack)(Object)this;
            if (ItemCapabilityRuntime.customUse(stack) && ItemCapabilityRuntime.edible(stack)) cir.setReturnValue(ItemCapabilityRuntime.duration(stack));
        }
        //? if <1.21 {
        @Inject(method="isEdible", at=@At("HEAD"), cancellable=true)
        private void itemcontrol$edible(CallbackInfoReturnable<Boolean> cir) {
            Boolean value = ItemCapabilityRuntime.edibleOverride((ItemStack)(Object)this);
            if (value != null) cir.setReturnValue(value);
        }
        //?}
    }
    @Mixin(LivingEntity.class)
    public abstract static class Equipment {
        //? if >=1.21 {
        /*@Unique private java.util.Map<EquipmentSlot,Integer> itemcontrol$equipmentDamage;
        @Inject(method="doHurtEquipment",at=@At("HEAD"))
        private void itemcontrol$beforeArmor(net.minecraft.world.damagesource.DamageSource source,float amount,EquipmentSlot[] slots,CallbackInfo ci){
            itemcontrol$equipmentDamage=new java.util.EnumMap<>(EquipmentSlot.class);for(var slot:slots)itemcontrol$equipmentDamage.put(slot,((LivingEntity)(Object)this).getItemBySlot(slot).getDamageValue());
        }
        @Inject(method="doHurtEquipment",at=@At("RETURN"))
        private void itemcontrol$afterArmor(net.minecraft.world.damagesource.DamageSource source,float amount,EquipmentSlot[] slots,CallbackInfo ci){
            if(itemcontrol$equipmentDamage!=null)for(var slot:slots)ItemCapabilityRuntime.hurtEquipment((LivingEntity)(Object)this,slot,source,amount,itemcontrol$equipmentDamage.getOrDefault(slot,0));
        }*/
        //?}
        @Inject(method="getEquipmentSlotForItem", at=@At("HEAD"), cancellable=true)
        //? if >=1.21 {
        /*private void itemcontrol$slot(ItemStack stack, CallbackInfoReturnable<EquipmentSlot> cir) {*/
        //?} else {
        private static void itemcontrol$slot(ItemStack stack, CallbackInfoReturnable<EquipmentSlot> cir) {
        //?}
            if (ItemCapabilityRuntime.overridesEquipment(stack)) {
                EquipmentSlot slot = ItemCapabilityRuntime.equipmentSlot(stack);
                cir.setReturnValue(slot == null ? EquipmentSlot.MAINHAND : slot);
            }
        }
    }
    @Mixin(net.minecraft.world.entity.player.Inventory.class)
    public abstract static class InventoryArmor{
        //? if <1.21 {
        @org.spongepowered.asm.mixin.Shadow public Player player;
        @Unique private java.util.Map<EquipmentSlot,Integer> itemcontrol$equipmentDamage;
        @Inject(method="hurtArmor",at=@At("HEAD"))
        private void itemcontrol$beforeArmor(net.minecraft.world.damagesource.DamageSource source,float amount,int[] slots,CallbackInfo ci){itemcontrol$equipmentDamage=new java.util.EnumMap<>(EquipmentSlot.class);for(int index:slots){var slot=EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR,index);itemcontrol$equipmentDamage.put(slot,player.getItemBySlot(slot).getDamageValue());}}
        @Inject(method="hurtArmor",at=@At("RETURN"))
        private void itemcontrol$afterArmor(net.minecraft.world.damagesource.DamageSource source,float amount,int[] slots,CallbackInfo ci){if(itemcontrol$equipmentDamage!=null)for(int index:slots){var slot=EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR,index);ItemCapabilityRuntime.hurtEquipment(player,slot,source,amount,itemcontrol$equipmentDamage.getOrDefault(slot,0));}}
        //?}
    }
}
