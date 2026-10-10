package dev.xyat.itemcontrol.item.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Independent capabilities; a missing field inherits the registered item's behavior. */
public record ItemCapabilitySettings(Boolean edible, String equipmentSlot, List<FoodEffect> foodEffects,
                                     boolean overridesFoodEffects, String foodEffectsMode, String foodRemainder) {
    public static final ItemCapabilitySettings EMPTY = new ItemCapabilitySettings(null, null, List.of(), false, "append", null);
    public record FoodEffect(String effect, int duration, int amplifier, double probability) {}
    public ItemCapabilitySettings { foodEffects = List.copyOf(foodEffects); }
    public boolean isEmpty() { return edible == null && equipmentSlot == null && !overridesFoodEffects && foodRemainder == null; }

    public static ItemCapabilitySettings parse(JsonObject json) {
        Boolean edible = null;
        if (json.has("edible")) {
            JsonElement raw = json.get("edible");
            if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("edible");
            edible = raw.getAsBoolean();
        }
        String slot = optionalString(json, "equipment_slot");
        if (slot != null && !List.of("head", "chest", "legs", "feet", "none").contains(slot)) throw new IllegalArgumentException("equipment_slot");
        String mode = optionalString(json, "food_effects_mode");
        if (mode == null) mode = "append";
        if (!List.of("append", "replace").contains(mode)) throw new IllegalArgumentException("food_effects_mode");
        String remainder = optionalString(json, "food_remainder");
        if (remainder != null && !isId(remainder)) throw new IllegalArgumentException("food_remainder");
        List<FoodEffect> effects = new ArrayList<>();
        if (json.has("food_effects")) {
            var raw = json.get("food_effects");
            if (!raw.isJsonArray() || raw.getAsJsonArray().size() > 64) throw new IllegalArgumentException("food_effects");
            for (var value : raw.getAsJsonArray()) {
                if (!value.isJsonObject()) throw new IllegalArgumentException("food_effect");
                var row = value.getAsJsonObject();
                String effect = optionalString(row, "effect");
                if (!isId(effect)) throw new IllegalArgumentException("effect");
                int duration = integer(row.get("duration"), 0, 7_200_000);
                int amplifier = integer(row.get("amplifier"), 0, 255);
                double probability = number(row.get("probability"));
                if (probability < 0 || probability > 1) throw new IllegalArgumentException("probability");
                effects.add(new FoodEffect(effect, duration, amplifier, probability));
            }
        }
        return new ItemCapabilitySettings(edible, slot, effects, json.has("food_effects"), mode, remainder);
    }
    static boolean isId(String id) { return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"); }
    static String optionalString(JsonObject object, String key) {
        if (!object.has(key)) return null;
        var raw = object.get(key);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key);
        return raw.getAsString();
    }
    static double number(JsonElement raw) {
        if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("number");
        double value = raw.getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("finite number");
        return value;
    }
    static int integer(JsonElement raw, int min, int max) {
        double value = number(raw);
        if (value != Math.rint(value) || value < min || value > max) throw new IllegalArgumentException("integer range");
        return (int)value;
    }
}
