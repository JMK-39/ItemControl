package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.search.ItemSearchIndex;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.itemcontrol.item.util.ItemBanControl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class ItemTagEditorScreen extends KineticScreen {
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_PITCH = 19;
    private static final int EDITED_OUTLINE_COLOR = 0xFF55FF55;
    private static final int SELECTED_OUTLINE_COLOR = 0xFFFFFF55;
    private static final int HOVER_OUTLINE_COLOR = 0xFF55AAFF;
    private static final int PLACEHOLDER_TEXT_COLOR = 0xBFFFFFFF;
    private static String rememberedItemSearch = "";
    private static String rememberedTagSearch = "";
    private static String rememberedSelectedItem = "";

    private final Screen parent;
    private final List<ItemSearchIndex.CachedItem> allItems;
    private final List<String> allTags;
    private final GridScrollController itemScroll = new GridScrollController();
    private final GridScrollController tagScroll = new GridScrollController();
    private final GridScrollController suggestionScroll = new GridScrollController();

    private EditBox itemSearch;
    private EditBox tagInput;
    private Button addTagButton;
    private Button copyButton;
    private Button saveButton;
    private Button backButton;

    private List<ItemSearchIndex.CachedItem> filteredItems = new ArrayList<>();
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

    public ItemTagEditorScreen(Screen parent) {
        super(Component.translatable("gui.itemcontrol.item.item_tag.title"));
        this.parent = parent;
        dev.xyat.kineticcore.api.client.screen.GuiSession.setParent(this, parent);
        configureDraft(BanItemConfig::snapshotData, this::restoreTagSnapshot);
        useFluidCanvas(640f, 360f, 4);
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
    protected void buildUi() {
        int pad = isCompactLayout() ? 6 : 10;
        int gap = 8;
        int topY = 6;
        int toolbarH = 20;
        int usableW = Math.max(220, canvasWidth - pad * 2 - gap);
        leftW = Math.max(150, (int) (usableW * 0.56f));
        rightW = Math.max(120, usableW - leftW);
        leftX = pad;
        rightX = leftX + leftW + gap;

        itemSearch = new EditBox(font, leftX, topY, leftW, toolbarH, Component.empty());
        itemSearch.setValue(rememberedItemSearch);
        itemSearch.setResponder(value -> {
            rememberedItemSearch = value == null ? "" : value;
            refreshItems();
        });
        addRenderableWidget(itemSearch);

        int buttonGap = 4;
        int smallButtonW = Math.max(42, (rightW - buttonGap * 3) / 4);
        copyButton = Button.builder(copyButtonText(), button -> {
            if (selectedItemId.isEmpty()) return;
            copyMode = !copyMode;
            button.setMessage(copyButtonText());
        }).bounds(rightX, topY, smallButtonW, toolbarH).build();
        addRenderableWidget(copyButton);

        saveButton = Button.builder(Component.translatable("gui.itemcontrol.item.item_tag.save"), button -> saveConfig())
                .bounds(rightX + smallButtonW + buttonGap, topY, smallButtonW, toolbarH).build();
        addRenderableWidget(saveButton);

        backButton = Button.builder(Component.translatable("gui.itemcontrol.item.item_tag.back"), button -> onClose())
                .bounds(rightX + (smallButtonW + buttonGap) * 2, topY, smallButtonW, toolbarH).build();
        addRenderableWidget(backButton);

        addTagButton = Button.builder(Component.translatable("gui.itemcontrol.item.item_tag.add"), button -> addTagFromInput())
                .bounds(rightX + (smallButtonW + buttonGap) * 3, topY, smallButtonW, toolbarH).build();
        addRenderableWidget(addTagButton);

        guideY = topY + toolbarH + 5;
        leftY = guideY + font.lineHeight + 7;
        leftH = Math.max(80, canvasHeight - leftY - pad);
        leftCols = Math.max(1, (leftW - 8 + SLOT_PITCH - SLOT_SIZE) / SLOT_PITCH);

        tagInput = new EditBox(font, rightX, leftY, rightW, toolbarH, Component.empty());
        tagInput.setValue(rememberedTagSearch);
        tagInput.setResponder(value -> {
            rememberedTagSearch = value == null ? "" : value;
            refreshTagSuggestions();
        });
        addRenderableWidget(tagInput);

        rightY = leftY + toolbarH + 6;
        rightH = Math.max(60, canvasHeight - rightY - pad);
        tagListY = rightY + font.lineHeight + 6;
        tagListH = Math.max(36, rightH - font.lineHeight - 6);

        refreshItems();
        refreshTagEntries();
        refreshTagSuggestions();
        refreshButtons();
    }

    private Component copyButtonText() {
        return Component.translatable(copyMode ? "gui.itemcontrol.item.item_tag.copy_select" : "gui.itemcontrol.item.item_tag.copy");
    }

    private void refreshButtons() {
        boolean hasSelection = !selectedItemId.isEmpty();
        if (copyButton != null) copyButton.active = hasSelection;
        if (addTagButton != null) addTagButton.active = hasSelection && normalizeTag(tagInput == null ? "" : tagInput.getValue()) != null;
    }

    private void refreshItems() {
        String query = itemSearch == null ? "" : itemSearch.getValue().trim().toLowerCase(Locale.ROOT);
        filteredItems = new ArrayList<>(ItemSearchCache.searchItems("item_tag_editor", allItems, query, ItemSearchCache.getAllItemsHash()));
        filteredItems.sort((left, right) -> Boolean.compare(isEditedItem(right), isEditedItem(left)));
        int rows = (int) Math.ceil((double) filteredItems.size() / Math.max(1, leftCols));
        itemScroll.update(rows * SLOT_PITCH, leftH);
    }

    private boolean isEditedItem(ItemSearchIndex.CachedItem item) {
        if (item == null || item.idStr == null || item.idStr.isBlank()) return false;
        String id = BanItemConfig.getBaseIdentifier(item.idStr);
        return !id.isEmpty() && !BanItemConfig.getConfiguredAddedTagIds(id).isEmpty();
    }

    private void refreshTagSuggestions() {
        if (tagInput == null) return;
        String query = tagInput.getValue().trim().toLowerCase(Locale.ROOT);
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

    private void selectItem(ItemSearchIndex.CachedItem item) {
        if (item == null || item.idStr == null || item.idStr.isBlank()) return;
        String id = BanItemConfig.getBaseIdentifier(item.idStr);
        if (id.isEmpty()) return;
        if (copyMode && !selectedItemId.isEmpty()) {
            if (!selectedItemId.equals(id)) copyTagsFrom(id);
            copyMode = false;
            if (copyButton != null) copyButton.setMessage(copyButtonText());
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
        String tag = normalizeTag(tagInput.getValue());
        if (tag == null) return;
        addManualTag(tag);
        tagInput.setValue("");
        tagInput.setFocused(true);
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
    public void onClose() {
        super.onClose();
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int smx, int smy, float pt) {
        g.fillGradient(0, 0, canvasWidth, canvasHeight, 0xFF222222, 0xFF111111);
        g.fill(leftX - 3, leftY - 3, leftX + leftW + 3, leftY + leftH + 3, 0xFF000000);
        g.fill(leftX - 2, leftY - 2, leftX + leftW + 2, leftY + leftH + 2, 0xFF2A2A2A);
        g.fill(rightX - 3, rightY - 3, rightX + rightW + 3, rightY + rightH + 3, 0xFF000000);
        g.fill(rightX - 2, rightY - 2, rightX + rightW + 2, rightY + rightH + 2, 0xFF2A2A2A);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int smx, int smy, float pt) {
        g.drawString(font, Component.translatable("gui.itemcontrol.item.item_tag.guide"), leftX, guideY, 0xFFFFFF, false);

        if (itemSearch != null && itemSearch.getValue().isEmpty() && !itemSearch.isFocused()) {
            g.drawString(font, Component.translatable("gui.itemcontrol.item.item_tag.item_search_hint"), itemSearch.getX() + 6, itemSearch.getY() + 6, PLACEHOLDER_TEXT_COLOR, false);
        }
        if (tagInput != null && tagInput.getValue().isEmpty() && !tagInput.isFocused()) {
            g.drawString(font, Component.translatable("gui.itemcontrol.item.item_tag.tag_input_hint"), tagInput.getX() + 6, tagInput.getY() + 6, PLACEHOLDER_TEXT_COLOR, false);
        }

        enableCanvasScissor(g, leftX, leftY, leftX + leftW, leftY + leftH);
        for (int i = 0; i < filteredItems.size(); i++) {
            ItemSearchIndex.CachedItem ci = filteredItems.get(i);
            int col = i % leftCols;
            int row = i / leftCols;
            int x = leftX + col * SLOT_PITCH;
            int y = leftY + row * SLOT_PITCH - (int) Math.round(itemScroll.smoothOffset());
            if (y + SLOT_SIZE <= leftY || y >= leftY + leftH) continue;
            boolean hovered = smx >= x && smx < x + SLOT_SIZE && smy >= y && smy < y + SLOT_SIZE;
            boolean selected = selectedItemId.equals(BanItemConfig.getBaseIdentifier(ci.idStr));
            boolean edited = isEditedItem(ci);
            GuiTheme.itemSlot(g, x, y, SLOT_SIZE, 4, false);
            ItemBanControl.withSkip(() -> {
                GuiTheme.item(g, font, ci.stack, x, y, SLOT_SIZE, 1.0F, true);
                return null;
            });
            int outlineColor = selected
                    ? SELECTED_OUTLINE_COLOR
                    : hovered
                    ? HOVER_OUTLINE_COLOR
                    : edited
                    ? EDITED_OUTLINE_COLOR
                    : 0;
            if (outlineColor != 0) g.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, outlineColor);
        }
        g.disableScissor();

        Component selectedText = selectedItemId.isEmpty()
                ? Component.translatable("gui.itemcontrol.item.item_tag.no_selection")
                : copyMode
                ? Component.translatable("gui.itemcontrol.item.item_tag.copy_mode_hint", selectedItemId)
                : Component.translatable("gui.itemcontrol.item.item_tag.selected", selectedItemId);
        g.drawString(font, selectedText, rightX + 2, rightY, 0xFFFFFF, false);

        enableCanvasScissor(g, rightX, tagListY, rightX + rightW, tagListY + tagListH);
        int y = tagListY - (int) Math.round(tagScroll.smoothOffset());
        for (TagEntry entry : displayedTags) {
            if (y + 13 > tagListY && y < tagListY + tagListH) {
                Component prefix = Component.translatable(entry.source.key);
                g.drawString(font, prefix.copy().append(" #" + entry.tag), rightX + 3, y + 2, 0xFFFFFF, false);
            }
            y += 14;
        }
        g.disableScissor();

        int suggestionY = tagInput == null ? 0 : tagInput.getY() + tagInput.getHeight() + 1;
        if (!tagSuggestions.isEmpty() && tagInput != null && tagInput.isFocused()) {
            int maxRows = Math.min(8, tagSuggestions.size());
            int boxH = maxRows * 12;
            suggestionScroll.update(tagSuggestions.size() * 12, boxH);
            double visual = suggestionScroll.smoothOffset();
            int first = Math.max(0, (int) Math.floor(visual / 12D));
            int last = Math.min(tagSuggestions.size(), first + maxRows + 1);
            g.fill(rightX, suggestionY, rightX + rightW, suggestionY + boxH, 0xEE111111);
            enableCanvasScissor(g, rightX, suggestionY, rightX + rightW, suggestionY + boxH);
            for (int i = first; i < last; i++) {
                String suggestion = tagSuggestions.get(i);
                int sy = suggestionY + (int) Math.round(i * 12D - visual);
                boolean hovered = smx >= rightX && smx < rightX + rightW && smy >= sy && smy < sy + 12;
                if (hovered) g.fill(rightX, sy, rightX + rightW, sy + 12, 0x66555555);
                g.drawString(font, suggestion, rightX + 3, sy + 2, 0xFFFFFF, false);
            }
            g.disableScissor();
        }

        GuiTheme.scrollbar(itemScroll, g, smx, smy, leftX + leftW - 6, leftY, 4, leftH, 20);
        GuiTheme.scrollbar(tagScroll, g, smx, smy, rightX + rightW - 6, tagListY, 4, tagListH, 20);
    }

    @Override
    protected void renderTooltips(GuiGraphics g, int smx, int smy, int mx, int my) {
        if (isHoveringButton(copyButton, smx, smy)) {
            GuiOverlay.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.copy"), mx, my);
            return;
        }
        if (isHoveringButton(saveButton, smx, smy)) {
            GuiOverlay.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.save"), mx, my);
            return;
        }
        if (isHoveringButton(backButton, smx, smy)) {
            GuiOverlay.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.back"), mx, my);
            return;
        }
        if (isHoveringButton(addTagButton, smx, smy)) {
            GuiOverlay.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.add"), mx, my);
            return;
        }
        if (isHoveringEditBox(itemSearch, smx, smy)) {
            GuiOverlay.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.item_search"), mx, my);
            return;
        }
        if (isHoveringEditBox(tagInput, smx, smy)) {
            GuiOverlay.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.input"), mx, my);
            return;
        }

        if (smx >= rightX && smx < rightX + rightW && smy >= tagListY && smy < tagListY + tagListH) {
            int idx = (int) ((smy - tagListY + tagScroll.smoothOffset()) / 14);
            if (idx >= 0 && idx < displayedTags.size()) {
                TagEntry entry = displayedTags.get(idx);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("#" + entry.tag));
                tooltip.add(Component.translatable(switch (entry.source) {
                    case NATIVE -> "gui.itemcontrol.item.item_tag.tooltip.source.native";
                    case MERGED -> "gui.itemcontrol.item.item_tag.tooltip.source.merged";
                    case MANUAL -> "gui.itemcontrol.item.item_tag.tooltip.source.manual";
                }));
                tooltip.add(Component.translatable(switch (entry.source) {
                    case NATIVE -> "gui.itemcontrol.item.item_tag.tooltip.action.native";
                    case MERGED -> "gui.itemcontrol.item.item_tag.tooltip.action.merged";
                    case MANUAL -> "gui.itemcontrol.item.item_tag.tooltip.action.manual";
                }));
                GuiOverlay.requestTooltip(tooltip, mx, my);
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
                ItemSearchIndex.CachedItem ci = filteredItems.get(idx);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(ItemCacheHudRenderer.getDisplayNameCustom(ci.stack));
                tooltip.add(Component.literal(BanItemConfig.getBaseIdentifier(ci.idStr)));
                tooltip.add(Component.translatable(copyMode ? "gui.itemcontrol.item.item_tag.tooltip.copy_source" : "gui.itemcontrol.item.item_tag.tooltip.select"));
                if (isEditedItem(ci)) {
                    tooltip.add(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.edited"));
                }
                GuiOverlay.requestTooltip(tooltip, mx, my);
            }
        }
    }

    private boolean isHoveringButton(Button button, double mx, double my) {
        return button != null
                && button.visible
                && mx >= button.getX()
                && mx < button.getX() + button.getWidth()
                && my >= button.getY()
                && my < button.getY() + button.getHeight();
    }

    private boolean isHoveringEditBox(EditBox box, double mx, double my) {
        return box != null
                && box.visible
                && mx >= box.getX()
                && mx < box.getX() + box.getWidth()
                && my >= box.getY()
                && my < box.getY() + box.getHeight();
    }

    @Override
    protected boolean canvasMouseClicked(double smx, double smy, int btn) {
        if (btn == 0 && itemScroll.beginDrag(smx, smy, leftX + leftW - 6, leftY, 4, leftH, 20, 0)) return true;
        if (btn == 0 && tagScroll.beginDrag(smx, smy, rightX + rightW - 6, tagListY, 4, tagListH, 20, 0)) return true;

        if (tagInput != null && tagInput.isFocused() && !tagSuggestions.isEmpty()) {
            int suggestionY = tagInput.getY() + tagInput.getHeight() + 1;
            if (smx >= rightX && smx < rightX + rightW && smy >= suggestionY) {
                int row = (int) Math.floor((smy - suggestionY + suggestionScroll.smoothOffset()) / 12D);
                int idx = row;
                if (smy < suggestionY + Math.min(8, tagSuggestions.size()) * 12 && idx >= 0 && idx < tagSuggestions.size()) {
                    String tag = normalizeTag(tagSuggestions.get(idx));
                    if (tag != null) addManualTag(tag);
                    tagInput.setValue("");
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
            if (btn == 0 && col >= 0 && col < leftCols && localX % SLOT_PITCH < SLOT_SIZE && localY % SLOT_PITCH < SLOT_SIZE && idx >= 0 && idx < filteredItems.size()) {
                selectItem(filteredItems.get(idx));
                return true;
            }
        }

        if (smx >= rightX && smx < rightX + rightW && smy >= tagListY && smy < tagListY + tagListH) {
            int idx = (int) ((smy - tagListY + tagScroll.smoothOffset()) / 14);
            if (idx >= 0 && idx < displayedTags.size()) {
                TagEntry entry = displayedTags.get(idx);
                if (btn == 1 && entry.source == TagSource.MANUAL) {
                    removeManualTag(entry.tag);
                    return true;
                }
            }
        }
        return super.canvasMouseClicked(smx, smy, btn);
    }

    @Override
    protected boolean canvasMouseReleased(double smx, double smy, int btn) {
        boolean handled = itemScroll.release(btn) | tagScroll.release(btn);
        return handled || super.canvasMouseReleased(smx, smy, btn);
    }

    @Override
    protected boolean canvasMouseDragged(double smx, double smy, int btn, double dx, double dy) {
        if (itemScroll.drag(smy, leftY, leftH, 20)) return true;
        if (tagScroll.drag(smy, tagListY, tagListH, 20)) return true;
        return super.canvasMouseDragged(smx, smy, btn, dx, dy);
    }

    @Override
    protected boolean canvasMouseScrolled(double smx, double smy, double delta) {
        if (tagInput != null && tagInput.isFocused() && !tagSuggestions.isEmpty()) {
            int suggestionY = tagInput.getY() + tagInput.getHeight() + 1;
            int visibleRows = Math.min(8, tagSuggestions.size());
            int boxH = visibleRows * 12;
            if (smx >= rightX && smx < rightX + rightW && smy >= suggestionY && smy < suggestionY + boxH) {
                suggestionScroll.update(tagSuggestions.size() * 12, boxH);
                return suggestionScroll.scroll(delta, 12 / 3.0D);
            }
        }
        if (smx >= leftX && smx < leftX + leftW && smy >= leftY && smy < leftY + leftH) {
            return itemScroll.scroll(delta, SLOT_PITCH / 3.0D);
        }
        if (smx >= rightX && smx < rightX + rightW && smy >= tagListY && smy < tagListY + tagListH) {
            return tagScroll.scroll(delta, 14 / 3.0D);
        }
        return super.canvasMouseScrolled(smx, smy, delta);
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
