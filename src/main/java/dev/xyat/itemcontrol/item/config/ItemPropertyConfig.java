package dev.xyat.itemcontrol.item.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.ItemModule;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Persistence and immutable startup snapshot for per-item property overrides. */
public final class ItemPropertyConfig {
    private static final String CONFIG_FILE = "itemcontrol/item_properties.json";
    private static final int MAX_RULES = 16_384;
    private static final int MAX_JSON_CHARS = 2 * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicReference<Map<String, JsonElement>> PENDING = new AtomicReference<>(Map.of());
    private static final AtomicReference<Map<ResourceLocation, ItemPropertyRule>> ACTIVE = new AtomicReference<>(Map.of());

    private ItemPropertyConfig() {
    }

    /** Loads disk data into both the editable document and the current process snapshot. */
    public static synchronized void load() {
        try {
            String raw = KineticPaths.configFileExists(CONFIG_FILE)
                    ? KineticPaths.readConfigText(CONFIG_FILE)
                    : "{}";
            Map<String, JsonElement> parsed = parseDocument(raw);
            PENDING.set(immutableCopy(parsed));
            ACTIVE.set(buildActiveSnapshot(parsed));
            if (!KineticPaths.configFileExists(CONFIG_FILE)) writePending(parsed);
        } catch (Exception e) {
            ItemModule.LOGGER.error("Failed to load item property overrides; retaining the last valid snapshot", e);
        }
    }

    /** Current process rules; saving pending edits never mutates this map. */
    public static ItemPropertyRule active(ResourceLocation itemId) {
        return itemId == null ? null : ACTIVE.get().get(itemId);
    }

    /** Current process rules for a registered item. */
    public static ItemPropertyRule active(Item item) {
        if (item == null || item == Items.AIR) return null;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        return active(itemId);
    }

    /** Immutable copy of the active startup snapshot for login synchronization. */
    public static Map<ResourceLocation, ItemPropertyRule> activeSnapshot() {
        return ACTIVE.get();
    }

    /** Rebuilds the process snapshot after vanilla and mod registries have finished registering. */
    public static synchronized void activatePendingSnapshot() {
        ACTIVE.set(buildActiveSnapshot(PENDING.get()));
    }

    /** JSON document currently saved on disk and shown by the editor, without a version envelope. */
    public static synchronized String pendingJson() {
        return GSON.toJson(toJsonObject(PENDING.get()));
    }

    /** Current process snapshot serialized in the same plain ID-to-properties shape. */
    public static synchronized String activeJson() {
        JsonObject root = new JsonObject();
        ACTIVE.get().forEach((id, rule) -> root.add(id.toString(), writeRule(rule)));
        return GSON.toJson(root);
    }

    /**
     * Validates the top-level document limits and atomically saves it as pending data.
     * Unknown, unresolved, and malformed individual rows are intentionally retained for repair.
     */
    public static synchronized SaveResult savePending(String json) {
        try {
            Map<String, JsonElement> parsed = parseDocument(json);
            if (!validateKnownEntries(parsed)) {
                return new SaveResult(false, "gui.itemcontrol.item_property.error.invalid_rule");
            }
            writePending(parsed);
            PENDING.set(immutableCopy(parsed));
            return new SaveResult(true, "");
        } catch (Exception e) {
            ItemModule.LOGGER.warn("Rejected item property override document", e);
            return new SaveResult(false, "gui.itemcontrol.item_property.error.invalid_document");
        }
    }

    /** Installs active and pending snapshots received from the authoritative server. */
    public static synchronized void applyServerSnapshots(String pendingJson, String activeJson) {
        try {
            PENDING.set(immutableCopy(parseDocument(pendingJson)));
            ACTIVE.set(buildActiveSnapshot(parseDocument(activeJson)));
            dev.xyat.itemcontrol.item.property.ItemPropertyOverrides.applyBlockOverrides();
        } catch (Exception e) {
            ItemModule.LOGGER.warn("Rejected item property snapshots received from server", e);
        }
    }

