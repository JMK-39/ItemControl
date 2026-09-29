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
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.gui.widget.list.*;

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
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_PITCH = 19;
    private static final int PLACEHOLDER_TEXT_COLOR = 0xBFFFFFFF;
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
    private int guideY;
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
        int pad = isCompactLayout() ? 6 : 10;
        int gap = 8;
        int topY = 6;
        int toolbarH = 20;
        int usableW = Math.max(220, width() - pad * 2 - gap);
        leftW = Math.max(150, (int) (usableW * 0.56f));
        rightW = Math.max(120, usableW - leftW);
        leftX = pad;
        rightX = leftX + leftW + gap;

        itemSearch = ui().textField(leftX, topY, leftW).build();
        itemSearch.setPlaceholder(KineticI18n.translatable("gui.itemcontrol.item.item_tag.item_search_hint"));
        itemSearch.setTextValue(rememberedItemSearch);
        itemSearch.setDefaultText(rememberedItemSearch);
        itemSearch.onTextChange(value -> {
            rememberedItemSearch = value == null ? "" : value;
            refreshItems();
        });

        int buttonGap = 4;
        int smallButtonW = Math.max(42, (rightW - buttonGap * 3) / 4);
        copyButton = ui().button(rightX, topY, smallButtonW).text(copyButtonText()).onClick(() -> {
            if (selectedItemId.isEmpty()) return;
            copyMode = !copyMode;
            copyButton.setText(copyButtonText());
        }).build();

        saveButton = ui().button(rightX + smallButtonW + buttonGap, topY, smallButtonW).text(KineticI18n.translatable("gui.itemcontrol.item.item_tag.save")).onClick(this::saveConfig).build();

        backButton = ui().button(rightX + (smallButtonW + buttonGap) * 2, topY, smallButtonW).text(KineticI18n.translatable("gui.itemcontrol.item.item_tag.back")).onClick(this::close).build();

        addTagButton = ui().button(rightX + (smallButtonW + buttonGap) * 3, topY, smallButtonW).text(KineticI18n.translatable("gui.itemcontrol.item.item_tag.add")).onClick(this::addTagFromInput).build();

        guideY = topY + toolbarH + 5;
        leftY = guideY + KineticText.lineHeight() + 7;
        leftH = Math.max(80, height() - leftY - pad);
        leftCols = Math.max(1, (leftW - 8 + SLOT_PITCH - SLOT_SIZE) / SLOT_PITCH);

        tagInput = ui().textField(rightX, leftY, rightW).build();
        tagInput.setPlaceholder(KineticI18n.translatable("gui.itemcontrol.item.item_tag.tag_input_hint"));
        tagInput.setTextValue(rememberedTagSearch);
        tagInput.setDefaultText(rememberedTagSearch);
        tagInput.onTextChange(value -> {
            rememberedTagSearch = value == null ? "" : value;
            refreshTagSuggestions();
        });

        rightY = leftY + toolbarH + 6;
        rightH = Math.max(60, height() - rightY - pad);
        tagListY = rightY + KineticText.lineHeight() + 6;
        tagListH = Math.max(36, rightH - KineticText.lineHeight() - 6);

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
        return !id.isEmpty() && !BanItemConfig.getConfiguredAddedTagIds(id).isEmpty();
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

        for (String tag : nativeTags) displayedTags.add(new TagEntry(tag, TagSource.NATIVE));
        for (String tag : mergedTags) displayedTags.add(new TagEntry(tag, TagSource.MERGED));
        for (String tag : manualTags) displayedTags.add(new TagEntry(tag, TagSource.MANUAL));
        tagScroll.update(displayedTags.size() * 14, tagListH);
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
        KineticTheme.canvasBackground(g, width(), height());
        KineticTheme.panelAlt(g, leftX - 3, leftY - 3, leftW + 6, leftH + 6);
        KineticTheme.panelAlt(g, rightX - 3, rightY - 3, rightW + 6, rightH + 6);
    }

    @Override
    protected void renderForeground(KineticGraphics g, int smx, int smy, float pt) {
        g.text(KineticI18n.translatable("gui.itemcontrol.item.item_tag.guide"), leftX, guideY, 0xFFFFFF, false);

        g.scissor(leftX, leftY, leftX + leftW, leftY + leftH);
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
        g.endScissor();

        Component selectedText = selectedItemId.isEmpty()
                ? KineticI18n.translatable("gui.itemcontrol.item.item_tag.no_selection")
                : copyMode
                ? KineticI18n.translatable("gui.itemcontrol.item.item_tag.copy_mode_hint", selectedItemId)
                : KineticI18n.translatable("gui.itemcontrol.item.item_tag.selected", selectedItemId);
        g.text(selectedText, rightX + 2, rightY, 0xFFFFFF, false);

        g.scissor(rightX, tagListY, rightX + rightW, tagListY + tagListH);
        int y = tagListY - (int) Math.round(tagScroll.smoothOffset());
        for (TagEntry entry : displayedTags) {
            if (y + 13 > tagListY && y < tagListY + tagListH) {
                Component prefix = KineticI18n.translatable(entry.source.key);
                g.text(prefix.copy().append(" #" + entry.tag), rightX + 3, y + 2, 0xFFFFFF, false);
            }
            y += 14;
        }
        g.endScissor();

        int suggestionY = tagInput == null ? 0 : tagInput.controlY() + tagInput.controlHeight() + 1;
        if (!tagSuggestions.isEmpty() && tagInput != null && isFocused(tagInput)) {
            int maxRows = Math.min(8, tagSuggestions.size());
            int boxH = maxRows * 12;
            suggestionScroll.update(tagSuggestions.size() * 12, boxH);
            double visual = suggestionScroll.smoothOffset();
            int first = Math.max(0, (int) Math.floor(visual / 12D));
            int last = Math.min(tagSuggestions.size(), first + maxRows + 1);
            KineticTheme.surface(g, rightX, suggestionY, rightW, boxH, KineticTheme.Surface.PANEL_ALT);
            g.scissor(rightX, suggestionY, rightX + rightW, suggestionY + boxH);
            for (int i = first; i < last; i++) {
                String suggestion = tagSuggestions.get(i);
                int sy = suggestionY + (int) Math.round(i * 12D - visual);
                boolean hovered = smx >= rightX && smx < rightX + rightW && smy >= sy && smy < sy + 12;
                if (hovered) KineticTheme.stateSurface(g, rightX, sy, rightW, 12, KineticTheme.Surface.PANEL_ALT, false, true, false);
                g.text(suggestion, rightX + 3, sy + 2, 0xFFFFFF, false);
            }
            g.endScissor();
        }

        itemScroll.render(g, smx, smy, leftX + leftW - 6, leftY, 4, leftH, 20);
        tagScroll.render(g, smx, smy, rightX + rightW - 6, tagListY, 4, tagListH, 20);
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
            int idx = (int) ((smy - tagListY + tagScroll.smoothOffset()) / 14);
            if (idx >= 0 && idx < displayedTags.size()) {
                TagEntry entry = displayedTags.get(idx);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("#" + entry.tag));
                tooltip.add(KineticI18n.translatable(switch (entry.source) {
                    case NATIVE -> "gui.itemcontrol.item.item_tag.tooltip.source.native";
                    case MERGED -> "gui.itemcontrol.item.item_tag.tooltip.source.merged";
                    case MANUAL -> "gui.itemcontrol.item.item_tag.tooltip.source.manual";
                }));
                tooltip.add(KineticI18n.translatable(switch (entry.source) {
                    case NATIVE -> "gui.itemcontrol.item.item_tag.tooltip.action.native";
                    case MERGED -> "gui.itemcontrol.item.item_tag.tooltip.action.merged";
                    case MANUAL -> "gui.itemcontrol.item.item_tag.tooltip.action.manual";
                }));
                showTooltip(tooltip);
                return;
            }
        }

        if (smx >= leftX && smx < leftX + leftW && smy >= leftY && smy < leftY + leftH) {
            int localX = smx - leftX;
            int localY = (int) Math.floor(smy - leftY + itemScroll.smoothOffset());
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
                showTooltip(tooltip);
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
        if (tagScroll.beginDrag(smx, smy, input.button(), rightX + rightW - 6, tagListY, 4, tagListH, 20, 0)) return true;

        if (tagInput != null && isFocused(tagInput) && !tagSuggestions.isEmpty()) {
            int suggestionY = tagInput.controlY() + tagInput.controlHeight() + 1;
            if (smx >= rightX && smx < rightX + rightW && smy >= suggestionY) {
                int row = (int) Math.floor((smy - suggestionY + suggestionScroll.smoothOffset()) / 12D);
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
            int localY = (int) (smy - leftY + itemScroll.smoothOffset());
            int col = localX / SLOT_PITCH;
            int row = localY / SLOT_PITCH;
            int idx = row * leftCols + col;
            if (input.isLeft() && col >= 0 && col < leftCols && localX % SLOT_PITCH < SLOT_SIZE && localY % SLOT_PITCH < SLOT_SIZE && idx >= 0 && idx < filteredItems.size()) {
                selectItem(filteredItems.get(idx));
                return true;
            }
        }

        if (smx >= rightX && smx < rightX + rightW && smy >= tagListY && smy < tagListY + tagListH) {
            int idx = (int) ((smy - tagListY + tagScroll.smoothOffset()) / 14);
            if (idx >= 0 && idx < displayedTags.size()) {
                TagEntry entry = displayedTags.get(idx);
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
            return tagScroll.scroll(delta, 14D);
        }
        return false;
    }

    private enum TagSource {
        NATIVE("gui.itemcontrol.item.item_tag.source.native"),
        MERGED("gui.itemcontrol.item.item_tag.source.merged"),
        MANUAL("gui.itemcontrol.item.item_tag.source.manual");

        private final String key;

        TagSource(String key) {
            this.key = key;
        }
    }

    private record TagEntry(String tag, TagSource source) {
    }
}
