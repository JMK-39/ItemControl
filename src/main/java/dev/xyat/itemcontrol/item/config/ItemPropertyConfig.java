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
import net.minecraft.world.item.ItemStack;
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
import java.util.function.Supplier;

/** Persistence and immutable startup snapshot for per-item property overrides. */
public final class ItemPropertyConfig {
    private static final String CONFIG_FILE = "itemcontrol/item_properties.json";
    private static final int MAX_RULES = 16_384;
    private static final int MAX_JSON_CHARS = 2 * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicReference<Map<String, JsonElement>> PENDING = new AtomicReference<>(Map.of());
    private static final AtomicReference<ActiveSnapshot> ACTIVE = new AtomicReference<>(ActiveSnapshot.empty());
    private static final ThreadLocal<Boolean> PREVIEW_ORIGINAL = ThreadLocal.withInitial(() -> false);

    private record ActiveSnapshot(
            Map<ResourceLocation, ItemPropertyRule> items,
            ItemProtectionPatternIndex patterns,
            Map<String, JsonElement> document
    ) {
        private static ActiveSnapshot empty() {
            return new ActiveSnapshot(Map.of(), ItemProtectionPatternIndex.empty(), Map.of());
        }
    }

    private ItemPropertyConfig() {
    }

    /** Loads disk data into both the editable document and the current process snapshot. */
    public static synchronized void load() {
        try {
            String raw = KineticPaths.configFileExists(CONFIG_FILE)
                    ? KineticPaths.readConfigText(CONFIG_FILE)
                    : "{}";
            Map<String, JsonElement> parsed = migrateLegacyProtectionRules(parseDocument(raw));
            PENDING.set(immutableCopy(parsed));
            ACTIVE.set(buildActiveSnapshot(parsed));
            if (!KineticPaths.configFileExists(CONFIG_FILE)) writePending(parsed);
        } catch (Exception e) {
            ItemModule.LOGGER.error("Failed to load item property overrides; retaining the last valid snapshot", e);
        }
    }

    /** Current process rules; saving pending edits never mutates this map. */
    public static ItemPropertyRule active(ResourceLocation itemId) {
        return itemId == null || PREVIEW_ORIGINAL.get() ? null : ACTIVE.get().items().get(itemId);
    }

    /** Reads an item's original values while temporarily excluding ItemControl's active overrides. */
    public static <T> T previewOriginal(Supplier<T> read) {
        boolean previous = PREVIEW_ORIGINAL.get();
        PREVIEW_ORIGINAL.set(true);
        try {
            return read.get();
        } finally {
            PREVIEW_ORIGINAL.set(previous);
        }
    }

    /** Current process rules for a registered item. */
    public static ItemPropertyRule active(Item item) {
        if (item == null || item == Items.AIR) return null;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        return active(itemId);
    }

    /** Immutable copy of the active startup snapshot for login synchronization. */
    public static Map<ResourceLocation, ItemPropertyRule> activeSnapshot() {
        return ACTIVE.get().items();
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
        return GSON.toJson(toJsonObject(ACTIVE.get().document()));
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

    /** Protection-only rules may target an exact item, an NBT variant, a tag, or a mod namespace. */
    public static ItemPropertyRule activeProtection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ActiveSnapshot snapshot = ACTIVE.get();
        ItemPropertyRule nbt = snapshot.patterns().matchingNbt(stack);
        if (nbt != null) return nbt;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        ItemPropertyRule exact = id == null ? null : snapshot.items().get(id);
        if (exact != null && exact.hasProtectionFields()) return exact;
        return snapshot.patterns().matchingScope(stack);
    }

    public static boolean isProtectionPattern(String key) {
        return ItemProtectionPatternIndex.isPatternKey(key)
                && ItemProtectionPatternIndex.isValidPatternKey(key);
    }

