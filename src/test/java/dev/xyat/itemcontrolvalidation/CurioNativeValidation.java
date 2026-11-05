package dev.xyat.itemcontrolvalidation;

import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.itemcontrol.item.property.ItemEquipmentRefresh;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Opt-in installed-Curios checks. Restores the configuration and empty test slots before chaining. */
public final class CurioNativeValidation {
    private static final Path OUTPUT = Path.of(System.getProperty("itemcontrol.curioValidation.output",
            "D:/IDEAWork/ItemControl/build/ui-fixes-20261016/curio-native"));
    private static final List<String> results = new ArrayList<>();
    private static boolean installed, finished;
    private static int ticks, stage, due = 80;
    private static String before;
    private static CompletableFuture<Void> tooltip;

    public static void install() {
        if (installed) return;
        installed = true;
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END, CurioNativeValidation::tick);
    }

    private static void tick(MinecraftServer server) {
        if (finished || server.getPlayerList().getPlayers().isEmpty() || ++ticks < due) return;
        try {
            switch (stage) {
                case 0 -> {
                    require(dev.xyat.itemcontrol.item.compat.ItemCuriosCompat.available(), "Curios is installed");
                    before = ItemPropertyConfig.pendingJson();
                    check("native-slot-query-and-existing-stack-capability", () -> queries(server));
                    check("existing-native-curio-behaviour-retained", () -> nativeBehaviour(server));
                    tooltip = new CompletableFuture<>();
                    net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        try { nativeTooltip(); tooltip.complete(null); }
                        catch (Throwable error) { tooltip.completeExceptionally(error); }
                    });
                }
                case 1 -> {
                    if (!tooltip.isDone()) { due = ticks + 1; return; }
                    tooltip.join();
                    pass("native-multiple-slot-and-attribute-tooltip");
                    check("real-slot-admission-lock-and-attribute-modifiers", () -> CurioRuntimeChecks.run(server));
                }
                case 2 -> check("equipped-live-replace-preserves-unrelated-native-attributes", () -> CurioRuntimeChecks.verifyMode(server, "replace", 9, 5));
                case 3 -> check("equipped-live-remove", () -> CurioRuntimeChecks.verifyMode(server, "remove", 5, 0));
                case 4 -> check("equip-independent-copy", CurioRuntimeChecks::equipSecond);
                case 5 -> check("multiple-copies-slot-count-and-independent-unequip", CurioRuntimeChecks::verifyStackedAndRemoveFirst);
                case 6 -> check("remaining-copy-live-negative-slot-count", () -> CurioRuntimeChecks.verifyRemainingAndNegative(server));
                case 7 -> check("negative-slot-count-and-remove", CurioRuntimeChecks::verifyNegativeAndRemove);
                case 8 -> {
                    restore(server);
                    check("original-slot-counts-restored", CurioRuntimeChecks::close);
                }
                case 9 -> { finish(null); return; }
                default -> throw new IllegalStateException("validation stage " + stage);
            }
            stage++;
            due = ticks + 12;
        } catch (Throwable error) {
            ItemPropertyRuntimeChecks.failed = true;
            try { CurioRuntimeChecks.removeFixture(); restore(server); CurioRuntimeChecks.close(); }
            catch (Throwable cleanup) { error.addSuppressed(cleanup); }
            finish(error);
        }
    }

    private static void queries(MinecraftServer server) {
        var player = server.getPlayerList().getPlayers().get(0);
        ItemStack stack = new ItemStack(Items.STICK);
        var original = ItemPropertyConfig.previewOriginal(() -> Set.copyOf(CuriosApi.getItemStackSlots(stack, player).keySet()));
        var draft = JsonParser.parseString("{\"curio\":{\"enabled\":true,\"slots\":[\"ring\",\"necklace\"]}}");
        ItemPropertyConfig.previewDraft(KineticRegistries.items().id(Items.STICK), draft, () -> {
            require(CuriosApi.getItemStackSlots(stack, player).keySet().equals(Set.of("ring", "necklace")), "entity native slots are exactly ring and necklace");
            require(CuriosApi.getItemStackSlots(stack, false).keySet().equals(Set.of("ring", "necklace")), "entity-free native slots are exactly ring and necklace");
            require(CuriosApi.getCurio(stack).isPresent(), "stack constructed before activation receives ICurio");
            // Inspect the capability itself, independently of CuriosApi's convenience method.
            //? if >=1.21 {
            /*require(stack.getCapability(top.theillusivec4.curios.api.CuriosCapability.ITEM) != null, "real item capability is present");*/
            //?} else {
            require(stack.getCapability(top.theillusivec4.curios.api.CuriosCapability.ITEM).isPresent(), "real item capability is present");
            //?}
            return null;
        });
        var disabled = JsonParser.parseString("{\"curio\":{\"enabled\":false,\"slots\":[\"necklace\"]}}");
        ItemPropertyConfig.previewDraft(KineticRegistries.items().id(Items.STICK), disabled, () -> {
            require(CuriosApi.getItemStackSlots(stack, player).keySet().equals(original), "disabling restores native slots on the same stack");
            return null;
        });
        var empty = JsonParser.parseString("{\"curio\":{\"enabled\":true,\"slots\":[]}}");
        ItemPropertyConfig.previewDraft(KineticRegistries.items().id(Items.STICK), empty, () -> {
            require(CuriosApi.getItemStackSlots(stack, player).isEmpty(), "explicit empty slots deny every native slot");
            return null;
        });
    }

    private static void nativeBehaviour(MinecraftServer server) {
        var player = server.getPlayerList().getPlayers().get(0);
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            var original = ItemPropertyConfig.previewOriginal(() -> CuriosApi.getCurio(stack).orElse(null));
            if (original == null) continue;
            var context = new top.theillusivec4.curios.api.SlotContext("ring", player, 0, false, true);
            boolean unequip = original.canUnequip(context), use = original.canEquipFromUse(context);
            ItemPropertyConfig.previewDraft(KineticRegistries.items().id(item),
                    JsonParser.parseString("{\"curio\":{\"enabled\":true,\"slots\":[\"ring\",\"necklace\"]}}"), () -> {
                        var configured = CuriosApi.getCurio(stack).orElseThrow(() -> new AssertionError("Native capability was removed"));
                        require(configured.getClass() == original.getClass(), "existing ICurio provider remains authoritative");
                        require(configured.getStack() == stack && configured.canUnequip(context) == unequip
                                && configured.canEquipFromUse(context) == use, "native stack and callback behaviour retained");
                        return null;
                    });
            results.add("NATIVE_ITEM " + KineticRegistries.items().id(item) + " " + original.getClass().getName());
            return;
        }
        // The installed pack has real native accessories; absence is an explicit failure, never an implicit pass.
        throw new AssertionError("No original ICurio capability available for native-behaviour check");
    }

    private static void nativeTooltip() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        require(mc.player != null && mc.level != null, "client is in the validation world");
        ItemStack stack = new ItemStack(Items.STICK);
        ItemPropertyConfig.previewDraft(KineticRegistries.items().id(Items.STICK),
                JsonParser.parseString("{\"curio\":{\"enabled\":true,\"slots\":[\"ring\",\"necklace\"],\"attributes\":[{\"attribute\":\"minecraft:generic.armor\",\"operation\":\"ADDITION\",\"amount\":3}]}}"), () -> {
                    //? if >=1.21 {
                    /*var lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);*/
                    //?} else {
                    var lines = stack.getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
                    //?}
                    require(countKey(lines, "curios.tooltip.slot") == 1, "Curios creates exactly one native slot line");
                    require(countKey(lines, "curios.identifier.ring") == 1 && countKey(lines, "curios.identifier.necklace") == 1, "native slot line contains both configured slots");
                    require(countKey(lines, "curios.modifiers.ring") == 1 && countKey(lines, "curios.modifiers.necklace") == 1, "Curios creates one native attribute group for each configured slot");
                    require(countKey(lines, "attribute.name.generic.armor") + countKey(lines, "attribute.name.armor") == 2, "native armor attribute appears under both real slots");
                    results.add("TOOLTIP " + lines.stream().map(net.minecraft.network.chat.Component::getString).toList());
                    return null;
                });
    }

    private static int countKey(List<net.minecraft.network.chat.Component> lines, String key) {
        return lines.stream().mapToInt(line -> countKey(line, key)).sum();
    }
    private static int countKey(net.minecraft.network.chat.Component line, String key) {
        int count = 0;
        if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated) {
            if (translated.getKey().equals(key)) count++;
            for (Object arg : translated.getArgs()) if (arg instanceof net.minecraft.network.chat.Component component) count += countKey(component, key);
        }
        for (var sibling : line.getSiblings()) count += countKey(sibling, key);
        return count;
    }

    private static void restore(MinecraftServer server) {
        if (before != null) {
            require(ItemPropertyConfig.savePending(before).success(), "restore original rule document");
            ItemEquipmentRefresh.applyPending(server);
        }
    }
    private static void check(String label, Runnable action) { action.run(); pass(label); }
    private static void pass(String label) { results.add("PASS " + label); writeResult("RUNNING"); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void writeResult(String status) {
        try {
            Files.createDirectories(OUTPUT);
            Files.writeString(OUTPUT.resolve("result.txt"), status + System.lineSeparator() + String.join(System.lineSeparator(), results) + System.lineSeparator());
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    private static void finish(Throwable error) {
        finished = true;
        if (error != null) {
            var trace = new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(trace)); results.add(trace.toString());
        }
        writeResult(error == null ? "PASS" : "FAIL");
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            String next = System.getProperty("itemcontrol.curioValidation.thenValidation", "").trim();
            if (error == null && !next.isEmpty()) {
                try {
                    if (next.endsWith(".install()")) next = next.substring(0, next.length() - ".install()".length());
                    Class.forName(next).getMethod("install").invoke(null);
                    return;
                } catch (Throwable chained) { results.add("CHAIN_FAIL " + chained); writeResult("FAIL"); }
            }
            if (!Boolean.getBoolean("itemcontrol.curioValidation.keepClient")) dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
        });
    }
}
