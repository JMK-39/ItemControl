package dev.xyat.itemcontrolvalidation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Actual registered items and server save API; the enclosing editor fixture restores the user's document. */
final class ItemPropertySaveChecks {
    private static JsonObject seeded;

    static void prepare() throws Exception {
        seeded = JsonParser.parseString(ItemPropertyConfig.pendingJson()).getAsJsonObject();
        seeded.add("minecraft:stick", JsonParser.parseString("{\"curio\":17}"));
        seeded.add("minecraft:feather", JsonParser.parseString("{\"curio\":{\"enabled\":true,\"attributes\":[{\"attribute\":\"missing:legacy_attribute\",\"operation\":\"ADDITION\",\"amount\":1}]}}"));
        seeded.add("minecraft:apple", JsonParser.parseString("{\"food_effects\":[{\"effect\":\"missing:legacy_effect\",\"duration\":60,\"amplifier\":0,\"probability\":1}]}"));
        seedPending(seeded);
        require(!ItemPropertyConfig.validateRule(seeded.get("minecraft:stick")).isEmpty(), "fixture has invalid legacy Curio data");
        require(!ItemPropertyConfig.validateRule(seeded.get("minecraft:feather")).isEmpty(), "fixture has an unresolved legacy attribute");
        require(!ItemPropertyConfig.validateRule(seeded.get("minecraft:apple")).isEmpty(), "fixture has an unresolved legacy effect");
        require(ItemPropertyConfig.savePending(seeded.toString()).success(), "no-op save preserves unchanged invalid legacy rules");
        verifyLegacyRows(JsonParser.parseString(KineticPaths.readConfigText("kineticcore/itemcontrol/item_properties.json")).getAsJsonObject());

        JsonObject baseline = JsonParser.parseString(ItemPropertyConfig.pendingJson()).getAsJsonObject();
        JsonObject invalid = baseline.deepCopy();
        invalid.add("minecraft:diamond_chestplate", JsonParser.parseString("{\"armor\":9999}"));
        rejectUnchanged(invalid, "new out-of-range armor is rejected");
        invalid = baseline.deepCopy();
        invalid.add("minecraft:stick", JsonParser.parseString("{\"curio\":19}"));
        rejectUnchanged(invalid, "modified invalid legacy row is rejected");
        invalid = baseline.deepCopy();
        invalid.add("minecraft:dirt", JsonParser.parseString("{\"curio\":17}"));
        rejectUnchanged(invalid, "new malformed row is rejected");
        invalid = baseline.deepCopy();
        invalid.add("stick", new JsonObject());
        rejectUnchanged(invalid, "canonical alias cannot bypass unchanged-row validation");
        invalid = baseline.deepCopy();
        invalid.add("#minecraft:logs", JsonParser.parseString("{\"armor\":10}"));
        rejectUnchanged(invalid, "new protection selector cannot contain combat fields");

        JsonObject repair = baseline.deepCopy();
        repair.add("minecraft:stick", JsonParser.parseString("{\"armor\":1}"));
        repair.remove("minecraft:feather");
        require(ItemPropertyConfig.savePending(repair.toString()).success(), "repair and delete legacy rows while retaining another invalid row");
        seedPending(seeded);
        require(ItemPropertyConfig.savePending(seeded.toString()).success(), "restore seeded baseline before real UI save");
    }

    static void verifyLegacyRows(JsonObject saved) {
        for (String id : new String[]{"minecraft:stick", "minecraft:feather", "minecraft:apple"})
            require(seeded.get(id).equals(saved.get(id)), "unrelated legacy row is preserved: " + id);
    }

    private static void rejectUnchanged(JsonObject document, String message) throws Exception {
        String pending = ItemPropertyConfig.pendingJson();
        String active = ItemPropertyConfig.activeJson();
        String disk = KineticPaths.readConfigText("kineticcore/itemcontrol/item_properties.json");
        require(!ItemPropertyConfig.savePending(document.toString()).success(), message);
        require(pending.equals(ItemPropertyConfig.pendingJson()) && active.equals(ItemPropertyConfig.activeJson())
                && disk.equals(KineticPaths.readConfigText("kineticcore/itemcontrol/item_properties.json")), "rejection leaves pending, active and disk unchanged");
    }

    @SuppressWarnings("unchecked")
    private static void seedPending(JsonObject document) throws Exception {
        Map<String, JsonElement> rows = new LinkedHashMap<>();
        document.entrySet().forEach(entry -> rows.put(entry.getKey(), entry.getValue().deepCopy()));
        var field = ItemPropertyConfig.class.getDeclaredField("PENDING");
        field.setAccessible(true);
        ((AtomicReference<Map<String, JsonElement>>) field.get(null)).set(java.util.Collections.unmodifiableMap(rows));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        org.slf4j.LoggerFactory.getLogger(ItemPropertySaveChecks.class).info("ITEM_PROPERTY_SAVE_CHECK {}", message);
    }
}