    public static boolean isProtectionSelectorCandidate(String key) {
        return ItemProtectionPatternIndex.isPatternKey(key);
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

    private static Map<String, JsonElement> migrateLegacyProtectionRules(Map<String, JsonElement> current) {
        List<String> legacy = ItemProtectionConfig.indestructibleItemsRaw;
        if (legacy.isEmpty()) return current;
        if (!ItemProtectionConfig.areValidProtectionRules(legacy)) {
            ItemModule.LOGGER.warn("Legacy item protection list contains invalid entries; migration was skipped");
            return current;
        }
        LinkedHashMap<String, JsonElement> merged = new LinkedHashMap<>(current);
        for (String entry : legacy) {
            String[] parts = entry.split(";", 5);
            String id = parts[0].trim();
            // The old matcher ignored NBT on tag and mod selectors. Keep that behavior when migrating.
            int nbtStart = id.indexOf('{');
            if (nbtStart >= 0 && (id.startsWith("#") || id.startsWith("@"))) {
                id = id.substring(0, nbtStart).trim();
            }
            if (isProtectionSelectorCandidate(id) && !isProtectionPattern(id)) {
                ItemModule.LOGGER.warn("Cannot migrate invalid protection selector {}", id);
                return current;
            }
            JsonElement existing = merged.get(id);
            if (existing != null && !existing.isJsonObject()) {
                ItemModule.LOGGER.warn("Cannot migrate protection rule {} over a malformed property rule", id);
                return current;
            }
            JsonObject rule = existing == null ? new JsonObject() : existing.getAsJsonObject().deepCopy();
            addIfAbsent(rule, "fire_resistant", Boolean.parseBoolean(parts[1].trim()));
            addIfAbsent(rule, "explosion_immune", Boolean.parseBoolean(parts[2].trim()));
            addIfAbsent(rule, "glowing", Boolean.parseBoolean(parts[3].trim()));
            addIfAbsent(rule, "no_gravity", Boolean.parseBoolean(parts[4].trim()));
            addIfAbsent(rule, "persistent", true);
            merged.put(id, rule);
        }
        try {
            parseDocument(GSON.toJson(toJsonObject(merged)));
            if (!validateKnownEntries(merged)) {
                ItemModule.LOGGER.warn("Cannot migrate legacy protection rules into invalid property rules");
                return current;
            }
            writePending(merged);
            ItemProtectionConfig.setProtectionRules(List.of());
            ItemProtectionConfig.save();
            ItemModule.LOGGER.info("Migrated {} legacy item protection rules into item_properties.json", legacy.size());
            return merged;
        } catch (Exception exception) {
            try {
                ItemProtectionConfig.setProtectionRules(legacy);
                ItemProtectionConfig.save();
            } catch (Exception rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            try {
                writePending(current);
            } catch (IOException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            ItemModule.LOGGER.error("Could not migrate legacy item protection rules", exception);
            return current;
        }
    }

    private static void addIfAbsent(JsonObject rule, String key, boolean value) {
        if (!rule.has(key)) rule.addProperty(key, value);
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

    private static ActiveSnapshot buildActiveSnapshot(Map<String, JsonElement> entries) {
        LinkedHashMap<ResourceLocation, ItemPropertyRule> result = new LinkedHashMap<>();
        LinkedHashMap<String, ItemPropertyRule> patternRules = new LinkedHashMap<>();
        entries.forEach((rawId, rawRule) -> {
            if (ItemProtectionPatternIndex.isPatternKey(rawId)) {
                if (!isProtectionPattern(rawId) || !hasOnlyProtectionFields(rawRule)) {
                    ItemModule.LOGGER.warn("Ignoring invalid item protection selector {}", rawId);
                    return;
                }
                ArrayList<String> errors = new ArrayList<>();
                ItemPropertyRule rule = parseRule(rawRule, errors);
                if (rule != null && rule.hasProtectionFields() && errors.isEmpty()) patternRules.put(rawId, rule);
                if (!errors.isEmpty()) ItemModule.LOGGER.warn("Ignoring invalid protection fields for {}: {}", rawId, errors);
                return;
            }
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
        return new ActiveSnapshot(Collections.unmodifiableMap(result),
                ItemProtectionPatternIndex.build(patternRules), immutableCopy(entries));
    }

    private static boolean validateKnownEntries(Map<String, JsonElement> entries) {
        java.util.HashSet<ResourceLocation> seen = new java.util.HashSet<>();
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            if (ItemProtectionPatternIndex.isPatternKey(entry.getKey())) {
                if (!isProtectionPattern(entry.getKey()) || !hasOnlyProtectionFields(entry.getValue())) return false;
                ArrayList<String> errors = new ArrayList<>();
                parseRule(entry.getValue(), errors);
                if (!errors.isEmpty()) return false;
                continue;
            }
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

    private static boolean hasOnlyProtectionFields(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) return false;
        for (String key : raw.getAsJsonObject().keySet()) {
            if (!key.equals("fire_resistant") && !key.equals("explosion_immune")
                    && !key.equals("glowing") && !key.equals("no_gravity")
                    && !key.equals("persistent")) return false;
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
                readBoolean(json, "non_consumable", errors),
                readInteger(json, "max_stack_size", errors),
                readInteger(json, "max_damage", errors),
                readInteger(json, "enchantability", errors),
                readString(json, errors),
                readBoolean(json, "fire_resistant", errors),
                readNumber(json, "block_hardness", errors),
                readNumber(json, "block_explosion_resistance", errors),
                readBoolean(json, "explosion_immune", errors),
                readBoolean(json, "glowing", errors),
                readBoolean(json, "no_gravity", errors),
                readBoolean(json, "persistent", errors)
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
            if (attributeId == null || ForgeRegistries.ATTRIBUTES.getValue(attributeId) == null) {
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

    private static String readString(JsonObject json, List<String> errors) {
        if (!json.has("rarity")) return null;
        String value = primitiveString(json.get("rarity"));
        if (value != null && !value.isBlank()) {
            String normalized = value.toLowerCase(Locale.ROOT);
            if (List.of("common", "uncommon", "rare", "epic").contains(normalized)) return normalized;
        }
        errors.add("gui.itemcontrol.item_property.error.invalid_string:" + "rarity");
        return null;
    }

    private static boolean inRange(String key, double value) {
        return switch (key) {
            // Stored values are item modifiers; the editor presents player base damage (1) and speed (4).
            // -2 stores the editor's -1 (infinite damage) without colliding with zero damage.
            case "attack_damage" -> value == -2 || value >= -1 && value <= Integer.MAX_VALUE - 1D;
            case "attack_speed" -> value >= -4;
            case "mining_speed", "block_explosion_resistance" -> value >= 0 && value <= 1_000_000;
            case "saturation" -> value >= 0 && value <= 1024;
            case "eat_seconds" -> value >= 0.05 && value <= 3600;
            case "block_hardness" -> value >= -1 && value <= 1_000_000;
            default -> value >= -2048 && value <= 2048;
        };
    }

    private static boolean inIntegerRange(String key, double value) {
        return switch (key) {
            case "mining_level" -> value >= 0 && value <= 255;
            case "nutrition" -> value >= 0 && value <= 1024;
            case "max_stack_size" -> value >= 1 && value <= 99;
            case "max_damage" -> value >= -1;
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
        putBoolean(json, "non_consumable", rule.nonConsumable());
        putNumber(json, "max_stack_size", rule.maxStackSize());
        putNumber(json, "max_damage", rule.maxDamage());
        putNumber(json, "enchantability", rule.enchantability());
        putString(json, rule.rarity());
        putBoolean(json, "fire_resistant", rule.fireResistant());
        putNumber(json, "block_hardness", rule.blockHardness());
        putNumber(json, "block_explosion_resistance", rule.blockExplosionResistance());
        putBoolean(json, "explosion_immune", rule.explosionImmune());
        putBoolean(json, "glowing", rule.glowing());
        putBoolean(json, "no_gravity", rule.noGravity());
        putBoolean(json, "persistent", rule.persistent());
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
