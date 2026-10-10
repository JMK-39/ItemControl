//? if >=1.21 {
/*package dev.xyat.itemcontrolvalidation;

import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.tabs.TabConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.common.Mod;

@Mod("itemcontrol_validation")
public final class RuntimeValidation {
*///?}
    // 26.1 lists enchantment levels directly in the enchantments component.
    //? if >=26.1 {
    /*private static final String ENCHANTED = "minecraft:diamond_sword[enchantments={\"minecraft:sharpness\":1}]";
    *///?} else if >=1.21 {
    /*private static final String ENCHANTED = "minecraft:diamond_sword[enchantments={levels:{\"minecraft:sharpness\":1}}]";
    *///?}
    //? if >=1.21 {
/*    private static int failures;
    private Object preheatedProtection;
    private BanItemConfig.ItemRule preheatedBan;
    private boolean preheatedTab;
    public RuntimeValidation() {
        if (Boolean.getBoolean("itemcontrol.propertyValidation")) ItemPropertyRuntimeChecks.install();
        if (Boolean.getBoolean("itemcontrol.guiValidation")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::started);
            return;
        }
        if (!Boolean.getBoolean("itemcontrol.runtimeValidation")) return;
        try {
            if (net.minecraft.client.Minecraft.getInstance() == null) {
                require(dev.xyat.kineticcore.api.runtime.KineticClientRuntime.currentLevel() == null, "startup client level unavailable");
                require(dev.xyat.kineticcore.api.runtime.KineticClientRuntime.currentScreen() == null, "startup client screen unavailable");
                require(dev.xyat.kineticcore.api.runtime.KineticClientRuntime.localPlayer() == null, "startup client player unavailable");
                require(!dev.xyat.kineticcore.api.runtime.KineticClientRuntime.connected(), "startup client disconnected");
                require(dev.xyat.kineticcore.api.runtime.KineticClientRuntime.currentServerAddress() == null, "startup server address unavailable");
                require(dev.xyat.kineticcore.api.runtime.KineticClientRuntime.connectionRevision() >= 0, "startup connection revision available");
                org.slf4j.LoggerFactory.getLogger(RuntimeValidation.class).info("ITEMCONTROL_VALIDATION_CLIENT_STARTUP_PASS");
            }
            String enchanted = ENCHANTED;
            preheatedBan = new BanItemConfig.ItemRule(enchanted);
            var tabs = new TabConfig.Data();
            var tab = new TabConfig.TabAddition();
            tab.tabId = "minecraft:ingredients";
            tab.items.add(new TabConfig.TabItem("minecraft:diamond_sword", enchanted.substring(enchanted.indexOf('['))));
            tabs.additions.add(tab);
            preheatedTab = TabConfig.isValidForServer(tabs);
            var build = dev.xyat.itemcontrol.item.config.ItemPropertyConfig.class.getDeclaredMethod("buildActiveSnapshot", java.util.Map.class);
            build.setAccessible(true);
            preheatedProtection = build.invoke(null, java.util.Map.of(enchanted, com.google.gson.JsonParser.parseString("{\"fire_resistant\":true}")));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::started);
    }

    private void started(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (Boolean.getBoolean("itemcontrol.guiValidation")) { GuiLongTextValidation.install(); return; }
        try {
            String json = TabConfig.GSON.toJson(new TabConfig.TabItem());
            require(json.contains("\"components\"") && json.contains("\"matchComponents\"")
                    && !json.contains("\"nbt\""), "Neo creative tab component schema: " + json);
            ItemStack parsed = BanItemConfig.parseItemStack("minecraft:diamond_sword[damage=5,custom_data={CaseKey:1}]");
            require(!parsed.isEmpty() && parsed.getDamageValue() == 5, "native components parse");
            require(BanItemConfig.parseItemStack("minecraft:diamond_sword{Damage:5}").isEmpty(), "reject old NBT");
            require(BanItemConfig.parseItemStack("minecraft:diamond[custom_data={x:1}]garbage").isEmpty(), "reject trailing text");
            require(new BanItemConfig.ItemRule("minecraft:diamond_sword[custom_data={CaseKey:1}]").matches(parsed), "component subset rule");
            require(!new BanItemConfig.ItemRule("minecraft:diamond_sword[custom_data={casekey:1}]").matches(parsed), "preserve data case");
            require(!new BanItemConfig.ItemRule("minecraft:diamond_sword[damage=0]").matches(parsed), "explicit default constraint");
            require(new BanItemConfig.ItemRule("minecraft:diamond_sword[!custom_name]").matches(parsed), "component absence constraint");
            require(!new BanItemConfig.ItemRule("minecraft:diamond{invalid:1}").matches(new ItemStack(Items.DIAMOND)), "invalid rule cannot become broad ID match");
            require(dev.xyat.itemcontrol.item.config.ItemProtectionConfig.areValidProtectionRules(java.util.List.of(
                    "minecraft:diamond_sword[custom_data={CaseKey:1}];true;false;false;false")), "protection component validation");
            require(!dev.xyat.itemcontrol.item.config.ItemProtectionConfig.areValidProtectionRules(java.util.List.of(
                    "minecraft:diamond_sword{Damage:5};true;false;false;false")), "protection rejects legacy NBT");
            require(!TabConfig.beginEdit("{\"removals\":[],\"additions\":[{\"tabId\":\"minecraft:ingredients\",\"items\":[{\"id\":\"minecraft:diamond\",\"nbt\":\"{}\"}]}],\"hiddenTabs\":[]}"), "old tab schema rejected");
            org.slf4j.LoggerFactory.getLogger(RuntimeValidation.class).info("ITEMCONTROL_VALIDATION_COMPONENT_SCHEMA_PASS");
            run("trash", () -> validateTrash(event));
            run("properties", RuntimeValidation::validateProperties);
            run("component-mutations", RuntimeValidation::validateMutations);
            run("startup-registries", this::validatePreheated);
            run("startup-tabs", () -> require(preheatedTab, "startup tab retains dynamic components"));
            require(failures == 0, "regression failures: " + failures);
            org.slf4j.LoggerFactory.getLogger(RuntimeValidation.class).info("ITEMCONTROL_VALIDATION_PASS native component rules and schema");
        } catch (Throwable error) {
            org.slf4j.LoggerFactory.getLogger(RuntimeValidation.class).error("ITEMCONTROL_VALIDATION_FAIL", error);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private interface Checked { void run() throws Exception; }
    private static void run(String name, Checked check) {
        try { check.run(); org.slf4j.LoggerFactory.getLogger(RuntimeValidation.class).info("ITEMCONTROL_CHECK_PASS {}", name); }
        catch (Throwable error) { failures++; org.slf4j.LoggerFactory.getLogger(RuntimeValidation.class).error("ITEMCONTROL_CHECK_FAIL " + name, error); }
    }

    private void validatePreheated() throws ReflectiveOperationException {
        var stack = BanItemConfig.parseItemStack(ENCHANTED);
        require(!stack.isEmpty() && preheatedBan.matches(stack), "preheated dynamic component ban rule");
        var field = dev.xyat.itemcontrol.item.config.ItemPropertyConfig.class.getDeclaredField("ACTIVE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var active = (java.util.concurrent.atomic.AtomicReference<Object>) field.get(null);
        Object before = active.get();
        try {
            active.set(preheatedProtection);
            var rule = dev.xyat.itemcontrol.item.config.ItemPropertyConfig.activeProtection(stack);
            require(rule != null && Boolean.TRUE.equals(rule.fireResistant()), "preheated dynamic protection rule");
        } finally { active.set(before); }
    }

    private static void validateMutations() {
        var before = BanItemConfig.snapshotData();
        boolean enabled = dev.xyat.itemcontrol.item.util.ItemBanControl.isReplacementEnabled();
        try {
            dev.xyat.itemcontrol.item.util.ItemBanControl.setReplacementEnabled(true);
            var data = new BanItemConfig.Data();
            data.mergedItems.put("minecraft:emerald", java.util.List.of("minecraft:diamond[!custom_name]"));
            BanItemConfig.restoreDataSnapshot(data);
            var diamond = BanItemConfig.parseItemStack("minecraft:diamond[custom_name='\"named\"']");
            diamond.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            require(diamond.getItem() == Items.EMERALD, "component remove triggers merge");
            data.mergedItems.put("minecraft:emerald", java.util.List.of("minecraft:diamond[custom_data={x:1}]"));
            BanItemConfig.restoreDataSnapshot(data);
            diamond = BanItemConfig.parseItemStack("minecraft:diamond");
            var target = dev.xyat.itemcontrol.item.data.ItemData.compile("minecraft:diamond", "[custom_data={x:1}]");
            diamond.applyComponentsAndValidate(target.patch());
            require(diamond.getItem() == Items.EMERALD, "validated patch triggers merge");
        } finally { BanItemConfig.restoreDataSnapshot(before); dev.xyat.itemcontrol.item.util.ItemBanControl.setReplacementEnabled(enabled); }
    }

    private static void validateTrash(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        var trash = new dev.xyat.itemcontrol.cleaner.CleanerSavedData.BigTrashContainer(9);
        var stack = BanItemConfig.parseItemStack("minecraft:diamond_sword[damage=5,custom_data={CaseKey:1}]");
        stack.setCount(1000000);
        require(trash.addItem(stack).isEmpty() && trash.getItem(0).getCount() == 1000000, "large trash insertion preserves count");
        var extra = stack.copyWithCount(123);
        require(trash.addItem(extra).isEmpty() && trash.getItem(0).getCount() == 1000123, "large trash aggregation preserves count");
        var different = BanItemConfig.parseItemStack("minecraft:diamond_sword[damage=6]");
        trash.addItem(different);
        require(trash.getItem(1).getDamageValue() == 6, "different components do not merge");
        var data = new dev.xyat.itemcontrol.cleaner.CleanerSavedData();
        boolean enabled = dev.xyat.itemcontrol.cleaner.config.CleanerConfig.enableTrashBin;
        try {
            dev.xyat.itemcontrol.cleaner.config.CleanerConfig.enableTrashBin = true;
            data.addRecord(trash);
            var saved = data.save(new net.minecraft.nbt.CompoundTag(), event.getServer().registryAccess());
            var loaded = dev.xyat.itemcontrol.cleaner.CleanerSavedData.load(saved, event.getServer().registryAccess());
            var restored = loaded.getRecord(0).getItem(0);
            require(restored.getCount() == 1000123 && ItemStack.isSameItemSameComponents(stack, restored), "trash SavedData component/count round trip");
            var payload = dev.xyat.kineticcore.api.network.NetworkBuffers.encode(buffer -> buffer.writeVarIntArray(new int[]{restored.getCount(), Integer.MAX_VALUE}));
            var counts = dev.xyat.kineticcore.api.network.NetworkBuffers.decode(payload, buffer -> buffer.readVarIntArray());
            require(counts[0] == 1000123 && counts[1] == Integer.MAX_VALUE, "trash count network round trip");
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), event.getServer().registryAccess());
            try {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, restored);
                var received = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                require(received.getCount() == 1000123 && ItemStack.isSameItemSameComponents(restored, received), "trash component network round trip");
            } finally { buffer.release(); }

        } finally { dev.xyat.itemcontrol.cleaner.config.CleanerConfig.enableTrashBin = enabled; }
    }

    private static void validateProperties() throws ReflectiveOperationException {
        var field = dev.xyat.itemcontrol.item.config.ItemPropertyConfig.class.getDeclaredField("ACTIVE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var active = (java.util.concurrent.atomic.AtomicReference<Object>) field.get(null);
        var build = dev.xyat.itemcontrol.item.config.ItemPropertyConfig.class.getDeclaredMethod("buildActiveSnapshot", java.util.Map.class);
        build.setAccessible(true);
        var previous = active.get();
        try {
*///?}
            // 26.1 food and eating time are default components, rebuilt by ItemControl's initializer when items bind.
