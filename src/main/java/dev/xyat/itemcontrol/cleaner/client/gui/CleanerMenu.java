package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.itemcontrol.cleaner.client.ScrollableContainer;
import dev.xyat.itemcontrol.cleaner.CleanerInit;
import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import dev.xyat.itemcontrol.cleaner.CleanerSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

public class CleanerMenu extends AbstractContainerMenu {
    public final ScrollableContainer scrollableContainer;
    private final Player player;

    public CleanerMenu(int id, Inventory inv) {
        this(id, inv, new CleanerSavedData.BigTrashContainer(CleanerConfig.getTrashBinSlots()));
    }

    public CleanerMenu(int id, Inventory inv, Container realData) {
        super(CleanerInit.TRASH_BIN.get(), id);
        this.player = inv.player;
        this.scrollableContainer = new ScrollableContainer(realData, 6);

        // 1. 垃圾桶区域：使用我们自定义的超级 TrashSlot
        for (int i = 0; i < 6; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new TrashSlot(this.scrollableContainer, j + i * 9, 8 + j * 18, 18 + i * 18));
            }
        }
        // 2. 玩家背包区域：使用原版普通 Slot
        for (int l = 0; l < 3; ++l) {
            for (int j1 = 0; j1 < 9; ++j1) {
                this.addSlot(new Slot(inv, j1 + l * 9 + 9, 8 + j1 * 18, 140 + l * 18));
            }
        }
        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(inv, i1, 8 + i1 * 18, 198));
        }
    }

    public void updateState(int historyIndex, int rowOffset) {
        if (!player.level().isClientSide && player.level() instanceof ServerLevel sl) {
            this.scrollableContainer.setRealData(CleanerSavedData.get(sl).getRecord(historyIndex));
            this.scrollableContainer.setScrollOffset(rowOffset);
            this.broadcastChanges();
        } else {
            this.scrollableContainer.setScrollOffset(rowOffset);
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.player instanceof ServerPlayer serverPlayer) {
            int[] counts = new int[54];
            for (int i = 0; i < 54; i++) {
                counts[i] = this.slots.get(i).getItem().getCount();
            }
            CleanerNetwork.sendToPlayer(new CleanerNetwork.SyncTrashBinCounts(this.containerId, counts), serverPlayer);
        }
    }

    @Override
    @Nonnull
    public ItemStack quickMoveStack(@Nonnull Player p, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            if (index < 54) {
                // 【情况A】 垃圾桶 -> 背包 (Shift 快捷拿取)
                // 动态获取物品原版最大堆叠(剑=1, 珍珠=16, 石头=64)
                int naturalMax = slotStack.getMaxStackSize();
                int amountToTake = Math.min(slotStack.getCount(), naturalMax);

                ItemStack stackToMove = slotStack.copy();
                stackToMove.setCount(amountToTake);

                if (!this.moveItemStackTo(stackToMove, 54, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }

                int movedAmount = amountToTake - stackToMove.getCount();
                slotStack.shrink(movedAmount);

            } else {
                // 【情况B】 背包 -> 垃圾桶 (Shift 存入)
                if (!this.moveItemStackTo(slotStack, 0, 54, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }

    @Override public boolean stillValid(@Nonnull Player p) { return true; }
    public int getTotalRows() { return CleanerConfig.trashBinRows; }

    // 自定义垃圾桶槽位：无限存，限流取
    public static class TrashSlot extends Slot {
        public TrashSlot(Container pContainer, int pSlot, int pX, int pY) {
            super(pContainer, pSlot, pX, pY);
        }

        // 允许各种途径突破 64 限制存入
        @Override
        public int getMaxStackSize() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMaxStackSize(@Nonnull ItemStack pStack) {
            return Integer.MAX_VALUE;
        }

        // 拦截鼠标单次点击直接抽出几万个物品
        @Override
        @Nonnull
        public ItemStack remove(int pAmount) {
            // 获取物品原版最大堆叠 (剑=1, 珍珠=16, 苹果=64)
            int naturalMax = this.getItem().isEmpty() ? 64 : this.getItem().getMaxStackSize();
            // 无论底层想拿多少，最高不能超过 naturalMax
            int safeAmount = Math.min(pAmount, naturalMax);
            return super.remove(safeAmount);
        }
    }
}
