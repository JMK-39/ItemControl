package dev.xyat.itemcontrol.item.event;

import dev.xyat.itemcontrol.item.ItemModule;
import dev.xyat.itemcontrol.item.config.ItemProtectionConfig;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.itemcontrol.item.util.ItemProtectionList;
import dev.xyat.itemcontrol.item.config.ItemPropertyRule;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.event.KineticExternalEvents;
import dev.xyat.kineticcore.api.world.event.KineticWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class ItemProtectionHandler {
    private static boolean registered;

    private ItemProtectionHandler() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        KineticWorldEvents.onEntityJoin(KineticEventPriority.NORMAL, context -> onEntityJoinWorld(context.entity()));
        KineticExternalEvents.subscribe(ItemEntityDamageEvent.class, ItemProtectionHandler::onItemDamage);
    }

    private static void onEntityJoinWorld(net.minecraft.world.entity.Entity entity) {
        if (!ItemProtectionConfig.enableItemProtection || entity.level().isClientSide) return;

        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) return;

            ItemProtectionConfig.ProtectionRule rule = ItemProtectionConfig.getProtectionRule(stack);
            ItemPropertyRule propertyRule = ItemPropertyConfig.activeProtection(stack);
            if (rule != null || propertyRule != null) {
                if (rule != null || Boolean.TRUE.equals(propertyRule.persistent())) {
                    itemEntity.setUnlimitedLifetime();
                }

                CompoundTag entityData = new CompoundTag();
                itemEntity.saveWithoutId(entityData);
                boolean hasOwner = entityData.hasUUID("Owner") || entityData.hasUUID("Thrower");

                if (!hasOwner) {
                    if ((rule != null && rule.noGravity)
                            || (propertyRule != null && Boolean.TRUE.equals(propertyRule.noGravity()))) {
                        itemEntity.setNoGravity(true);
                        itemEntity.setDeltaMovement(Vec3.ZERO);
                    }
                    if ((rule != null && rule.glowing)
                            || (propertyRule != null && Boolean.TRUE.equals(propertyRule.glowing()))) {
                        itemEntity.setGlowingTag(true);
                    }
                }
            }
        }
    }

    private static void onItemDamage(ItemEntityDamageEvent event) {
        ItemEntity itemEntity = event.getEntity();
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        DamageSource source = event.getSource();

        if (ItemProtectionConfig.isGlobalDirectEntityImmune(source)) {
            event.setCanceled(true);
            return;
        }

        if (ItemProtectionConfig.isGlobalItemDamageImmune(source)) {
            event.setCanceled(true);
            return;
        }

        ItemPropertyRule propertyRule = ItemProtectionConfig.enableItemProtection
                ? ItemPropertyConfig.activeProtection(stack) : null;
        if (propertyRule != null && (Boolean.TRUE.equals(propertyRule.fireResistant())
                && source.is(DamageTypeTags.IS_FIRE)
                || Boolean.TRUE.equals(propertyRule.explosionImmune())
                && source.is(DamageTypeTags.IS_EXPLOSION))) {
            event.setCanceled(true);
            return;
        }

        Level level = itemEntity.level();
        String damageId = ItemProtectionConfig.getDamageSourceId(source);

        ItemProtectionConfig.ProtectionRule rule = ItemProtectionConfig.getProtectionRule(stack);
        boolean isProtected = rule != null
                || propertyRule != null && propertyRule.hasEnabledProtection()
                || ItemProtectionList.isFireImmune(stack);

        if (!isProtected) return;

        if (damageId.equals("minecraft:out_of_world") || source.getMsgId().equals("outOfWorld")) {
            if (ItemProtectionConfig.enableVoidSalvage) {
                event.setCanceled(true);

                int safeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, itemEntity.getBlockX(), itemEntity.getBlockZ());

                if (safeY > level.getMinBuildHeight()) {
                    itemEntity.teleportTo(itemEntity.getX(), safeY + 1.5, itemEntity.getZ());
                } else {
                    BlockPos spawn = level.getSharedSpawnPos();
                    itemEntity.teleportTo(spawn.getX(), spawn.getY() + 1.0, spawn.getZ());
                }

                itemEntity.setDeltaMovement(Vec3.ZERO);
                ItemModule.LOGGER.debug("Void Salvage: Teleported protected item {} to safety", stack.getHoverName().getString());
                return;
            }
        }

        if (ItemProtectionList.isFireImmune(stack) && source.is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
            return;
        }

        if (rule != null) {
            if (rule.fireImmune && source.is(DamageTypeTags.IS_FIRE)) {
                event.setCanceled(true);
            } else if (rule.explosionImmune && source.is(DamageTypeTags.IS_EXPLOSION)) {
                event.setCanceled(true);
            }
        }
    }
}