    /** Pending rows, including IDs or fields that cannot currently be interpreted. */
    public static synchronized List<PendingEntry> pendingEntries() {
        ArrayList<PendingEntry> result = new ArrayList<>();
        PENDING.get().forEach((id, raw) -> {
            ArrayList<String> errors = new ArrayList<>();
            ItemPropertyRule parsed = parseRule(raw, errors);
            ResourceLocation location = ResourceLocation.tryParse(id);
            Item item = location == null ? null : ForgeRegistries.ITEMS.getValue(location);
            boolean unresolved = location == null || item == null || item == Items.AIR;
            if (unresolved) errors.add("gui.itemcontrol.item_property.error.unknown_item");
            result.add(new PendingEntry(id, parsed, raw.deepCopy(), unresolved, List.copyOf(errors)));
        });
        result.sort((left, right) -> {
            int invalid = Boolean.compare(left.invalid(), right.invalid());
            if (invalid != 0) return invalid;
            boolean leftHasProperties = left.rule() != null && !left.rule().isEmpty();
            boolean rightHasProperties = right.rule() != null && !right.rule().isEmpty();
            int modified = Boolean.compare(!leftHasProperties, !rightHasProperties);
            return modified != 0 ? modified : left.id().compareToIgnoreCase(right.id());
        });
        return List.copyOf(result);
    }

    public static int maxJsonChars() {
        return MAX_JSON_CHARS;
    }

