package dev.xyat.itemcontrol.item.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Optional conversion rules. The generic Curios slot is the explicit any-slot choice. */
public record ItemCurioSettings(boolean enabled, List<String> slots, boolean overridesSlots, boolean canUnequip, boolean overridesUnequip,
                                List<AttributeRule> attributes) {
    public record AttributeRule(String attribute, String operation, double amount, String mode) {}
    public ItemCurioSettings {
        slots = List.copyOf(new LinkedHashSet<>(slots));
        attributes = List.copyOf(attributes);
    }

    public boolean allows(String slot) { return enabled && (slots.contains("curio") || slots.contains(slot)); }

    public static ItemCurioSettings parse(JsonElement raw) {
        if (raw == null) return null;
        if (!raw.isJsonObject()) throw new IllegalArgumentException("curio must be an object");
        JsonObject json = raw.getAsJsonObject();
        List<String> slots = new ArrayList<>();
        if (json.has("slots")) {
            if (!json.get("slots").isJsonArray() || json.getAsJsonArray("slots").size() > 64)
                throw new IllegalArgumentException("curio slots");
            for (JsonElement value : json.getAsJsonArray("slots")) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                        || !value.getAsString().matches("[a-z0-9_./-]{1,128}"))
                    throw new IllegalArgumentException("curio slot identifier");
                slots.add(value.getAsString());
            }
        }
        List<AttributeRule> attributes = new ArrayList<>();
        if (json.has("attributes")) {
            if (!json.get("attributes").isJsonArray() || json.getAsJsonArray("attributes").size() > 128)
                throw new IllegalArgumentException("curio attributes");
            for (JsonElement value : json.getAsJsonArray("attributes")) {
                if (!value.isJsonObject()) throw new IllegalArgumentException("curio attribute");
                JsonObject row = value.getAsJsonObject();
                String id = string(row, "attribute", "");
                String mode = string(row, "mode", "add").toLowerCase(Locale.ROOT);
                if (!List.of("add", "replace", "remove").contains(mode)) throw new IllegalArgumentException("curio attribute mode");
                String operation = string(row, "operation", "ADDITION").toUpperCase(Locale.ROOT);
                operation = switch (operation) {
                    case "ADD_VALUE" -> "ADDITION";
                    case "ADD_MULTIPLIED_BASE" -> "MULTIPLY_BASE";
                    case "ADD_MULTIPLIED_TOTAL" -> "MULTIPLY_TOTAL";
                    default -> operation;
                };
                JsonElement amount = row.get("amount");
                if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("curio attribute identifier");
                if (mode.equals("remove")) { attributes.add(new AttributeRule(id, "ADDITION", 0, mode)); continue; }
                if (amount == null || !amount.isJsonPrimitive()
                        || !amount.getAsJsonPrimitive().isNumber() || !Double.isFinite(amount.getAsDouble())
                        || !List.of("ADDITION", "MULTIPLY_BASE", "MULTIPLY_TOTAL").contains(operation))
                    throw new IllegalArgumentException("curio attribute value");
                attributes.add(new AttributeRule(id, operation, amount.getAsDouble(), mode));
            }
        }
        return new ItemCurioSettings(bool(json, "enabled", false), slots, json.has("slots"), bool(json, "can_unequip", true), json.has("can_unequip"), attributes);
    }

    private static String string(JsonObject json, String key, String fallback) {
        if (!json.has(key)) return fallback;
        var value = json.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key);
        return value.getAsString();
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        JsonElement raw = json.get(key);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key);
        return raw.getAsBoolean();
    }
}
