package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import java.util.function.Consumer;

public final class ItemDataEditor {
    private ItemDataEditor() {}

    public static void open(KineticPage parent, String itemId, String initial, Consumer<String> onSave) {
        //? if >=1.21 {
        /*parent.openChild(new ComponentsEditorPage(itemId, initial, onSave));
        *///?} else {
        KineticSelectors.openNbtEditor(initial, onSave);
        //?}
    }
}