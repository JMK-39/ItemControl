package dev.xyat.itemcontrol.cleaner.client;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

public class ScrollableContainer implements Container {
    private Container realData;
    private final int viewRows;
    private int scrollOffset = 0;

    public ScrollableContainer(Container realData, int viewRows) {
        this.realData = realData;
        this.viewRows = viewRows;
    }

    public void setRealData(Container newData) { this.realData = newData; }
    public void setScrollOffset(int rowIndex) {
        int totalRows = realData.getContainerSize() / 9;
        this.scrollOffset = Math.max(0, Math.min(rowIndex, Math.max(0, totalRows - viewRows)));
    }

    private int getRealIndex(int viewSlotIndex) { return viewSlotIndex + (scrollOffset * 9); }
    @Override public int getContainerSize() { return viewRows * 9; }
    @Override public boolean isEmpty() { return realData.isEmpty(); }
    @Override @Nonnull public ItemStack getItem(int i) {
        int idx = getRealIndex(i);
        return idx < realData.getContainerSize() ? realData.getItem(idx) : ItemStack.EMPTY;
    }
    @Override @Nonnull public ItemStack removeItem(int i, int j) { return realData.removeItem(getRealIndex(i), j); }
    @Override @Nonnull public ItemStack removeItemNoUpdate(int i) { return realData.removeItemNoUpdate(getRealIndex(i)); }
    @Override public void setItem(int i, @Nonnull ItemStack s) { realData.setItem(getRealIndex(i), s); }
    @Override public void setChanged() { realData.setChanged(); }
    @Override public boolean stillValid(@Nonnull Player p) { return realData.stillValid(p); }
    @Override public void clearContent() { realData.clearContent(); }
}
