package dev.xyat.itemcontrol.item.config;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Portable attribute schema. Registry existence is checked by the authoritative config loader. */
public final class ItemAttributeSettings {
    private ItemAttributeSettings() {}
    public static List<ItemPropertyRule.AttributeModifier> parse(JsonElement raw) {
        if (raw == null) return List.of();
        if (!raw.isJsonArray() || raw.getAsJsonArray().size() > 128) throw new IllegalArgumentException("attributes");
        List<ItemPropertyRule.AttributeModifier> result = new ArrayList<>();
        for (var value : raw.getAsJsonArray()) {
            if (!value.isJsonObject()) throw new IllegalArgumentException("attribute row");
            var row = value.getAsJsonObject();
            String id = ItemCapabilitySettings.optionalString(row, "attribute");
            String slot = ItemCapabilitySettings.optionalString(row, "slot");
            String mode = ItemCapabilitySettings.optionalString(row, "mode");
            if (mode == null) mode = "replace";
            if (!List.of("add", "replace", "remove").contains(mode) || !ItemCapabilitySettings.isId(id) || slot == null) throw new IllegalArgumentException("attribute selector");
            slot = slot.toUpperCase(Locale.ROOT);
            if (!List.of("MAINHAND", "OFFHAND", "HEAD", "CHEST", "LEGS", "FEET", "BODY", "SADDLE").contains(slot)) throw new IllegalArgumentException("attribute slot");
            String operation = "ADDITION";
            double amount = 0;
            if (!mode.equals("remove")) {
                String rawOperation = ItemCapabilitySettings.optionalString(row, "operation");
                if (rawOperation == null) throw new IllegalArgumentException("attribute operation");
                operation = switch (rawOperation.toUpperCase(Locale.ROOT)) {
                    case "ADD_VALUE" -> "ADDITION";
                    case "ADD_MULTIPLIED_BASE" -> "MULTIPLY_BASE";
                    case "ADD_MULTIPLIED_TOTAL" -> "MULTIPLY_TOTAL";
                    default -> rawOperation.toUpperCase(Locale.ROOT);
                };
                if (!List.of("ADDITION", "MULTIPLY_BASE", "MULTIPLY_TOTAL").contains(operation)) throw new IllegalArgumentException("attribute operation");
                amount = ItemCapabilitySettings.number(row.get("amount"));
            }
            result.add(new ItemPropertyRule.AttributeModifier(id, slot, operation, amount, mode));
        }
        return List.copyOf(result);
    }
}