//? if >=26.1 {
/*            active.set(build.invoke(null, java.util.Map.of("minecraft:apple", com.google.gson.JsonParser.parseString("{\"nutrition\":8}"))));
            var apple = new ItemStack(Items.APPLE);
            var components = net.minecraft.core.component.DataComponentMap.builder().addAll(Items.APPLE.components());
            dev.xyat.itemcontrol.item.property.ItemPropertyOverrides.applyDefaultComponents(Items.APPLE, components);
            var food = components.build().get(net.minecraft.core.component.DataComponents.FOOD);
            require(food.nutrition() == 8 && Math.abs(food.saturation() - 4.8F) < 0.001F, "nutrition override preserves saturation coefficient");
            active.set(build.invoke(null, java.util.Map.of("minecraft:apple", com.google.gson.JsonParser.parseString("{\"max_stack_size\":12,\"rarity\":\"epic\",\"eat_seconds\":2.0,\"enchantability\":7}"))));
            components = net.minecraft.core.component.DataComponentMap.builder().addAll(Items.APPLE.components());
            dev.xyat.itemcontrol.item.property.ItemPropertyOverrides.applyDefaultComponents(Items.APPLE, components);
            var built = components.build();
            require(apple.getMaxStackSize() == 12 && apple.getRarity() == net.minecraft.world.item.Rarity.EPIC, "existing stack property Mixins");
            require(Math.abs(built.get(net.minecraft.core.component.DataComponents.CONSUMABLE).consumeSeconds() - 2.0F) < 0.001F
                    && built.get(net.minecraft.core.component.DataComponents.ENCHANTABLE).value() == 7, "eating time and enchantability components");
*///?} else if >=1.21 {
/*            active.set(build.invoke(null, java.util.Map.of("minecraft:apple", com.google.gson.JsonParser.parseString("{\"nutrition\":8}"))));
            var apple = new ItemStack(Items.APPLE);
            var food = apple.getFoodProperties(null);
            require(food.nutrition() == 8 && Math.abs(food.saturation() - 4.8F) < 0.001F, "nutrition override preserves saturation coefficient");
            active.set(build.invoke(null, java.util.Map.of("minecraft:apple", com.google.gson.JsonParser.parseString("{\"max_stack_size\":12,\"rarity\":\"epic\",\"eat_seconds\":2.0}"))));
            require(apple.getMaxStackSize() == 12 && apple.getRarity() == net.minecraft.world.item.Rarity.EPIC
                    && apple.getUseDuration(null) == 40, "existing stack property Mixins");
*///?}
//? if >=1.21 {
/*            active.set(build.invoke(null, java.util.Map.of("minecraft:diamond_sword", com.google.gson.JsonParser.parseString("{\"attack_damage\":5,\"attributes\":[{\"attribute\":\"minecraft:generic.attack_speed\",\"slot\":\"mainhand\",\"operation\":\"ADDITION\",\"amount\":6}]}"))));
            var sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                    .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                            new net.minecraft.world.entity.ai.attributes.AttributeModifier(net.minecraft.resources.ResourceLocation.parse("validation:shared"), 2,
                                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE), net.minecraft.world.entity.EquipmentSlotGroup.ANY).build());
            double[] values = {0, 0, 0};
            sword.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                if (attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) values[0] += modifier.amount();
                if (attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)) values[1] += modifier.amount();
            });
            sword.forEachModifier(net.minecraft.world.entity.EquipmentSlot.OFFHAND, (attribute, modifier) -> {
                if (attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) values[2] += modifier.amount();
            });
            require(values[0] == 5 && values[1] == 6 && values[2] == 2, "attribute operation and unaffected slot preservation: " + java.util.Arrays.toString(values));
        } finally { active.set(previous); }
    }
}
*///?}
