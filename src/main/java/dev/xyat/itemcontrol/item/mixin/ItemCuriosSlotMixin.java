package dev.xyat.itemcontrol.item.mixin;

import dev.xyat.itemcontrol.item.compat.ItemCuriosCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import java.util.Map;

/** Uses Curios' slot objects so its inventory, quick equip and tooltip share the same eligibility. */
@Pseudo
//? if >=26.1 {
/*@Mixin(targets = "top.theillusivec4.curios.api.CuriosSlotTypes", remap = false)*/
//?} else {
@Mixin(targets = "top.theillusivec4.curios.mixin.CuriosImplMixinHooks", remap = false)
//?}
public abstract class ItemCuriosSlotMixin {
    //? if >=26.1 {
    /*@Inject(method = "getItemSlotTypes(Lnet/minecraft/world/item/ItemStack;Z)Ljava/util/Map;", at = @At("RETURN"), cancellable = true, remap = false)*/
    //?} else {
    @Inject(method = "getItemStackSlots(Lnet/minecraft/world/item/ItemStack;Z)Ljava/util/Map;", at = @At("RETURN"), cancellable = true, remap = false)
    //?}
    private static void itemcontrol$slots(ItemStack stack, boolean client, CallbackInfoReturnable<Map<String, ISlotType>> cir) {
        cir.setReturnValue(ItemCuriosCompat.configuredSlots(stack, CuriosApi.getSlots(client), cir.getReturnValue()));
    }

    //? if >=26.1 {
    /*@Inject(method = "getItemSlotTypes(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Map;", at = @At("RETURN"), cancellable = true, remap = false)*/
    //?} else {
    @Inject(method = "getItemStackSlots(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Map;", at = @At("RETURN"), cancellable = true, remap = false)
    //?}
    private static void itemcontrol$entitySlots(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Map<String, ISlotType>> cir) {
        cir.setReturnValue(ItemCuriosCompat.configuredSlots(stack, CuriosApi.getEntitySlots(entity), cir.getReturnValue()));
    }
}
