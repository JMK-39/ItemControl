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
            // Allow vanilla login invulnerability to expire before real damage checks.
            int step = ++ticks - 80;
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
                // Modpack spawn handlers may equip extra armor. Establish the controlled loadout after joining.
                for(var slot:EquipmentSlot.values())entity.setItemSlot(slot,ItemStack.EMPTY);
                entity.setItemSlot(EquipmentSlot.MAINHAND,sword);
                entity.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
            }
            if (step == 25) {
                damage = entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
                armor = entity.getAttributeValue(Attributes.ARMOR);
                JsonObject document = JsonParser.parseString(before).getAsJsonObject();
                document.add("minecraft:diamond_sword", JsonParser.parseString("{\"attack_damage\":13,\"max_damage\":333}"));
                document.add("minecraft:diamond_chestplate", JsonParser.parseString("{\"armor\":17}"));
                require(ItemPropertyConfig.savePending(document.toString()).success(), "save valid rules");
                ItemEquipmentRefresh.applyPending(server);
                verifyCapabilities(server);
                require(entity.getAttributeValue(Attributes.ATTACK_DAMAGE) == entity.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue()+13, "held attack updates on save");
                require(entity.getAttributeValue(Attributes.ARMOR) == entity.getAttribute(Attributes.ARMOR).getBaseValue()+17, "worn armor updates on save actual="+entity.getAttributeValue(Attributes.ARMOR)+" base="+entity.getAttribute(Attributes.ARMOR).getBaseValue()+" slot="+dev.xyat.itemcontrol.item.property.ItemPropertyReads.armorSlot(entity.getItemBySlot(EquipmentSlot.CHEST))+" modifiers="+entity.getAttribute(Attributes.ARMOR).getModifiers());
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
            if (step == 65 && dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) CurioRuntimeChecks.equipSecond();
            if (step == 70 && dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) CurioRuntimeChecks.verifyStackedAndRemoveFirst();
            if (step == 75 && dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) CurioRuntimeChecks.verifyRemainingAndNegative(server);
            if (step == 80 && dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) CurioRuntimeChecks.verifyNegativeAndRemove();
            if (step == 90) {
                restore(server);
                if (dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available()) CurioRuntimeChecks.close();
            }
            if (step == 100) {
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
    private static void verifyCapabilities(MinecraftServer server) {
        var player = server.getPlayerList().getPlayers().get(0);
        JsonObject document = JsonParser.parseString(ItemPropertyConfig.pendingJson()).getAsJsonObject();
        var originalChestRule=document.has("minecraft:diamond_chestplate")?document.get("minecraft:diamond_chestplate").deepCopy():null;
        document.add("minecraft:brick", JsonParser.parseString("{\"equipment_slot\":\"chest\",\"armor\":8,\"edible\":true,\"nutrition\":4,\"saturation\":0.25,\"food_remainder\":\"minecraft:bowl\",\"food_effects\":[{\"effect\":\"minecraft:speed\",\"duration\":200,\"amplifier\":1,\"probability\":1}]}"));
        document.add("minecraft:iron_sword", JsonParser.parseString("{\"attributes\":[{\"attribute\":\"minecraft:generic.attack_damage\",\"slot\":\"mainhand\",\"mode\":\"add\",\"amount\":3,\"operation\":\"ADDITION\"}]}"));
        document.add("minecraft:stick",JsonParser.parseString("{\"attack_damage\":5,\"max_damage\":16,\"equipment_slot\":\"head\"}"));
        require(ItemPropertyConfig.savePending(document.toString()).success(), "save stacked capabilities");
        ItemEquipmentRefresh.applyPending(server);
        ItemStack brick = new ItemStack(Items.BRICK, 2);
        require(dev.xyat.itemcontrol.item.property.ItemPropertyReads.armorSlot(brick) == EquipmentSlot.CHEST, "arbitrary item receives equipment slot");
        require(dev.xyat.itemcontrol.item.property.ItemPropertyReads.isFood(brick), "equipment is also food");
        ItemStack held = player.getMainHandItem().copy();
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST).copy();
        ItemStack head=player.getItemBySlot(EquipmentSlot.HEAD).copy();
        boolean shift = player.isShiftKeyDown(), creative = player.getAbilities().instabuild,invulnerable=player.getAbilities().invulnerable;
        float health=player.getHealth();
        var gameMode=player.gameMode.getGameModeForPlayer();boolean entityInvulnerable=player.isInvulnerable();int frames=player.invulnerableTime;
        int hunger = player.getFoodData().getFoodLevel();
        float saturation = player.getFoodData().getSaturationLevel();
        //? if >=26.1 {
        /*var speed = net.minecraft.world.effect.MobEffects.SPEED;*/
        //?} else {
        var speed = net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED;
        //?}
        var oldSpeed = player.getEffect(speed);
        var inventory = new java.util.ArrayList<ItemStack>();
        for(int index=0;index<player.getInventory().getContainerSize();index++)inventory.add(player.getInventory().getItem(index).copy());
        try {
            player.getAbilities().instabuild = false;
            player.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);
            document.add("minecraft:diamond_chestplate",JsonParser.parseString("{\"equipment_slot\":\"none\"}"));
            require(ItemPropertyConfig.savePending(document.toString()).success(),"save armor not wearable");ItemEquipmentRefresh.applyPending(server);
            var chestplate=new ItemStack(Items.DIAMOND_CHESTPLATE);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,chestplate);player.setShiftKeyDown(false);
            chestplate.use(server.overworld(),player,net.minecraft.world.InteractionHand.MAIN_HAND);
            require(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()&&chestplate.getCount()==1,"not wearable blocks original armor use");
            require(!player.inventoryMenu.slots.get(6).mayPlace(chestplate),"not wearable blocks inventory armor placement");
            document.add("minecraft:diamond_chestplate",JsonParser.parseString("{\"edible\":true,\"nutrition\":4}"));
            require(ItemPropertyConfig.savePending(document.toString()).success(),"save inherited armor food");ItemEquipmentRefresh.applyPending(server);
            player.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);
            chestplate=new ItemStack(Items.DIAMOND_CHESTPLATE);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,chestplate);player.setShiftKeyDown(true);
            chestplate.use(server.overworld(),player,net.minecraft.world.InteractionHand.MAIN_HAND);
            require(player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)&&!player.isUsingItem(),"native food armor inherits sneak-equipping");
            if(originalChestRule==null)document.remove("minecraft:diamond_chestplate");else document.add("minecraft:diamond_chestplate",originalChestRule);require(ItemPropertyConfig.savePending(document.toString()).success(),"restore native armor rule");ItemEquipmentRefresh.applyPending(server);
            org.slf4j.LoggerFactory.getLogger(ItemPropertyRuntimeChecks.class).info("ITEM_NATIVE_EQUIPMENT_PASS inherited food armor and explicit not-wearable use/inventory");
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, brick);
            player.setShiftKeyDown(true);
            brick.use(server.overworld(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
            require(player.getItemBySlot(EquipmentSlot.CHEST).is(Items.BRICK) && brick.getCount() == 1, "sneak use equips one item without duplication");
            player.setShiftKeyDown(false);
            player.getFoodData().setFoodLevel(10);
            brick.use(server.overworld(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
            require(player.isUsingItem(), "ordinary use starts eating converted equipment");
            var result = brick.finishUsingItem(server.overworld(), player);
            player.stopUsingItem();
            require(result.is(Items.BOWL) && result.getCount() == 1, "last item returns one configured container");
            require(player.getFoodData().getFoodLevel() == 14, "converted food restores configured hunger once");
            require(player.getEffect(speed) != null && player.getEffect(speed).getAmplifier() == 1, "converted food applies selected effect");
            int bowls=count(player,Items.BOWL);
            var multiple=new ItemStack(Items.BRICK,3);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,multiple);
            player.getFoodData().setFoodLevel(10);
            require(multiple.finishUsingItem(server.overworld(),player)==multiple&&multiple.getCount()==2,"multi-stack food consumes exactly one");
            require(count(player,Items.BOWL)==bowls+1&&player.getFoodData().getFoodLevel()==14,"multi-stack food returns one container and feeds once");
            document.getAsJsonObject("minecraft:brick").addProperty("non_consumable",true);
            require(ItemPropertyConfig.savePending(document.toString()).success(),"save non-consumable food");ItemEquipmentRefresh.applyPending(server);
            bowls=count(player,Items.BOWL);multiple.finishUsingItem(server.overworld(),player);
            require(multiple.getCount()==2&&count(player,Items.BOWL)==bowls,"non-consumable food creates no duplicate container");
            document.getAsJsonObject("minecraft:brick").remove("non_consumable");
            document.add("minecraft:mushroom_stew",JsonParser.parseString("{\"nutrition\":5}"));
            require(ItemPropertyConfig.savePending(document.toString()).success(),"save existing food override");ItemEquipmentRefresh.applyPending(server);
            player.getAbilities().instabuild=true;
            multiple.finishUsingItem(server.overworld(),player);
            require(multiple.getCount()==2&&count(player,Items.BOWL)==bowls,"creative eating does not consume or duplicate containers");
            player.getAbilities().instabuild=false;player.getFoodData().setFoodLevel(10);
            var stew=new ItemStack(Items.MUSHROOM_STEW);var bowl=stew.finishUsingItem(server.overworld(),player);
            require(bowl.is(Items.BOWL)&&bowl.getCount()==1&&player.getFoodData().getFoodLevel()==15,"native food override preserves container and feeds once");
            require(ItemPropertyConfig.previewOriginal(()->dev.xyat.itemcontrol.item.property.ItemPropertyReads.nutrition(new ItemStack(Items.MUSHROOM_STEW)))==6,"food comparison reads original nutrition");
            document.getAsJsonObject("minecraft:brick").addProperty("edible",false);
            require(ItemPropertyConfig.savePending(document.toString()).success(),"save disabled food");ItemEquipmentRefresh.applyPending(server);
            int beforeFood=player.getFoodData().getFoodLevel();multiple.finishUsingItem(server.overworld(),player);
            require(multiple.getCount()==2&&player.getFoodData().getFoodLevel()==beforeFood,"disabling food while using prevents consumption");
            org.slf4j.LoggerFactory.getLogger(ItemPropertyRuntimeChecks.class).info("ITEM_FOOD_EDGE_PASS multiple, non-consumable, creative, native container, original comparison, disable");
            ItemStack weapon=new ItemStack(Items.STICK);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,weapon);
            require(weapon.isDamageableItem(),"converted weapon has usable durability");
            weapon.hurtEnemy(entity,player);
            require(weapon.getDamageValue()==1,"converted weapon loses one durability on hit");
            player.setItemSlot(EquipmentSlot.HEAD,weapon);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setInvulnerable(false);player.invulnerableTime=0;player.setHealth(player.getMaxHealth());
            player.getAbilities().invulnerable=false;
            //? if >=26.1 {
            /*boolean hurt=player.hurtServer(server.overworld(),player.damageSources().playerAttack(player),8);*/
            //?} else {
            boolean hurt=player.hurt(player.damageSources().playerAttack(player),8);
            //?}
            require(hurt&&weapon.getDamageValue()>1,"converted worn equipment loses durability when hit accepted="+hurt+" damage="+weapon.getDamageValue()+" invulnerable="+player.isInvulnerable()+" hp="+player.getHealth());
            org.slf4j.LoggerFactory.getLogger(ItemPropertyRuntimeChecks.class).info("ITEM_CONVERTED_DURABILITY_PASS weapon and worn equipment");
            org.slf4j.LoggerFactory.getLogger(ItemPropertyRuntimeChecks.class).info("ITEM_CAPABILITY_RUNTIME_PASS equipment, mixed food use, nutrition, effect, container");
        } finally {
            for(int index=0;index<inventory.size();index++)player.getInventory().setItem(index,inventory.get(index));
            player.setGameMode(gameMode);player.setInvulnerable(entityInvulnerable);player.invulnerableTime=frames;
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
            player.setItemSlot(EquipmentSlot.CHEST, worn);
            player.setItemSlot(EquipmentSlot.HEAD,head);player.getAbilities().invulnerable=invulnerable;player.setHealth(health);
            player.setShiftKeyDown(shift); player.getAbilities().instabuild = creative;
            player.getFoodData().setFoodLevel(hunger); player.getFoodData().setSaturation(saturation);
            player.removeEffect(speed);
            if (oldSpeed != null) player.addEffect(oldSpeed);
        }
        var swordStack = new ItemStack(Items.IRON_SWORD);
        //? if >=1.21 {
        /*double[] total = {0};
        swordStack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> { if(attribute.equals(Attributes.ATTACK_DAMAGE)) total[0] += modifier.amount(); });
        require(total[0] == 8, "attribute add preserves native weapon damage");*/
        //?} else {
        double total = swordStack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream().mapToDouble(net.minecraft.world.entity.ai.attributes.AttributeModifier::getAmount).sum();
        require(total == 8, "attribute add preserves native weapon damage");
        //?}
    }
    private static int count(net.minecraft.world.entity.player.Player player,net.minecraft.world.item.Item item){int count=0;for(int index=0;index<player.getInventory().getContainerSize();index++){var stack=player.getInventory().getItem(index);if(stack.is(item))count+=stack.getCount();}return count;}
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
