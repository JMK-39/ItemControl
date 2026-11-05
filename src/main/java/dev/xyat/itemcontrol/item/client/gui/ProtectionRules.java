package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Item protection rules in the Core item list editor: green outlines mark protected items, left-click opens the rule
 * dialog, right-click removes the rule, #tag and @mod picks get the default flags. Rules are
 * {@code identifier;fireproof;explosionproof;glowing;noGravity}.
 */
public final class ProtectionRules {
    private static final String DEFAULT_FLAGS = ";true;true;false;false";

    private ProtectionRules() {
    }

    /** Opens the editor on the given rules and sends the result to the server when saved. */
    public static void open(List<String> rules) {
        KineticSelectors.openItemListEditor(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.title"), rules,
                KineticSelectors.ItemListMode.ITEMS_TAGS_MODS, ADAPTER, ItemNetwork::saveProtectionRules);
    }

    static final KineticSelectors.ItemListRuleAdapter ADAPTER = new KineticSelectors.ItemListRuleAdapter() {
        @Override public boolean acceptsItem(ItemStack stack) { return true; }

        @Override public String itemRule(ItemStack stack) { return itemId(stack) + DEFAULT_FLAGS; }

        // The ban list's rule matcher covers item IDs, #tags and @mods on every version.
        @Override public boolean matches(String rule, ItemStack stack) {
            return new dev.xyat.itemcontrol.item.config.BanItemConfig.ItemRule(identifier(rule)).matches(stack);
        }

        @Override public boolean acceptsRule(String rule) { return rule != null && rule.split(";", -1).length == 5; }

        @Override public String groupRule(String rule) { return rule + DEFAULT_FLAGS; }

        @Override public Component ruleLabel(String rule) {
            String[] parts = rule.split(";", -1);
            if (parts.length != 5) return Component.literal(rule);
            return Component.literal(parts[0] + "  ").append(flags(Boolean.parseBoolean(parts[1]), Boolean.parseBoolean(parts[2]),
                    Boolean.parseBoolean(parts[3]), Boolean.parseBoolean(parts[4])));
        }

        @Override public boolean editItem(ItemStack stack, String existingRule, int mouseButton, boolean shift, Consumer<String> replace) {
            if (mouseButton == 1) {
                if (existingRule != null) replace.accept(null);
                return true;
            }
            if (mouseButton != 0) return false;
            KineticGui.openChild(new Dialog(stack, existingRule, replace));
            return true;
        }
    };

    private static String identifier(String rule) {
        int split = rule.indexOf(';');
        return split < 0 ? rule.trim() : rule.substring(0, split).trim();
    }

    private static String itemId(ItemStack stack) {
        var id = KineticRegistries.items().id(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static Component flags(boolean fire, boolean explosion, boolean glowing, boolean noGravity) {
        return Component.empty()
                .append(KineticI18n.translatable(fire ? "gui.itemcontrol.item.protection_editor.fire.on" : "gui.itemcontrol.item.protection_editor.fire.off")).append(" ")
                .append(KineticI18n.translatable(explosion ? "gui.itemcontrol.item.protection_editor.explosion.on" : "gui.itemcontrol.item.protection_editor.explosion.off")).append(" ")
                .append(KineticI18n.translatable(glowing ? "gui.itemcontrol.item.protection_editor.glowing.on" : "gui.itemcontrol.item.protection_editor.glowing.off")).append(" ")
                .append(KineticI18n.translatable(noGravity ? "gui.itemcontrol.item.protection_editor.gravity.off" : "gui.itemcontrol.item.protection_editor.gravity.on"));
    }

    /** One item's protection flags: Back, Delete and Apply in the header row, the item, then the four toggles. */
    public static final class Dialog extends KineticPage {
        private static final int W = 260, H = 123;
        private static final int X = (640 - W) / 2, Y = (360 - H) / 2;
        private static final int APPLY_X = X + W - 8 - 40, DELETE_X = APPLY_X - 4 - 64;
        private final ItemStack stack;
        private final String existing;
        private final Consumer<String> replace;
        private boolean fire = true, explosion = true, glowing, noGravity;

        public Dialog(ItemStack stack, String existing, Consumer<String> replace) {
            super(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.modal.title"));
            useCanvas(640, 360, 4);
            this.stack = stack.copy();
            this.existing = existing;
            this.replace = replace;
            if (existing != null) {
                String[] parts = existing.split(";", -1);
                if (parts.length == 5) {
                    fire = Boolean.parseBoolean(parts[1]);
                    explosion = Boolean.parseBoolean(parts[2]);
                    glowing = Boolean.parseBoolean(parts[3]);
                    noGravity = Boolean.parseBoolean(parts[4]);
                }
            }
        }

        @Override protected void build(KineticUi ui) {
            ui.button(X + 8, Y + 6, 40).text(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.back"))
                    .tooltip(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.cancel")).onClick(this::close).build();
            KineticButton delete = ui.button(DELETE_X, Y + 6, 64).text(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.delete"))
                    .tooltip(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.delete"))
                    .onClick(() -> { replace.accept(null); close(); }).build();
            delete.setControlVisible(existing != null);
            ui.button(APPLY_X, Y + 6, 40).text(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.apply"))
                    .tooltip(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.apply"))
                    .onClick(() -> { replace.accept(itemId(stack) + ";" + fire + ";" + explosion + ";" + glowing + ";" + noGravity); close(); }).build();
            toggle(ui, X + 14, Y + 69, fire ? "fire.on" : "fire.off", "fire", () -> fire = !fire);
            toggle(ui, X + 138, Y + 69, explosion ? "explosion.on" : "explosion.off", "explosion", () -> explosion = !explosion);
            toggle(ui, X + 14, Y + 97, glowing ? "glowing.on" : "glowing.off", "glowing", () -> glowing = !glowing);
            toggle(ui, X + 138, Y + 97, noGravity ? "gravity.off" : "gravity.on", "gravity", () -> noGravity = !noGravity);
        }

        private void toggle(KineticUi ui, int x, int y, String textKey, String tooltipKey, Runnable flip) {
            ui.button(x, y, 108).text(KineticI18n.translatable("gui.itemcontrol.item.protection_editor." + textKey))
                    .tooltip(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip." + tooltipKey))
                    .onClick(() -> { flip.run(); rebuild(); }).build();
        }

        @Override protected void renderBackground(KineticGraphics g, int mouseX, int mouseY, float partialTick) {
            KineticTheme.shadow(g, width(), height());
            KineticTheme.panel(g, X, Y, W, H);
            g.scrollingTextCentered(title(), (X + 52 + DELETE_X - 4) / 2, Y + 10, DELETE_X - 4 - (X + 52), 0xFFFFFF, true);
            int slot = dev.xyat.kineticcore.api.client.gui.theme.KineticSizes.ITEM_SLOT;
            KineticTheme.itemSlot(g, X + 14, Y + 27, slot, false);
            KineticTheme.item(g, stack, X + 14, Y + 27, slot, 1.0F, false);
            g.scrollingText(stack.getHoverName(), X + 40, Y + 28, W - 54, 0xFFFFFF, false);
            g.scrollingText(Component.literal(itemId(stack)), X + 40, Y + 41, W - 54, 0xFFAAAAAA, false);
            g.scrollingText(KineticI18n.translatable(existing == null ? "gui.itemcontrol.item.protection_editor.modal.new"
                    : "gui.itemcontrol.item.protection_editor.modal.existing"), X + 14, Y + 55, W - 28, 0xFFFFFF, false);
        }
    }
}
