package dev.xyat.itemcontrol.item.mixin.client;

import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.util.ItemBanControl;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Set;

@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {
    @Inject(method = "buildContents", at = @At("HEAD"))
    private void itemcontrol_item$beginBuildingContents(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        ItemBanControl.beginBuildingTab();
    }

    @Inject(method = "buildContents", at = @At("RETURN"))
    private void itemcontrol_item$endBuildingContents(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        ItemBanControl.endBuildingTab();
    }

    @Inject(method = "getDisplayItems", at = @At("RETURN"), cancellable = true)
    private void itemcontrol_item$filterDisplayItems(CallbackInfoReturnable<Collection<ItemStack>> cir) {
        cir.setReturnValue(itemcontrol_item$filterHiddenItems(cir.getReturnValue()));
    }

    @Inject(method = "getSearchTabDisplayItems", at = @At("RETURN"), cancellable = true)
    private void itemcontrol_item$filterSearchTabDisplayItems(CallbackInfoReturnable<Collection<ItemStack>> cir) {
        cir.setReturnValue(itemcontrol_item$filterHiddenItems(cir.getReturnValue()));
    }

    @Inject(method = "contains", at = @At("HEAD"), cancellable = true)
    private void itemcontrol_item$hideFilteredFromContains(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && !stack.isEmpty() && BanItemConfig.shouldHideFromCreative(stack)) {
            cir.setReturnValue(false);
        }
    }

    private static Set<ItemStack> itemcontrol_item$filterHiddenItems(Collection<ItemStack> source) {
        Set<ItemStack> result = ItemStackLinkedSet.createTypeAndTagSet();
        if (source == null || source.isEmpty()) return result;
        for (ItemStack stack : source) {
            if (stack == null || stack.isEmpty()) continue;
            if (!BanItemConfig.shouldHideFromCreative(stack)) {
                result.add(stack);
            }
        }
        return result;
    }
}
