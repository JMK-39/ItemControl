package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class DirectEntityImmunityEditorPage extends KineticPage {
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

    private final Set<String> selectedEntries = new LinkedHashSet<>();
    private final List<String> allEntityIds = new ArrayList<>();
    private final List<GridEntry> displayEntries = new ArrayList<>();
    private final Map<String, String> searchData = new HashMap<>();
    private final KineticScrollController scroll = new KineticScrollController();
    // 原 EntityPreviewRenderer 默认填充比 0.56、最大自动缩放 0.55 / Former EntityPreviewRenderer defaults: fill 0.56, max auto-scale 0.55.
    private final KineticEntityPreview previewRenderer = KineticEntityPreview.create(160, 0.56F, 0.55F);

    private KineticTextField searchBox;
    private KineticButton specialRuleButton;
    private KineticButton saveButton;
    private boolean saving;

    public DirectEntityImmunityEditorPage(List<String> initialEntries) {
        super(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.title"));
        if (initialEntries != null) {
            for (String raw : initialEntries) {
                if (raw != null && !raw.isBlank()) selectedEntries.add(raw.trim());
            }
        }

        KineticRegistries.entityTypes().ids().stream()
                .map(ResourceLocation::toString)
                .sorted(String::compareToIgnoreCase)
                .forEach(allEntityIds::add);
        for (String selected : selectedEntries) {
            if (!selected.startsWith("#") && !allEntityIds.contains(selected)) allEntityIds.add(selected);
        }
        allEntityIds.sort(String::compareToIgnoreCase);
        buildSearchData();
        useCanvas(640, 360, 6);
        configureDraft(() -> new LinkedHashSet<>(selectedEntries), this::restoreSelectedEntries);
    }

    private void restoreSelectedEntries(Set<String> snapshot) {
        selectedEntries.clear();
        if (snapshot != null) selectedEntries.addAll(snapshot);
        refreshDisplay(false);
    }

    @Override
    protected void build(KineticUi ui) {
        searchBox = ui().textField(SEARCH_X, SEARCH_Y, SEARCH_W).build();
        searchBox.setPlaceholder(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.search.hint"));
        searchBox.limitTextLength(512);
        searchBox.onTextChange(value -> refreshDisplay(true));

        specialRuleButton = ui().button(SPECIAL_X, SEARCH_Y, SPECIAL_W).text(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.tag.add")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.tooltip.tag")).onClick(this::toggleTagFromSearch).build();

        saveButton = ui().button(500, 30, 52).text(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.save")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.tooltip.save")).onClick(this::save).build();

        ui().button(558, 30, 56).text(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.back")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.tooltip.back")).onClick(this::close).build();

        refreshDisplay(false);
    }

    private void buildSearchData() {
        searchData.clear();
        for (String id : allEntityIds) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
            String name = type == null ? id : type.getDescription().getString();
            String raw = id + " " + name;
            searchData.put(id, (raw + " " + KineticSearch.pinyin(raw)).toLowerCase(Locale.ROOT));
        }
    }

    private void refreshDisplay(boolean resetScroll) {
        if (resetScroll) scroll.reset();
        displayEntries.clear();
        String query = searchBox == null ? "" : searchBox.textValue().trim().toLowerCase(Locale.ROOT);

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
        String value = searchBox.textValue().trim();
        boolean isTag = isValidTagIdentifier(value);
        specialRuleButton.setControlVisible(value.startsWith("#"));
        specialRuleButton.setEnabled(isTag);
        specialRuleButton.setText(KineticI18n.translatable(selectedEntries.contains(value)
                ? "gui.itemcontrol.item.direct_entity_editor.tag.remove"
                : "gui.itemcontrol.item.direct_entity_editor.tag.add"));
    }

    private static boolean isValidTagIdentifier(String value) {
        if (value == null || !value.startsWith("#") || value.length() < 2) return false;
        return ResourceLocation.tryParse(value.substring(1).trim()) != null;
    }

    private void toggleTagFromSearch() {
        if (searchBox == null) return;
        String value = searchBox.textValue().trim();
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
        if (saveButton != null) saveButton.setEnabled(false);
        ItemNetwork.saveDirectEntitySources(new ArrayList<>(selectedEntries));
    }

    public void applySaveResult(boolean success, List<String> serverEntries) {
        saving = false;
        if (saveButton != null) saveButton.setEnabled(true);
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
    protected void onRemoved() {
        previewRenderer.clear();
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width(), height(), 0xFF171717, 0xFF0E0E0E);
        KineticTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        KineticTheme.panelAlt(graphics, GRID_X - 3, GRID_Y - 3, GRID_W + 6, GRID_H + 6);
        renderGrid(graphics, mouseX, mouseY);
        scroll.render(graphics, mouseX, mouseY, SCROLL_X, GRID_Y, 4, GRID_H, 18);
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(title(), width() / 2, 9, 0xFFFFFF, true);
        if (specialRuleButton == null || !specialRuleButton.controlVisible()) {
            Component count = KineticI18n.translatable(
                    "gui.itemcontrol.item.direct_entity_editor.count",
                    selectedEntries.size(),
                    displayEntries.size()
            );
            graphics.text(count, 334, SEARCH_Y + 6, 0xFFFFFF, false);
        }
        graphics.text(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.hint"), 26, 342, 0xFFFFFF, false);
    }

    private void renderGrid(KineticGraphics graphics, int mouseX, int mouseY) {
        int firstRow = scroll.smoothIndexOffset();
        int shift = scroll.visualShift(CELL_PITCH);
        int start = firstRow * GRID_COLS;
        int end = Math.min(displayEntries.size(), start + (GRID_ROWS + 1) * GRID_COLS);
        graphics.scissor(GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local % GRID_COLS;
            int row = local / GRID_COLS;
            int x = GRID_X + col * CELL_PITCH;
            int y = GRID_Y + row * CELL_PITCH - shift;
            GridEntry entry = displayEntries.get(index);
            boolean selected = selectedEntries.contains(entry.identifier);
            boolean hovered = contains(mouseX, mouseY, x, y, CELL_SIZE, CELL_SIZE);

            KineticEntityPreview.drawCheckerboard(graphics, x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2);
            if (entry.tagRule) {
                graphics.centeredText("#", x + CELL_SIZE / 2, y + 19, 0xFFFFFFFF, true);
            } else {
                boolean rendered = previewRenderer.render(
                        graphics,
                        entry.identifier,
                        "direct-entity:" + entry.identifier,
                        x + 3,
                        y + 3,
                        CELL_SIZE - 6,
                        CELL_SIZE - 6,
                        hovered
                );
                if (!rendered) {
                    graphics.centeredText("?", x + CELL_SIZE / 2, y + 19, 0xFFFFFFFF, true);
                }
            }

            if (hovered) {
                KineticTheme.stateOutline(graphics, x, y, CELL_SIZE, CELL_SIZE, false, true, false);
            } else if (selected) {
                KineticTheme.indicatorOutline(graphics, x, y, CELL_SIZE, CELL_SIZE, KineticTheme.Indicator.SUCCESS);
            } else {
                KineticTheme.indicatorOutline(graphics, x, y, CELL_SIZE, CELL_SIZE, KineticTheme.Indicator.MUTED);
            }
        }
        graphics.endScissor();
    }

    @Override
    protected void renderTooltips(int mouseX, int mouseY) {
        int index = gridIndexAt(mouseX, mouseY);
        if (index < 0) return;
        GridEntry entry = displayEntries.get(index);
        List<Component> lines = new ArrayList<>();
        if (entry.tagRule) {
            lines.add(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.tag_rule"));
            lines.add(Component.literal(entry.identifier));
        } else {
            ResourceLocation id = ResourceLocation.tryParse(entry.identifier);
            EntityType<?> type = id == null ? null : KineticRegistries.entityTypes().get(id);
            lines.add(type == null ? Component.literal(entry.identifier) : type.getDescription());
            lines.add(Component.literal(entry.identifier));
        }
        lines.add(KineticI18n.translatable(selectedEntries.contains(entry.identifier)
                ? "gui.itemcontrol.item.direct_entity_editor.tooltip.selected"
                : "gui.itemcontrol.item.direct_entity_editor.tooltip.unselected"));
        showTooltip(lines);
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 canvasMouseClicked 在调用控件之前开始滚动条拖动 / The old canvasMouseClicked started the scrollbar drag before controls.
        return scroll.beginDrag(input.x(), input.y(), input.button(), SCROLL_X, GRID_Y, 6, GRID_H, 18, 2);
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0) {
            GridEntry entry = displayEntries.get(index);
            if (input.isLeft()) {
                toggleEntity(entry.identifier);
                return true;
            }
            if (input.isRight() && selectedEntries.remove(entry.identifier)) {
                refreshDisplay(false);
                return true;
            }
        }

        if (searchBox != null && !searchBox.contains(mouseX, mouseY)) {
            blur(searchBox);
        }
        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        if (scroll.release(input.button())) return true;
        return false;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double mouseY = input.y();
        if (scroll.drag(mouseY, GRID_Y, GRID_H, 18)) return true;
        return false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        double delta = input.deltaY();
        if (KineticClientRuntime.controlModifierDown()) {
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
        return false;
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
