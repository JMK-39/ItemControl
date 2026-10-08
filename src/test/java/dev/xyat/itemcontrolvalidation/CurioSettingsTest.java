package dev.xyat.itemcontrolvalidation;

import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.config.ItemCurioSettings;

/** Pure schema regression: no game bootstrap or Curios runtime required. */
public final class CurioSettingsTest {
    public static void main(String[] args) {
        var settings = ItemCurioSettings.parse(JsonParser.parseString("{\"enabled\":true,\"slots\":[\"ring\",\"necklace\",\"ring\"],\"can_unequip\":false,\"attributes\":[{\"attribute\":\"minecraft:generic.armor\",\"amount\":3,\"operation\":\"ADDITION\"}]}"));
        require(settings.allows("ring") && settings.allows("necklace") && !settings.allows("head"), "multiple slots");
        require(settings.slots().size() == 2 && !settings.canUnequip(), "deduplicate and removal lock");
        require(settings.attributes().size() == 1 && settings.attributes().get(0).amount() == 3, "attribute bonus");
        var edits = ItemCurioSettings.parse(JsonParser.parseString("{\"attributes\":[{\"attribute\":\"minecraft:generic.armor\",\"mode\":\"remove\"},{\"attribute\":\"minecraft:generic.attack_damage\",\"mode\":\"replace\",\"amount\":5,\"operation\":\"ADDITION\"}]}"));
        require(edits.attributes().get(0).mode().equals("remove") && edits.attributes().get(1).mode().equals("replace"), "explicit removal and replacement");
        require(ItemCurioSettings.parse(JsonParser.parseString("{\"enabled\":true,\"slots\":[\"curio\"]}")).allows("custom_slot"), "any slot");
        require(!ItemCurioSettings.parse(JsonParser.parseString("{\"enabled\":true,\"slots\":[]}")).allows("ring"), "empty slots do not broaden rule");
        require(!ItemCurioSettings.parse(JsonParser.parseString("{\"enabled\":true}")).overridesSlots(), "missing slots inherit native admission");
        require(!ItemCurioSettings.parse(JsonParser.parseString("{\"enabled\":true}")).overridesUnequip(), "missing unequip setting inherits native restrictions");
        require(ItemCurioSettings.parse(JsonParser.parseString("{\"enabled\":true,\"slots\":[]}")).overridesSlots(), "explicit empty slots override native admission");
        require(!ItemCurioSettings.parse(JsonParser.parseString("{\"enabled\":false,\"slots\":[\"curio\"]}")).allows("ring"), "disabled conversion");
        for (String raw : new String[]{"[]", "{\"enabled\":\"true\"}", "{\"slots\":[\"Ring!\"]}", "{\"attributes\":[{\"attribute\":\"minecraft:armor\",\"amount\":1,\"operation\":\"typo\"}]}", "{\"attributes\":[{\"attribute\":\"minecraft:armor\",\"amount\":\"NaN\",\"operation\":\"ADDITION\"}]}"}) {
            boolean rejected = false;
            try { ItemCurioSettings.parse(JsonParser.parseString(raw)); } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "reject malformed Curios settings: " + raw);
        }
        System.out.println("CURIO_SETTINGS_PASS");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
