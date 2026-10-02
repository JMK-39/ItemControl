package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.state.EditedEntryTracker;
import dev.xyat.kineticcore.api.client.gui.state.LayerState;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ProtectionItemEditorPage extends KineticPage {
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
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_PITCH = 19;
    private static final int GRID_COLS = 30;
    private static final int GRID_ROWS = 15;
    private static final int GRID_W = GRID_COLS * SLOT_PITCH;
    private static final int GRID_H = GRID_ROWS * SLOT_PITCH;
    private static final int SCROLL_X = GRID_X + GRID_W + 5;
    private static final float ITEM_SCALE = 1.0F;

    private static final int MODAL_X = 190;
    private static final int MODAL_Y = 99;
    private static final int MODAL_W = 260;
    private static final int MODAL_H = 164;

    private enum Layer {
        RULE_EDITOR
    }

    private final List<RuleDraft> rules = new ArrayList<>();
    private final List<GridEntry> displayEntries = new ArrayList<>();
    private final KineticScrollController gridScroll = new KineticScrollController();
    private final EditedEntryTracker<RuleDraft> editedTracker = new EditedEntryTracker<>();
    private final LayerState<Layer> layerManager = new LayerState<>();

    private List<KineticItemSearch.CachedItem> allItems = List.of();
    private KineticTextField searchBox;
    private KineticButton specialRuleButton;
    private KineticButton saveButton;
    private KineticButton backButton;

    private KineticButton modalFireButton;
    private KineticButton modalExplosionButton;
    private KineticButton modalGlowingButton;
    private KineticButton modalGravityButton;
    private KineticButton modalApplyButton;
    private KineticButton modalDeleteButton;
    private KineticButton modalCancelButton;

    private RuleDraft modalExistingRule;
    private String modalIdentifier = "";
    private ItemStack modalPreview = ItemStack.EMPTY;
    private boolean modalFireImmune;
    private boolean modalExplosionImmune;
    private boolean modalGlowing;
    private boolean modalNoGravity;
    private boolean saving;

    public ProtectionItemEditorPage(List<String> initialRules) {
        super(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.title"));
        if (initialRules != null) {
            for (String raw : initialRules) {
                RuleDraft rule = RuleDraft.parse(raw);
                if (rule != null) rules.add(rule);
            }
        }
        editedTracker.refresh(rules, RuleDraft::isEdited);
        useCanvas(640, 360, 6);
        configureDraft(this::serializeRules, this::restoreSerializedRules);
    }

    private void restoreSerializedRules(List<String> snapshot) {
        rules.clear();
        if (snapshot != null) {
            for (String raw : snapshot) {
                RuleDraft parsed = RuleDraft.parse(raw);
                if (parsed != null) rules.add(parsed);
            }
        }
        editedTracker.refresh(rules, RuleDraft::isEdited);
        closeRuleEditor();
        refreshDisplay(false);
    }

    @Override
    protected void build(KineticUi ui) {
        searchBox = ui().textField(SEARCH_X, SEARCH_Y, SEARCH_W).build();
        searchBox.setPlaceholder(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.search.hint"));
        searchBox.limitTextLength(1024);
        searchBox.onTextChange(value -> refreshDisplay(true));

        specialRuleButton = ui().button(SPECIAL_X, SEARCH_Y, SPECIAL_W).text(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.special_rule")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.special_rule")).onClick(this::openSpecialRuleFromSearch).build();

        saveButton = ui().button(500, 30, 52).text(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.save")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.save")).onClick(this::save).build();

        backButton = ui().button(558, 30, 56).text(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.back")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.back")).onClick(this::close).build();

        modalFireButton = addModalButton(204, 158, 108, () -> {
            modalFireImmune = !modalFireImmune;
            refreshModalButtons();
        }, "gui.itemcontrol.item.protection_editor.tooltip.fire");
        modalExplosionButton = addModalButton(328, 158, 108, () -> {
            modalExplosionImmune = !modalExplosionImmune;
            refreshModalButtons();
        }, "gui.itemcontrol.item.protection_editor.tooltip.explosion");
        modalGlowingButton = addModalButton(204, 186, 108, () -> {
            modalGlowing = !modalGlowing;
            refreshModalButtons();
        }, "gui.itemcontrol.item.protection_editor.tooltip.glowing");
        modalGravityButton = addModalButton(328, 186, 108, () -> {
            modalNoGravity = !modalNoGravity;
            refreshModalButtons();
        }, "gui.itemcontrol.item.protection_editor.tooltip.gravity");

        modalApplyButton = ui().button(204, 222, 72).text(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.apply")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.apply")).layer(1).onClick(this::applyModalRule).build();
        modalDeleteButton = ui().button(284, 222, 72).text(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.delete")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.delete")).layer(1).onClick(this::deleteModalRule).build();
        modalCancelButton = ui().button(364, 222, 72).text(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.cancel")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.cancel")).layer(1).onClick(this::closeRuleEditor).build();

        setModalWidgetsVisible(false);
        refreshSpecialRuleButton();

        // Use the same resilient ItemControl search snapshot as the other item editors.
        // It provides a registry fallback immediately, then rebuilds from Kinetic's shared
        // index once that index becomes ready. The screen therefore never has to hide its
        // entire content area behind KineticItemSearch.ready().
        allItems = ItemSearchCache.getAllItems();
        refreshDisplay(false);
        ItemSearchCache.prepareCache(() -> {
            if (!isOpen()) return;
            allItems = ItemSearchCache.getAllItems();
            refreshDisplay(false);
        });
    }

    private KineticButton addModalButton(int x, int y, int width, Runnable action, String tooltipKey) {
        return ui().button(x, y, width).text(Component.empty()).tooltip(KineticI18n.translatable(tooltipKey)).layer(1).onClick(action).build();
    }

    private void refreshDisplay(boolean resetScroll) {
        if (resetScroll) gridScroll.reset();
        displayEntries.clear();

        Map<String, RuleDraft> exactRules = new LinkedHashMap<>();
        Map<String, Boolean> cachedIdentifiers = new HashMap<>();
        for (KineticItemSearch.CachedItem cached : allItems) {
            if (cached == null || cached.stack() == null || cached.stack().isEmpty()) continue;
            cachedIdentifiers.put(identifierForCachedItem(cached), Boolean.TRUE);
            cachedIdentifiers.put(cached.id(), Boolean.TRUE);
        }

        List<GridEntry> specialEntries = new ArrayList<>();
        int specialOrder = 0;
        for (RuleDraft rule : rules) {
            if (!rule.isGroupRule() && cachedIdentifiers.containsKey(rule.identifier)) {
                exactRules.putIfAbsent(rule.identifier, rule);
            } else {
                specialEntries.add(specialEntry(rule, specialOrder++));
            }
        }

        String query = searchBox == null ? "" : searchBox.textValue().trim();
        specialEntries.removeIf(entry -> !entry.matches(query));
        specialEntries.sort((left, right) -> compareEntries(left, right));
        displayEntries.addAll(specialEntries);

        List<GridEntry> itemEntries = new ArrayList<>();
        int itemOrder = 100000;
        for (KineticItemSearch.CachedItem cached : allItems) {
            if (cached == null || cached.stack() == null || cached.stack().isEmpty()) continue;
            if (!matchesItemSearch(cached, query)) continue;
            String itemIdentifier = identifierForCachedItem(cached);
            RuleDraft rule = exactRules.get(itemIdentifier);
            if (rule == null) rule = exactRules.get(cached.id());
            itemEntries.add(itemEntry(cached, rule, itemIdentifier, itemOrder++));
        }
        itemEntries.sort(this::compareEntries);
        displayEntries.addAll(itemEntries);

        int rows = (int) Math.ceil(displayEntries.size() / (double) GRID_COLS);
        gridScroll.update(rows, GRID_ROWS);
        refreshSpecialRuleButton();
    }

    private int compareEntries(GridEntry left, GridEntry right) {
        RuleDraft leftRule = left.rule;
        RuleDraft rightRule = right.rule;
        boolean leftHasRule = leftRule != null;
        boolean rightHasRule = rightRule != null;
        if (leftHasRule != rightHasRule) return leftHasRule ? -1 : 1;
        if (leftHasRule) {
            boolean leftEdited = editedTracker.isEdited(leftRule);
            boolean rightEdited = editedTracker.isEdited(rightRule);
            if (leftEdited != rightEdited) return leftEdited ? -1 : 1;
        }
        return Integer.compare(left.sourceOrder, right.sourceOrder);
    }

    private static boolean matchesItemSearch(KineticItemSearch.CachedItem item, String query) {
        if (query == null || query.isBlank()) return true;
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("@") && normalized.length() > 1) {
            return item.namespace().contains(normalized.substring(1));
        }
        if (normalized.startsWith("#") && normalized.length() > 1) {
            String tagQuery = normalized.substring(1);
            return item.tagIds().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(tagQuery));
        }
        return KineticSearch.match(item.searchText(), normalized);
    }

    private void refreshSpecialRuleButton() {
        if (specialRuleButton == null || searchBox == null) return;
        String value = searchBox.textValue().trim();
        boolean special = value.startsWith("#") || value.startsWith("@");
        RuleDraft existing = special ? findRule(value) : null;
        specialRuleButton.setControlVisible(special);
        specialRuleButton.setEnabled(special && (existing != null || isValidSpecialIdentifier(value)));
        specialRuleButton.setText(KineticI18n.translatable(existing == null
                ? "gui.itemcontrol.item.protection_editor.special_rule.add"
                : "gui.itemcontrol.item.protection_editor.special_rule.edit"));
    }

    private boolean isValidSpecialIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 2) return false;
        if (identifier.startsWith("@")) {
            String namespace = identifier.substring(1).trim().toLowerCase(Locale.ROOT);
            if (namespace.isEmpty()) return false;
            for (KineticItemSearch.CachedItem item : allItems) {
                if (namespace.equals(item.namespace().toLowerCase(Locale.ROOT))) return true;
            }
            return false;
        }
        if (identifier.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(identifier.substring(1).trim());
            if (tagId == null) return false;
            String expected = tagId.toString();
            for (KineticItemSearch.CachedItem item : allItems) {
                if (item.tagIds().contains(expected)) return true;
            }
        }
        return false;
    }

    private void openSpecialRuleFromSearch() {
        if (searchBox == null) return;
        String identifier = searchBox.textValue().trim();
        RuleDraft existing = findRule(identifier);
        if (existing == null && !isValidSpecialIdentifier(identifier)) {
            KineticOverlays.toast(KineticI18n.translatable("msg.itemcontrol.item.protection_editor.invalid_selection"));
            return;
        }
        ItemStack preview = existing == null ? RuleDraft.createPreview(identifier) : existing.preview();
        openRuleEditor(identifier, preview, existing);
    }

    private void openRuleEditor(String identifier, ItemStack preview, RuleDraft existing) {
        if (identifier == null || identifier.isBlank()) return;
        modalIdentifier = identifier;
        modalPreview = preview == null ? ItemStack.EMPTY : preview.copy();
        modalExistingRule = existing;
        if (existing == null) {
            modalFireImmune = true;
            modalExplosionImmune = true;
            modalGlowing = false;
            modalNoGravity = false;
        } else {
            modalFireImmune = existing.fireImmune;
            modalExplosionImmune = existing.explosionImmune;
            modalGlowing = existing.glowing;
            modalNoGravity = existing.noGravity;
        }
        layerManager.open(Layer.RULE_EDITOR);
        setMainWidgetsVisible(false);
        setModalWidgetsVisible(true);
        refreshModalButtons();
    }

    private void closeRuleEditor() {
        layerManager.closeAll();
        modalIdentifier = "";
        modalPreview = ItemStack.EMPTY;
        modalExistingRule = null;
        setModalWidgetsVisible(false);
        setMainWidgetsVisible(true);
        refreshSpecialRuleButton();
    }

    private void applyModalRule() {
        if (!layerManager.isOpen(Layer.RULE_EDITOR) || modalIdentifier.isBlank()) return;
        RuleDraft target = modalExistingRule;
        if (target == null) {
            target = RuleDraft.createNew(
                    modalIdentifier,
                    modalFireImmune,
                    modalExplosionImmune,
                    modalGlowing,
                    modalNoGravity
            );
            rules.add(target);
        } else {
            target.fireImmune = modalFireImmune;
            target.explosionImmune = modalExplosionImmune;
            target.glowing = modalGlowing;
            target.noGravity = modalNoGravity;
        }
        editedTracker.update(target, target.isEdited());
        closeRuleEditor();
        refreshDisplay(false);
    }

    private void deleteModalRule() {
        if (modalExistingRule == null) return;
        RuleDraft removed = modalExistingRule;
        rules.remove(removed);
        editedTracker.update(removed, false);
        closeRuleEditor();
        refreshDisplay(false);
    }

    private void removeRule(RuleDraft rule) {
        if (rule == null) return;
        rules.remove(rule);
        editedTracker.update(rule, false);
        refreshDisplay(false);
    }

    private void refreshModalButtons() {
        if (modalFireButton == null) return;
        modalFireButton.setText(KineticI18n.translatable(modalFireImmune
                ? "gui.itemcontrol.item.protection_editor.fire.on"
                : "gui.itemcontrol.item.protection_editor.fire.off"));
        modalExplosionButton.setText(KineticI18n.translatable(modalExplosionImmune
                ? "gui.itemcontrol.item.protection_editor.explosion.on"
                : "gui.itemcontrol.item.protection_editor.explosion.off"));
        modalGlowingButton.setText(KineticI18n.translatable(modalGlowing
                ? "gui.itemcontrol.item.protection_editor.glowing.on"
                : "gui.itemcontrol.item.protection_editor.glowing.off"));
        modalGravityButton.setText(KineticI18n.translatable(modalNoGravity
                ? "gui.itemcontrol.item.protection_editor.gravity.off"
                : "gui.itemcontrol.item.protection_editor.gravity.on"));
        if (modalDeleteButton != null) modalDeleteButton.setControlVisible(modalExistingRule != null);
    }

    private void setMainWidgetsVisible(boolean visible) {
        if (searchBox != null) searchBox.setControlVisible(visible);
        if (specialRuleButton != null) specialRuleButton.setControlVisible(visible && isSpecialSearch());
        if (saveButton != null) saveButton.setControlVisible(visible);
        if (backButton != null) backButton.setControlVisible(visible);
    }

    private void setModalWidgetsVisible(boolean visible) {
        if (modalFireButton != null) modalFireButton.setControlVisible(visible);
        if (modalExplosionButton != null) modalExplosionButton.setControlVisible(visible);
        if (modalGlowingButton != null) modalGlowingButton.setControlVisible(visible);
        if (modalGravityButton != null) modalGravityButton.setControlVisible(visible);
        if (modalApplyButton != null) modalApplyButton.setControlVisible(visible);
        if (modalDeleteButton != null) modalDeleteButton.setControlVisible(visible && modalExistingRule != null);
        if (modalCancelButton != null) modalCancelButton.setControlVisible(visible);
    }

    private boolean isSpecialSearch() {
        if (searchBox == null) return false;
        String value = searchBox.textValue().trim();
        return value.startsWith("#") || value.startsWith("@");
    }

    private RuleDraft findRule(String identifier) {
        if (identifier == null) return null;
        for (RuleDraft rule : rules) {
            if (rule.identifier.equals(identifier)) return rule;
        }
        return null;
    }

    private void save() {
        if (saving) return;
        List<String> serialized = serializeRules();
        saving = true;
        if (saveButton != null) saveButton.setEnabled(false);
        ItemNetwork.saveProtectionRules(serialized);
    }

    public void applySaveResult(boolean success, List<String> serverRules) {
        saving = false;
        if (saveButton != null) saveButton.setEnabled(true);
        if (success) {
            Map<String, String> savedByIdentifier = new HashMap<>();
            if (serverRules != null) {
                for (String raw : serverRules) {
                    RuleDraft parsed = RuleDraft.parse(raw);
                    if (parsed != null) savedByIdentifier.put(parsed.identifier, parsed.serialize());
                }
            }
            for (RuleDraft rule : rules) {
                String saved = savedByIdentifier.get(rule.identifier);
                if (saved != null && saved.equals(rule.serialize())) {
                    rule.markSaved();
                }
            }
            editedTracker.refresh(rules, RuleDraft::isEdited);
            refreshDisplay(false);
            commitDraft();
        }
    }

    private List<String> serializeRules() {
        List<String> serialized = new ArrayList<>(rules.size());
        for (RuleDraft rule : rules) serialized.add(rule.serialize());
        return serialized;
    }

    @Override
    protected boolean onCloseRequested() {
        if (layerManager.isAnyOpen()) {
            closeRuleEditor();
            return true;
        }
        return false;
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width(), height(), 0xFF171717, 0xFF0E0E0E);
        KineticTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        if (layerManager.isOpen(Layer.RULE_EDITOR)) {
            KineticTheme.panel(graphics, MODAL_X, MODAL_Y, MODAL_W, MODAL_H);
        }
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(title(), width() / 2, 9, 0xFFFFFF, true);
        if (layerManager.isOpen(Layer.RULE_EDITOR)) {
            renderModal(graphics);
            return;
        }
        renderScrollingGridBackground(graphics);
        renderItems(graphics, mouseX, mouseY);
        gridScroll.render(
                graphics,
                mouseX,
                mouseY,
                SCROLL_X,
                GRID_Y,
                4,
                GRID_H,
                18
        );
        graphics.text(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.hint"), 26, 346, 0xFFFFFF, false);
        if (specialRuleButton == null || !specialRuleButton.controlVisible()) {
            Component count = KineticI18n.translatable("gui.itemcontrol.item.protection_editor.count", rules.size(), displayEntries.size());
            graphics.text(count, 334, SEARCH_Y + 6, 0xFFFFFF, false);
        }
    }

    private void renderScrollingGridBackground(KineticGraphics graphics) {
        int shift = gridScroll.visualShift(SLOT_PITCH);
        graphics.scissor(GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        for (int row = 0; row <= GRID_ROWS; row++) {
            int y = GRID_Y + row * SLOT_PITCH - shift;
            if (y + SLOT_SIZE <= GRID_Y || y >= GRID_Y + GRID_H) continue;
            for (int col = 0; col < GRID_COLS; col++) {
                int x = GRID_X + col * SLOT_PITCH;
                KineticTheme.itemSlot(graphics, x, y, SLOT_SIZE, 4, false);
            }
        }
        graphics.endScissor();
    }

    private void renderItems(KineticGraphics graphics, int mouseX, int mouseY) {
        int firstRow = gridScroll.smoothIndexOffset();
        int shift = gridScroll.visualShift(SLOT_PITCH);
        int start = firstRow * GRID_COLS;
        int end = Math.min(displayEntries.size(), start + (GRID_ROWS + 1) * GRID_COLS);
        graphics.scissor(GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local % GRID_COLS;
            int row = local / GRID_COLS;
            int x = GRID_X + col * SLOT_PITCH;
            int y = GRID_Y + row * SLOT_PITCH - shift;
            GridEntry entry = displayEntries.get(index);
            boolean hovered = contains(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE);
            KineticTheme.item(graphics, entry.stack, x, y, SLOT_SIZE, ITEM_SCALE, false);
            if (hovered) {
                KineticTheme.stateOutline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, false, true, false);
            } else if (entry.rule != null) {
                KineticTheme.indicatorOutline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, KineticTheme.Indicator.SUCCESS);
            }
            if (entry.rule != null && editedTracker.isEdited(entry.rule)) {
                KineticTheme.indicatorFill(graphics, x + SLOT_SIZE - 3, y + 1, 2, 2, KineticTheme.Indicator.SUCCESS);
            }
        }
        graphics.endScissor();
    }

    private void renderModal(KineticGraphics graphics) {
        graphics.centeredText(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.modal.title"), width() / 2, MODAL_Y + 10, 0xFFFFFF, true);
        int previewX = MODAL_X + 18;
        int previewY = MODAL_Y + 27;
        KineticTheme.itemSlot(graphics, previewX, previewY, SLOT_SIZE, 4, false);
        KineticTheme.item(graphics, modalPreview, previewX, previewY, SLOT_SIZE, ITEM_SCALE, false);
        String name = modalPreview.isEmpty()
                ? KineticI18n.translatable("gui.itemcontrol.item.protection_editor.unknown").getString()
                : modalPreview.getHoverName().getString();
        graphics.text(KineticText.trim(name, 190), MODAL_X + 44, MODAL_Y + 28, 0xFFFFFF, false);
        graphics.text(KineticText.trim(modalIdentifier, 190), MODAL_X + 44, MODAL_Y + 41, 0xFFAAAAAA, false);
        Component state = KineticI18n.translatable(modalExistingRule == null
                ? "gui.itemcontrol.item.protection_editor.modal.new"
                : "gui.itemcontrol.item.protection_editor.modal.existing");
        graphics.text(state, MODAL_X + 14, MODAL_Y + 55, 0xFFFFFF, false);
    }

    @Override
    protected void renderTooltips(int mouseX, int mouseY) {
        if (layerManager.isAnyOpen()) return;
        int index = gridIndexAt(mouseX, mouseY);
        if (index < 0) return;
        GridEntry entry = displayEntries.get(index);
        showTooltip(entryTooltip(entry));
    }

    private List<Component> entryTooltip(GridEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(entry.stack.isEmpty()
                ? KineticI18n.translatable("gui.itemcontrol.item.protection_editor.unknown")
                : entry.stack.getHoverName());
        lines.add(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.identifier", entry.identifier));
        RuleDraft rule = entry.rule;
        if (rule == null) {
            lines.add(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.no_rule"));
            lines.add(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.left_create"));
            return lines;
        }
        lines.add(KineticI18n.translatable(rule.typeKey()));
        lines.add(KineticI18n.translatable(rule.fireImmune
                ? "gui.itemcontrol.item.protection_editor.fire.on"
                : "gui.itemcontrol.item.protection_editor.fire.off"));
        lines.add(KineticI18n.translatable(rule.explosionImmune
                ? "gui.itemcontrol.item.protection_editor.explosion.on"
                : "gui.itemcontrol.item.protection_editor.explosion.off"));
        lines.add(KineticI18n.translatable(rule.glowing
                ? "gui.itemcontrol.item.protection_editor.glowing.on"
                : "gui.itemcontrol.item.protection_editor.glowing.off"));
        lines.add(KineticI18n.translatable(rule.noGravity
                ? "gui.itemcontrol.item.protection_editor.gravity.off"
                : "gui.itemcontrol.item.protection_editor.gravity.on"));
        lines.add(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.left_edit"));
        lines.add(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.right_delete"));
        if (editedTracker.isEdited(rule)) {
            lines.add(KineticI18n.translatable("gui.itemcontrol.item.protection_editor.tooltip.edited"));
        }
        return lines;
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 canvasMouseClicked：弹窗打开时先交给控件；否则滚动条拖动先于控件
        // Former canvasMouseClicked: with the modal open, controls go first; otherwise the scrollbar drag precedes controls.
        if (layerManager.isAnyOpen()) return false;
        return gridScroll.beginDrag(input.x(), input.y(), input.button(), SCROLL_X, GRID_Y, 4, GRID_H, 18, 2);
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        // 弹窗打开时吞掉未被控件处理的点击 / With the modal open, swallow clicks no control handled.
        if (layerManager.isAnyOpen()) {
            return true;
        }

        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0) {
            GridEntry entry = displayEntries.get(index);
            if (input.isLeft()) {
                openRuleEditor(entry.identifier, entry.stack, entry.rule);
                return true;
            }
            if (input.isRight() && entry.rule != null) {
                removeRule(entry.rule);
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
        if (gridScroll.release(input.button())) return true;
        return false;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double mouseY = input.y();
        if (!layerManager.isAnyOpen() && gridScroll.drag(mouseY, GRID_Y, GRID_H, 18)) return true;
        return false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        double delta = input.deltaY();
        if (layerManager.isAnyOpen()) return true;
        if (contains(mouseX, mouseY, GRID_X, GRID_Y, GRID_W + 14, GRID_H) && gridScroll.scroll(delta)) return true;
        return false;
    }

    private int gridIndexAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY, GRID_X, GRID_Y, GRID_W, GRID_H)) return -1;
        int shift = gridScroll.visualShift(SLOT_PITCH);
        int col = (int) ((mouseX - GRID_X) / SLOT_PITCH);
        int row = (int) ((mouseY - GRID_Y + shift) / SLOT_PITCH);
        if (col < 0 || col >= GRID_COLS || row < 0 || row > GRID_ROWS) return -1;
        double localX = mouseX - GRID_X - col * SLOT_PITCH;
        double localY = mouseY - GRID_Y + shift - row * SLOT_PITCH;
        if (localX >= SLOT_SIZE || localY < 0 || localY >= SLOT_SIZE) return -1;
        int index = (gridScroll.smoothIndexOffset() + row) * GRID_COLS + col;
        return index >= 0 && index < displayEntries.size() ? index : -1;
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private final class GridEntry {
        private final ItemStack stack;
        private final String identifier;
        private final RuleDraft rule;
        private final String searchData;
        private final int sourceOrder;

        private GridEntry(ItemStack stack, String identifier, RuleDraft rule, String searchData, int sourceOrder) {
            this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
            this.identifier = identifier == null ? "" : identifier;
            this.rule = rule;
            this.searchData = searchData == null ? "" : searchData;
            this.sourceOrder = sourceOrder;
        }

        private boolean matches(String query) {
            return query == null || query.isBlank() || KineticSearch.match(searchData, query);
        }

    }

    private GridEntry itemEntry(KineticItemSearch.CachedItem cached, RuleDraft rule, String itemIdentifier, int sourceOrder) {
        String identifier = rule == null ? itemIdentifier : rule.identifier;
        return new GridEntry(cached.stack(), identifier, rule, cached.searchText(), sourceOrder);
    }

    private static String identifierForCachedItem(KineticItemSearch.CachedItem cached) {
        if (cached == null || cached.stack() == null || cached.stack().isEmpty()) return "";
        if (cached.stack().getTag() != null && !cached.stack().getTag().isEmpty()) {
            return cached.id() + cached.stack().getTag();
        }
        return cached.id();
    }

    private GridEntry specialEntry(RuleDraft rule, int sourceOrder) {
        ItemStack preview = rule.preview();
        String name = preview.isEmpty() ? "" : preview.getHoverName().getString();
        String searchData = (name + " " + rule.identifier).toLowerCase(Locale.ROOT).trim();
        return new GridEntry(preview, rule.identifier, rule, searchData, sourceOrder);
    }

    private static final class RuleDraft {
        private final String identifier;
        private boolean fireImmune;
        private boolean explosionImmune;
        private boolean glowing;
        private boolean noGravity;
        private ItemStack cachedPreview;
        private String baseline;

        private RuleDraft(String identifier, boolean fireImmune, boolean explosionImmune, boolean glowing, boolean noGravity) {
            this.identifier = identifier;
            this.fireImmune = fireImmune;
            this.explosionImmune = explosionImmune;
            this.glowing = glowing;
            this.noGravity = noGravity;
        }

        private static RuleDraft parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String[] parts = raw.split(";", 5);
            if (parts.length != 5) return null;
            RuleDraft rule = new RuleDraft(
                    parts[0].trim(),
                    Boolean.parseBoolean(parts[1].trim()),
                    Boolean.parseBoolean(parts[2].trim()),
                    Boolean.parseBoolean(parts[3].trim()),
                    Boolean.parseBoolean(parts[4].trim())
            );
            rule.baseline = rule.serialize();
            return rule;
        }

        private static RuleDraft createNew(String identifier, boolean fireImmune, boolean explosionImmune, boolean glowing, boolean noGravity) {
            return new RuleDraft(identifier, fireImmune, explosionImmune, glowing, noGravity);
        }

        private void markSaved() {
            baseline = serialize();
        }

        private boolean isEdited() {
            return baseline == null || !baseline.equals(serialize());
        }

        private String serialize() {
            return identifier + ";" + fireImmune + ";" + explosionImmune + ";" + glowing + ";" + noGravity;
        }

        private String typeKey() {
            if (identifier.startsWith("@")) return "gui.itemcontrol.item.protection_editor.type.mod";
            if (identifier.startsWith("#")) return "gui.itemcontrol.item.protection_editor.type.tag";
            return "gui.itemcontrol.item.protection_editor.type.item";
        }

        private boolean isGroupRule() {
            return identifier.startsWith("@") || identifier.startsWith("#");
        }

        private ItemStack preview() {
            if (cachedPreview == null) cachedPreview = createPreview(identifier);
            return cachedPreview;
        }

        private static ItemStack createPreview(String identifier) {
            if (identifier == null || identifier.isBlank()) return new ItemStack(Items.BARRIER);
            if (identifier.startsWith("@")) {
                String namespace = identifier.substring(1).trim();
                for (Item item : KineticRegistries.items().values()) {
                    ResourceLocation id = KineticRegistries.items().id(item);
                    if (id != null && id.getNamespace().equals(namespace) && item != Items.AIR) return new ItemStack(item);
                }
                return new ItemStack(Items.BARRIER);
            }
            if (identifier.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(identifier.substring(1).trim());
                if (tagId != null) {
                    var tag = ItemTags.create(tagId);
                    for (Item item : KineticRegistries.items().values()) {
                        if (item == Items.AIR) continue;
                        ItemStack stack = new ItemStack(item);
                        if (stack.is(tag)) return stack;
                    }
                }
                return new ItemStack(Items.BARRIER);
            }

            int nbtStart = identifier.indexOf('{');
            String itemId = nbtStart >= 0 ? identifier.substring(0, nbtStart).trim() : identifier.trim();
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) return new ItemStack(Items.BARRIER);
            Item item = KineticRegistries.items().get(id);
            if (item == null || item == Items.AIR) return new ItemStack(Items.BARRIER);
            ItemStack stack = new ItemStack(item);
            if (nbtStart >= 0) {
                try {
                    stack.setTag(TagParser.parseTag(identifier.substring(nbtStart)));
                } catch (Exception ignored) {
                }
            }
            return stack;
        }
    }
}
