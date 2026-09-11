package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class DirectEntityImmunityEditorScreen extends KineticScreen {
    private static final int PANEL_X = 14;
    private static final int PANEL_Y = 24;
    private static final int PANEL_W = 612;
    private static final int PANEL_H = 332;
    private static final int SEARCH_X = 26;
    private static final int SEARCH_Y = 30;
    private static final int SEARCH_W = 300;
    private static final int SPECIAL_X = 334;
    private static final int SPECIAL_W = 120;
    private static final int GRID_X = 26;
    private static final int GRID_Y = 58;
    private static final int CELL_SIZE = 52;
    private static final int CELL_PITCH = 53;
    private static final int GRID_COLS = 11;
    private static final int GRID_ROWS = 5;
    private static final int GRID_W = GRID_COLS * CELL_PITCH;
    private static final int GRID_H = GRID_ROWS * CELL_PITCH;
    private static final int SCROLL_X = GRID_X + GRID_W + 3;
    private static final int SELECTED_OUTLINE = 0xFF55FF55;
    private static final int HOVER_OUTLINE = 0xFFAAAAAA;
    private static final int NORMAL_OUTLINE = 0xFF555555;

    private final Screen parent;
    private final Set<String> selectedEntries = new LinkedHashSet<>();
    private final List<String> allEntityIds = new ArrayList<>();
    private final List<GridEntry> displayEntries = new ArrayList<>();
    private final Map<String, String> searchData = new HashMap<>();
    private final GridScrollController scroll = new GridScrollController();
    private final EntityPreviewRenderer previewRenderer = new EntityPreviewRenderer(160, 0.72F, 0.9F);

    private EditBox searchBox;
    private Button specialRuleButton;
    private Button saveButton;
    private Button backButton;
    private boolean saving;

    public DirectEntityImmunityEditorScreen(Screen parent, List<String> initialEntries) {
        super(Component.translatable("gui.itemcontrol.item.direct_entity_editor.title"));
        this.parent = parent;
        if (initialEntries != null) {
            for (String raw : initialEntries) {
                if (raw != null && !raw.isBlank()) selectedEntries.add(raw.trim());
            }
        }

        ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .map(ResourceLocation::toString)
                .sorted(String::compareToIgnoreCase)
                .forEach(allEntityIds::add);
        for (String selected : selectedEntries) {
            if (!selected.startsWith("#") && !allEntityIds.contains(selected)) allEntityIds.add(selected);
        }
        allEntityIds.sort(String::compareToIgnoreCase);
        buildSearchData();
        useCanvas(640F, 360F, 6);
        dev.xyat.kineticcore.api.client.screen.GuiSession.setParent(this, parent);
        configureDraft(() -> new LinkedHashSet<>(selectedEntries), this::restoreSelectedEntries);
    }

    private void restoreSelectedEntries(Set<String> snapshot) {
        selectedEntries.clear();
        if (snapshot != null) selectedEntries.addAll(snapshot);
        refreshDisplay(false);
    }

    @Override
    protected void buildUi() {
        searchBox = addRenderableWidget(new EditBox(font, SEARCH_X, SEARCH_Y, SEARCH_W, 20, Component.empty()));
        searchBox.setMaxLength(512);
        searchBox.setResponder(value -> refreshDisplay(true));

        specialRuleButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemcontrol.item.direct_entity_editor.tag.add"),
                        button -> toggleTagFromSearch())
                .bounds(SPECIAL_X, SEARCH_Y, SPECIAL_W, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.itemcontrol.item.direct_entity_editor.tooltip.tag")))
                .build());

        saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemcontrol.item.direct_entity_editor.save"),
                        button -> save())
                .bounds(500, 30, 52, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.itemcontrol.item.direct_entity_editor.tooltip.save")))
                .build());

        backButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemcontrol.item.direct_entity_editor.back"),
                        button -> onClose())
                .bounds(558, 30, 56, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.itemcontrol.item.direct_entity_editor.tooltip.back")))
                .build());

        refreshDisplay(false);
    }

    private void buildSearchData() {
        searchData.clear();
        for (String id : allEntityIds) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            EntityType<?> type = location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
            String name = type == null ? id : type.getDescription().getString();
            String raw = id + " " + name;
            searchData.put(id, (raw + " " + KineticSearch.pinyin(raw)).toLowerCase(Locale.ROOT));
        }
    }

    private void refreshDisplay(boolean resetScroll) {
        if (resetScroll) scroll.reset();
        displayEntries.clear();
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);

        int order = 0;
        for (String selected : selectedEntries) {
            if (!selected.startsWith("#")) continue;
            String search = selected.toLowerCase(Locale.ROOT);
            if (query.isEmpty() || KineticSearch.match(search, query)) {
                displayEntries.add(GridEntry.tag(selected, order++));
            }
        }

        int entityOrder = 100000;
        for (String id : allEntityIds) {
            String data = searchData.getOrDefault(id, id.toLowerCase(Locale.ROOT));
            if (!query.isEmpty() && !KineticSearch.match(data, query)) continue;
            displayEntries.add(GridEntry.entity(id, entityOrder++));
        }

        displayEntries.sort((left, right) -> {
            boolean leftSelected = selectedEntries.contains(left.identifier);
            boolean rightSelected = selectedEntries.contains(right.identifier);
            if (leftSelected != rightSelected) return leftSelected ? -1 : 1;
            return Integer.compare(left.sourceOrder, right.sourceOrder);
        });

        int rows = (int) Math.ceil(displayEntries.size() / (double) GRID_COLS);
        scroll.update(rows, GRID_ROWS);
        refreshSpecialRuleButton();
    }

    private void refreshSpecialRuleButton() {
        if (specialRuleButton == null || searchBox == null) return;
        String value = searchBox.getValue().trim();
        boolean isTag = isValidTagIdentifier(value);
        specialRuleButton.visible = value.startsWith("#");
        specialRuleButton.active = isTag;
        specialRuleButton.setMessage(Component.translatable(selectedEntries.contains(value)
                ? "gui.itemcontrol.item.direct_entity_editor.tag.remove"
                : "gui.itemcontrol.item.direct_entity_editor.tag.add"));
    }

    private static boolean isValidTagIdentifier(String value) {
        if (value == null || !value.startsWith("#") || value.length() < 2) return false;
        return ResourceLocation.tryParse(value.substring(1).trim()) != null;
    }

    private void toggleTagFromSearch() {
        if (searchBox == null) return;
        String value = searchBox.getValue().trim();
        if (!isValidTagIdentifier(value)) return;
        if (!selectedEntries.remove(value)) selectedEntries.add(value);
        refreshDisplay(false);
    }

    private void toggleEntity(String id) {
        if (id == null || id.isBlank()) return;
        if (!selectedEntries.remove(id)) selectedEntries.add(id);
        refreshDisplay(false);
    }

    private void save() {
        if (saving) return;
        saving = true;
        if (saveButton != null) saveButton.active = false;
        ItemNetwork.saveDirectEntitySources(new ArrayList<>(selectedEntries));
    }

    public void applySaveResult(boolean success, List<String> serverEntries) {
        saving = false;
        if (saveButton != null) saveButton.active = true;
        if (!success) return;
        selectedEntries.clear();
        if (serverEntries != null) {
            for (String raw : serverEntries) {
                if (raw != null && !raw.isBlank()) selectedEntries.add(raw.trim());
            }
        }
        refreshDisplay(false);
        commitDraft();
    }

    @Override
    public void onClose() {
        previewRenderer.clear();
        super.onClose();
    }

    @Override
    public void removed() {
        previewRenderer.clear();
        super.removed();
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, canvasWidth, canvasHeight, 0xFF171717, 0xFF0E0E0E);
        GuiTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H, 0xEE1C1C1C, 0xFFAAAAAA);
        GuiTheme.panelAlt(graphics, GRID_X - 3, GRID_Y - 3, GRID_W + 6, GRID_H + 6);
        renderGrid(graphics, mouseX, mouseY);
        GuiTheme.scrollbar(scroll, graphics, mouseX, mouseY, SCROLL_X, GRID_Y, 4, GRID_H, 18);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(font, title, canvasWidth / 2, 9, 0xFFFFFF);
        renderSearchHint(graphics);
        if (specialRuleButton == null || !specialRuleButton.visible) {
            Component count = Component.translatable(
                    "gui.itemcontrol.item.direct_entity_editor.count",
                    selectedEntries.size(),
                    displayEntries.size()
            );
            graphics.drawString(font, count, 334, SEARCH_Y + 6, 0xFFFFFF, false);
        }
        graphics.drawString(font, Component.translatable("gui.itemcontrol.item.direct_entity_editor.hint"), 26, 342, 0xFFFFFF, false);
    }

    private void renderSearchHint(GuiGraphics graphics) {
        if (searchBox == null || searchBox.isFocused() || !searchBox.getValue().isEmpty()) return;
        String hint = Component.translatable("gui.itemcontrol.item.direct_entity_editor.search.hint").getString();
        graphics.drawString(font, font.plainSubstrByWidth(hint, SEARCH_W - 8), SEARCH_X + 4, SEARCH_Y + 6, 0xFFAAAAAA, false);
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int firstRow = scroll.smoothIndexOffset();
        int shift = scroll.visualShift(CELL_PITCH);
        int start = firstRow * GRID_COLS;
        int end = Math.min(displayEntries.size(), start + (GRID_ROWS + 1) * GRID_COLS);
        enableCanvasScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local % GRID_COLS;
            int row = local / GRID_COLS;
            int x = GRID_X + col * CELL_PITCH;
            int y = GRID_Y + row * CELL_PITCH - shift;
            GridEntry entry = displayEntries.get(index);
            boolean selected = selectedEntries.contains(entry.identifier);
            boolean hovered = contains(mouseX, mouseY, x, y, CELL_SIZE, CELL_SIZE);

            EntityPreviewRenderer.drawCheckerboard(graphics, x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2);
            if (entry.tagRule) {
                graphics.drawCenteredString(font, "#", x + CELL_SIZE / 2, y + 19, 0xFFFFFFFF);
            } else {
                boolean rendered = previewRenderer.render(
                        graphics,
                        entry.identifier,
                        "direct-entity:" + entry.identifier,
                        x + 3,
                        y + 3,
                        CELL_SIZE - 6,
                        CELL_SIZE - 6,
                        canvasScale,
                        canvasX,
                        canvasY,
                        hovered
                );
                if (!rendered) {
                    graphics.drawCenteredString(font, "?", x + CELL_SIZE / 2, y + 19, 0xFFFFFFFF);
                }
            }

            graphics.renderOutline(
                    x,
                    y,
                    CELL_SIZE,
                    CELL_SIZE,
                    hovered ? HOVER_OUTLINE : selected ? SELECTED_OUTLINE : NORMAL_OUTLINE
            );
        }
        graphics.disableScissor();
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        int index = gridIndexAt(scaledMouseX, scaledMouseY);
        if (index < 0) return;
        GridEntry entry = displayEntries.get(index);
        List<Component> lines = new ArrayList<>();
        if (entry.tagRule) {
            lines.add(Component.translatable("gui.itemcontrol.item.direct_entity_editor.tag_rule"));
            lines.add(Component.literal(entry.identifier));
        } else {
            ResourceLocation id = ResourceLocation.tryParse(entry.identifier);
            EntityType<?> type = id == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
            lines.add(type == null ? Component.literal(entry.identifier) : type.getDescription());
            lines.add(Component.literal(entry.identifier));
        }
        lines.add(Component.translatable(selectedEntries.contains(entry.identifier)
                ? "gui.itemcontrol.item.direct_entity_editor.tooltip.selected"
                : "gui.itemcontrol.item.direct_entity_editor.tooltip.unselected"));
        GuiOverlay.requestTooltip(lines, mouseX, mouseY);
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && scroll.beginDrag(mouseX, mouseY, SCROLL_X, GRID_Y, 6, GRID_H, 18, 2)) return true;
        if (super.canvasMouseClicked(mouseX, mouseY, button)) return true;

        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0) {
            GridEntry entry = displayEntries.get(index);
            if (button == 0) {
                toggleEntity(entry.identifier);
                return true;
            }
            if (button == 1 && selectedEntries.remove(entry.identifier)) {
                refreshDisplay(false);
                return true;
            }
        }

        if (searchBox != null && !searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setFocused(false);
            if (getFocused() == searchBox) setFocused(null);
        }
        return false;
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        if (scroll.release(button)) return true;
        return super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scroll.drag(mouseY, GRID_Y, GRID_H, 18)) return true;
        return super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (Screen.hasControlDown()) {
            int index = gridIndexAt(mouseX, mouseY);
            if (index >= 0) {
                GridEntry entry = displayEntries.get(index);
                if (!entry.tagRule) {
                    previewRenderer.adjustZoom("direct-entity:" + entry.identifier, delta);
                    return true;
                }
            }
        }
        if (contains(mouseX, mouseY, GRID_X, GRID_Y, GRID_W + 12, GRID_H) && scroll.scroll(delta)) return true;
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    private int gridIndexAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY, GRID_X, GRID_Y, GRID_W, GRID_H)) return -1;
        int shift = scroll.visualShift(CELL_PITCH);
        int col = (int) ((mouseX - GRID_X) / CELL_PITCH);
        int row = (int) ((mouseY - GRID_Y + shift) / CELL_PITCH);
        if (col < 0 || col >= GRID_COLS || row < 0 || row > GRID_ROWS) return -1;
        double localX = mouseX - GRID_X - col * CELL_PITCH;
        double localY = mouseY - GRID_Y + shift - row * CELL_PITCH;
        if (localX >= CELL_SIZE || localY < 0 || localY >= CELL_SIZE) return -1;
        int index = (scroll.smoothIndexOffset() + row) * GRID_COLS + col;
        return index >= 0 && index < displayEntries.size() ? index : -1;
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static final class GridEntry {
        private final String identifier;
        private final boolean tagRule;
        private final int sourceOrder;

        private GridEntry(String identifier, boolean tagRule, int sourceOrder) {
            this.identifier = identifier;
            this.tagRule = tagRule;
            this.sourceOrder = sourceOrder;
        }

        private static GridEntry entity(String id, int sourceOrder) {
            return new GridEntry(id, false, sourceOrder);
        }

        private static GridEntry tag(String id, int sourceOrder) {
            return new GridEntry(id, true, sourceOrder);
        }
    }
}
