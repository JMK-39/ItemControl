package dev.xyat.itemcontrol.item;

import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class VoidPlaceholderItem extends Item {
    public VoidPlaceholderItem() {
        super(properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC));
    }

    // Since 1.21.2 an item must know its registry id when it is built.
    private static Properties properties() {
        Properties properties = new Properties();
        //? if >=26.1 {
        /*properties.setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,
                dev.xyat.kineticcore.api.resource.KineticResourceIds.of("itemcontrol", "void_placeholder")));
        *///?}
        return properties;
    }

    /**
     * 添加物品说明 (Tips)
     */
    @Override
    //? if >=26.1 {
/*public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.accept(KineticI18n.translatable("tip.itemcontrol.item.void_placeholder.warning"));
        tooltip.accept(KineticI18n.translatable("tip.itemcontrol.item.void_placeholder.usage"));
    }*/
//?} else if >=1.21 {
/*public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {*/
//?} else {
public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
//?}
//? if <26.1 {

        // 第一行：警告这是禁用物品 (红色)
        tooltip.add(KineticI18n.translatable("tip.itemcontrol.item.void_placeholder.warning"));
        // 第二行：说明无法获取和使用 (灰色)
        tooltip.add(KineticI18n.translatable("tip.itemcontrol.item.void_placeholder.usage"));
    }
//?}

    @Override
    //? if >=26.1 {
    /*public void inventoryTick(@NotNull ItemStack stack, @NotNull net.minecraft.server.level.ServerLevel level, @NotNull Entity entity, @Nullable net.minecraft.world.entity.EquipmentSlot slot) {
    *///?} else {
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
    //?}
        if (!level.isClientSide() && entity instanceof Player player) {
            if (!player.isCreative()) {
                stack.setCount(0);
            }
        }
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return player.isCreative();
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.getOwner() == null || (entity.getOwner() instanceof Player p && !p.isCreative())) {
            entity.discard();
            return true;
        }
        return false;
    }
}
