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
                //? if >=26.1 {
/*.computeIfAbsent(TYPE);*/
//?} else if >=1.21 {
/*.computeIfAbsent(new Factory<>(CleanerSavedData::new, CleanerSavedData::load, null), DATA_NAME);*/
//?} else {
.computeIfAbsent(CleanerSavedData::load, CleanerSavedData::new, DATA_NAME);
//?}

    }

    // 26.1 saved data is typed by id and codec; the stored compound keeps its layout and items use the world's registries.
    //? if >=26.1 {
    /*private static final net.minecraft.world.level.saveddata.SavedDataType<CleanerSavedData> TYPE = new net.minecraft.world.level.saveddata.SavedDataType<>(
            dev.xyat.kineticcore.api.resource.KineticResourceIds.of("itemcontrol", DATA_NAME), level -> new CleanerSavedData(),
            level -> CompoundTag.CODEC.xmap(tag -> load(tag, lookup(level)), data -> data.save(new CompoundTag(), lookup(level))));

    private static net.minecraft.core.HolderLookup.Provider lookup(ServerLevel level) {
        return level != null ? level.registryAccess() : dev.xyat.kineticcore.api.runtime.KineticServerRuntime.currentServer().registryAccess();
    }
    *///?}

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

    //? if >=1.21 {
/*public static CleanerSavedData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookup) {*/
//?} else {
public static CleanerSavedData load(CompoundTag tag) {
//?}

        CleanerSavedData data = new CleanerSavedData();
        if (tag.contains("History", Tag.TAG_LIST)) {
            ListTag historyTag = tag.getList("History", Tag.TAG_LIST);

            for (Tag t : historyTag) {
                if (t instanceof ListTag itemTagList) {
                    BigTrashContainer container = new BigTrashContainer(CleanerConfig.getTrashBinSlots());

                    for (int i = 0; i < itemTagList.size(); i++) {
                        CompoundTag itemTag = dev.xyat.itemcontrol.util.Nbt.compound(itemTagList, i);
                        int slot = dev.xyat.itemcontrol.util.Nbt.intValue(itemTag, "Slot");
                        //? if >=1.21 {
/*ItemStack stack = dev.xyat.itemcontrol.util.Nbt.loadItem(itemTag, lookup);*/
//?} else {
ItemStack stack = ItemStack.of(itemTag);
//?}


                        // 原版 tag 里 Count 是 byte，超过 127 会变负数。
                        // 这里我们读取自己写入的真实 int 数量
                        if (itemTag.contains("RealCount")) {
                            stack.setCount(dev.xyat.itemcontrol.util.Nbt.intValue(itemTag, "RealCount"));
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

    //? if >=26.1 {
    /*@Nonnull
    public CompoundTag save(@NotNull CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookup) {
    *///?} else if >=1.21 {
    /*@Override
    @Nonnull
    public CompoundTag save(@NotNull CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookup) {
    *///?} else {
    @Override
    @Nonnull
    public CompoundTag save(@NotNull CompoundTag tag) {
    //?}

        ListTag historyTag = new ListTag();

        for (BigTrashContainer container : history) {
            ListTag itemTagList = new ListTag();

            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putInt("Slot", i);
//? if >=1.21 {
/*                    // Count codec is bounded; save a one-item template and retain RealCount separately.
                    itemTag.merge(dev.xyat.itemcontrol.util.Nbt.saveItem(stack.copyWithCount(1), lookup));*/
//?} else {
                    stack.save(itemTag);
//?}


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

        //? if >=1.21 {
        /*@Override
        public int getMaxStackSize(ItemStack stack) {
            return Integer.MAX_VALUE;
        }
        *///?}

        @Override
        public @NotNull ItemStack addItem(@Nonnull ItemStack pStack) {
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
