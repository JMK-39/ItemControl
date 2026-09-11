package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.AutoCompleteBox;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.AutoCompleteBoxGroup;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.itemcontrol.item.config.ItemProtectionConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
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

    private final Screen parent;
    private final List<String> entries = new ArrayList<>();
    private final AutoCompleteBoxGroup inputGroup = new AutoCompleteBoxGroup();
    private final GridScrollController listScroll = new GridScrollController();
    private AutoCompleteBox searchBox;
    private Button addButton;
    private Button saveButton;
    private List<String> pendingSaveSnapshot = List.of();
    private boolean saving;

    public DamageTypeEditorScreen(Screen parent, List<String> initialEntries) {
        super(Component.translatable("gui.itemcontrol.item.damage_type_editor.title"));
        this.parent = parent;
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
        dev.xyat.kineticcore.api.client.screen.GuiSession.setParent(this, parent);
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

        searchBox = new AutoCompleteBox(
                font,
                SEARCH_X,
                SEARCH_Y,
                SEARCH_W,
                20,
                Component.empty(),
                DamageTypeEditorScreen::damageDictionary
        );
        searchBox.setResponder(value -> refreshAddButton());
        searchBox.setSelectionResponder(value -> refreshAddButton());
        addRenderableWidget(searchBox);
        inputGroup.set(searchBox);

        addButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemcontrol.item.damage_type_editor.add"),
                        button -> addCurrent())
                .bounds(474, SEARCH_Y, 90, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.add")))
                .build());

        saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemcontrol.item.damage_type_editor.save"),
                        button -> save())
                .bounds(424, 328, 90, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.save")))
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemcontrol.item.damage_type_editor.back"),
                        button -> onClose())
                .bounds(524, 328, 90, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.back")))
                .build());

        refreshAddButton();
    }

    private void addCurrent() {
        if (searchBox == null) return;
        String value = AutoCompleteBox.normalizeValue(searchBox.getValue()).trim();
        if (!ItemProtectionConfig.areValidResourceEntries(List.of(value))) {
            GuiOverlay.toast(Component.translatable("msg.itemcontrol.item.damage_type_editor.invalid"));
            return;
        }
        if (entries.contains(value)) {
            GuiOverlay.toast(Component.translatable("msg.itemcontrol.item.damage_type_editor.duplicate"));
            return;
        }
        entries.add(value);
        listScroll.update(entries.size(), VISIBLE_ROWS);
        listScroll.setOffset(Math.max(0, entries.size() - VISIBLE_ROWS));
        searchBox.setValue("");
        searchBox.setFocused(false);
        refreshAddButton();
    }

    private void refreshAddButton() {
        if (addButton == null || searchBox == null) return;
        String value = AutoCompleteBox.normalizeValue(searchBox.getValue()).trim();
        addButton.active = !value.isEmpty();
    }

    private void save() {
        if (saving) return;
        pendingSaveSnapshot = List.copyOf(entries);
        saving = true;
        if (saveButton != null) saveButton.active = false;
        ItemNetwork.saveDamageSources(pendingSaveSnapshot);
    }

    public void applySaveResult(boolean success, List<String> serverEntries) {
        saving = false;
        if (saveButton != null) saveButton.active = true;
        if (success && entries.equals(pendingSaveSnapshot) && serverEntries != null) {
            entries.clear();
            entries.addAll(serverEntries);
            listScroll.update(entries.size(), VISIBLE_ROWS);
            commitDraft();
        }
        pendingSaveSnapshot = List.of();
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, canvasWidth, canvasHeight, 0xFF171717, 0xFF0E0E0E);
        GuiTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H, 0xEE1C1C1C, 0xFFAAAAAA);
        GuiTheme.panel(graphics, LIST_X - 5, LIST_Y - 5, LIST_W + 10, LIST_H + 10, 0xEE101010, 0xFF777777);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(font, title, canvasWidth / 2, 9, 0xFFFFFF);
        if (searchBox != null && !searchBox.isFocused() && searchBox.getValue().isEmpty()) {
            String hint = Component.translatable("gui.itemcontrol.item.damage_type_editor.search.hint").getString();
            graphics.drawString(font, font.plainSubstrByWidth(hint, SEARCH_W - 8), SEARCH_X + 4, SEARCH_Y + 6, 0x999999, false);
        }
        renderEntries(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("gui.itemcontrol.item.damage_type_editor.hint"), 26, 307, 0xFFFFFF, false);
        inputGroup.renderSuggestions(graphics, mouseX, mouseY);
    }

    private void renderEntries(GuiGraphics graphics, int mouseX, int mouseY) {
        listScroll.update(entries.size(), VISIBLE_ROWS);
        int start = listScroll.smoothIndexOffset();
        int shift = listScroll.visualShift(ROW_PITCH);
        int end = Math.min(entries.size(), start + VISIBLE_ROWS + 1);
        List<String> dictionary = damageDictionary();

        enableCanvasScissor(graphics, LIST_X, LIST_Y, LIST_X + LIST_W, LIST_Y + LIST_H);
        for (int index = start; index < end; index++) {
            int row = index - start;
            int x = LIST_X;
            int y = LIST_Y + row * ROW_PITCH - shift;
            boolean hovered = contains(mouseX, mouseY, x, y, LIST_W, ROW_H);
            graphics.fill(x, y, x + LIST_W, y + ROW_H, hovered ? 0xFF2A2A2A : 0xFF161616);
            graphics.renderOutline(x, y, LIST_W, ROW_H, hovered ? 0xFFAAAAAA : 0xFF555555);

            String value = entries.get(index);
            String display = displayName(value, dictionary);
            graphics.drawString(font, font.plainSubstrByWidth(display, LIST_W - 10), x + 5, y + 6, 0xFFFFFF, false);
        }
        graphics.disableScissor();

        listScroll.render(
                graphics,
                mouseX,
                mouseY,
                SCROLL_X,
                LIST_Y,
                4,
                LIST_H,
                18,
                GuiTheme.current().scrollTrack(),
                GuiTheme.current().scrollThumb(),
                GuiTheme.current().scrollThumbHover()
        );
    }

    private static String displayName(String value, List<String> dictionary) {
        String name = KineticSearch.dictionaryName(value, dictionary);
        return name.equals(value) ? value : value + " - " + name;
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        int index = rowIndexAt(scaledMouseX, scaledMouseY);
        if (index < 0) return;
        String value = entries.get(index);
        GuiOverlay.requestTooltip(List.of(
                        Component.literal(value),
                        Component.translatable("gui.itemcontrol.item.damage_type_editor.tooltip.remove")
                ), mouseX, mouseY);
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (inputGroup.handleSuggestionClick(mouseX, mouseY)) return true;
        if (button == 0 && listScroll.beginDrag(mouseX, mouseY, SCROLL_X, LIST_Y, 4, LIST_H, 18, 2)) return true;

        int index = rowIndexAt(mouseX, mouseY);
        if (index >= 0 && button == 1) {
            entries.remove(index);
            listScroll.update(entries.size(), VISIBLE_ROWS);
            return true;
        }

        boolean handled = super.canvasMouseClicked(mouseX, mouseY, button);
        inputGroup.clearFocusOutside(mouseX, mouseY);
        return handled;
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (inputGroup.handleMouseScrolled(delta)) return true;
        if (contains(mouseX, mouseY, LIST_X, LIST_Y, LIST_W + 12, LIST_H) && listScroll.scroll(delta)) return true;
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (inputGroup.handleMouseDragged(mouseX, mouseY)) return true;
        if (listScroll.drag(mouseY, LIST_Y, LIST_H, 18)) return true;
        return super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        if (inputGroup.handleMouseReleased(button)) return true;
        if (listScroll.release(button)) return true;
        return super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return inputGroup.handleKeyPressed(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
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

    private static List<String> damageDictionary() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();

        List<String> result = new ArrayList<>();
        var registry = minecraft.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);

        registry.entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().location();
            String msgId = entry.getValue().msgId();
            String name = KineticSearch.cleanTranslatedName(
                    "damage_type." + msgId.replace(":", "."),
                    "dmg." + msgId,
                    "damage_type." + id.getNamespace() + "." + id.getPath()
            );
            result.add(name == null ? id.toString() : id + " - " + name);
        });

        registry.getTagNames().forEach(tagKey -> {
            ResourceLocation id = tagKey.location();
            String name = KineticSearch.cleanTranslatedName(
                    "tag.damage_type." + id.getNamespace() + "." + id.getPath(),
                    "tag." + id.getNamespace() + "." + id.getPath(),
                    "tag." + id.getPath()
            );
            String raw = "#" + id;
            result.add(name == null ? raw : raw + " - " + name);
        });

        result.sort((left, right) -> {
            String leftId = AutoCompleteBox.normalizeValue(left).toLowerCase(Locale.ROOT);
            String rightId = AutoCompleteBox.normalizeValue(right).toLowerCase(Locale.ROOT);
            return leftId.compareTo(rightId);
        });
        return result;
    }
}
