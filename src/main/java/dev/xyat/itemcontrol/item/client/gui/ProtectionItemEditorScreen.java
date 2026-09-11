package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.search.ItemSearchIndex;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.EditedEntryTracker;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.LayerState;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.HighZButton;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public final class ProtectionItemEditorScreen extends KineticScreen {
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
    private static final int RULE_OUTLINE_COLOR = 0xFF55FF55;
    private static final int HOVER_OUTLINE_COLOR = 0xFFAAAAAA;
    private static final int PLACEHOLDER_TEXT_COLOR = 0xBFFFFFFF;
    private static final float ITEM_SCALE = 1.0F;

    private static final int MODAL_X = 190;
    private static final int MODAL_Y = 99;
    private static final int MODAL_W = 260;
    private static final int MODAL_H = 164;

    private enum Layer {
        RULE_EDITOR
    }

    private final Screen parent;
    private final List<RuleDraft> rules = new ArrayList<>();
    private final List<GridEntry> displayEntries = new ArrayList<>();
    private final GridScrollController gridScroll = new GridScrollController();
    private final EditedEntryTracker<RuleDraft> editedTracker = new EditedEntryTracker<>();
    private final LayerState<Layer> layerManager = new LayerState<>();

    private List<ItemSearchIndex.CachedItem> allItems = List.of();
    private EditBox searchBox;
    private Button specialRuleButton;
    private Button saveButton;
    private Button backButton;

    private Button modalFireButton;
    private Button modalExplosionButton;
    private Button modalGlowingButton;
    private Button modalGravityButton;
    private Button modalApplyButton;
    private Button modalDeleteButton;
    private Button modalCancelButton;

    private RuleDraft modalExistingRule;
    private String modalIdentifier = "";
    private ItemStack modalPreview = ItemStack.EMPTY;
    private boolean modalFireImmune;
    private boolean modalExplosionImmune;
    private boolean modalGlowing;
    private boolean modalNoGravity;
    private boolean saving;

    public ProtectionItemEditorScreen(Screen parent, List<String> initialRules) {
        super(Component.translatable("gui.itemcontrol.item.protection_editor.title"));
        this.parent = parent;
        if (initialRules != null) {
            for (String raw : initialRules) {
                RuleDraft rule = RuleDraft.parse(raw);
                if (rule != null) rules.add(rule);
            }
        }
        editedTracker.refresh(rules, RuleDraft::isEdited);
        useCanvas(640F, 360F, 6);
        dev.xyat.kineticcore.api.client.screen.GuiSession.setParent(this, parent);
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
    protected void buildUi() {
        searchBox = new EditBox(font, SEARCH_X, SEARCH_Y, SEARCH_W, 20, Component.empty());
        searchBox.setMaxLength(1024);
        searchBox.setResponder(value -> refreshDisplay(true));
        addRenderableWidget(searchBox);

        specialRuleButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemcontrol.item.protection_editor.special_rule"),
                        button -> openSpecialRuleFromSearch())
                .bounds(SPECIAL_X, SEARCH_Y, SPECIAL_W, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.special_rule")))
                .build());

        saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemcontrol.item.protection_editor.save"),
                        button -> save())
                .bounds(500, 30, 52, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.save")))
                .build());

        backButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemcontrol.item.protection_editor.back"),
                        button -> onClose())
                .bounds(558, 30, 56, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.back")))
                .build());

        modalFireButton = addModalButton(204, 166, 108, 20, button -> {
            modalFireImmune = !modalFireImmune;
            refreshModalButtons();
        }, "gui.itemcontrol.item.protection_editor.tooltip.fire");
        modalExplosionButton = addModalButton(328, 166, 108, 20, button -> {
            modalExplosionImmune = !modalExplosionImmune;
            refreshModalButtons();
        }, "gui.itemcontrol.item.protection_editor.tooltip.explosion");
        modalGlowingButton = addModalButton(204, 194, 108, 20, button -> {
            modalGlowing = !modalGlowing;
            refreshModalButtons();
        }, "gui.itemcontrol.item.protection_editor.tooltip.glowing");
        modalGravityButton = addModalButton(328, 194, 108, 20, button -> {
            modalNoGravity = !modalNoGravity;
            refreshModalButtons();
        }, "gui.itemcontrol.item.protection_editor.tooltip.gravity");

        modalApplyButton = addRenderableWidget(new HighZButton(
                204, 228, 72, 20,
                Component.translatable("gui.itemcontrol.item.protection_editor.apply"),
                button -> applyModalRule(),
                Tooltip.create(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.apply"))
        ));
        modalDeleteButton = addRenderableWidget(new HighZButton(
                284, 228, 72, 20,
                Component.translatable("gui.itemcontrol.item.protection_editor.delete"),
                button -> deleteModalRule(),
                Tooltip.create(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.delete"))
        ));
        modalCancelButton = addRenderableWidget(new HighZButton(
                364, 228, 72, 20,
                Component.translatable("gui.itemcontrol.item.protection_editor.cancel"),
                button -> closeRuleEditor(),
                Tooltip.create(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.cancel"))
        ));

        setModalWidgetsVisible(false);
        refreshSpecialRuleButton();
        refreshDisplay(false);

        ItemSearchIndex.prepareCache(() -> {
            if (minecraft != null && minecraft.screen == this) {
                allItems = ItemSearchIndex.getItems();
                refreshDisplay(false);
            }
        });
        if (ItemSearchIndex.isReady()) {
            allItems = ItemSearchIndex.getItems();
            refreshDisplay(false);
        }
    }

    private Button addModalButton(int x, int y, int w, int h, Button.OnPress onPress, String tooltipKey) {
        return addRenderableWidget(new HighZButton(
                x, y, w, h, Component.empty(), onPress,
                Tooltip.create(Component.translatable(tooltipKey))
        ));
    }

    private void refreshDisplay(boolean resetScroll) {
        if (resetScroll) gridScroll.reset();
        displayEntries.clear();

        Map<String, RuleDraft> exactRules = new LinkedHashMap<>();
        Map<String, Boolean> cachedIdentifiers = new HashMap<>();
        for (ItemSearchIndex.CachedItem cached : allItems) {
            if (cached == null || cached.stack == null || cached.stack.isEmpty()) continue;
            cachedIdentifiers.put(identifierForCachedItem(cached), Boolean.TRUE);
            cachedIdentifiers.put(cached.idStr, Boolean.TRUE);
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

        String query = searchBox == null ? "" : searchBox.getValue().trim();
        specialEntries.removeIf(entry -> !entry.matches(query));
        specialEntries.sort((left, right) -> compareEntries(left, right));
        displayEntries.addAll(specialEntries);

        List<GridEntry> itemEntries = new ArrayList<>();
        int itemOrder = 100000;
        for (ItemSearchIndex.CachedItem cached : allItems) {
            if (cached == null || cached.stack == null || cached.stack.isEmpty()) continue;
            if (!matchesItemSearch(cached, query)) continue;
            String itemIdentifier = identifierForCachedItem(cached);
            RuleDraft rule = exactRules.get(itemIdentifier);
            if (rule == null) rule = exactRules.get(cached.idStr);
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

    private static boolean matchesItemSearch(ItemSearchIndex.CachedItem item, String query) {
        if (query == null || query.isBlank()) return true;
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("@") && normalized.length() > 1) {
            return item.namespace.contains(normalized.substring(1));
        }
        if (normalized.startsWith("#") && normalized.length() > 1) {
            String tagQuery = normalized.substring(1);
            return item.tagIds.stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(tagQuery));
        }
        return KineticSearch.match(item.searchData, normalized);
    }

    private void refreshSpecialRuleButton() {
        if (specialRuleButton == null || searchBox == null) return;
        String value = searchBox.getValue().trim();
        boolean special = value.startsWith("#") || value.startsWith("@");
        RuleDraft existing = special ? findRule(value) : null;
        specialRuleButton.visible = special;
        specialRuleButton.active = special && (existing != null || isValidSpecialIdentifier(value));
        specialRuleButton.setMessage(Component.translatable(existing == null
                ? "gui.itemcontrol.item.protection_editor.special_rule.add"
                : "gui.itemcontrol.item.protection_editor.special_rule.edit"));
    }

    private boolean isValidSpecialIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 2) return false;
        if (identifier.startsWith("@")) {
            String namespace = identifier.substring(1).trim().toLowerCase(Locale.ROOT);
            if (namespace.isEmpty()) return false;
            for (ItemSearchIndex.CachedItem item : allItems) {
                if (namespace.equals(item.namespace.toLowerCase(Locale.ROOT))) return true;
            }
            return false;
        }
        if (identifier.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(identifier.substring(1).trim());
            if (tagId == null) return false;
            String expected = tagId.toString();
            for (ItemSearchIndex.CachedItem item : allItems) {
                if (item.tagIds.contains(expected)) return true;
            }
        }
        return false;
    }

    private void openSpecialRuleFromSearch() {
        if (searchBox == null) return;
        String identifier = searchBox.getValue().trim();
        RuleDraft existing = findRule(identifier);
        if (existing == null && !isValidSpecialIdentifier(identifier)) {
            GuiOverlay.toast(Component.translatable("msg.itemcontrol.item.protection_editor.invalid_selection"));
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
        modalFireButton.setMessage(Component.translatable(modalFireImmune
                ? "gui.itemcontrol.item.protection_editor.fire.on"
                : "gui.itemcontrol.item.protection_editor.fire.off"));
        modalExplosionButton.setMessage(Component.translatable(modalExplosionImmune
                ? "gui.itemcontrol.item.protection_editor.explosion.on"
                : "gui.itemcontrol.item.protection_editor.explosion.off"));
        modalGlowingButton.setMessage(Component.translatable(modalGlowing
                ? "gui.itemcontrol.item.protection_editor.glowing.on"
                : "gui.itemcontrol.item.protection_editor.glowing.off"));
        modalGravityButton.setMessage(Component.translatable(modalNoGravity
                ? "gui.itemcontrol.item.protection_editor.gravity.off"
                : "gui.itemcontrol.item.protection_editor.gravity.on"));
        if (modalDeleteButton != null) modalDeleteButton.visible = modalExistingRule != null;
    }

    private void setMainWidgetsVisible(boolean visible) {
        if (searchBox != null) searchBox.visible = visible;
        if (specialRuleButton != null) specialRuleButton.visible = visible && isSpecialSearch();
        if (saveButton != null) saveButton.visible = visible;
        if (backButton != null) backButton.visible = visible;
    }

    private void setModalWidgetsVisible(boolean visible) {
        if (modalFireButton != null) modalFireButton.visible = visible;
        if (modalExplosionButton != null) modalExplosionButton.visible = visible;
        if (modalGlowingButton != null) modalGlowingButton.visible = visible;
        if (modalGravityButton != null) modalGravityButton.visible = visible;
        if (modalApplyButton != null) modalApplyButton.visible = visible;
        if (modalDeleteButton != null) modalDeleteButton.visible = visible && modalExistingRule != null;
        if (modalCancelButton != null) modalCancelButton.visible = visible;
    }

    private boolean isSpecialSearch() {
        if (searchBox == null) return false;
        String value = searchBox.getValue().trim();
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
        if (saveButton != null) saveButton.active = false;
        ItemNetwork.saveProtectionRules(serialized);
    }

    public void applySaveResult(boolean success, List<String> serverRules) {
        saving = false;
        if (saveButton != null) saveButton.active = true;
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
    public void onClose() {
        if (layerManager.isAnyOpen()) {
            closeRuleEditor();
            return;
        }
        super.onClose();
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, canvasWidth, canvasHeight, 0xFF171717, 0xFF0E0E0E);
        GuiTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H, 0xEE1C1C1C, 0xFFAAAAAA);
        if (layerManager.isOpen(Layer.RULE_EDITOR)) {
            graphics.fill(0, 0, canvasWidth, canvasHeight, 0x99000000);
            GuiTheme.panel(graphics, MODAL_X, MODAL_Y, MODAL_W, MODAL_H, 0xFF171717, 0xFFAAAAAA);
        }
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(font, title, canvasWidth / 2, 9, 0xFFFFFF);
        if (layerManager.isOpen(Layer.RULE_EDITOR)) {
            renderModal(graphics);
            return;
        }
        renderSearchHint(graphics);
        if (!ItemSearchIndex.isReady()) return;
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
                18,
                GuiTheme.current().scrollTrack(),
                GuiTheme.current().scrollThumb(),
                GuiTheme.current().scrollThumbHover()
        );
        graphics.drawString(font, Component.translatable("gui.itemcontrol.item.protection_editor.hint"), 26, 346, 0xFFFFFF, false);
        if (specialRuleButton == null || !specialRuleButton.visible) {
            Component count = Component.translatable("gui.itemcontrol.item.protection_editor.count", rules.size(), displayEntries.size());
            graphics.drawString(font, count, 334, SEARCH_Y + 6, 0xFFFFFF, false);
        }
    }

    private void renderSearchHint(GuiGraphics graphics) {
        if (searchBox == null || searchBox.isFocused() || !searchBox.getValue().isEmpty()) return;
        String hint = Component.translatable("gui.itemcontrol.item.protection_editor.search.hint").getString();
        graphics.drawString(font, font.plainSubstrByWidth(hint, SEARCH_W - 8), SEARCH_X + 4, SEARCH_Y + 6, PLACEHOLDER_TEXT_COLOR, false);
    }

    private void renderScrollingGridBackground(GuiGraphics graphics) {
        int shift = gridScroll.visualShift(SLOT_PITCH);
        enableCanvasScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        for (int row = 0; row <= GRID_ROWS; row++) {
            int y = GRID_Y + row * SLOT_PITCH - shift;
            if (y + SLOT_SIZE <= GRID_Y || y >= GRID_Y + GRID_H) continue;
            for (int col = 0; col < GRID_COLS; col++) {
                int x = GRID_X + col * SLOT_PITCH;
                GuiTheme.itemSlot(graphics, x, y, SLOT_SIZE, 4, false);
            }
        }
        graphics.disableScissor();
    }

    private void renderItems(GuiGraphics graphics, int mouseX, int mouseY) {
        int firstRow = gridScroll.smoothIndexOffset();
        int shift = gridScroll.visualShift(SLOT_PITCH);
        int start = firstRow * GRID_COLS;
        int end = Math.min(displayEntries.size(), start + (GRID_ROWS + 1) * GRID_COLS);
        enableCanvasScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local % GRID_COLS;
            int row = local / GRID_COLS;
            int x = GRID_X + col * SLOT_PITCH;
            int y = GRID_Y + row * SLOT_PITCH - shift;
            GridEntry entry = displayEntries.get(index);
            boolean hovered = contains(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE);
            GuiTheme.item(graphics, font, entry.stack, x, y, SLOT_SIZE, ITEM_SCALE, false);
            if (hovered) {
                graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, HOVER_OUTLINE_COLOR);
            } else if (entry.rule != null) {
                graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, RULE_OUTLINE_COLOR);
            }
            if (entry.rule != null && editedTracker.isEdited(entry.rule)) {
                graphics.fill(x + SLOT_SIZE - 3, y + 1, x + SLOT_SIZE - 1, y + 3, RULE_OUTLINE_COLOR);
            }
        }
        graphics.disableScissor();
    }

    private void renderModal(GuiGraphics graphics) {
        graphics.drawCenteredString(font, Component.translatable("gui.itemcontrol.item.protection_editor.modal.title"), canvasWidth / 2, MODAL_Y + 10, 0xFFFFFF);
        int previewX = MODAL_X + 18;
        int previewY = MODAL_Y + 27;
        GuiTheme.itemSlot(graphics, modalPreview, previewX, previewY, SLOT_SIZE, 4, false);
        GuiTheme.item(graphics, font, modalPreview, previewX, previewY, SLOT_SIZE, ITEM_SCALE, false);
        String name = modalPreview.isEmpty()
                ? Component.translatable("gui.itemcontrol.item.protection_editor.unknown").getString()
                : modalPreview.getHoverName().getString();
        graphics.drawString(font, font.plainSubstrByWidth(name, 190), MODAL_X + 44, MODAL_Y + 28, 0xFFFFFF, false);
        graphics.drawString(font, font.plainSubstrByWidth(modalIdentifier, 190), MODAL_X + 44, MODAL_Y + 41, 0xFFAAAAAA, false);
        Component state = Component.translatable(modalExistingRule == null
                ? "gui.itemcontrol.item.protection_editor.modal.new"
                : "gui.itemcontrol.item.protection_editor.modal.existing");
        graphics.drawString(font, state, MODAL_X + 14, MODAL_Y + 55, 0xFFFFFF, false);
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        if (layerManager.isAnyOpen() || !ItemSearchIndex.isReady()) return;
        int index = gridIndexAt(scaledMouseX, scaledMouseY);
        if (index < 0) return;
        GridEntry entry = displayEntries.get(index);
        GuiOverlay.requestTooltip(entryTooltip(entry), mouseX, mouseY);
    }

    private List<Component> entryTooltip(GridEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(entry.stack.isEmpty()
                ? Component.translatable("gui.itemcontrol.item.protection_editor.unknown")
                : entry.stack.getHoverName());
        lines.add(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.identifier", entry.identifier));
        RuleDraft rule = entry.rule;
        if (rule == null) {
            lines.add(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.no_rule"));
            lines.add(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.left_create"));
            return lines;
        }
        lines.add(Component.translatable(rule.typeKey()));
        lines.add(Component.translatable(rule.fireImmune
                ? "gui.itemcontrol.item.protection_editor.fire.on"
                : "gui.itemcontrol.item.protection_editor.fire.off"));
        lines.add(Component.translatable(rule.explosionImmune
                ? "gui.itemcontrol.item.protection_editor.explosion.on"
                : "gui.itemcontrol.item.protection_editor.explosion.off"));
        lines.add(Component.translatable(rule.glowing
                ? "gui.itemcontrol.item.protection_editor.glowing.on"
                : "gui.itemcontrol.item.protection_editor.glowing.off"));
        lines.add(Component.translatable(rule.noGravity
                ? "gui.itemcontrol.item.protection_editor.gravity.off"
                : "gui.itemcontrol.item.protection_editor.gravity.on"));
        lines.add(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.left_edit"));
        lines.add(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.right_delete"));
        if (editedTracker.isEdited(rule)) {
            lines.add(Component.translatable("gui.itemcontrol.item.protection_editor.tooltip.edited"));
        }
        return lines;
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (layerManager.isAnyOpen()) {
            if (super.canvasMouseClicked(mouseX, mouseY, button)) return true;
            return true;
        }

        if (button == 0 && gridScroll.beginDrag(mouseX, mouseY, SCROLL_X, GRID_Y, 4, GRID_H, 18, 2)) {
            return true;
        }
        if (super.canvasMouseClicked(mouseX, mouseY, button)) return true;

        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0) {
            GridEntry entry = displayEntries.get(index);
            if (button == 0) {
                openRuleEditor(entry.identifier, entry.stack, entry.rule);
                return true;
            }
            if (button == 1 && entry.rule != null) {
                removeRule(entry.rule);
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
        if (gridScroll.release(button)) return true;
        return super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!layerManager.isAnyOpen() && gridScroll.drag(mouseY, GRID_Y, GRID_H, 18)) return true;
        return super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (layerManager.isAnyOpen()) return true;
        if (contains(mouseX, mouseY, GRID_X, GRID_Y, GRID_W + 14, GRID_H) && gridScroll.scroll(delta)) return true;
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
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

    private GridEntry itemEntry(ItemSearchIndex.CachedItem cached, RuleDraft rule, String itemIdentifier, int sourceOrder) {
        String identifier = rule == null ? itemIdentifier : rule.identifier;
        return new GridEntry(cached.stack, identifier, rule, cached.searchData, sourceOrder);
    }

    private static String identifierForCachedItem(ItemSearchIndex.CachedItem cached) {
        if (cached == null || cached.stack == null || cached.stack.isEmpty()) return "";
        if (cached.stack.getTag() != null && !cached.stack.getTag().isEmpty()) {
            return cached.idStr + cached.stack.getTag();
        }
        return cached.idStr;
    }

    private GridEntry specialEntry(RuleDraft rule, int sourceOrder) {
        ItemStack preview = rule.preview();
        String name = preview.isEmpty() ? "" : preview.getHoverName().getString();
        String searchData = KineticSearch.normalize(name + " " + rule.identifier);
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
                for (Item item : ForgeRegistries.ITEMS.getValues()) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id != null && id.getNamespace().equals(namespace) && item != Items.AIR) return new ItemStack(item);
                }
                return new ItemStack(Items.BARRIER);
            }
            if (identifier.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(identifier.substring(1).trim());
                if (tagId != null) {
                    var tag = ItemTags.create(tagId);
                    for (Item item : ForgeRegistries.ITEMS.getValues()) {
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
            Item item = ForgeRegistries.ITEMS.getValue(id);
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
