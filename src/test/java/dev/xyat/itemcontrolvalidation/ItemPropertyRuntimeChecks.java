package dev.xyat.itemcontrolvalidation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.itemcontrol.item.property.ItemEquipmentRefresh;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
//? if >=26.1 {
/*import net.minecraft.world.entity.monster.zombie.Zombie;*/
//?} else {
import net.minecraft.world.entity.monster.Zombie;
//?}
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Opt-in real server equipment tests; disk rules and fixture entities are restored before capture. */
public final class ItemPropertyRuntimeChecks {
    public static volatile boolean done;
    public static volatile boolean failed;
    private static int ticks;
    private static Zombie entity;
    private static String before;
    private static ItemStack sword;
    private static double damage, armor;
    public static void install() {
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END, ItemPropertyRuntimeChecks::tick);
    }
    private static void tick(MinecraftServer server) {
        if (done || server.getPlayerList().getPlayers().isEmpty()) return;
        try {
            int step = ++ticks;
            if (step == 10) {
                if (Boolean.getBoolean("itemcontrol.requireNoCurios")) require(!dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available(), "Curios absent in optional-dependency validation");
                before = ItemPropertyConfig.pendingJson();
                var player = server.getPlayerList().getPlayers().get(0);
                entity = new Zombie(EntityType.ZOMBIE, server.overworld());
                entity.setPos(player.getX()+2, player.getY(), player.getZ()); entity.setNoAi(true); entity.setInvulnerable(true);
                sword = new ItemStack(Items.DIAMOND_SWORD);
                entity.setItemSlot(EquipmentSlot.MAINHAND, sword);
                entity.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                server.overworld().addFreshEntity(entity);
            }
            if (step == 25) {
                damage = entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
                armor = entity.getAttributeValue(Attributes.ARMOR);
                JsonObject document = JsonParser.parseString(before).getAsJsonObject();
                document.add("minecraft:diamond_sword", JsonParser.parseString("{\"attack_damage\":13,\"max_damage\":333}"));
                document.add("minecraft:diamond_chestplate", JsonParser.parseString("{\"armor\":17}"));
                require(ItemPropertyConfig.savePending(document.toString()).success(), "save valid rules");
                ItemEquipmentRefresh.applyPending(server);
                require(entity.getAttributeValue(Attributes.ATTACK_DAMAGE) == entity.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue()+13, "held attack updates on save");
                require(entity.getAttributeValue(Attributes.ARMOR) == entity.getAttribute(Attributes.ARMOR).getBaseValue()+17, "worn armor updates on save");
                require(sword.getMaxDamage() == 333 && entity.getMainHandItem() == sword, "existing stack durability without reequip");
                org.slf4j.LoggerFactory.getLogger(ItemPropertyRuntimeChecks.class).info("ITEM_PROPERTY_LIVE_PASS held damage, worn armor and existing durability");
            }
            if (step == 40) {
                require(entity.getAttributeValue(Attributes.ATTACK_DAMAGE) == entity.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue()+13, "damage stable after native equipment ticks");
                require(entity.getAttributeValue(Attributes.ARMOR) == entity.getAttribute(Attributes.ARMOR).getBaseValue()+17, "armor stable after native equipment ticks");
                if (dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) CurioRuntimeChecks.run(server);
            }
            if (step == 50) {
                if (dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) CurioRuntimeChecks.verifyMode(server,"replace",9,5);
            }
            if (step == 60 && dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) CurioRuntimeChecks.verifyMode(server,"remove",5,0);
            if (step == 70 && dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) CurioRuntimeChecks.removeFixture();
            if (step == 80) {
                restore(server);
                if (dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) CurioRuntimeChecks.close();
            }
            if (step == 90) {
                require(Math.abs(entity.getAttributeValue(Attributes.ATTACK_DAMAGE)-damage)<0.0001, "reset removes stale damage modifiers");
                require(Math.abs(entity.getAttributeValue(Attributes.ARMOR)-armor)<0.0001, "reset removes stale armor modifiers");
                entity.discard(); done=true;
                org.slf4j.LoggerFactory.getLogger(ItemPropertyRuntimeChecks.class).info("ITEM_PROPERTY_RUNTIME_PASS stable equipment and reset");
            }
        } catch (Throwable error) {
            failed=true; done=true;
            org.slf4j.LoggerFactory.getLogger(ItemPropertyRuntimeChecks.class).error("ITEM_PROPERTY_RUNTIME_FAIL", error);
            try { restore(server); } catch (Throwable cleanup) { error.addSuppressed(cleanup); }
            if(dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()){CurioRuntimeChecks.removeFixture();CurioRuntimeChecks.close();}
            if(entity!=null) entity.discard();
        }
    }
    private static void restore(MinecraftServer server) {
        if(before!=null) { require(ItemPropertyConfig.savePending(before).success(), "restore disk rules"); ItemEquipmentRefresh.applyPending(server); }
    }
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
