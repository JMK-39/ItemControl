package dev.xyat.itemcontrolvalidation;

import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.config.ItemCapabilitySettings;
import dev.xyat.itemcontrol.item.config.ItemAttributeSettings;

/** Rules are validated without starting Minecraft or resolving optional integrations. */
public final class ItemCapabilitySettingsTest {
    public static void main(String[] args) {
        var empty = ItemCapabilitySettings.parse(JsonParser.parseString("{}").getAsJsonObject());
        require(empty.isEmpty() && empty.edible() == null && empty.equipmentSlot() == null, "missing fields inherit");
        var combined = ItemCapabilitySettings.parse(JsonParser.parseString("{\"edible\":true,\"equipment_slot\":\"chest\",\"food_effects\":[{\"effect\":\"minecraft:speed\",\"duration\":200,\"amplifier\":1,\"probability\":0.5}],\"food_remainder\":\"minecraft:bowl\"}").getAsJsonObject());
        require(Boolean.TRUE.equals(combined.edible()) && combined.equipmentSlot().equals("chest"), "food and equipment coexist");
        require(combined.foodEffects().get(0).duration() == 200 && combined.foodEffects().get(0).probability() == 0.5, "food effect values");
        require(combined.foodRemainder().equals("minecraft:bowl") && combined.foodEffectsMode().equals("append"), "container and native effect preservation");
        var remove = ItemCapabilitySettings.parse(JsonParser.parseString("{\"edible\":false,\"equipment_slot\":\"none\",\"food_effects\":[],\"food_effects_mode\":\"replace\",\"food_remainder\":\"minecraft:air\"}").getAsJsonObject());
        require(Boolean.FALSE.equals(remove.edible()) && remove.overridesFoodEffects() && remove.foodEffects().isEmpty(), "explicit empty effects and disabling differ from inherit");
        for (String raw : new String[]{"{\"edible\":\"true\"}", "{\"equipment_slot\":\"mainhand\"}", "{\"food_effects_mode\":\"typo\"}", "{\"food_remainder\":\"bad id\"}", "{\"food_effects\":[{\"effect\":\"minecraft:speed\",\"duration\":-1,\"amplifier\":0,\"probability\":1}]}", "{\"food_effects\":[{\"effect\":\"minecraft:speed\",\"duration\":200,\"amplifier\":256,\"probability\":1}]}", "{\"food_effects\":[{\"effect\":\"minecraft:speed\",\"duration\":200,\"amplifier\":0,\"probability\":1.1}]}"}) {
            reject(() -> ItemCapabilitySettings.parse(JsonParser.parseString(raw).getAsJsonObject()), raw);
        }
        var legacy = ItemAttributeSettings.parse(JsonParser.parseString("[{\"attribute\":\"minecraft:generic.armor\",\"slot\":\"CHEST\",\"operation\":\"ADDITION\",\"amount\":2}]"));
        require(legacy.get(0).mode().equals("replace"), "legacy attribute rules replace");
        var modes = ItemAttributeSettings.parse(JsonParser.parseString("[{\"attribute\":\"minecraft:generic.armor\",\"slot\":\"chest\",\"mode\":\"remove\"},{\"attribute\":\"minecraft:generic.attack_damage\",\"slot\":\"mainhand\",\"mode\":\"add\",\"operation\":\"ADD_MULTIPLIED_BASE\",\"amount\":0.2}]"));
        require(modes.get(0).mode().equals("remove") && modes.get(1).operation().equals("MULTIPLY_BASE"), "attribute modes and cross-version aliases");
        reject(() -> ItemAttributeSettings.parse(JsonParser.parseString("[{\"attribute\":\"minecraft:generic.armor\",\"slot\":\"CHEST\",\"mode\":\"unknown\",\"operation\":\"ADDITION\",\"amount\":2}]")), "invalid attribute mode");
        System.out.println("ITEM_CAPABILITY_SETTINGS_PASS");
    }
    private static void reject(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid rule: " + label);
    }
    private static void require(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
