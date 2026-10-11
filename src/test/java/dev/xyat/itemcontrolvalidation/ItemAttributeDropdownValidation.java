package dev.xyat.itemcontrolvalidation;

import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import net.minecraft.client.Minecraft;
import java.util.List;

/** Real mouse/key dropdown regression; reflection only observes the core's private overlay bounds. */
public final class ItemAttributeDropdownValidation {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ItemAttributeDropdownValidation.class);
    public static void verifyAndLeaveOpen() throws Exception {
        var page = KineticGui.currentPage();
        var attribute = dev.xyat.itemcontrol.item.property.ItemPropertyOverrides.attribute(KineticResourceIds.parse("minecraft:generic.armor"));
        String id = KineticRegistries.attributes().id(attribute).toString();
        open();
        Object menu = menu();
        require(menu != null && field(menu, "search") != null, "large attribute menu has core search input");
        List<?> rows = rows();
        require(rows.size() >= 10, "attribute registry list is searchable");
        for (int index = 1; index < rows.size(); index++) {
            require((int) field(rows.get(index), "y") - (int) field(rows.get(index - 1), "y")
                    - (int) field(rows.get(index - 1), "height") == 2, "attribute text rows have 2px gaps");
        }
        boolean english = KineticClientRuntime.selectedLanguage().startsWith("en_");
        var item = rows.stream().map(row -> {
            try { return item(row); } catch (Exception failure) { throw new IllegalStateException(failure); }
        }).filter(row -> row.label().getString().equals(id)).findFirst().orElseThrow();
        require(english ? item.detail().getString().isEmpty()
                : item.detail().getString().equals(KineticI18n.translatable(attribute.getDescriptionId()).getString()),
                "English hides translation; localized detail is pure translation without duplicate ID");
        type(id);
        choose(id);
        require(id.equals(field(page, "attribute")), "filtered ID click selects original registry ID");
        if (!english) {
            open();
            type(KineticI18n.translatable(attribute.getDescriptionId()).getString());
            choose(id);
            require(id.equals(field(page, "attribute")), "translated search selects original registry ID");
        }
        open();
        type("itemcontrol_validation:no_such_attribute");
        require(rows().isEmpty(), "search with no matches has no selectable row");
        Object bounds = bounds();
        click((int) field(bounds, "x") + 10, (int) field(bounds, "viewportTop") + 5);
        require(id.equals(field(page, "attribute")), "empty result click cannot select another attribute");
        escape();
        require(menu() == null && KineticGui.currentPage() == page, "ESC closes only the dropdown overlay");
        // Exercise long mod IDs with the same filtered action mapping when the modpack contributes them.
        String longId = KineticRegistries.attributes().ids().stream().map(Object::toString)
                .max(java.util.Comparator.comparingInt(String::length)).orElse(id);
        open(); type(longId); choose(longId);
        require(longId.equals(field(page, "attribute")), "long ID selects correct filtered row");
        open();
        LOG.info("ITEM_ATTRIBUTE_DROPDOWN_PASS language={} mouse open, ID/translation search, original action index, no results, ESC, 2px rows, long ID={}",
                KineticClientRuntime.selectedLanguage(), longId);
    }
    private static void open() throws Exception {
        var screen = Minecraft.getInstance().screen;
        int x = (int) screen.getClass().getMethod("canvasX").invoke(screen);
        int y = (int) screen.getClass().getMethod("canvasY").invoke(screen);
        float scale = (float) screen.getClass().getMethod("canvasScale").invoke(screen);
        click(x + 24 * scale, y + 49 * scale);
    }
    private static void choose(String id) throws Exception {
        Object bounds = bounds();
        Object selected = rows().stream().filter(row -> {
            try { return item(row).label().getString().equals(id); } catch (Exception failure) { throw new IllegalStateException(failure); }
        }).findFirst().orElseThrow(() -> new AssertionError("filtered attribute row missing: " + id));
        click((int) field(bounds, "x") + 10,
                (int) field(selected, "y") + (int) field(selected, "height") / 2.0);
        require(menu() == null, "choosing filtered row closes dropdown");
    }
    private static KineticOverlays.MenuItem item(Object row) throws Exception {
        return (KineticOverlays.MenuItem) field(field(row, "control"), "item");
    }
    @SuppressWarnings("unchecked") private static List<?> rows() throws Exception { return (List<?>) field(bounds(), "rows"); }
    private static Object bounds() throws Exception {
        var screen = Minecraft.getInstance().screen;
        Object overlays = overlays();
        for (var method : overlays.getClass().getDeclaredMethods()) {
            if (method.getName().equals("menuBounds")) {
                method.setAccessible(true);
                return method.invoke(null, menu(), screen.width, screen.height, Minecraft.getInstance().font, field(overlays, "menuScroll"));
            }
        }
        throw new AssertionError("core menu bounds unavailable");
    }
    private static Object menu() throws Exception { return field(overlays(), "contextMenu"); }
    private static Object overlays() throws Exception {
        Object runtime = field(Minecraft.getInstance().screen, "runtime");
        return runtime.getClass().getMethod("overlays").invoke(runtime);
    }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void type(String query) {
        for (char character : query.toCharArray()) {
            //? if >=26.1 {
            /*Minecraft.getInstance().screen.charTyped(new net.minecraft.client.input.CharacterEvent(character));*/
            //?} else {
            Minecraft.getInstance().screen.charTyped(character, 0);
            //?}
        }
    }
    private static void click(double x, double y) {
        //? if >=26.1 {
        /*Minecraft.getInstance().screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x, y,
                new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);*/
        //?} else {
        Minecraft.getInstance().screen.mouseClicked(x, y, 0);
        //?}
    }
    private static void escape() {
        //? if >=26.1 {
        /*Minecraft.getInstance().screen.keyPressed(new net.minecraft.client.input.KeyEvent(256, 0, 0));*/
        //?} else {
        Minecraft.getInstance().screen.keyPressed(256, 0, 0);
        //?}
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
