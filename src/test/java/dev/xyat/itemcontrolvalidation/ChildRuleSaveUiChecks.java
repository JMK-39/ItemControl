package dev.xyat.itemcontrolvalidation;

import com.google.gson.JsonArray;
import dev.xyat.itemcontrol.item.client.gui.*;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.widget.KineticTextField;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.concurrent.atomic.AtomicReference;

/** Real child windows and button handlers for the four rule editors, before the server-save round trip. */
public final class ChildRuleSaveUiChecks {
    public static void run() {
        var parent = KineticGui.currentPage();
        for (int kind = 0; kind < 4; kind++) {
            for (int action = 0; action < 4; action++) {
                var result = new AtomicReference<JsonArray>();
                KineticPage child = page(kind, new JsonArray(), result);
                KineticGui.openChild(child);
                if (action != 0) field(kind).setTextValue("7");
                if (action == 2) {
                    press("curio.apply_rule");
                    press("curio.delete_rule");
                }
                if (action == 3) field(kind).setTextValue("invalid");
                press("save");
                if (action == 3) {
                    require(KineticGui.currentPage() == child && result.get() == null, "invalid current input stays in child " + kind);
                    child.close();
                } else {
                    require(KineticGui.currentPage() == parent, "Save returns to exact parent " + kind);
                    require(result.get() != null, "Save callback invoked " + kind);
                    if (action == 1) {
                        require(result.get().size() == 1, "current input creates exactly one rule " + kind);
                        var row = result.get().get(0).getAsJsonObject();
                        require(row.get(kind == 2 ? "duration" : "amount").getAsDouble() == (kind == 2 ? 140 : 7),
                                "Save automatically applies current input " + kind);
                    } else require(result.get().isEmpty(), "browse/delete Save does not create or resurrect rule " + kind);
                }
            }
        }
        org.slf4j.LoggerFactory.getLogger(ChildRuleSaveUiChecks.class).info("ITEM_CHILD_RULE_SAVE_PASS four real UI editors: browse, edit, Delete, invalid, parent navigation");
    }
    private static KineticPage page(int kind, JsonArray rules, AtomicReference<JsonArray> result) {
        return switch (kind) {
            case 0 -> new EquipmentAttributeEditorPage(new ItemStack(Items.BRICK), rules, result::set);
            case 1 -> new CurioAttributeEditorPage(new ItemStack(Items.BRICK), rules, result::set);
            case 2 -> new FoodEffectEditorPage(rules, "append", (value, mode) -> result.set(value));
            default -> new CurioSlotModifierEditorPage(rules, result::set);
        };
    }
    private static KineticTextField field(int kind) {
        int x = kind == 2 ? 12 : kind == 3 ? 326 : 160;
        int y = kind == 2 ? 98 : kind == 3 ? 58 : 90;
        for (var control : Minecraft.getInstance().screen.children())
            if (control instanceof KineticTextField field && field.controlX() == x && field.controlY() == y) return field;
        throw new AssertionError("actual child input missing " + kind);
    }
    private static void press(String key) {
        String label = KineticI18n.translatable("gui.itemcontrol.item_property." + key).getString();
        for (var control : Minecraft.getInstance().screen.children()) {
            if (control instanceof AbstractButton button && button.getMessage().getString().equals(label)) {
                //? if >=26.1 {
                /*button.onPress(new net.minecraft.client.input.MouseButtonInfo(0, 0));*/
                //?} else {
                button.onPress();
                //?}
                return;
            }
        }
        throw new AssertionError("actual child button missing " + key);
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
