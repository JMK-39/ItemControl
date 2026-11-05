package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;

import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.itemcontrol.item.util.ItemBanControl;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class ItemTagEditorPage extends KineticPage {
    private Runnable hoveredDelete;
    // Standard item slots (the same size in every add-on); the item list shows ten columns, the tag list takes the rest.
    private static final int SLOT_SIZE = dev.xyat.kineticcore.api.client.gui.theme.KineticSizes.ITEM_SLOT;
    private static final int SLOT_PITCH = dev.xyat.kineticcore.api.client.gui.theme.KineticSizes.ITEM_PITCH;
    private static final int ITEM_COLUMNS = 10;
    // Keep text clear of panel edges and the tag list's inset scrollbar.
    private static final int TEXT_GAP = 4;
    // The tag list scroll bar is 4 px wide and 2 px clear of the list's right edge.
    private static final int TAG_SCROLLBAR_INSET = 6;
    private static final int TAG_ROW_PITCH = 18;
    private static String rememberedItemSearch = "";
    private static String rememberedTagSearch = "";
    private static String rememberedSelectedItem = "";

    private final List<KineticItemSearch.CachedItem> allItems;
    private final List<String> allTags;
    private final KineticScrollController itemScroll = new KineticScrollController();
    private final KineticScrollController tagScroll = new KineticScrollController();
    private final KineticScrollController suggestionScroll = new KineticScrollController();

    private KineticTextField itemSearch;
    private KineticTextField tagInput;
    private KineticButton addTagButton;
    private KineticButton copyButton;
    private KineticButton saveButton;
    private KineticButton clearNativeButton;
    private KineticButton backButton;

    private List<KineticItemSearch.CachedItem> filteredItems = new ArrayList<>();
    private List<String> tagSuggestions = new ArrayList<>();
    private List<TagEntry> displayedTags = new ArrayList<>();
    private String selectedItemId = "";
    private boolean copyMode;

    private int leftX;
    private int leftY;
    private int leftW;
    private int leftH;
    private int leftCols;
    private int rightX;
    private int rightY;
    private int rightW;
    private int rightH;
    private int tagListY;
    private int tagListH;

    public ItemTagEditorPage() {
        super(KineticI18n.translatable("gui.itemcontrol.item.item_tag.title"));
        configureDraft(BanItemConfig::snapshotData, this::restoreTagSnapshot);
        useCanvas(640, 360, 4);
        this.allItems = ItemSearchCache.getAllItems();
        this.allTags = ItemSearchCache.getAllTags();
        this.selectedItemId = rememberedSelectedItem;
    }

    private void restoreTagSnapshot(BanItemConfig.Data snapshot) {
        BanItemConfig.restoreDataSnapshot(snapshot);
        ItemSearchCache.markRulesChanged();
        if (tagInput != null) {
            refreshTagEntries();
            refreshTagSuggestions();
            refreshButtons();
        }
    }

    @Override
    protected void build(KineticUi ui) {
        bindDeleteAction(() -> hoveredDelete != null, () -> { var action = hoveredDelete; hoveredDelete = null; if (action != null) action.run(); });
        // Panel surfaces sit 3 px outside their content and 4 px inside the frame; the header row is 4 px from the top.
        int pad = 4;
        int topY = 4;
        int toolbarH = 20;
        leftX = pad + 3;
        // Ten columns, 2 px to the 4 px scroll bar, 2 px to the panel edge.
        leftW = ITEM_COLUMNS * SLOT_PITCH - 2 + 2 + 4 + 2;
        rightX = leftX + leftW + 3 + 4 + 3;
        rightW = width() - pad - 3 - rightX;

        int buttonGap = 4;
        int smallButtonW = 64;
        itemSearch = ui().textField(48, topY, leftX + leftW + 3 - 48).build();
        itemSearch.setPlaceholder(KineticI18n.translatable("gui.itemcontrol.item.item_tag.item_search_hint"));
        itemSearch.setTextValue(rememberedItemSearch);
        itemSearch.setDefaultText(rememberedItemSearch);
        itemSearch.onTextChange(value -> {
            rememberedItemSearch = value == null ? "" : value;
            refreshItems();
        });

        copyButton = ui().button(rightX - 3, topY, smallButtonW).text(copyButtonText()).onClick(() -> {
            if (selectedItemId.isEmpty()) return;
            copyMode = !copyMode;
            copyButton.setText(copyButtonText());
        }).build();

        saveButton = ui().button(width() - pad - 40, topY, 40).text(KineticI18n.translatable("gui.itemcontrol.item.item_tag.save")).onClick(this::saveConfig).build();

        backButton = ui().button(pad, topY, 40).text(KineticI18n.translatable("gui.itemcontrol.item.item_tag.back")).onClick(this::close).build();

        addTagButton = ui().button(rightX - 3 + smallButtonW + buttonGap, topY, 40).text(KineticI18n.translatable("gui.itemcontrol.item.item_tag.add")).onClick(this::addTagFromInput).build();
        // Takes every original tag off the selected item (or gives them back), like clearing its tags in a datapack.
        clearNativeButton = ui().button(rightX - 3 + smallButtonW + buttonGap + 40 + buttonGap, topY, 64).text(clearNativeText())
                .onClick(this::toggleClearNative).build();

        leftY = topY + toolbarH + 4 + 3;
        leftH = Math.max(80, height() - leftY - pad - 3);
        leftCols = ITEM_COLUMNS;

        // The tag field sits right under the header and the tag list right under the field.
        tagInput = ui().textField(rightX, 28, rightW).build();
        tagInput.setPlaceholder(KineticI18n.translatable("gui.itemcontrol.item.item_tag.tag_input_hint"));
        tagInput.setTextValue(rememberedTagSearch);
        tagInput.setDefaultText(rememberedTagSearch);
        tagInput.onTextChange(value -> {
            rememberedTagSearch = value == null ? "" : value;
            refreshTagSuggestions();
        });

        rightY = tagInput.controlY() + tagInput.controlHeight() + 2 + 3;
        rightH = Math.max(60, height() - rightY - pad - 3);
        tagListY = rightY;
        tagListH = rightH;

        refreshItems();
        refreshTagEntries();
        refreshTagSuggestions();
        refreshButtons();
    }

    private Component copyButtonText() {
        return KineticI18n.translatable(copyMode ? "gui.itemcontrol.item.item_tag.copy_select" : "gui.itemcontrol.item.item_tag.copy");
    }

    private void refreshButtons() {
        boolean hasSelection = !selectedItemId.isEmpty();
        if (copyButton != null) copyButton.setEnabled(hasSelection);
        if (clearNativeButton != null) {
            clearNativeButton.setEnabled(hasSelection);
            clearNativeButton.setText(clearNativeText());
        }
        if (addTagButton != null) addTagButton.setEnabled(hasSelection && normalizeTag(tagInput == null ? "" : tagInput.textValue()) != null);
    }

    private void refreshItems() {
        String query = itemSearch == null ? "" : itemSearch.textValue().trim().toLowerCase(Locale.ROOT);
        filteredItems = new ArrayList<>(ItemSearchCache.searchItems("item_tag_editor", allItems, query, ItemSearchCache.getAllItemsHash()));
        filteredItems.sort((left, right) -> Boolean.compare(isEditedItem(right), isEditedItem(left)));
        int rows = (int) Math.ceil((double) filteredItems.size() / Math.max(1, leftCols));
        itemScroll.update(rows * SLOT_PITCH, leftH);
    }

    private boolean isEditedItem(KineticItemSearch.CachedItem item) {
        if (item == null || item.id() == null || item.id().isBlank()) return false;
        String id = BanItemConfig.getBaseIdentifier(item.id());
        return !id.isEmpty() && (!BanItemConfig.getConfiguredAddedTagIds(id).isEmpty() || !BanItemConfig.getConfiguredRemovedTagIds(id).isEmpty());
    }

    private void refreshTagSuggestions() {
        if (tagInput == null) return;
        String query = tagInput.textValue().trim().toLowerCase(Locale.ROOT);
        String normalizedQuery = query.startsWith("#") ? query : "#" + query;
        tagSuggestions = query.isEmpty()
                ? allTags
                : ItemSearchCache.searchStrings("item_tag_suggestions", allTags, normalizedQuery);
        suggestionScroll.reset();
        refreshButtons();
    }

    private void refreshTagEntries() {
        displayedTags = new ArrayList<>();
        if (selectedItemId.isEmpty()) {
            tagScroll.update(0, tagListH);
            return;
        }

        Set<String> nativeTags = new TreeSet<>(ItemSearchCache.getRegistryTagIdsForId(selectedItemId));
        Set<String> manualTags = new TreeSet<>(BanItemConfig.getConfiguredAddedTagIds(selectedItemId));
        Set<String> extraTags = new TreeSet<>(BanItemConfig.getEffectiveExtraTagIds(selectedItemId));
        Set<String> mergedTags = new TreeSet<>(extraTags);
        mergedTags.removeAll(manualTags);
        mergedTags.removeAll(nativeTags);

        // Removed tags show as their own rows (they stay in the loaded tags until the data reloads).
        Set<String> removedTags = new TreeSet<>(BanItemConfig.getConfiguredRemovedTagIds(selectedItemId));
        boolean allRemoved = removedTags.contains(BanItemConfig.ALL_TAGS);
        if (allRemoved) displayedTags.add(new TagEntry(BanItemConfig.ALL_TAGS, TagSource.REMOVED));
        for (String tag : nativeTags) {
            if (!allRemoved && !removedTags.contains(tag)) displayedTags.add(new TagEntry(tag, TagSource.NATIVE));
        }
        for (String tag : removedTags) {
            if (!tag.equals(BanItemConfig.ALL_TAGS)) displayedTags.add(new TagEntry(tag, TagSource.REMOVED));
        }
        for (String tag : mergedTags) displayedTags.add(new TagEntry(tag, TagSource.MERGED));
        for (String tag : manualTags) displayedTags.add(new TagEntry(tag, TagSource.MANUAL));
        tagScroll.update(displayedTags.size() * TAG_ROW_PITCH, tagListH);
    }

    private void selectItem(KineticItemSearch.CachedItem item) {
        if (item == null || item.id() == null || item.id().isBlank()) return;
        String id = BanItemConfig.getBaseIdentifier(item.id());
        if (id.isEmpty()) return;
        if (copyMode && !selectedItemId.isEmpty()) {
            if (!selectedItemId.equals(id)) copyTagsFrom(id);
            copyMode = false;
            if (copyButton != null) copyButton.setText(copyButtonText());
            return;
        }
        selectedItemId = id;
        rememberedSelectedItem = id;
        tagScroll.reset();
        refreshTagEntries();
        refreshButtons();
    }

    private void copyTagsFrom(String sourceItemId) {
        Set<String> sourceTags = ItemSearchCache.getEffectiveTagIdsForId(sourceItemId);
        if (sourceTags.isEmpty()) return;
        Set<String> existing = ItemSearchCache.getEffectiveTagIdsForId(selectedItemId);
        LinkedHashSet<String> toAdd = new LinkedHashSet<>(sourceTags);
        toAdd.removeAll(existing);
        if (toAdd.isEmpty()) return;
        List<String> manual = new ArrayList<>(BanItemConfig.data.addedItemTags.getOrDefault(selectedItemId, Collections.emptyList()));
        LinkedHashSet<String> deduped = new LinkedHashSet<>(manual);
        deduped.addAll(toAdd);
        BanItemConfig.data.addedItemTags.put(selectedItemId, new ArrayList<>(deduped));
        onRulesChanged();
    }

    private void addTagFromInput() {
        if (selectedItemId.isEmpty() || tagInput == null) return;
        String tag = normalizeTag(tagInput.textValue());
        if (tag == null) return;
        addManualTag(tag);
        tagInput.setTextValue("");
        focus(tagInput);
    }

    private void addManualTag(String tag) {
        if (selectedItemId.isEmpty() || tag == null) return;
        if (ItemSearchCache.getEffectiveTagIdsForId(selectedItemId).contains(tag)) return;
        List<String> manual = new ArrayList<>(BanItemConfig.data.addedItemTags.getOrDefault(selectedItemId, Collections.emptyList()));
        LinkedHashSet<String> deduped = new LinkedHashSet<>(manual);
        if (!deduped.add(tag)) return;
        BanItemConfig.data.addedItemTags.put(selectedItemId, new ArrayList<>(deduped));
        onRulesChanged();
    }

    private void removeManualTag(String tag) {
        if (selectedItemId.isEmpty() || tag == null) return;
        List<String> manual = new ArrayList<>(BanItemConfig.data.addedItemTags.getOrDefault(selectedItemId, Collections.emptyList()));
        if (!manual.remove(tag)) return;
        if (manual.isEmpty()) BanItemConfig.data.addedItemTags.remove(selectedItemId);
        else BanItemConfig.data.addedItemTags.put(selectedItemId, manual);
        onRulesChanged();
    }

    private Component clearNativeText() {
        boolean all = !selectedItemId.isEmpty() && BanItemConfig.getConfiguredRemovedTagIds(selectedItemId).contains(BanItemConfig.ALL_TAGS);
        return KineticI18n.translatable(all ? "gui.itemcontrol.item.item_tag.restore_native" : "gui.itemcontrol.item.item_tag.clear_native");
    }

    private void toggleClearNative() {
        if (selectedItemId.isEmpty()) return;
        List<String> removed = new ArrayList<>(BanItemConfig.data.removedItemTags.getOrDefault(selectedItemId, Collections.emptyList()));
        if (removed.contains(BanItemConfig.ALL_TAGS)) removed.clear();
        else removed = new ArrayList<>(List.of(BanItemConfig.ALL_TAGS));
        setRemoved(removed);
    }

    /** Takes one original tag off the selected item, or gives a removed one back. */
    private void toggleRemovedTag(String tag) {
        if (selectedItemId.isEmpty() || tag == null) return;
        List<String> removed = new ArrayList<>(BanItemConfig.data.removedItemTags.getOrDefault(selectedItemId, Collections.emptyList()));
        if (!removed.remove(tag)) removed.add(tag);
        setRemoved(removed);
    }

    private void setRemoved(List<String> removed) {
        if (removed.isEmpty()) BanItemConfig.data.removedItemTags.remove(selectedItemId);
        else BanItemConfig.data.removedItemTags.put(selectedItemId, new ArrayList<>(new LinkedHashSet<>(removed)));
        onRulesChanged();
    }

    private void onRulesChanged() {
        BanItemConfig.rebuildCache();
        ItemSearchCache.markRulesChanged();
        refreshItems();
        itemScroll.reset();
        refreshTagEntries();
        refreshTagSuggestions();
        refreshButtons();
    }

    private String normalizeTag(String raw) {
        if (raw == null) return null;
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.isEmpty()) return null;
        try {
            return new ResourceLocation(clean).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveConfig() {
        ItemNetwork.CHANNEL.sendToServer(new ItemNetwork.SaveBanConfigPacket(
                ItemNetwork.EDITOR_ITEM_TAG,
                BanItemConfig.GSON.toJson(BanItemConfig.data)
        ));
    }

    public void applySaveResult(boolean success) {
        if (success) commitDraft();
    }

    @Override
    protected void renderBackground(KineticGraphics g, int smx, int smy, float pt) {
        hoveredDelete = null;
        KineticTheme.panel(g, 0, 0, width(), height());
        KineticTheme.surface(g, leftX - 3, leftY - 3, leftW + 6, leftH + 6, KineticTheme.Surface.PANEL_ALT);
        KineticTheme.surface(g, rightX - 3, rightY - 3, rightW + 6, rightH + 6, KineticTheme.Surface.PANEL_ALT);
        KineticTheme.verticalSeparator(g, (leftX + leftW + rightX) / 2, leftY - 3, height() - leftY - 1);
        KineticTheme.separator(g, 4, 24, width() - 8);
    }

    @Override
    protected void renderForeground(KineticGraphics g, int smx, int smy, float pt) {

        g.scissor(leftX, leftY, leftX + leftW, leftY + leftH);
        // All icons of the grid are drawn together (one batch instead of one draw call each).
        g.batchItems(() -> {
        for (int i = 0; i < filteredItems.size(); i++) {
            KineticItemSearch.CachedItem ci = filteredItems.get(i);
            int col = i % leftCols;
            int row = i / leftCols;
            int x = leftX + col * SLOT_PITCH;
            int y = leftY + row * SLOT_PITCH - (int) Math.round(itemScroll.smoothOffset());
            if (y + SLOT_SIZE <= leftY || y >= leftY + leftH) continue;
            boolean hovered = smx >= x && smx < x + SLOT_SIZE && smy >= y && smy < y + SLOT_SIZE;
            boolean selected = selectedItemId.equals(BanItemConfig.getBaseIdentifier(ci.id()));
            boolean edited = isEditedItem(ci);
            KineticTheme.itemSlot(g, x, y, SLOT_SIZE, 4, false);
            ItemBanControl.withSkip(() -> {
                KineticTheme.item(g, ci.stack(), x, y, SLOT_SIZE, 1.0F, true);
                return null;
            });
            if (selected) {
                KineticTheme.stateOutline(g, x, y, SLOT_SIZE, SLOT_SIZE, true, false, false);
            } else if (hovered) {
                KineticTheme.stateOutline(g, x, y, SLOT_SIZE, SLOT_SIZE, false, true, false);
            } else if (edited) {
                KineticTheme.indicatorOutline(g, x, y, SLOT_SIZE, SLOT_SIZE, KineticTheme.Indicator.SUCCESS);
            }
        }
        });
        g.endScissor();

        g.scissor(rightX, tagListY, rightX + rightW, tagListY + tagListH);
        int y = tagListY - (int) Math.round(tagScroll.smoothOffset());
        for (TagEntry entry : displayedTags) {
            if (y + TAG_ROW_PITCH - 2 > tagListY && y < tagListY + tagListH) {
                boolean rowHovered = smx >= rightX && smx < rightX + rightW && smy >= Math.max(y, tagListY) && smy < Math.min(y + TAG_ROW_PITCH, tagListY + tagListH);
                if (rowHovered && entry.source == TagSource.MANUAL) hoveredDelete = () -> removeManualTag(entry.tag);
                if (rowHovered && (entry.source == TagSource.NATIVE || entry.source == TagSource.REMOVED)) hoveredDelete = () -> toggleRemovedTag(entry.tag);
                g.outline(rightX, y, rightW - TAG_SCROLLBAR_INSET - 3, TAG_ROW_PITCH - 2, entry.source == TagSource.REMOVED ? 0xFFFF5555 : 0xFFFFFFFF);
                Component prefix = KineticI18n.translatable(entry.source.key);
                Component name = entry.tag.equals(BanItemConfig.ALL_TAGS)
                        ? KineticI18n.translatable("gui.itemcontrol.item.item_tag.all_native")
                        : Component.literal(" #" + entry.tag);
                g.scrollingText(prefix.copy().append(name), rightX + 3, y + 4, rightW - 6 - TAG_SCROLLBAR_INSET - TEXT_GAP, 0xFFFFFF, false);
            }
            y += TAG_ROW_PITCH;
        }
        g.endScissor();

        // Scrollbars first: the suggestion dropdown opens over the tag list and must cover its scrollbar.
        itemScroll.render(g, smx, smy, leftX + leftW - 6, leftY, 4, leftH, 20);
        tagScroll.render(g, smx, smy, rightX + rightW - TAG_SCROLLBAR_INSET, tagListY, 4, tagListH, 20);

        int suggestionY = tagInput == null ? 0 : tagInput.controlY() + tagInput.controlHeight() + 1;
        if (!tagSuggestions.isEmpty() && tagInput != null && isFocused(tagInput)) {
            int maxRows = Math.min(8, tagSuggestions.size());
            int boxH = maxRows * 12;
            suggestionScroll.update(tagSuggestions.size() * 12, boxH);
            double visual = suggestionScroll.smoothOffset();
            int first = Math.max(0, (int) Math.floor(visual / 12D));
            int last = Math.min(tagSuggestions.size(), first + maxRows + 1);
            // The dropdown covers the text below it, so it gets an opaque backing and its own outline, 2 px clear of its rows.
            g.push();
            g.raise(1);
            g.fill(rightX, suggestionY - 1, rightX + rightW, suggestionY + boxH + 2, 0xFF161616);
            KineticTheme.panelAlt(g, rightX, suggestionY - 1, rightW, boxH + 3);
            g.scissor(rightX, suggestionY, rightX + rightW, suggestionY + boxH);
            for (int i = first; i < last; i++) {
                String suggestion = tagSuggestions.get(i);
                int sy = suggestionY + i * 12 - (int) Math.round(visual);
                boolean hovered = smx >= rightX && smx < rightX + rightW && smy >= sy && smy < sy + 12;
                if (hovered) KineticTheme.stateSurface(g, rightX, sy, rightW, 12, KineticTheme.Surface.PANEL_ALT, false, true, false);
                g.scrollingText(Component.literal(suggestion), rightX + 3, sy + 2, rightW - 3 - TEXT_GAP, 0xFFFFFF, false);
            }
            g.endScissor();
            g.pop();
        }

    }

    @Override
    protected void renderTooltips(int smx, int smy) {
        if (isHoveringButton(copyButton, smx, smy)) {
            showTooltip(KineticI18n.translatable("gui.itemcontrol.item.item_tag.tooltip.copy"));
            return;
        }
        if (isHoveringButton(saveButton, smx, smy)) {
            showTooltip(KineticI18n.translatable("gui.itemcontrol.item.item_tag.tooltip.save"));
            return;
        }
        if (isHoveringButton(backButton, smx, smy)) {
            showTooltip(KineticI18n.translatable("gui.itemcontrol.item.item_tag.tooltip.back"));
            return;
        }
        if (isHoveringButton(addTagButton, smx, smy)) {
            showTooltip(KineticI18n.translatable("gui.itemcontrol.item.item_tag.tooltip.add"));
            return;
        }
        if (isHoveringEditBox(itemSearch, smx, smy)) {
            showTooltip(KineticI18n.translatable("gui.itemcontrol.item.item_tag.tooltip.item_search"));
            return;
        }
        if (isHoveringEditBox(tagInput, smx, smy)) {
            showTooltip(KineticI18n.translatable("gui.itemcontrol.item.item_tag.tooltip.input"));
            return;
        }

        if (smx >= rightX && smx < rightX + rightW && smy >= tagListY && smy < tagListY + tagListH) {
            int idx = (int) ((smy - tagListY + Math.round(tagScroll.smoothOffset())) / TAG_ROW_PITCH);
            if (idx >= 0 && idx < displayedTags.size()) {
                TagEntry entry = displayedTags.get(idx);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("#" + entry.tag));
                tooltip.add(KineticI18n.translatable(switch (entry.source) {
                    case NATIVE -> "gui.itemcontrol.item.item_tag.tooltip.source.native";
                    case MERGED -> "gui.itemcontrol.item.item_tag.tooltip.source.merged";
                    case MANUAL -> "gui.itemcontrol.item.item_tag.tooltip.source.manual";
                    case REMOVED -> "gui.itemcontrol.item.item_tag.tooltip.source.removed";
                }));
                tooltip.add(KineticI18n.translatable(switch (entry.source) {
                    case NATIVE -> "gui.itemcontrol.item.item_tag.tooltip.action.native";
                    case MERGED -> "gui.itemcontrol.item.item_tag.tooltip.action.merged";
                    case MANUAL -> "gui.itemcontrol.item.item_tag.tooltip.action.manual";
                    case REMOVED -> "gui.itemcontrol.item.item_tag.tooltip.action.removed";
                }));
                showTooltip(tooltip);
                return;
            }
        }

        if (smx >= leftX && smx < leftX + leftW && smy >= leftY && smy < leftY + leftH) {
            int localX = smx - leftX;
            int localY = (int) Math.floor(smy - leftY + Math.round(itemScroll.smoothOffset()));
            int col = localX / SLOT_PITCH;
            int row = localY / SLOT_PITCH;
            int idx = row * leftCols + col;
            if (col >= 0 && col < leftCols && localX % SLOT_PITCH < SLOT_SIZE && localY % SLOT_PITCH < SLOT_SIZE && idx >= 0 && idx < filteredItems.size()) {
                KineticItemSearch.CachedItem ci = filteredItems.get(idx);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(ItemCacheHudRenderer.getDisplayNameCustom(ci.stack()));
                tooltip.add(Component.literal(BanItemConfig.getBaseIdentifier(ci.id())));
                tooltip.add(KineticI18n.translatable(copyMode ? "gui.itemcontrol.item.item_tag.tooltip.copy_source" : "gui.itemcontrol.item.item_tag.tooltip.select"));
                if (isEditedItem(ci)) {
                    tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.item_tag.tooltip.edited"));
                }
                if (ci.stack().isEmpty()) showTooltip(tooltip);
                else showItemTooltip(ci.stack(), tooltip.subList(1, tooltip.size()));
            }
        }
    }

    private boolean isHoveringButton(KineticButton button, double mx, double my) {
        return button != null
                && button.controlVisible()
                && button.contains(mx, my);
    }

    private boolean isHoveringEditBox(KineticTextField box, double mx, double my) {
        return box != null
                && box.controlVisible()
                && box.contains(mx, my);
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 canvasMouseClicked 全部在控件之前处理（候选列表覆盖在控件上方）
        // The old canvasMouseClicked handled all of this before controls (the suggestion list overlays controls).
        double smx = input.x();
        double smy = input.y();
        if (itemScroll.beginDrag(smx, smy, input.button(), leftX + leftW - 6, leftY, 4, leftH, 20, 0)) return true;
        if (tagScroll.beginDrag(smx, smy, input.button(), rightX + rightW - TAG_SCROLLBAR_INSET, tagListY, 4, tagListH, 20, 0)) return true;

        if (tagInput != null && isFocused(tagInput) && !tagSuggestions.isEmpty()) {
            int suggestionY = tagInput.controlY() + tagInput.controlHeight() + 1;
            if (smx >= rightX && smx < rightX + rightW && smy >= suggestionY) {
                int row = (int) Math.floor((smy - suggestionY + Math.round(suggestionScroll.smoothOffset())) / 12D);
                int idx = row;
                if (smy < suggestionY + Math.min(8, tagSuggestions.size()) * 12 && idx >= 0 && idx < tagSuggestions.size()) {
                    String tag = normalizeTag(tagSuggestions.get(idx));
                    if (tag != null) addManualTag(tag);
                    tagInput.setTextValue("");
                    return true;
                }
            }
        }

        if (smx >= leftX && smx < leftX + leftW && smy >= leftY && smy < leftY + leftH) {
            int localX = (int) (smx - leftX);
            int localY = (int) (smy - leftY + Math.round(itemScroll.smoothOffset()));
            int col = localX / SLOT_PITCH;
            int row = localY / SLOT_PITCH;
            int idx = row * leftCols + col;
            if (input.isLeft() && col >= 0 && col < leftCols && localX % SLOT_PITCH < SLOT_SIZE && localY % SLOT_PITCH < SLOT_SIZE && idx >= 0 && idx < filteredItems.size()) {
                selectItem(filteredItems.get(idx));
                return true;
            }
        }

        if (smx >= rightX && smx < rightX + rightW && smy >= tagListY && smy < tagListY + tagListH) {
            int idx = (int) ((smy - tagListY + Math.round(tagScroll.smoothOffset())) / TAG_ROW_PITCH);
            if (idx >= 0 && idx < displayedTags.size()) {
                TagEntry entry = displayedTags.get(idx);
                if (input.isRight() && (entry.source == TagSource.NATIVE || entry.source == TagSource.REMOVED)) {
                    toggleRemovedTag(entry.tag);
                    return true;
                }
                if (input.isRight() && entry.source == TagSource.MANUAL) {
                    removeManualTag(entry.tag);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        boolean handled = itemScroll.release(input.button()) | tagScroll.release(input.button());
        return handled;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double smy = input.y();
        if (itemScroll.drag(smy, leftY, leftH, 20)) return true;
        if (tagScroll.drag(smy, tagListY, tagListH, 20)) return true;
        return false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double smx = input.x();
        double smy = input.y();
        double delta = input.deltaY();
        if (tagInput != null && isFocused(tagInput) && !tagSuggestions.isEmpty()) {
            int suggestionY = tagInput.controlY() + tagInput.controlHeight() + 1;
            int visibleRows = Math.min(8, tagSuggestions.size());
            int boxH = visibleRows * 12;
            if (smx >= rightX && smx < rightX + rightW && smy >= suggestionY && smy < suggestionY + boxH) {
                suggestionScroll.update(tagSuggestions.size() * 12, boxH);
                return suggestionScroll.scroll(delta, 12D);
            }
        }
        if (smx >= leftX && smx < leftX + leftW && smy >= leftY && smy < leftY + leftH) {
            return itemScroll.scroll(delta, SLOT_PITCH);
        }
        if (smx >= rightX && smx < rightX + rightW && smy >= tagListY && smy < tagListY + tagListH) {
            return tagScroll.scroll(delta, TAG_ROW_PITCH);
        }
        return false;
    }

    private enum TagSource {
        NATIVE("gui.itemcontrol.item.item_tag.source.native"),
        MERGED("gui.itemcontrol.item.item_tag.source.merged"),
        MANUAL("gui.itemcontrol.item.item_tag.source.manual"),
        REMOVED("gui.itemcontrol.item.item_tag.source.removed");

        private final String key;

        TagSource(String key) {
            this.key = key;
        }
    }

    private record TagEntry(String tag, TagSource source) {
    }
}
