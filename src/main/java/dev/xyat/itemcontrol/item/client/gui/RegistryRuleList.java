package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.widget.list.KineticRowList;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Every attribute or effect as a plain row: the registry ID in white and, outside English, the translated name in
 * gold after it. Entries with a rule have a green outline and their value at the end of the row; the entry being
 * edited has a yellow outline. Clicking a row edits that entry.
 */
final class RegistryRuleList extends KineticRowList<String> {
    private static final int VALUE_WIDTH = 96;
    private final boolean effects;
    private final Predicate<String> configured;
    private final Function<String, Component> summary;

    RegistryRuleList(int x, int y, int width, int height, boolean effects, Predicate<String> configured,
                     Function<String, Component> summary) {
        super(x, y, width, height, 20);
        this.effects = effects;
        this.configured = configured;
        this.summary = summary;
    }

    /** The row label; also what the search matches against. */
    static Component label(boolean effects, String id) {
        var label = Component.literal(id).withStyle(ChatFormatting.WHITE);
        if (KineticClientRuntime.selectedLanguage().startsWith("en_")) return label;
        var name = name(effects, id);
        return name.getString().equals(id) ? label
                : label.append(Component.literal(" — ").withStyle(ChatFormatting.GRAY)).append(name.copy().withStyle(ChatFormatting.GOLD));
    }

    static Component name(boolean effects, String id) {
        var key = KineticResourceIds.tryParse(id);
        if (key == null) return Component.literal(id);
        if (effects) {
            var effect = KineticRegistries.mobEffects().get(key);
            return effect == null ? Component.literal(id) : ItemRuleLabels.translation(effect.getDescriptionId());
        }
        var attribute = KineticRegistries.attributes().get(key);
        return attribute == null ? Component.literal(id) : ItemRuleLabels.translation(attribute.getDescriptionId());
    }

    @Override
    protected void renderRowBackground(KineticGraphics graphics, int index, int x, int y, int width, int height,
                                       boolean hovered, boolean selected) {
        KineticTheme.surface(graphics, x, y + 1, width, height - 2,
                index % 2 == 0 ? KineticTheme.Surface.PANEL_ALT : KineticTheme.Surface.PANEL);
        String id = items().get(index);
        if (selected) KineticTheme.indicatorOutline(graphics, x, y + 1, width, height - 2, KineticTheme.Indicator.WARNING);
        else if (configured.test(id)) KineticTheme.indicatorOutline(graphics, x, y + 1, width, height - 2, KineticTheme.Indicator.SUCCESS);
        else KineticTheme.stateOutline(graphics, x, y + 1, width, height - 2, false, hovered, false);
    }

    @Override
    protected void renderRow(KineticGraphics graphics, String id, int index, int x, int y, int width, int height,
                             boolean hovered, boolean selected) {
        int textX = x + 6;
        if (effects) {
            var effect = KineticRegistries.mobEffects().get(KineticResourceIds.tryParse(id));
            if (effect != null) graphics.effectIcon(effect, x + 3, y + 2, 16);
            textX = x + 23;
        }
        Component value = configured.test(id) ? summary.apply(id) : Component.empty();
        graphics.scrollingTextRight(value, x + width - 6, y + 6, VALUE_WIDTH, KineticTheme.current().text(), false);
        graphics.scrollingText(label(effects, id), textX, y + 6, Math.max(0, x + width - 6 - VALUE_WIDTH - 4 - textX),
                KineticTheme.current().text(), false);
    }
}