    private static Map<String, JsonElement> parseDocument(String json) {
        if (json == null || json.length() > MAX_JSON_CHARS) throw new IllegalArgumentException("document size");
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("root must be an object");
        JsonObject root = parsed.getAsJsonObject();
        if (root.size() > MAX_RULES) throw new IllegalArgumentException("too many item rules");
        LinkedHashMap<String, JsonElement> entries = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String id = entry.getKey() == null ? "" : entry.getKey().trim();
            if (id.isEmpty() || entry.getValue() == null) continue;
            entries.put(id, entry.getValue().deepCopy());
        }
        return entries;
    }

    private static void writePending(Map<String, JsonElement> entries) throws IOException {
        KineticPaths.writeConfigTextsAtomic(Map.of(CONFIG_FILE, GSON.toJson(toJsonObject(entries))));
    }

    private static JsonObject toJsonObject(Map<String, JsonElement> entries) {
        JsonObject root = new JsonObject();
        entries.forEach((id, value) -> root.add(id, value.deepCopy()));
        return root;
    }

    private static Map<String, JsonElement> immutableCopy(Map<String, JsonElement> source) {
        LinkedHashMap<String, JsonElement> copy = new LinkedHashMap<>();
        source.forEach((id, value) -> copy.put(id, value.deepCopy()));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<ResourceLocation, ItemPropertyRule> buildActiveSnapshot(Map<String, JsonElement> entries) {
        LinkedHashMap<ResourceLocation, ItemPropertyRule> result = new LinkedHashMap<>();
        entries.forEach((rawId, rawRule) -> {
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null) {
                ItemModule.LOGGER.warn("Ignoring invalid item property ID {}", rawId);
                return;
            }
            ArrayList<String> errors = new ArrayList<>();
            ItemPropertyRule rule = parseRule(rawRule, errors);
            if (rule != null && !rule.isEmpty()) result.put(id, rule);
            if (!errors.isEmpty()) ItemModule.LOGGER.warn("Some item properties were ignored for {}: {}", rawId, errors);
        });
        return Collections.unmodifiableMap(result);
    }

    private static boolean validateKnownEntries(Map<String, JsonElement> entries) {
        java.util.HashSet<ResourceLocation> seen = new java.util.HashSet<>();
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) continue;
            if (!seen.add(id)) return false;
            ArrayList<String> errors = new ArrayList<>();
            parseRule(entry.getValue(), errors);
            if (!errors.isEmpty()) return false;
        }
        return true;
    }

    private static ItemPropertyRule parseRule(JsonElement raw, List<String> errors) {
        if (raw == null || !raw.isJsonObject()) {
            errors.add("gui.itemcontrol.item_property.error.expected_object");
            return null;
        }
        JsonObject json = raw.getAsJsonObject();
        List<ItemPropertyRule.AttributeModifier> attributes = parseAttributes(json.get("attributes"), errors);
        return new ItemPropertyRule(
                readNumber(json, "attack_damage", errors),
                readNumber(json, "attack_speed", errors),
                readNumber(json, "armor", errors),
                readNumber(json, "armor_toughness", errors),
                readNumber(json, "knockback_resistance", errors),
                attributes,
                readNumber(json, "mining_speed", errors),
                readInteger(json, "mining_level", errors),
                readInteger(json, "nutrition", errors),
                readNumber(json, "saturation", errors),
                readNumber(json, "eat_seconds", errors),
                readBoolean(json, "always_eat", errors),
                readInteger(json, "max_stack_size", errors),
                readInteger(json, "max_damage", errors),
                readInteger(json, "enchantability", errors),
                readString(json, "rarity", errors),
                readBoolean(json, "fire_resistant", errors),
                readNumber(json, "block_hardness", errors),
                readNumber(json, "block_explosion_resistance", errors),
                readBoolean(json, "explosion_immune", errors)
        );
    }

    private static List<ItemPropertyRule.AttributeModifier> parseAttributes(JsonElement raw, List<String> errors) {
        if (raw == null) return List.of();
        if (!raw.isJsonArray()) {
            errors.add("gui.itemcontrol.item_property.error.invalid_attributes");
            return List.of();
        }
        JsonArray array = raw.getAsJsonArray();
        if (array.size() > 128) {
            errors.add("gui.itemcontrol.item_property.error.too_many_attributes");
            return List.of();
        }
        ArrayList<ItemPropertyRule.AttributeModifier> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                errors.add("gui.itemcontrol.item_property.error.invalid_attribute");
                continue;
            }
            JsonObject value = element.getAsJsonObject();
            String attribute = primitiveString(value.get("attribute"));
            String slot = primitiveString(value.get("slot"));
            String operation = primitiveString(value.get("operation"));
            Double amount = primitiveNumber(value.get("amount"));
            if (attribute == null || slot == null || operation == null || amount == null || !Double.isFinite(amount)) {
                errors.add("gui.itemcontrol.item_property.error.invalid_attribute");
                continue;
            }
            ResourceLocation attributeId = ResourceLocation.tryParse(attribute);
            EquipmentSlot equipmentSlot;
            AttributeModifier.Operation modifierOperation;
            try {
                equipmentSlot = EquipmentSlot.valueOf(slot.toUpperCase(Locale.ROOT));
                modifierOperation = AttributeModifier.Operation.valueOf(operation.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                errors.add("gui.itemcontrol.item_property.error.invalid_attribute");
                continue;
            }
            if (attributeId == null || amount < -2048 || amount > 2048
                    || ForgeRegistries.ATTRIBUTES.getValue(attributeId) == null) {
                errors.add("gui.itemcontrol.item_property.error.invalid_attribute");
                continue;
            }
            result.add(new ItemPropertyRule.AttributeModifier(
                    attributeId.toString(), equipmentSlot.name(), modifierOperation.name(), amount
            ));
        }
        return List.copyOf(result);
    }

    private static Double readNumber(JsonObject json, String key, List<String> errors) {
        if (!json.has(key)) return null;
        Double value = primitiveNumber(json.get(key));
        if (value == null || !Double.isFinite(value) || !inRange(key, value)) {
            errors.add("gui.itemcontrol.item_property.error.invalid_number:" + key);
            return null;
        }
        return value;
    }

    private static Integer readInteger(JsonObject json, String key, List<String> errors) {
        if (!json.has(key)) return null;
        Double value = primitiveNumber(json.get(key));
        if (value == null || !Double.isFinite(value) || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE || !inIntegerRange(key, value)) {
            errors.add("gui.itemcontrol.item_property.error.invalid_integer:" + key);
            return null;
        }
        return value.intValue();
    }

    private static Boolean readBoolean(JsonObject json, String key, List<String> errors) {
        if (!json.has(key)) return null;
        JsonElement value = json.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean();
        errors.add("gui.itemcontrol.item_property.error.invalid_boolean:" + key);
        return null;
    }

    private static String readString(JsonObject json, String key, List<String> errors) {
        if (!json.has(key)) return null;
        String value = primitiveString(json.get(key));
        if (value != null && !value.isBlank()) {
            String normalized = value.toLowerCase(Locale.ROOT);
            if (key.equals("rarity") && List.of("common", "uncommon", "rare", "epic").contains(normalized)) return normalized;
        }
        errors.add("gui.itemcontrol.item_property.error.invalid_string:" + key);
        return null;
    }

    private static boolean inRange(String key, double value) {
        return switch (key) {
            case "mining_speed" -> value >= 0 && value <= 1_000_000;
            case "saturation" -> value >= 0 && value <= 1024;
            case "eat_seconds" -> value >= 0.05 && value <= 3600;
            case "block_hardness" -> value >= -1 && value <= 1_000_000;
            case "block_explosion_resistance" -> value >= 0 && value <= 1_000_000;
            default -> value >= -2048 && value <= 2048;
        };
    }

    private static boolean inIntegerRange(String key, double value) {
        return switch (key) {
            case "mining_level" -> value >= 0 && value <= 255;
            case "nutrition" -> value >= 0 && value <= 1024;
            case "max_stack_size" -> value >= 1 && value <= 99;
            case "max_damage" -> value >= 0;
            case "enchantability" -> value >= 0 && value <= 100_000;
            default -> true;
        };
    }

    private static Double primitiveNumber(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return null;
        try {
            return element.getAsDouble();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String primitiveString(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
        return element.getAsString();
    }

    private static JsonObject writeRule(ItemPropertyRule rule) {
        JsonObject json = new JsonObject();
        putNumber(json, "attack_damage", rule.attackDamage());
        putNumber(json, "attack_speed", rule.attackSpeed());
        putNumber(json, "armor", rule.armor());
        putNumber(json, "armor_toughness", rule.armorToughness());
        putNumber(json, "knockback_resistance", rule.knockbackResistance());
        if (!rule.attributes().isEmpty()) {
            JsonArray attributes = new JsonArray();
            for (ItemPropertyRule.AttributeModifier modifier : rule.attributes()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("attribute", modifier.attribute());
                entry.addProperty("slot", modifier.slot());
                entry.addProperty("operation", modifier.operation());
                entry.addProperty("amount", modifier.amount());
                attributes.add(entry);
            }
            json.add("attributes", attributes);
        }
        putNumber(json, "mining_speed", rule.miningSpeed());
        putNumber(json, "mining_level", rule.miningLevel());
        putNumber(json, "nutrition", rule.nutrition());
        putNumber(json, "saturation", rule.saturation());
        putNumber(json, "eat_seconds", rule.eatSeconds());
        putBoolean(json, "always_eat", rule.alwaysEat());
        putNumber(json, "max_stack_size", rule.maxStackSize());
        putNumber(json, "max_damage", rule.maxDamage());
        putNumber(json, "enchantability", rule.enchantability());
        putString(json, rule.rarity());
        putBoolean(json, "fire_resistant", rule.fireResistant());
        putNumber(json, "block_hardness", rule.blockHardness());
        putNumber(json, "block_explosion_resistance", rule.blockExplosionResistance());
        putBoolean(json, "explosion_immune", rule.explosionImmune());
        return json;
    }

    private static void putNumber(JsonObject json, String key, Number value) {
        if (value != null) json.addProperty(key, value);
    }

    private static void putBoolean(JsonObject json, String key, Boolean value) {
        if (value != null) json.addProperty(key, value);
    }

    private static void putString(JsonObject json, String value) {
        if (value != null) json.addProperty("rarity", value);
    }

    public record PendingEntry(String id, ItemPropertyRule rule, JsonElement raw, boolean unresolved, List<String> errors) {
        public boolean invalid() {
            return !unresolved && errors.isEmpty();
        }
    }

    public record SaveResult(boolean success, String messageKey) {
    }
}
