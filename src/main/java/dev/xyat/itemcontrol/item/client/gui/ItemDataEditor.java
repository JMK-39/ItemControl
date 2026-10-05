package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import java.util.function.Consumer;

/** Edits an item rule's data in Core's NBT editor, the same screen on every version. */
public final class ItemDataEditor {
    private ItemDataEditor() {}

    public static void open(KineticPage parent, String itemId, String initial, Consumer<String> onSave) {
        //? if >=1.21 {
        /*// Item component text ([damage=5]) is checked against the item instead of as NBT.
        KineticSelectors.openNbtEditor(initial, text -> {
            try {
                dev.xyat.itemcontrol.item.data.ItemData.compile(itemId, text);
                return null;
            } catch (RuntimeException invalid) {
                return invalid.getMessage() == null ? invalid.toString() : invalid.getMessage();
            }
        }, saved -> onSave.accept(saved.isBlank() ? dev.xyat.itemcontrol.item.data.ItemData.emptyData() : saved));
        *///?} else {
        KineticSelectors.openNbtEditor(initial, onSave);
        //?}
    }
}
