package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.itemcontrol.item.config.ItemProtectionConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticControl;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticAutoComplete;
import dev.xyat.kineticcore.api.client.widget.input.KineticAutoComplete.AutoCompleteBox;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DamageTypeEditorScreen extends KineticScreen {
    private static final int PANEL_X = 14;
    private static final int PANEL_Y = 24;
    private static final int PANEL_W = 612;
    private static final int PANEL_H = 296;
    private static final int SEARCH_X = 26;
    private static final int SEARCH_Y = 48;
    private static final int SEARCH_W = 438;
    private static final int LIST_X = 26;
    private static final int LIST_Y = 82;
    private static final int LIST_W = 578;
    private static final int LIST_H = 220;
    private static final int ROW_H = 20;
    private static final int ROW_PITCH = 21;
    private static final int VISIBLE_ROWS = 10;
    private static final int SCROLL_X = 610;

    private final List<String> entries = new ArrayList<>();
    private final GridScrollController listScroll = new GridScrollController();
    private AutoCompleteBox searchBox;
    private StateButton addButton;
    private StateButton saveButton;
    private List<String> pendingSaveSnapshot = List.of();
    private boolean saving;

    public DamageTypeEditorScreen(Screen parent, List<String> initialEntries) {
        super(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.title"));
        if (initialEntries != null) {
            Set<String> unique = new LinkedHashSet<>();
            for (String raw : initialEntries) {
                if (raw == null) continue;
                String value = raw.trim();
                if (!value.isEmpty()) unique.add(value);
            }
            entries.addAll(unique);
        }
        useCanvas(640F, 360F, 6);
        setParentScreen(parent);
        configureDraft(() -> List.copyOf(entries), this::restoreEntries);
    }

    private void restoreEntries(List<String> snapshot) {
        entries.clear();
        if (snapshot != null) entries.addAll(snapshot);
        listScroll.update(entries.size(), VISIBLE_ROWS);
    }

    @Override
    protected void buildUi() {
        listScroll.update(entries.size(), VISIBLE_ROWS);

        searchBox = addAutoCompleteField(
                SEARCH_X,
                SEARCH_Y,
                SEARCH_W,
                Component.empty(),
                KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.search.hint"),
                DamageTypeEditorScreen::damageSuggestions,
                null
        );
        searchBox.setResponder(value -> refreshAddButton());
        searchBox.setSelectionResponder(value -> refreshAddButton());

        addButton = addButton(
                474,
                SEARCH_Y,
                90,
                KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.add"),
                KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.add"),
                this::addCurrent
        );

        saveButton = addButton(
                424,
                328,
                90,
                KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.save"),
                KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.save"),
                this::save
        );

        addButton(
                524,
                328,
                90,
                KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.back"),
                KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.back"),
                this::onClose
        );

        refreshAddButton();
    }

    private void addCurrent() {
        if (searchBox == null) return;
        String value = searchBox.getValue().trim();
        if (!ItemProtectionConfig.areValidResourceEntries(List.of(value))) {
            KineticOverlays.toast(KineticI18n.translatable("msg.itemcontrol.item.damage_type_editor.invalid"));
            return;
        }
        if (entries.contains(value)) {
            KineticOverlays.toast(KineticI18n.translatable("msg.itemcontrol.item.damage_type_editor.duplicate"));
            return;
        }
        entries.add(value);
        listScroll.update(entries.size(), VISIBLE_ROWS);
        listScroll.setOffset(Math.max(0, entries.size() - VISIBLE_ROWS));
        searchBox.setValue("");
        blurControl(searchBox);
        refreshAddButton();
    }

    private void refreshAddButton() {
        if (addButton == null || searchBox == null) return;
        ((KineticControl) addButton).setEnabled(!searchBox.getValue().trim().isEmpty());
    }

    private void save() {
        if (saving) return;
        pendingSaveSnapshot = List.copyOf(entries);
        saving = true;
        if (saveButton != null) ((KineticControl) saveButton).setEnabled(false);
        ItemNetwork.saveDamageSources(pendingSaveSnapshot);
    }

    public void applySaveResult(boolean success, List<String> serverEntries) {
        saving = false;
        if (saveButton != null) ((KineticControl) saveButton).setEnabled(true);
        if (success && entries.equals(pendingSaveSnapshot) && serverEntries != null) {
            entries.clear();
            entries.addAll(serverEntries);
            listScroll.update(entries.size(), VISIBLE_ROWS);
            commitDraft();
        }
        pendingSaveSnapshot = List.of();
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.canvasBackground(graphics, canvasWidth(), canvasHeight());
        GuiTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        GuiTheme.panelAlt(graphics, LIST_X - 5, LIST_Y - 5, LIST_W + 10, LIST_H + 10);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(font, title, canvasWidth() / 2, 9, GuiTheme.current().text());
        renderEntries(graphics, mouseX, mouseY);
        graphics.drawString(
                font,
                KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.hint"),
                26,
                307,
                GuiTheme.current().text(),
                false
        );
    }

    private void renderEntries(GuiGraphics graphics, int mouseX, int mouseY) {
        listScroll.update(entries.size(), VISIBLE_ROWS);
        int start = listScroll.smoothIndexOffset();
        int shift = listScroll.visualShift(ROW_PITCH);
        int end = Math.min(entries.size(), start + VISIBLE_ROWS + 1);
        List<KineticAutoComplete.Suggestion> dictionary = damageSuggestions();

        enableCanvasScissor(graphics, LIST_X, LIST_Y, LIST_X + LIST_W, LIST_Y + LIST_H);
        try {
            for (int index = start; index < end; index++) {
                int row = index - start;
                int x = LIST_X;
                int y = LIST_Y + row * ROW_PITCH - shift;
                boolean hovered = contains(mouseX, mouseY, x, y, LIST_W, ROW_H);
                String value = entries.get(index);
                boolean valid = isValidDamageEntry(value);
                GuiTheme.stateSurface(
                        graphics,
                        x,
                        y,
                        LIST_W,
                        ROW_H,
                        GuiTheme.Surface.PANEL_ALT,
                        false,
                        hovered,
                        !valid
                );
                if (valid) {
                    GuiTheme.indicatorOutline(
                            graphics,
                            x,
                            y,
                            LIST_W,
                            ROW_H,
                            hovered ? GuiTheme.Indicator.INFO : GuiTheme.Indicator.SUCCESS
                    );
                }

                String display = displayName(value, dictionary);
                graphics.drawString(
                        font,
                        font.plainSubstrByWidth(display, LIST_W - 10),
                        x + 5,
                        y + 6,
                        GuiTheme.current().text(),
                        false
                );
            }
        } finally {
            disableCanvasScissor(graphics);
        }

        listScroll.render(graphics, mouseX, mouseY, SCROLL_X, LIST_Y, 4, LIST_H, 18);
    }

    private static boolean isValidDamageEntry(String value) {
        if (value == null || value.isBlank()) return false;
        var level = KineticClientRuntime.currentLevel();
        if (level == null) return false;
        var registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        String raw = value.trim();
        boolean tag = raw.startsWith("#");
        String idText = tag ? raw.substring(1) : raw;
        ResourceLocation id = KineticResourceIds.tryParse(idText);
        if (id == null) return false;
        if (tag) {
            return registry.getTagNames().anyMatch(key -> key.location().equals(id));
        }
        return registry.containsKey(id);
    }

    private static String displayName(String value, List<KineticAutoComplete.Suggestion> dictionary) {
        for (KineticAutoComplete.Suggestion suggestion : dictionary) {
            if (!suggestion.value().equals(value)) continue;
            String translation = suggestion.translation().getString();
            return translation.isBlank() ? value : value + " - " + translation;
        }
        return value;
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        int index = rowIndexAt(scaledMouseX, scaledMouseY);
        if (index < 0) return;
        String value = entries.get(index);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(value));
        if (!isValidDamageEntry(value)) {
            tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.invalid"));
        }
        tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.remove"));
        KineticOverlays.requestTooltip(tooltip, mouseX, mouseY);
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (KineticMouseButtons.isPrimary(button) && listScroll.beginDrag(mouseX, mouseY, SCROLL_X, LIST_Y, 4, LIST_H, 18, 2)) return true;

        int index = rowIndexAt(mouseX, mouseY);
        if (index >= 0 && KineticMouseButtons.isSecondary(button)) {
            entries.remove(index);
            listScroll.update(entries.size(), VISIBLE_ROWS);
            return true;
        }
        return super.canvasMouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (contains(mouseX, mouseY, LIST_X, LIST_Y, LIST_W + 12, LIST_H) && listScroll.scroll(delta)) return true;
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (listScroll.drag(mouseY, LIST_Y, LIST_H, 18)) return true;
        return super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        if (listScroll.release(button)) return true;
        return super.canvasMouseReleased(mouseX, mouseY, button);
    }

    private int rowIndexAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY, LIST_X, LIST_Y, LIST_W, LIST_H)) return -1;
        int shift = listScroll.visualShift(ROW_PITCH);
        int row = (int) ((mouseY - LIST_Y + shift) / ROW_PITCH);
        if (row < 0 || row > VISIBLE_ROWS) return -1;
        double localY = mouseY - LIST_Y + shift - row * ROW_PITCH;
        if (localY < 0 || localY >= ROW_H) return -1;
        int index = listScroll.smoothIndexOffset() + row;
        return index >= 0 && index < entries.size() ? index : -1;
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static List<KineticAutoComplete.Suggestion> damageSuggestions() {
        var level = KineticClientRuntime.currentLevel();
        if (level == null) return List.of();

        List<KineticAutoComplete.Suggestion> result = new ArrayList<>();
        var registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);

        registry.entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().location();
            String msgId = entry.getValue().msgId();
            String name = KineticSearch.resolveTranslation(
                    "damage_type." + msgId.replace(":", "."),
                    "dmg." + msgId,
                    "damage_type." + id.getNamespace() + "." + id.getPath()
            );
            result.add(new KineticAutoComplete.Suggestion(
                    id.toString(),
                    name == null ? Component.empty() : Component.literal(name)
            ));
        });

        registry.getTagNames().forEach(tagKey -> {
            ResourceLocation id = tagKey.location();
            String name = KineticSearch.resolveTranslation(
                    "tag.damage_type." + id.getNamespace() + "." + id.getPath(),
                    "tag." + id.getNamespace() + "." + id.getPath(),
                    "tag." + id.getPath()
            );
            result.add(new KineticAutoComplete.Suggestion(
                    "#" + id,
                    name == null ? Component.empty() : Component.literal(name)
            ));
        });

        result.sort((left, right) -> left.value().toLowerCase(Locale.ROOT).compareTo(right.value().toLowerCase(Locale.ROOT)));
        return List.copyOf(result);
    }
}
