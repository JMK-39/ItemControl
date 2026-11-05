package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.gui.widget.list.KineticEntityGrid;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
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

// NeoForge 26.1 no longer strips @OnlyIn members and warns about the annotation; this class is only used on the client.
//? if <26.1
@OnlyIn(Dist.CLIENT)
public final class DirectEntityImmunityEditorPage extends KineticPage {
    private String hoveredDeleteId;
    private static final int SEARCH_X = 48;
    private static final int SEARCH_Y = 4;
    private static final int SEARCH_W = 456;
    private static final int SPECIAL_X = 508;
    private static final int SPECIAL_W = 80;
    private static final int SAVE_X = 596;
    private static final int GRID_X = 4;
    private static final int GRID_Y = 28;
    private static final int GRID_W = 632;
    private static final int GRID_H = 328;

    private final Set<String> selectedEntries = new LinkedHashSet<>();
    private final List<String> allEntityIds = new ArrayList<>();
    private final List<GridEntry> displayEntries = new ArrayList<>();
    private final Map<String, String> searchData = new HashMap<>();
    private KineticEntityGrid entityGrid;

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
        useCanvas(640, 360, 4);
        configureDraft(() -> new LinkedHashSet<>(selectedEntries), this::restoreSelectedEntries);
    }

    private void restoreSelectedEntries(Set<String> snapshot) {
        selectedEntries.clear();
        if (snapshot != null) selectedEntries.addAll(snapshot);
        refreshDisplay(false);
    }

    @Override
    protected void build(KineticUi ui) {
        bindDeleteAction(() -> hoveredDeleteId != null && selectedEntries.contains(hoveredDeleteId), () -> removeEntity(hoveredDeleteId));
        searchBox = ui().textField(SEARCH_X, SEARCH_Y, SEARCH_W).build();
        searchBox.setPlaceholder(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.search.hint"));
        searchBox.limitTextLength(512);
        searchBox.onTextChange(value -> refreshDisplay(true));

        specialRuleButton = ui().button(SPECIAL_X, SEARCH_Y, SPECIAL_W).text(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.tag.add")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.tooltip.tag")).onClick(this::toggleTagFromSearch).build();

        saveButton = ui().button(SAVE_X, SEARCH_Y, 40).text(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.save")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.tooltip.save")).onClick(this::save).build();

        ui().button(4, SEARCH_Y, 40).text(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.back")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.direct_entity_editor.tooltip.back")).onClick(this::close).build();

        entityGrid = ui.add(new KineticEntityGrid(GRID_X, GRID_Y, GRID_W, GRID_H));
        entityGrid.onClick((index, input) -> {
            String id = entityGrid.items().get(index).entityId();
            if (input.isLeft()) toggleEntity(id);
            else if (input.isRight()) removeEntity(id);
        });
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
        if (resetScroll && entityGrid != null) entityGrid.setScrollOffset(0);
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

        if (entityGrid != null) entityGrid.setItems(displayEntries.stream().map(entry -> {
            ResourceLocation id = ResourceLocation.tryParse(entry.identifier);
            EntityType<?> type = entry.tagRule || id == null ? null : KineticRegistries.entityTypes().get(id);
            Component tooltip = (type == null ? Component.literal(entry.identifier) : type.getDescription().copy()
                    .append("\n").append(entry.identifier)).copy().append("\n")
                    .append(KineticI18n.translatable(selectedEntries.contains(entry.identifier)
                            ? "gui.itemcontrol.item.direct_entity_editor.tooltip.selected"
                            : "gui.itemcontrol.item.direct_entity_editor.tooltip.unselected"));
            return new KineticEntityGrid.Entry(entry.identifier, tooltip, selectedEntries.contains(entry.identifier));
        }).toList());
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

    private void removeEntity(String id) { if (selectedEntries.remove(id)) refreshDisplay(false); }

    @Override protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int index = entityGrid == null ? -1 : entityGrid.itemAt(mouseX, mouseY);
        hoveredDeleteId = index < 0 ? null : entityGrid.items().get(index).entityId();
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
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 0, 0, width(), height());
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
