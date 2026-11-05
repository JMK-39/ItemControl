package dev.xyat.itemcontrol.item.mixin;

import dev.xyat.itemcontrol.item.config.BanItemConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/**
 * Keeps banned items out of the world at every place they enter it, not only when they are put into a menu slot:
 * generated loot (chests, mob and block drops, fishing, archaeology, loot modifiers), every dropped item entity
 * (including mob equipment and items from saves), items given straight into a player's inventory, and banned items
 * already sitting in a container or inventory when it is opened. Every hook returns at once when nothing is banned.
 */
public final class ItemBanMixins {
    private ItemBanMixins() {
    }

    @Mixin(LootTable.class)
    public static abstract class LootTables {
        /** Every generated stack passes this consumer; banned ones are dropped before they reach a chest or the ground. */
        @ModifyVariable(
                method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
                at = @At("HEAD"), argsOnly = true)
        private Consumer<ItemStack> itemcontrol_ban$filterLoot(Consumer<ItemStack> output) {
            if (!BanItemConfig.hasBanRules()) return output;
            return stack -> {
                if (!BanItemConfig.isVoided(stack)) output.accept(stack);
            };
        }

        /** Global loot modifiers add their stacks after the table ran; filter the finished list too. */
        @Inject(
                method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
                at = @At("RETURN"))
        private void itemcontrol_ban$filterModifiedLoot(LootContext context, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
            ObjectArrayList<ItemStack> items = cir.getReturnValue();
            if (items != null && !items.isEmpty() && BanItemConfig.hasBanRules()) items.removeIf(BanItemConfig::isVoided);
        }
    }

    @Mixin(ItemEntity.class)
    public static abstract class DroppedItems {
        /** Any item entity, however it was made or loaded, holds nothing when its item is banned; it then removes itself. */
        @ModifyVariable(method = "setItem", at = @At("HEAD"), argsOnly = true)
        private ItemStack itemcontrol_ban$voidDroppedItem(ItemStack stack) {
            if (((ItemEntity) (Object) this).level().isClientSide()) return stack;
            return BanItemConfig.isVoided(stack) ? ItemStack.EMPTY : stack;
        }
    }

    @Mixin(Inventory.class)
    public static abstract class PlayerInventory {
        /** Items given straight into an inventory (commands, rewards, pick-ups) are used up instead of stored. */
        @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
        private void itemcontrol_ban$voidAddedItem(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
            if (!((Inventory) (Object) this).player.level().isClientSide() && BanItemConfig.isVoided(stack)) stack.setCount(0);
        }

        @ModifyVariable(method = "setItem", at = @At("HEAD"), argsOnly = true)
        private ItemStack itemcontrol_ban$voidSetItem(ItemStack stack) {
            if (((Inventory) (Object) this).player.level().isClientSide()) return stack;
            return BanItemConfig.isVoided(stack) ? ItemStack.EMPTY : stack;
        }
    }

    @Mixin(ServerPlayer.class)
    public static abstract class OpenedMenus {
        /** Containers filled before an item was banned are cleaned when someone opens them (and the player's own slots). */
        @Inject(method = "initMenu", at = @At("HEAD"))
        private void itemcontrol_ban$sweepOpenedMenu(AbstractContainerMenu menu, CallbackInfo ci) {
            if (menu == null || !BanItemConfig.hasBanRules()) return;
            for (Slot slot : menu.slots) {
                if (BanItemConfig.isVoided(slot.getItem())) slot.set(ItemStack.EMPTY);
            }
        }
    }
}
