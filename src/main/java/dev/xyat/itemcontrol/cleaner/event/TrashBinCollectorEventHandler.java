package dev.xyat.itemcontrol.cleaner.event;

import dev.xyat.itemcontrol.cleaner.CleanerSavedData;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class TrashBinCollectorEventHandler {

    public static final class TrashAccumulator {
        private final Map<StackKey, AggregatedStack> entries = new LinkedHashMap<>();

        public void add(ItemStack stack) {
            if (!CleanerConfig.enableTrashBin || stack == null || stack.isEmpty()) {
                return;
            }
            if (CleanerConfig.isItemTrashBlacklisted(stack)) {
                return;
            }

            StackKey lookupKey = StackKey.lookup(stack);
            AggregatedStack existing = entries.get(lookupKey);
            if (existing != null) {
                existing.addCount(stack.getCount());
                return;
            }

            if (entries.size() >= CleanerConfig.getTrashBinSlots()) {
                return;
            }

            ItemStack template = stack.copy();
            template.setCount(1);
            entries.put(StackKey.stored(stack), new AggregatedStack(template, stack.getCount()));
        }

        public void commit(MinecraftServer server) {
            if (!CleanerConfig.enableTrashBin || server == null || entries.isEmpty()) {
                entries.clear();
                return;
            }

            CleanerSavedData.BigTrashContainer container =
                    new CleanerSavedData.BigTrashContainer(CleanerConfig.getTrashBinSlots());
            boolean hasContent = false;
            boolean full = false;

            for (AggregatedStack entry : entries.values()) {
                long remaining = entry.count;
                while (remaining > 0L) {
                    int partCount = (int) Math.min(Integer.MAX_VALUE, remaining);
                    ItemStack part = entry.template.copy();
                    part.setCount(partCount);
                    ItemStack remainder = container.addItem(part);
                    int remainingPartCount = remainder.isEmpty() ? 0 : Math.max(0, remainder.getCount());
                    int inserted = partCount - remainingPartCount;

                    if (inserted <= 0) {
                        full = true;
                        break;
                    }

                    hasContent = true;
                    remaining -= inserted;

                    if (!remainder.isEmpty()) {
                        full = true;
                        break;
                    }
                }

                if (full) {
                    break;
                }
            }

            if (hasContent) {
                CleanerSavedData.get(server.overworld()).addRecord(container);
            }
            entries.clear();
        }
    }

    private static final class AggregatedStack {
        private final ItemStack template;
        private long count;

        private AggregatedStack(ItemStack template, int count) {
            this.template = template;
            this.count = Math.max(0, count);
        }

        private void addCount(int amount) {
            if (amount <= 0 || count == Long.MAX_VALUE) {
                return;
            }
            long maxAdd = Long.MAX_VALUE - count;
            count += Math.min(maxAdd, amount);
        }
    }

    private static final class StackKey {
        private final Item item;
        private final CompoundTag tag;
        private final int hashCode;

        private StackKey(Item item, CompoundTag tag) {
            this.item = item;
            this.tag = tag;
            this.hashCode = 31 * System.identityHashCode(item) + Objects.hashCode(tag);
        }

        private static StackKey lookup(ItemStack stack) {
            return new StackKey(stack.getItem(), stack.getTag());
        }

        private static StackKey stored(ItemStack stack) {
            CompoundTag stackTag = stack.getTag();
            return new StackKey(stack.getItem(), stackTag == null ? null : stackTag.copy());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StackKey other)) {
                return false;
            }
            return item == other.item && Objects.equals(tag, other.tag);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
