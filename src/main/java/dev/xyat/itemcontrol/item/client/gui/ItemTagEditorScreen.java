package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticControl;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.itemcontrol.item.util.ItemBanControl;
import net.minecraft.client.gui.GuiGraphics;
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
    private static final int PLACEHOLDER_TEXT_COLOR = 0xBFFFFFFF;
    private static String rememberedItemSearch = "";
    private static String rememberedTagSearch = "";
    private static String rememberedSelectedItem = "";

    private final Screen parent;
    private final List<KineticItemSearch.CachedItem> allItems;
    private final List<String> allTags;
    private final GridScrollController itemScroll = new GridScrollController();
    private final GridScrollController tagScroll = new GridScrollController();
    private final GridScrollController suggestionScroll = new GridScrollController();

    private KineticEditBox itemSearch;
    private KineticEditBox tagInput;
    private StateButton addTagButton;
    private StateButton copyButton;
    private StateButton saveButton;
    private StateButton backButton;

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

    public ItemTagEditorScreen(Screen parent) {
        super(Component.translatable("gui.itemcontrol.item.item_tag.title"));
        this.parent = parent;
        setParentScreen(parent);
        configureDraft(BanItemConfig::snapshotData, this::restoreTagSnapshot);
        useCanvas(640f, 360f, 4);
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
        int usableW = Math.max(220, canvasWidth() - pad * 2 - gap);
        leftW = Math.max(150, (int) (usableW * 0.56f));
        rightW = Math.max(120, usableW - leftW);
        leftX = pad;
        rightX = leftX + leftW + gap;

        itemSearch = addTextField(leftX, topY, leftW, Component.empty());
        itemSearch.setPlaceholder(Component.translatable("gui.itemcontrol.item.item_tag.item_search_hint"));
        itemSearch.setValue(rememberedItemSearch);
        itemSearch.setResponder(value -> {
            rememberedItemSearch = value == null ? "" : value;
            refreshItems();
        });

        int buttonGap = 4;
        int smallButtonW = Math.max(42, (rightW - buttonGap * 3) / 4);
        copyButton = addButton(rightX, topY, smallButtonW, copyButtonText(), null, () -> {
            if (selectedItemId.isEmpty()) return;
            copyMode = !copyMode;
            ((KineticControl) copyButton).setText(copyButtonText());
        });

        saveButton = addButton(
                rightX + smallButtonW + buttonGap, topY, smallButtonW,
                Component.translatable("gui.itemcontrol.item.item_tag.save"), null, this::saveConfig
        );

        backButton = addButton(
                rightX + (smallButtonW + buttonGap) * 2, topY, smallButtonW,
                Component.translatable("gui.itemcontrol.item.item_tag.back"), null, this::onClose
        );

        addTagButton = addButton(
                rightX + (smallButtonW + buttonGap) * 3, topY, smallButtonW,
                Component.translatable("gui.itemcontrol.item.item_tag.add"), null, this::addTagFromInput
        );

        guideY = topY + toolbarH + 5;
        leftY = guideY + font.lineHeight + 7;
        leftH = Math.max(80, canvasHeight() - leftY - pad);
        leftCols = Math.max(1, (leftW - 8 + SLOT_PITCH - SLOT_SIZE) / SLOT_PITCH);

        tagInput = addTextField(rightX, leftY, rightW, Component.empty());
        tagInput.setPlaceholder(Component.translatable("gui.itemcontrol.item.item_tag.tag_input_hint"));
        tagInput.setValue(rememberedTagSearch);
        tagInput.setResponder(value -> {
            rememberedTagSearch = value == null ? "" : value;
            refreshTagSuggestions();
        });

        rightY = leftY + toolbarH + 6;
        rightH = Math.max(60, canvasHeight() - rightY - pad);
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
        if (copyButton != null) ((KineticControl) copyButton).setEnabled(hasSelection);
        if (addTagButton != null) ((KineticControl) addTagButton).setEnabled(hasSelection && normalizeTag(tagInput == null ? "" : tagInput.getValue()) != null);
    }

    private void refreshItems() {
        String query = itemSearch == null ? "" : itemSearch.getValue().trim().toLowerCase(Locale.ROOT);
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

    private void selectItem(KineticItemSearch.CachedItem item) {
        if (item == null || item.id() == null || item.id().isBlank()) return;
        String id = BanItemConfig.getBaseIdentifier(item.id());
        if (id.isEmpty()) return;
        if (copyMode && !selectedItemId.isEmpty()) {
            if (!selectedItemId.equals(id)) copyTagsFrom(id);
            copyMode = false;
            if (copyButton != null) ((KineticControl) copyButton).setText(copyButtonText());
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
        focusControl(tagInput);
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
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int smx, int smy, float pt) {
        GuiTheme.canvasBackground(g, canvasWidth(), canvasHeight());
        GuiTheme.panelAlt(g, leftX - 3, leftY - 3, leftW + 6, leftH + 6);
        GuiTheme.panelAlt(g, rightX - 3, rightY - 3, rightW + 6, rightH + 6);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int smx, int smy, float pt) {
        g.drawString(font, Component.translatable("gui.itemcontrol.item.item_tag.guide"), leftX, guideY, 0xFFFFFF, false);

        enableCanvasScissor(g, leftX, leftY, leftX + leftW, leftY + leftH);
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
            GuiTheme.itemSlot(g, x, y, SLOT_SIZE, 4, false);
            ItemBanControl.withSkip(() -> {
                GuiTheme.item(g, font, ci.stack(), x, y, SLOT_SIZE, 1.0F, true);
                return null;
            });
            if (selected) {
                GuiTheme.stateOutline(g, x, y, SLOT_SIZE, SLOT_SIZE, true, false, false);
            } else if (hovered) {
                GuiTheme.stateOutline(g, x, y, SLOT_SIZE, SLOT_SIZE, false, true, false);
            } else if (edited) {
                GuiTheme.indicatorOutline(g, x, y, SLOT_SIZE, SLOT_SIZE, GuiTheme.Indicator.SUCCESS);
            }
        }
        disableCanvasScissor(g);

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
        disableCanvasScissor(g);

        int suggestionY = tagInput == null ? 0 : tagInput.getY() + tagInput.getHeight() + 1;
        if (!tagSuggestions.isEmpty() && tagInput != null && isControlFocused(tagInput)) {
            int maxRows = Math.min(8, tagSuggestions.size());
            int boxH = maxRows * 12;
            suggestionScroll.update(tagSuggestions.size() * 12, boxH);
            double visual = suggestionScroll.smoothOffset();
            int first = Math.max(0, (int) Math.floor(visual / 12D));
            int last = Math.min(tagSuggestions.size(), first + maxRows + 1);
            GuiTheme.surface(g, rightX, suggestionY, rightW, boxH, GuiTheme.Surface.PANEL_ALT);
            enableCanvasScissor(g, rightX, suggestionY, rightX + rightW, suggestionY + boxH);
            for (int i = first; i < last; i++) {
                String suggestion = tagSuggestions.get(i);
                int sy = suggestionY + (int) Math.round(i * 12D - visual);
                boolean hovered = smx >= rightX && smx < rightX + rightW && smy >= sy && smy < sy + 12;
                if (hovered) GuiTheme.stateSurface(g, rightX, sy, rightW, 12, GuiTheme.Surface.PANEL_ALT, false, true, false);
                g.drawString(font, suggestion, rightX + 3, sy + 2, 0xFFFFFF, false);
            }
            disableCanvasScissor(g);
        }

        GuiTheme.scrollbar(itemScroll, g, smx, smy, leftX + leftW - 6, leftY, 4, leftH, 20);
        GuiTheme.scrollbar(tagScroll, g, smx, smy, rightX + rightW - 6, tagListY, 4, tagListH, 20);
    }

    @Override
    protected void renderTooltips(GuiGraphics g, int smx, int smy, int mx, int my) {
        if (isHoveringButton(copyButton, smx, smy)) {
            KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.copy"), mx, my);
            return;
        }
        if (isHoveringButton(saveButton, smx, smy)) {
            KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.save"), mx, my);
            return;
        }
        if (isHoveringButton(backButton, smx, smy)) {
            KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.back"), mx, my);
            return;
        }
        if (isHoveringButton(addTagButton, smx, smy)) {
            KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.add"), mx, my);
            return;
        }
        if (isHoveringEditBox(itemSearch, smx, smy)) {
            KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.item_search"), mx, my);
            return;
        }
        if (isHoveringEditBox(tagInput, smx, smy)) {
            KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.input"), mx, my);
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
                KineticOverlays.requestTooltip(tooltip, mx, my);
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
                tooltip.add(Component.translatable(copyMode ? "gui.itemcontrol.item.item_tag.tooltip.copy_source" : "gui.itemcontrol.item.item_tag.tooltip.select"));
                if (isEditedItem(ci)) {
                    tooltip.add(Component.translatable("gui.itemcontrol.item.item_tag.tooltip.edited"));
                }
                KineticOverlays.requestTooltip(tooltip, mx, my);
            }
        }
    }

    private boolean isHoveringButton(StateButton button, double mx, double my) {
        return button != null
                && ((KineticControl) button).isVisible()
                && mx >= button.getX()
                && mx < button.getX() + button.getWidth()
                && my >= button.getY()
                && my < button.getY() + button.getHeight();
    }

    private boolean isHoveringEditBox(KineticEditBox box, double mx, double my) {
        return box != null
                && ((KineticControl) box).isVisible()
                && mx >= box.getX()
                && mx < box.getX() + box.getWidth()
                && my >= box.getY()
                && my < box.getY() + box.getHeight();
    }

    @Override
    protected boolean canvasMouseClicked(double smx, double smy, int btn) {
        if (KineticMouseButtons.isPrimary(btn) && itemScroll.beginDrag(smx, smy, leftX + leftW - 6, leftY, 4, leftH, 20, 0)) return true;
        if (KineticMouseButtons.isPrimary(btn) && tagScroll.beginDrag(smx, smy, rightX + rightW - 6, tagListY, 4, tagListH, 20, 0)) return true;

        if (tagInput != null && isControlFocused(tagInput) && !tagSuggestions.isEmpty()) {
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
            if (KineticMouseButtons.isPrimary(btn) && col >= 0 && col < leftCols && localX % SLOT_PITCH < SLOT_SIZE && localY % SLOT_PITCH < SLOT_SIZE && idx >= 0 && idx < filteredItems.size()) {
                selectItem(filteredItems.get(idx));
                return true;
            }
        }

        if (smx >= rightX && smx < rightX + rightW && smy >= tagListY && smy < tagListY + tagListH) {
            int idx = (int) ((smy - tagListY + tagScroll.smoothOffset()) / 14);
            if (idx >= 0 && idx < displayedTags.size()) {
                TagEntry entry = displayedTags.get(idx);
                if (KineticMouseButtons.isSecondary(btn) && entry.source == TagSource.MANUAL) {
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
        if (tagInput != null && isControlFocused(tagInput) && !tagSuggestions.isEmpty()) {
            int suggestionY = tagInput.getY() + tagInput.getHeight() + 1;
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
