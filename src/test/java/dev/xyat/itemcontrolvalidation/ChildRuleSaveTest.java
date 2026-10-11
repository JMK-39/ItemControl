package dev.xyat.itemcontrolvalidation;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.client.gui.*;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import java.lang.reflect.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Real page save/delete/selection code, with only the window lifecycle replaced by a no-window host. */
public final class ChildRuleSaveTest {
    private static final Class<?>[] PAGES = {EquipmentAttributeEditorPage.class, CurioAttributeEditorPage.class,
            FoodEffectEditorPage.class, CurioSlotModifierEditorPage.class};
    private static int failures;
    public static void main(String[] args) throws Exception {
        for (Class<?> type : PAGES) {
            check(type, "browse", () -> {
                var page = page(type, false); save(page);
                require(page.result != null && page.result.isEmpty(), "viewing defaults must not create a rule");
            });
            check(type, "edit", () -> {
                var page = page(type, true); set(page.value, field(type), "7"); save(page);
                require(page.result != null && page.result.size() == 1, "edited rule is returned");
                require(page.result.get(0).getAsJsonObject().get(type == FoodEffectEditorPage.class ? "duration" : "amount")
                        .getAsDouble() == (type == FoodEffectEditorPage.class ? 140 : 7), "Save applies current input without Apply");
            });
            check(type, "delete", () -> {
                var page = page(type, true); call(page.value, attribute(type) ? "deleteRule" : "remove"); save(page);
                require(page.result != null && page.result.isEmpty(), "Delete then Save must not resurrect the rule");
            });
            check(type, "invalid", () -> {
                var page = page(type, true); set(page.value, field(type), "invalid"); save(page);
                require(page.result == null && !page.closed, "invalid edited input must stay open without submitting");
            });
            check(type, "selection", () -> {
                var page = page(type, false);
                if (attribute(type)) call(page.value, "select", String.class, "minecraft:generic.max_health");
                else { set(page.value, type == FoodEffectEditorPage.class ? "selected" : "slot", type == FoodEffectEditorPage.class ? "minecraft:strength" : "ring"); call(page.value, "selectExisting"); }
                save(page); require(page.result != null && page.result.isEmpty(), "changing selection only must not add defaults");
            });
            check(type, "apply then save", () -> {
                var page = page(type, false); set(page.value, field(type), "7");
                call(page.value, type == CurioSlotModifierEditorPage.class ? "apply" : "applyRule"); save(page);
                require(page.result != null && page.result.size() == 1, "Apply then Save retains exactly one rule");
            });
            check(type, "edit after delete", () -> {
                var page = page(type, true); call(page.value, attribute(type) ? "deleteRule" : "remove");
                set(page.value, field(type), "7"); save(page);
                require(page.result != null && page.result.size() == 1, "editing a deleted form can deliberately create a new rule");
            });
        }
        if (failures > 0) throw new AssertionError("CHILD_RULE_SAVE_FAIL cases=" + failures);
        System.out.println("CHILD_RULE_SAVE_PASS 28 actual page save/delete/selection cases");
    }
    private static boolean attribute(Class<?> type) { return type == EquipmentAttributeEditorPage.class || type == CurioAttributeEditorPage.class; }
    private static String field(Class<?> type) { return type == FoodEffectEditorPage.class ? "seconds" : "amount"; }
    private static final class Page { Object value; JsonArray result; boolean closed; }
    private static Page page(Class<?> type, boolean existing) throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var page = new Page(); page.value = unsafe.allocateInstance(type);
        String row = attribute(type) ? "{\"attribute\":\"minecraft:generic.armor\",\"slot\":\"MAINHAND\",\"mode\":\"add\",\"operation\":\"ADDITION\",\"amount\":3}"
                : type == FoodEffectEditorPage.class ? "{\"effect\":\"minecraft:speed\",\"duration\":60,\"amplifier\":0,\"probability\":1}"
                : "{\"slot\":\"curio\",\"amount\":3}";
        var rules = existing ? JsonParser.parseString("[" + row + "]").getAsJsonArray() : new JsonArray();
        set(page.value, "rules", rules); set(page.value, "error", "");
        if (type == FoodEffectEditorPage.class) {
            set(page.value, "done", (BiConsumer<JsonArray, String>) (result, mode) -> page.result = result);
            set(page.value, "selected", "minecraft:speed"); set(page.value, "seconds", "10");
            set(page.value, "level", "1"); set(page.value, "probability", "100"); set(page.value, "mode", "append");
            call(page.value, "selectExisting");
        } else {
            set(page.value, "done", (Consumer<JsonArray>) result -> page.result = result);
            if (attribute(type)) {
                set(page.value, "originals", List.of());
                if (type == EquipmentAttributeEditorPage.class) set(page.value, "slot", "MAINHAND");
                call(page.value, "select", String.class, "minecraft:generic.armor");
            } else {
                set(page.value, "slot", "curio"); set(page.value, "amount", "1");
                call(page.value, "selectExisting");
            }
        }
        var host = KineticPage.class.getDeclaredField("host"); host.setAccessible(true);
        host.set(page.value, Proxy.newProxyInstance(host.getType().getClassLoader(), new Class<?>[]{host.getType()},
                (proxy, method, args) -> { if (method.getName().equals("close")) page.closed = true; return null; }));
        return page;
    }
    private static void save(Page page) throws Exception {
        call(page.value, "save");
    }
    private static void check(Class<?> type, String name, Checked action) throws Exception {
        try { action.run(); }
        catch (AssertionError failure) { failures++; System.out.println(type.getSimpleName() + " " + name + ": " + failure.getMessage()); }
    }
    private interface Checked { void run() throws Exception; }
    private static void set(Object page, String name, Object value) throws Exception { var field = page.getClass().getDeclaredField(name); field.setAccessible(true); field.set(page, value); }
    private static void call(Object page, String name) throws Exception { call(page, name, null, null); }
    private static void call(Object page, String name, Class<?> type, Object value) throws Exception {
        var method = type == null ? page.getClass().getDeclaredMethod(name) : page.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        try { if (type == null) method.invoke(page); else method.invoke(page, value); }
        catch (InvocationTargetException failure) { if (failure.getCause() instanceof Error error) throw error; if (failure.getCause() instanceof Exception exception) throw exception; throw failure; }
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
