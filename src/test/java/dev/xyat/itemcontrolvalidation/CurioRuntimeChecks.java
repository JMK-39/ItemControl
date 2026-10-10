package dev.xyat.itemcontrolvalidation;

import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.itemcontrol.item.property.ItemEquipmentRefresh;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;

/** Event and real inventory admission checks using installed Curios. */
final class CurioRuntimeChecks {
    private static dev.xyat.kineticcore.api.event.KineticEventSubscription seed;
    private static net.minecraft.server.level.ServerPlayer wearer;
    private static top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler equipped;
    private static ItemStack previous;
    private static double originalArmor;
    private static int ringCount,necklaceCount;
    private static top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler second;
    private static ItemStack secondPrevious;
    static void run(MinecraftServer server) {
        seed = dev.xyat.kineticcore.api.event.KineticExternalEvents.subscribe(top.theillusivec4.curios.api.event.CurioAttributeModifierEvent.class,
                dev.xyat.kineticcore.api.event.KineticEventPriority.HIGHEST, false, CurioRuntimeChecks::seedOriginal);
        var player = server.getPlayerList().getPlayers().get(0);
        // Failed, interrupted validation runs can save their transient count modifiers before the next Curios tick.
        CuriosApi.getCuriosInventory(player).ifPresent(inventory->{
            var stale=com.google.common.collect.HashMultimap.<String,net.minecraft.world.entity.ai.attributes.AttributeModifier>create();
            inventory.getModifiers().forEach((slot,modifier)->{
                //? if >=1.21 {
                /*if(modifier.id().getNamespace().equals("itemcontrol")&&modifier.id().getPath().startsWith("curio_slots/"))stale.put(slot,modifier);*/
                //?}
            });
            if(!stale.isEmpty())inventory.removeSlotModifiers(stale);
        });
        verifyNativeSlots(server, player);
        ItemPropertyRuntimeChecks.require(CuriosApi.getCuriosInventory(player).isPresent(), "fixture player Curios inventory attached");
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            var slots = inventory.getCurios();
            ringCount=slots.get("ring").getStacks().getSlots();necklaceCount=slots.get("necklace").getStacks().getSlots();
            ItemPropertyRuntimeChecks.require(!slots.isEmpty(), "existing Curios slots available");
            String first = slots.entrySet().stream().filter(row -> row.getValue().getStacks().getSlots() > 0)
                    .map(java.util.Map.Entry::getKey).findFirst().orElseThrow(() -> new AssertionError("nonempty real Curios slot available"));
            var document = JsonParser.parseString(ItemPropertyConfig.pendingJson()).getAsJsonObject();
            document.add("minecraft:stick", JsonParser.parseString("{\"curio\":{\"enabled\":true,\"slots\":[\""+first+"\",\"validation_secondary\"],\"can_unequip\":false,\"attributes\":[{\"attribute\":\"minecraft:generic.armor\",\"operation\":\"ADDITION\",\"amount\":3}]}}"));
            document.getAsJsonObject("minecraft:stick").getAsJsonObject("curio").add("slot_modifiers",JsonParser.parseString("[{\"slot\":\"ring\",\"amount\":2},{\"slot\":\"necklace\",\"amount\":1}]"));
            ItemPropertyRuntimeChecks.require(ItemPropertyConfig.savePending(document.toString()).success(), "save curio conversion");
            ItemEquipmentRefresh.applyPending(server);
            ItemStack stick = new ItemStack(Items.STICK);
            var handler = slots.get(first).getStacks();
            ItemPropertyRuntimeChecks.require(handler.isItemValid(0, stick), "ordinary stick accepted in configured real slot");
            var context = new top.theillusivec4.curios.api.SlotContext(first, player, 0, false, true);
            //? if >=1.21 {
            /*//? if >=26.1 {
            var unequip = new top.theillusivec4.curios.api.event.CurioCanUnequipEvent(stick, context, true);
            //?} else {
            /^var unequip = new top.theillusivec4.curios.api.event.CurioCanUnequipEvent(stick, context);^/
            //?}
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(unequip);
            //? if >=26.1 {
            ItemPropertyRuntimeChecks.require(unequip.getUnequipResult() == net.minecraft.util.TriState.FALSE, "removal lock");
            //?} else {
            /^ItemPropertyRuntimeChecks.require(unequip.getUnequipResult() == net.neoforged.neoforge.common.util.TriState.FALSE, "removal lock");^/
            //?}
            var modifiers = CuriosApi.getAttributeModifiers(context, CuriosApi.getSlotId(context), stick);
            ItemPropertyRuntimeChecks.require(modifiers.entries().stream().anyMatch(row -> row.getKey().equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) && row.getValue().amount()==3), "accessory armor bonus actual="+modifiers);
            *///?} else {
            var unequip = new top.theillusivec4.curios.api.event.CurioUnequipEvent(stick, context);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(unequip);
            ItemPropertyRuntimeChecks.require(unequip.getResult() == net.minecraftforge.eventbus.api.Event.Result.DENY, "removal lock");
            var modifiers = CuriosApi.getAttributeModifiers(context, java.util.UUID.randomUUID(), stick);
            ItemPropertyRuntimeChecks.require(modifiers.entries().stream().anyMatch(row -> row.getKey().equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) && row.getValue().getAmount()==3), "accessory armor bonus");
            //?}
            document.getAsJsonObject("minecraft:stick").getAsJsonObject("curio").add("slots", JsonParser.parseString("[\"curio\"]"));
            document.getAsJsonObject("minecraft:stick").getAsJsonObject("curio").addProperty("can_unequip", true);
            ItemPropertyRuntimeChecks.require(ItemPropertyConfig.savePending(document.toString()).success(), "save any slot");
            ItemEquipmentRefresh.applyPending(server);
            slots.forEach((name, entry) -> {
                if (entry.getStacks().getSlots() > 0) ItemPropertyRuntimeChecks.require(entry.getStacks().isItemValid(0, stick), "any-slot conversion: "+name);
            });
            wearer=player; equipped=handler; previous=handler.getStackInSlot(0).copy();
            ItemPropertyRuntimeChecks.require(previous.isEmpty(), "fixture uses an empty accessory slot");
            originalArmor=player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            equipped.setStackInSlot(0, stick);
            org.slf4j.LoggerFactory.getLogger(CurioRuntimeChecks.class).info("ITEM_CURIO_RUNTIME_PASS configured and universal slot admission, removal lock, attributes");
        });
    }

    private static void verifyNativeSlots(MinecraftServer server, net.minecraft.server.level.ServerPlayer player) {
        var nativeItem = new ItemStack(Items.BLAZE_ROD);
        var slots = CuriosApi.getCuriosInventory(player).orElseThrow(() -> new IllegalStateException("Curios inventory missing")).getCurios();
        var ring = slots.get("ring").getStacks();
        var necklace = slots.get("necklace").getStacks();
        ItemPropertyRuntimeChecks.require(ring.isItemValid(0, nativeItem) && !necklace.isItemValid(0, nativeItem), "native ring-only accessory baseline");
        ItemPropertyRuntimeChecks.require(dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.originalSlots(nativeItem, player).contains("ring"), "read existing accessory slots");
        var document = JsonParser.parseString(ItemPropertyConfig.pendingJson()).getAsJsonObject();
        document.add("minecraft:blaze_rod", JsonParser.parseString("{\"curio\":{\"enabled\":true}}"));
        ItemPropertyRuntimeChecks.require(ItemPropertyConfig.savePending(document.toString()).success(), "save inherited native accessory slots");
        ItemEquipmentRefresh.applyPending(server);
        ItemPropertyRuntimeChecks.require(ring.isItemValid(0, nativeItem) && !necklace.isItemValid(0, nativeItem), "attribute-only override preserves native slot restrictions");
        document.add("minecraft:blaze_rod", JsonParser.parseString("{\"curio\":{\"enabled\":true,\"slots\":[\"necklace\"]}}"));
        ItemPropertyRuntimeChecks.require(ItemPropertyConfig.savePending(document.toString()).success(), "save existing accessory slot replacement");
        ItemEquipmentRefresh.applyPending(server);
        ItemPropertyRuntimeChecks.require(!ring.isItemValid(0, nativeItem) && necklace.isItemValid(0, nativeItem), "replace native ring with necklace");
        ItemPropertyRuntimeChecks.require(dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.originalSlots(nativeItem, player).contains("ring"), "native slot preview ignores overrides");
        document.getAsJsonObject("minecraft:blaze_rod").getAsJsonObject("curio").addProperty("enabled", false);
        ItemPropertyRuntimeChecks.require(ItemPropertyConfig.savePending(document.toString()).success(), "disable existing accessory override");
        ItemEquipmentRefresh.applyPending(server);
        ItemPropertyRuntimeChecks.require(ring.isItemValid(0, nativeItem) && !necklace.isItemValid(0, nativeItem), "disable override restores native slots");
        document.remove("minecraft:blaze_rod");
        ItemPropertyRuntimeChecks.require(ItemPropertyConfig.savePending(document.toString()).success(), "clear native accessory fixture");
        ItemEquipmentRefresh.applyPending(server);
        org.slf4j.LoggerFactory.getLogger(CurioRuntimeChecks.class).info("ITEM_CURIO_NATIVE_SLOTS_PASS ring to necklace and native restoration");
    }
    static void verifyMode(MinecraftServer server, String mode, double expectedBefore, double expectedAfter) {
        if(wearer==null)return;
        verifyCounts(mode.equals("replace")?2:3,mode.equals("replace")?1:2);
        ItemPropertyRuntimeChecks.require(Math.abs(wearer.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)-originalArmor-expectedBefore)<0.0001, "native equipped Curios modifier stable before "+mode);
        var document=JsonParser.parseString(ItemPropertyConfig.pendingJson()).getAsJsonObject();
        document.getAsJsonObject("minecraft:stick").getAsJsonObject("curio").add("attributes",JsonParser.parseString("[{\"attribute\":\"minecraft:generic.armor\",\"operation\":\"ADDITION\",\"amount\":5,\"mode\":\""+mode+"\"}]"));
        document.getAsJsonObject("minecraft:stick").getAsJsonObject("curio").add("slot_modifiers",JsonParser.parseString("[{\"slot\":\"ring\",\"amount\":3},{\"slot\":\"necklace\",\"amount\":2}]"));
        ItemPropertyRuntimeChecks.require(ItemPropertyConfig.savePending(document.toString()).success(), "save "+mode);
        ItemEquipmentRefresh.applyPending(server);
        ItemPropertyRuntimeChecks.require(Math.abs(wearer.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)-originalArmor-expectedAfter)<0.0001, "equipped accessory live "+mode);
        ItemPropertyRuntimeChecks.require(wearer.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)>=22, "unrelated original attribute preserved");
        org.slf4j.LoggerFactory.getLogger(CurioRuntimeChecks.class).info("ITEM_CURIO_EDIT_PASS {}", mode);
    }
    static void equipSecond(){if(wearer==null)return;second=CuriosApi.getCuriosInventory(wearer).orElseThrow(()->new IllegalStateException("Missing Curios inventory")).getCurios().get("ring").getStacks();secondPrevious=second.getStackInSlot(1).copy();ItemPropertyRuntimeChecks.require(secondPrevious.isEmpty(),"second test slot is empty");second.setStackInSlot(1,new ItemStack(Items.STICK));}
    static void verifyStackedAndRemoveFirst(){if(wearer==null)return;verifyCounts(6,4);equipped.setStackInSlot(0,previous);}
    static void verifyRemainingAndNegative(MinecraftServer server){if(wearer==null)return;verifyCounts(3,2);var document=JsonParser.parseString(ItemPropertyConfig.pendingJson()).getAsJsonObject();document.getAsJsonObject("minecraft:stick").getAsJsonObject("curio").add("slot_modifiers",JsonParser.parseString("[{\"slot\":\"ring\",\"amount\":3},{\"slot\":\"necklace\",\"amount\":-1}]"));ItemPropertyRuntimeChecks.require(ItemPropertyConfig.savePending(document.toString()).success(),"save negative count");ItemEquipmentRefresh.applyPending(server);}
    static void verifyNegativeAndRemove(){if(wearer==null)return;verifyCounts(3,-1);removeFixture();org.slf4j.LoggerFactory.getLogger(CurioRuntimeChecks.class).info("ITEM_CURIO_COUNTS_PASS multiple copies, independent unequip, live negative count");}
    static void removeFixture() { if(equipped!=null)equipped.setStackInSlot(0,previous);if(second!=null&&second.getSlots()>1)second.setStackInSlot(1,secondPrevious); }
    static void close() { if(seed!=null)seed.close(); if(!ItemPropertyRuntimeChecks.failed)verifyCounts(0,0); }
    private static void verifyCounts(int ring,int necklace){if(wearer==null)return;var slots=CuriosApi.getCuriosInventory(wearer).orElseThrow(()->new IllegalStateException("Missing Curios inventory")).getCurios();ItemPropertyRuntimeChecks.require(slots.get("ring").getStacks().getSlots()==ringCount+ring,"equipped/live/removed ring slot modifier expected="+(ringCount+ring)+" actual="+slots.get("ring").getStacks().getSlots());ItemPropertyRuntimeChecks.require(slots.get("necklace").getStacks().getSlots()==necklaceCount+necklace,"independent necklace slot modifier");}
    private static void seedOriginal(top.theillusivec4.curios.api.event.CurioAttributeModifierEvent event) {
        if(!event.getItemStack().is(Items.STICK))return;
        //? if >=1.21 {
        /*event.addModifier(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,new net.minecraft.world.entity.ai.attributes.AttributeModifier(dev.xyat.kineticcore.api.resource.KineticResourceIds.parse("validation:original_armor"),6,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        event.addModifier(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,new net.minecraft.world.entity.ai.attributes.AttributeModifier(dev.xyat.kineticcore.api.resource.KineticResourceIds.parse("validation:unrelated_health"),2,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        *///?} else {
        event.addModifier(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,new net.minecraft.world.entity.ai.attributes.AttributeModifier(java.util.UUID.fromString("e3d7c004-1f7a-446d-b24b-0b171d07eeac"),"original armor",6,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        event.addModifier(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,new net.minecraft.world.entity.ai.attributes.AttributeModifier(java.util.UUID.fromString("8fd7ba28-1c1e-421b-9e5a-3509b8d67278"),"unrelated health",2,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        //?}
    }
}
