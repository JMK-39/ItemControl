package dev.xyat.itemcontrol.cleaner;

import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.LinkedList;

public class CleanerSavedData extends net.minecraft.world.level.saveddata.SavedData {
    public static final String DATA_NAME = "itemcontrol_trashbin";

    // 历史记录改为使用能容纳 21亿 的超级容器
    private final LinkedList<BigTrashContainer> history = new LinkedList<>();

    public static CleanerSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(CleanerSavedData::load, CleanerSavedData::new, DATA_NAME);
    }

    public void addRecord(BigTrashContainer container) {
        if (!CleanerConfig.enableTrashBin) return;
        history.addFirst(container);
        while (history.size() > CleanerConfig.trashBinHistorySize) {
            history.removeLast();
        }
        this.setDirty();
    }

    public BigTrashContainer getRecord(int index) {
        if (index >= 0 && index < history.size()) {
            return history.get(index);
        }
        return new BigTrashContainer(CleanerConfig.getTrashBinSlots());
    }

    public int getHistorySize() {
        return history.size();
    }

    public void clearAll() {
        history.clear();
        this.setDirty();
    }

    public static CleanerSavedData load(CompoundTag tag) {
        CleanerSavedData data = new CleanerSavedData();
        if (tag.contains("History", Tag.TAG_LIST)) {
            ListTag historyTag = tag.getList("History", Tag.TAG_LIST);

            for (Tag t : historyTag) {
                if (t instanceof ListTag itemTagList) {
                    BigTrashContainer container = new BigTrashContainer(CleanerConfig.getTrashBinSlots());

                    for (int i = 0; i < itemTagList.size(); i++) {
                        CompoundTag itemTag = itemTagList.getCompound(i);
                        int slot = itemTag.getInt("Slot");
                        ItemStack stack = ItemStack.of(itemTag);

                        // 原版 tag 里 Count 是 byte，超过 127 会变负数。
                        // 这里我们读取自己写入的真实 int 数量
                        if (itemTag.contains("RealCount")) {
                            stack.setCount(itemTag.getInt("RealCount"));
                        }

                        if (slot >= 0 && slot < container.getContainerSize()) {
                            container.setItem(slot, stack);
                        }
                    }
                    data.history.add(container);
                }
            }
        }
        return data;
    }

    @Override
    @Nonnull
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag historyTag = new ListTag();

        for (BigTrashContainer container : history) {
            ListTag itemTagList = new ListTag();

            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putInt("Slot", i);
                    stack.save(itemTag);

                    // 保存真正的 int 型数量，规避 byte 溢出
                    itemTag.putInt("RealCount", stack.getCount());
                    itemTagList.add(itemTag);
                }
            }
            historyTag.add(itemTagList);
        }

        tag.put("History", historyTag);
        return tag;
    }

    // 自定义无视堆叠上限的容器 (支持 2,147,483,647)
    public static class BigTrashContainer extends SimpleContainer {
        public BigTrashContainer(int size) {
            super(size);
        }

        @Override
        public int getMaxStackSize() {
            return Integer.MAX_VALUE; // 突破 64 限制
        }

        @Override
        public @NotNull ItemStack addItem(ItemStack pStack) {
            ItemStack itemstack = pStack.copy();
            this.moveItemToOccupiedSlotsWithSameType(itemstack);
            if (itemstack.isEmpty()) {
                return ItemStack.EMPTY;
            } else {
                this.moveItemToEmptySlots(itemstack);
                return itemstack.isEmpty() ? ItemStack.EMPTY : itemstack;
            }
        }

        private void moveItemToOccupiedSlotsWithSameType(ItemStack pStack) {
            for (int i = 0; i < this.getContainerSize(); ++i) {
                ItemStack itemstack = this.getItem(i);
                if (ItemStack.isSameItemSameTags(itemstack, pStack)) {
                    // 计算离 int32 上限还差多少
                    int availableSpace = Integer.MAX_VALUE - itemstack.getCount();
                    int toMove = Math.min(pStack.getCount(), availableSpace);
                    if (toMove > 0) {
                        itemstack.grow(toMove);
                        pStack.shrink(toMove);
                        this.setChanged();
                    }
                    if (pStack.isEmpty()) return;
                }
            }
        }

        private void moveItemToEmptySlots(ItemStack pStack) {
            for (int i = 0; i < this.getContainerSize(); ++i) {
                ItemStack itemstack = this.getItem(i);
                if (itemstack.isEmpty()) {
                    this.setItem(i, pStack.copy());
                    pStack.setCount(0);
                    return;
                }
            }
        }
    }
}
