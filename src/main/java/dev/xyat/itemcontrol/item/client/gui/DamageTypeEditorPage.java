package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.search.KineticSuggestion;

import dev.xyat.itemcontrol.item.config.ItemProtectionConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DamageTypeEditorPage extends KineticPage {
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
    private final KineticScrollController listScroll = new KineticScrollController();
    private KineticAutoCompleteField searchBox;
    private KineticButton addButton;
    private KineticButton saveButton;
    private List<String> pendingSaveSnapshot = List.of();
    private boolean saving;

    public DamageTypeEditorPage(List<String> initialEntries) {
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
        useCanvas(640, 360, 6);
        configureDraft(() -> List.copyOf(entries), this::restoreEntries);
    }

    private void restoreEntries(List<String> snapshot) {
        entries.clear();
        if (snapshot != null) entries.addAll(snapshot);
        listScroll.update(entries.size(), VISIBLE_ROWS);
    }

    @Override
    protected void build(KineticUi ui) {
        listScroll.update(entries.size(), VISIBLE_ROWS);

        searchBox = ui().autoComplete(SEARCH_X, SEARCH_Y, SEARCH_W, DamageTypeEditorPage::damageSuggestions).placeholder(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.search.hint")).build();
        searchBox.onTextChange(value -> refreshAddButton());
        searchBox.setSelectionResponder(value -> refreshAddButton());

        addButton = ui().button(474, SEARCH_Y, 90).text(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.add")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.add")).onClick(this::addCurrent).build();

        saveButton = ui().button(424, 328, 90).text(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.save")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.save")).onClick(this::save).build();

        ui().button(524, 328, 90).text(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.back")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.back")).onClick(this::close).build();

        refreshAddButton();
    }

    private void addCurrent() {
        if (searchBox == null) return;
        String value = searchBox.textValue().trim();
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
        searchBox.setTextValue("");
        blur(searchBox);
        refreshAddButton();
    }

    private void refreshAddButton() {
        if (addButton == null || searchBox == null) return;
        addButton.setEnabled(!searchBox.textValue().trim().isEmpty());
    }

    private void save() {
        if (saving) return;
        pendingSaveSnapshot = List.copyOf(entries);
        saving = true;
        if (saveButton != null) saveButton.setEnabled(false);
        ItemNetwork.saveDamageSources(pendingSaveSnapshot);
    }

    public void applySaveResult(boolean success, List<String> serverEntries) {
        saving = false;
        if (saveButton != null) saveButton.setEnabled(true);
        if (success && entries.equals(pendingSaveSnapshot) && serverEntries != null) {
            entries.clear();
            entries.addAll(serverEntries);
            listScroll.update(entries.size(), VISIBLE_ROWS);
            commitDraft();
        }
        pendingSaveSnapshot = List.of();
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.canvasBackground(graphics, width(), height());
        KineticTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        KineticTheme.panelAlt(graphics, LIST_X - 5, LIST_Y - 5, LIST_W + 10, LIST_H + 10);
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.scrollingTextCentered(title(), width() / 2, 9, PANEL_W - 4, KineticTheme.current().text(), true);
        renderEntries(graphics, mouseX, mouseY);
        graphics.scrollingText(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.hint"), LIST_X, 307, LIST_W - 4, KineticTheme.current().text(), false);
    }

    private void renderEntries(KineticGraphics graphics, int mouseX, int mouseY) {
        listScroll.update(entries.size(), VISIBLE_ROWS);
        int start = listScroll.smoothIndexOffset();
        int shift = listScroll.visualShift(ROW_PITCH);
        int end = Math.min(entries.size(), start + VISIBLE_ROWS + 1);
        List<KineticSuggestion> dictionary = damageSuggestions();

        graphics.scissor(LIST_X, LIST_Y, LIST_X + LIST_W, LIST_Y + LIST_H);
        try {
            for (int index = start; index < end; index++) {
                int row = index - start;
                int x = LIST_X;
                int y = LIST_Y + row * ROW_PITCH - shift;
                boolean hovered = contains(mouseX, mouseY, x, y, LIST_W, ROW_H);
                String value = entries.get(index);
                boolean valid = isValidDamageEntry(value);
                KineticTheme.stateSurface(
                        graphics,
                        x,
                        y,
                        LIST_W,
                        ROW_H,
                        KineticTheme.Surface.PANEL_ALT,
                        false,
                        hovered,
                        !valid
                );
                if (valid) {
                    KineticTheme.indicatorOutline(
                            graphics,
                            x,
                            y,
                            LIST_W,
                            ROW_H,
                            hovered ? KineticTheme.Indicator.INFO : KineticTheme.Indicator.SUCCESS
                    );
                }

                String display = displayName(value, dictionary);
                graphics.scrollingText(Component.literal(display), x + 5, y + 6, LIST_W - 10, KineticTheme.current().text(), false);
            }
        } finally {
            graphics.endScissor();
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

    private static String displayName(String value, List<KineticSuggestion> dictionary) {
        for (KineticSuggestion suggestion : dictionary) {
            if (!suggestion.value().equals(value)) continue;
            String translation = suggestion.translation().getString();
            return translation.isBlank() ? value : value + " - " + translation;
        }
        return value;
    }

    @Override
    protected void renderTooltips(int mouseX, int mouseY) {
        int index = rowIndexAt(mouseX, mouseY);
        if (index < 0) return;
        String value = entries.get(index);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(value));
        if (!isValidDamageEntry(value)) {
            tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.invalid"));
        }
        tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.remove"));
        showTooltip(tooltip);
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 canvasMouseClicked 在控件之前处理 / The old canvasMouseClicked handled this before controls.
        double mouseX = input.x();
        double mouseY = input.y();
        if (listScroll.beginDrag(mouseX, mouseY, input.button(), SCROLL_X, LIST_Y, 4, LIST_H, 18, 2)) return true;

        int index = rowIndexAt(mouseX, mouseY);
        if (index >= 0 && input.isRight()) {
            entries.remove(index);
            listScroll.update(entries.size(), VISIBLE_ROWS);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        double delta = input.deltaY();
        if (contains(mouseX, mouseY, LIST_X, LIST_Y, LIST_W + 12, LIST_H) && listScroll.scroll(delta)) return true;
        return false;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double mouseY = input.y();
        if (listScroll.drag(mouseY, LIST_Y, LIST_H, 18)) return true;
        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        if (listScroll.release(input.button())) return true;
        return false;
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

    private static List<KineticSuggestion> damageSuggestions() {
        var level = KineticClientRuntime.currentLevel();
        if (level == null) return List.of();

        List<KineticSuggestion> result = new ArrayList<>();
        var registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);

        registry.entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().location();
            String msgId = entry.getValue().msgId();
            String name = KineticSearch.resolveTranslation(
                    "damage_type." + msgId.replace(":", "."),
                    "dmg." + msgId,
                    "damage_type." + id.getNamespace() + "." + id.getPath()
            );
            result.add(new KineticSuggestion(
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
            result.add(new KineticSuggestion(
                    "#" + id,
                    name == null ? Component.empty() : Component.literal(name)
            ));
        });

        result.sort((left, right) -> left.value().toLowerCase(Locale.ROOT).compareTo(right.value().toLowerCase(Locale.ROOT)));
        return List.copyOf(result);
    }
}
