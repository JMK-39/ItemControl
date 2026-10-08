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
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;

import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.itemcontrol.item.util.ItemBanControl;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class MergeItemPage extends KineticPage {
    private KineticTextField searchBox;
    private KineticTextField leftSearchBox;
    private KineticButton addBtn, saveBtn, closeBtn, tagFilterBtn;
    private final KineticScrollController leftScroll = new KineticScrollController();
    private final KineticScrollController rightScroll = new KineticScrollController();
    private String selectedTarget = null;
    private boolean isCreatingRule = false;
    private boolean targetTagFilterActive = false;
    private final Set<String> expandedTargets = new HashSet<>();

    private final List<KineticItemSearch.CachedItem> allItemsCache;
    private final Map<String, List<String>> tempRules = new HashMap<>();
    private final List<LeftEntry> leftEntries = new ArrayList<>();
    private List<KineticItemSearch.CachedItem> rightDisplayList = new ArrayList<>();

    private static final int SLOT_SIZE = 22;
    private static final int SLOT_PITCH = SLOT_SIZE + 2;
    // Preserve the existing row anchors while reserving space for the trailing symbol and count.
    private static final int ROW_NAME_OFFSET = 28;
    private static final int ROW_COUNT_WIDTH = 24;
    private static final int ROW_EXPAND_WIDTH = 12;
    private static final int TEXT_GAP = 4;
    // Full-size icons fit in 22 px slots, two pixels inside each rule row.
    private static final int TARGET_ROW_H = 28;
    private static final int SOURCE_ROW_H = 28;
    private static final int ROW_INSET = 3;
    private static final int SOURCE_INDENT = 12;
    // Space between rows of the rule list, so neighbouring row frames never touch.
    private static final int ROW_GAP = 2;
    private static String rememberedLeftSearch = "";
    private static String rememberedRightSearch = "";
    private int leftX, leftY, leftW, leftH;
    private int rightX, rightY, rightW, rightH;
    private int gridCols;
    private int gridAreaH;
    private int rightInfoY;
    // Right end of the count text; on narrow windows the buttons share its row.
    private int rightInfoRight;

    public MergeItemPage() {
        super(KineticI18n.translatable("gui.itemcontrol.item.banitem.merge_overview_title"));
        useCanvas(
                640,
                360,
                4
        );

        this.allItemsCache = ItemSearchCache.getAllItems();
        if (BanItemConfig.data != null && BanItemConfig.data.mergedItems != null) {
            for (Map.Entry<String, List<String>> entry : BanItemConfig.data.mergedItems.entrySet()) {
                this.tempRules.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        configureDraft(this::snapshotTempRules, this::restoreTempRules);
    }

    private Map<String, List<String>> snapshotTempRules() {
        LinkedHashMap<String, List<String>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : tempRules.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot;
    }

    private void restoreTempRules(Map<String, List<String>> snapshot) {
        tempRules.clear();
        if (snapshot != null) {
            for (Map.Entry<String, List<String>> entry : snapshot.entrySet()) {
                tempRules.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        if (leftSearchBox != null) updateLeftEntries();
        if (searchBox != null) updateRightPanel();
    }

    @Override
    protected void build(KineticUi ui) {
        int sidePadding =
                switch (layoutMetrics().level()) {
                    case LARGE -> 14;
                    case NORMAL -> 10;
                    case SMALL -> 8;
                    case COMPACT -> 6;
                };

        int gap = 6;
        int buttonHeight = 20;

        initLayout(sidePadding, gap, buttonHeight);

        updateLeftEntries();
        updateRightPanel();
    }

    // The rule list stays on the left and the item grid on the right at every window size. When the right column is
    // too narrow for the search box, tag filter and the three buttons in one row, the buttons move down to the count row.
    private void initLayout(int sidePadding, int gap, int buttonHeight) {
        int searchY = 5;
        int panelY = 30;
        int rightGridTop = panelY + 28;
        leftW = Math.max(120, Math.min(170, width() / 4));
        leftX = sidePadding;
        leftY = panelY;
        leftH = Math.max(80, height() - panelY - 8);
        rightX = leftX + leftW + gap;
        rightY = rightGridTop;
        rightW = Math.max(SLOT_SIZE + 10, width() - sidePadding - rightX);
        rightH = Math.max(SLOT_PITCH * 2 - (SLOT_PITCH - SLOT_SIZE), height() - rightY - 8);
        gridCols = Math.max(1, (rightW - 10 + SLOT_PITCH - SLOT_SIZE) / SLOT_PITCH);
        gridAreaH = rightH;

        leftSearchBox = createLeftSearchBox(leftX, searchY, leftW);

        int buttonWidth = 60;
        int tagWidth = 76;
        boolean oneRow = rightW >= 80 + 4 + tagWidth + gap + buttonWidth * 3 + gap * 2;
        int buttonsY = oneRow ? searchY : panelY;
        int closeX = rightX + rightW - buttonWidth;
        int saveX = closeX - gap - buttonWidth;
        int addX = saveX - gap - buttonWidth;
        int searchRight = oneRow ? addX - gap : rightX + rightW;
        int rightSearchWidth = Math.max(80, Math.min(oneRow ? 150 : rightW, searchRight - rightX - 4 - tagWidth));

        searchBox = createRightSearchBox(rightX, searchY, rightSearchWidth);
        tagFilterBtn = createTagFilterButton(rightX + rightSearchWidth + 4, searchY, tagWidth);
        addBtn = createAddButton(addX, buttonsY, buttonWidth, buttonHeight);
        saveBtn = createSaveButton(saveX, buttonsY, buttonWidth, buttonHeight);
        closeBtn = createCloseButton(closeX, buttonsY, buttonWidth, buttonHeight);
        rightInfoY = oneRow ? panelY + 3 : panelY + (buttonHeight - KineticText.lineHeight()) / 2;
        rightInfoRight = oneRow ? rightX + rightW : addX - gap;
    }

    private KineticTextField createLeftSearchBox(int x, int y, int width) {
        KineticTextField box = ui().textField(x, y, width).build();
        box.setPlaceholder(KineticI18n.translatable("gui.itemcontrol.item.banitem.search.hint"));
        box.onTextChange(query -> {
            rememberedLeftSearch = query == null ? "" : query;
            updateLeftEntries();
        });
        box.setTextValue(rememberedLeftSearch);
        box.setDefaultText(rememberedLeftSearch);
        return box;
    }

    private KineticTextField createRightSearchBox(int x, int y, int width) {
        KineticTextField box = ui().textField(x, y, width).build();
        box.setPlaceholder(KineticI18n.translatable("gui.itemcontrol.item.banitem.search.hint"));
        box.onTextChange(query -> {
            rememberedRightSearch = query == null ? "" : query;
            updateRightPanel();
        });
        box.setTextValue(rememberedRightSearch);
        box.setDefaultText(rememberedRightSearch);
        return box;
    }

    private KineticButton createTagFilterButton(int x, int y, int width) {
        KineticButton button = ui().button(x, y, width).text(KineticI18n.translatable("gui.itemcontrol.item.banitem.merge_tag_filter")).onClick(() -> {
                    targetTagFilterActive = !targetTagFilterActive;
                    updateRightPanel();
                }).build();
        button.setControlVisible(false);
        return button;
    }

    private KineticButton createAddButton(int x, int y, int width, int height) {
        return ui().button(x, y, width).text(KineticI18n.translatable("gui.itemcontrol.item.banitem.merge_add")).onClick(() -> {
                    isCreatingRule = true;
                    selectedTarget = null;
                    targetTagFilterActive = false;
                    updateLeftEntries();
                    updateRightPanel();
                }).build();
    }

    private KineticButton createSaveButton(int x, int y, int width, int height) {
        return ui().button(x, y, width).text(KineticI18n.translatable("gui.itemcontrol.item.banitem.btn.save")).onClick(() -> {
                    BanItemConfig.Data data = new BanItemConfig.Data();
                    data.bannedItems = new ArrayList<>(BanItemConfig.data.bannedItems);
                    data.mergedItems.putAll(buildMergedRulesForSave());
                    ItemNetwork.CHANNEL.sendToServer(
                            new ItemNetwork.SaveBanConfigPacket(ItemNetwork.EDITOR_MERGE_ITEM, BanItemConfig.GSON.toJson(data))
                    );
                }).build();
    }

    public void applySaveResult(boolean success) {
        if (success) commitDraft();
    }

    private KineticButton createCloseButton(int x, int y, int width, int height) {
        return ui().button(x, y, width).text(KineticI18n.translatable("gui.itemcontrol.item.banitem.btn.back")).onClick(this::close).build();
    }

    private String getSearchDataForId(String idStr) {
        return ItemSearchCache.getSearchDataForId(idStr);
    }

    private void updateLeftEntries() {
        leftEntries.clear();
        String leftQuery = leftSearchBox != null ? leftSearchBox.textValue().toLowerCase(Locale.ROOT).trim() : "";

        for (Map.Entry<String, List<String>> entry : tempRules.entrySet()) {
            String target = entry.getKey();

            boolean targetMatches = leftQuery.isEmpty() || KineticSearch.match(getSearchDataForId(target), leftQuery);
            boolean sourceMatches = false;

            if (!leftQuery.isEmpty()) {
                for (String src : entry.getValue()) {
                    if (KineticSearch.match(getSearchDataForId(src), leftQuery)) {
                        sourceMatches = true;
                        break;
                    }
                }
            }

            if (targetMatches || sourceMatches) {
                leftEntries.add(new TargetEntry(target, entry.getValue().size()));
                if (expandedTargets.contains(target) || !leftQuery.isEmpty()) {
                    for (String source : entry.getValue()) {
                        if (leftQuery.isEmpty() || targetMatches || KineticSearch.match(getSearchDataForId(source), leftQuery)) {
                            leftEntries.add(new SourceEntry(target, source));
                        }
                    }
                }
            }
        }
        int totalLeftH = 0;
        for (LeftEntry e : leftEntries) { e.h = (e instanceof TargetEntry) ? TARGET_ROW_H : SOURCE_ROW_H; totalLeftH += e.h + ROW_GAP; }
        leftScroll.update(totalLeftH, leftH);
    }

    private void updateRightPanel() {
        if (searchBox == null) return;
        searchBox.setControlVisible(isCreatingRule || selectedTarget != null);
        searchBox.setEnabled(searchBox.controlVisible());
        updateTagFilterButton();

        if (!searchBox.controlVisible()) {
            rightDisplayList = new ArrayList<>();
            rightScroll.reset();
            rightScroll.update(0, gridAreaH);
            return;
        }

        String query = searchBox.textValue().toLowerCase(Locale.ROOT).trim();
        Set<String> excluded = buildExcludedIdentifiers();
        Set<ItemUnificationHelper.MergeGroup> targetGroups = targetTagFilterActive && selectedTarget != null ? ItemSearchCache.getUnificationGroupsForId(selectedTarget) : Collections.emptySet();
        int sourceHash = 31 * ItemSearchCache.getAllItemsHash() + ItemSearchCache.hashStrings(excluded);
        if (targetTagFilterActive) sourceHash = 31 * sourceHash + hashGroups(targetGroups);

        rightDisplayList = new ArrayList<>(ItemSearchCache.searchItems(targetTagFilterActive ? "merge_right_unify" : "merge_right", allItemsCache, query, c -> {
            if (excluded.contains(c.id()) || excluded.contains(getBaseIdentifier(c.id()))) return false;
            return !targetTagFilterActive || ItemSearchCache.hasAnyUnificationGroup(c.stack(), targetGroups);
        }, sourceHash));

        int totalRightRows = (int) Math.ceil((double) rightDisplayList.size() / gridCols);
        int totalRightH = totalRightRows * SLOT_PITCH;
        rightScroll.update(totalRightH, gridAreaH);
    }

    private void updateTagFilterButton() {
        if (tagFilterBtn == null) return;
        boolean show = selectedTarget != null && !isCreatingRule;
        Set<ItemUnificationHelper.MergeGroup> groups = show ? ItemSearchCache.getUnificationGroupsForId(selectedTarget) : Collections.emptySet();
        tagFilterBtn.setControlVisible(show);
        tagFilterBtn.setEnabled(show && !groups.isEmpty());
        if (!tagFilterBtn.isEnabled()) targetTagFilterActive = false;
        tagFilterBtn.setText(KineticI18n.translatable(targetTagFilterActive ? "gui.itemcontrol.item.banitem.merge_tag_filter_on" : "gui.itemcontrol.item.banitem.merge_tag_filter"));
    }

    private Set<String> buildExcludedIdentifiers() {
        Set<String> excluded = new HashSet<>(BanItemConfig.data.bannedItems);
        for (String banned : BanItemConfig.data.bannedItems) addIdentifierExclusion(excluded, banned);
        addRuleMapExclusions(excluded, tempRules);
        return excluded;
    }

    private void addRuleMapExclusions(Set<String> excluded, Map<String, List<String>> rules) {
        if (rules == null || rules.isEmpty()) return;
        for (Map.Entry<String, List<String>> entry : rules.entrySet()) {
            addIdentifierExclusion(excluded, entry.getKey());
            if (entry.getValue() != null) {
                for (String source : entry.getValue()) addIdentifierExclusion(excluded, source);
            }
        }
    }

    private void addIdentifierExclusion(Set<String> excluded, String idStr) {
        if (idStr == null || idStr.isEmpty()) return;
        excluded.add(idStr);
        excluded.add(getBaseIdentifier(idStr));
    }

    private String getBaseIdentifier(String idStr) {
        if (idStr == null) return "";
//? if >=1.21 {
/*        int bracket = idStr.indexOf('[');*/
//?} else {
        int bracket = idStr.indexOf('{');
//?}
        return bracket == -1 ? idStr : idStr.substring(0, bracket);
    }

    private Map<String, List<String>> buildMergedRulesForSave() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : tempRules.entrySet()) {
            List<String> sources = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
            sources.removeIf(s -> s == null || s.isEmpty() || getBaseIdentifier(s).equals(getBaseIdentifier(entry.getKey())));
            if (sources.isEmpty()) continue;

            String targetWithTags = buildTargetWithMergedTags(entry.getKey());
            List<String> targetSources = result.computeIfAbsent(targetWithTags, k -> new ArrayList<>());
            for (String source : sources) {
                if (!targetSources.contains(source)) targetSources.add(source);
            }
        }
        return result;
    }

    private String buildTargetWithMergedTags(String targetId) {
        return targetId;
    }

    private int hashGroups(Set<ItemUnificationHelper.MergeGroup> groups) {
        if (groups == null || groups.isEmpty()) return 0;
        int hash = 1;
        for (ItemUnificationHelper.MergeGroup group : groups) hash = 31 * hash + group.key().hashCode();
        return hash;
    }


    //? if >=1.21 {
/*private void openNbtEditor(String idStr, int context, String targetParent, ItemStack fallbackStack) {
        String baseId;
        String initNbt;
        int bracket = idStr.indexOf('[');
        if (bracket == -1) {
            baseId = idStr;
            initNbt = fallbackStack == null ? "[]" : dev.xyat.itemcontrol.item.data.ItemData.format(fallbackStack);
        } else {
            baseId = idStr.substring(0, bracket);
            initNbt = idStr.substring(bracket);
        }

        {
            ItemDataEditor.open(this, baseId, initNbt, (savedNbt) -> {
                String newIdStr = baseId + savedNbt;
                switch (context) {
                    case 0:
                        tempRules.putIfAbsent(newIdStr, new ArrayList<>());
                        selectedTarget = newIdStr; isCreatingRule = false; targetTagFilterActive = false; expandedTargets.add(newIdStr);
                        break;
                    case 1:
                        tempRules.get(selectedTarget).add(newIdStr);
                        break;
                    case 2:
                        List<String> src = tempRules.remove(idStr);
                        if (src == null) src = new ArrayList<>();
                        tempRules.put(newIdStr, src);
                        if (idStr.equals(selectedTarget)) selectedTarget = newIdStr;
                        targetTagFilterActive = false; expandedTargets.remove(idStr); expandedTargets.add(newIdStr);
                        break;
                    case 3:
                        List<String> pSrc = tempRules.get(targetParent);
                        if (pSrc != null) { pSrc.remove(idStr); pSrc.add(newIdStr); }
                        break;
                }
                updateLeftEntries(); updateRightPanel();
            });
        }
    }*/
//?} else {
private void openNbtEditor(String idStr, int context, String targetParent, ItemStack fallbackStack) {
        String baseId;
        String initNbt;
        int bracket = idStr.indexOf('{');
        if (bracket == -1) {
            baseId = idStr;
            initNbt = (fallbackStack != null && fallbackStack.hasTag() && fallbackStack.getTag() != null) ? fallbackStack.getTag().toString() : "";
        } else {
            baseId = idStr.substring(0, bracket);
            initNbt = idStr.substring(bracket);
        }

        {
            KineticSelectors.openNbtEditor(initNbt, (savedNbt) -> {
                String newIdStr = baseId + savedNbt;
                switch (context) {
                    case 0:
                        tempRules.putIfAbsent(newIdStr, new ArrayList<>());
                        selectedTarget = newIdStr; isCreatingRule = false; targetTagFilterActive = false; expandedTargets.add(newIdStr);
                        break;
                    case 1:
                        tempRules.get(selectedTarget).add(newIdStr);
                        break;
                    case 2:
                        List<String> src = tempRules.remove(idStr);
                        if (src == null) src = new ArrayList<>();
                        tempRules.put(newIdStr, src);
                        if (idStr.equals(selectedTarget)) selectedTarget = newIdStr;
                        targetTagFilterActive = false; expandedTargets.remove(idStr); expandedTargets.add(newIdStr);
                        break;
                    case 3:
                        List<String> pSrc = tempRules.get(targetParent);
                        if (pSrc != null) { pSrc.remove(idStr); pSrc.add(newIdStr); }
                        break;
                }
                updateLeftEntries(); updateRightPanel();
            });
        }
    }
//?}


    @Override
    protected void renderBackground(KineticGraphics g, int smx, int smy, float pt) {
        KineticTheme.canvasBackground(g, width(), height());
        KineticTheme.panel(g, leftX, leftY, leftW, leftH);
        KineticTheme.panel(g, rightX, rightY, rightW, rightH);
    }

    @Override
    protected void renderForeground(KineticGraphics g, int smx, int smy, float pt) {
        int curY = leftY - (int) Math.round(leftScroll.smoothOffset());
        g.scissor(leftX, leftY, leftX + leftW, leftY + leftH);
        for (LeftEntry e : leftEntries) {
            if (curY + e.h >= leftY && curY <= leftY + leftH) {
                e.x = leftX + 2;
                e.y = curY; e.w = leftW - 4; e.render(g, smx, smy);
            }
            curY += e.h + ROW_GAP;
        }
        g.endScissor();

        leftScroll.render(
                g,
                smx,
                smy,
                leftX + leftW + 2,
                leftY,
                4,
                leftH,
                20
        );

        if (isCreatingRule || selectedTarget != null) {
            int countX = getRightCountX();
            Component countText = KineticI18n.translatable(
                    "gui.itemcontrol.item.banitem.merge_count",
                    rightDisplayList.size(),
                    allItemsCache.size()
            );
            g.scrollingText(countText, countX, rightInfoY, rightInfoRight - countX - TEXT_GAP, 0xFFFFFF, false);

            int gridX = rightX + 2;
            int gridY = rightY;
            g.scissor(rightX, gridY, rightX + rightW, gridY + gridAreaH);
            for (int i = 0; i < rightDisplayList.size(); i++) {
                int col = i % gridCols;
                int row = i / gridCols;
                int rX = gridX + col * SLOT_PITCH;
                int rY = gridY + row * SLOT_PITCH - (int) Math.round(rightScroll.smoothOffset());
                if (rY + SLOT_SIZE > gridY && rY < gridY + gridAreaH) {
                    ItemStack stack = rightDisplayList.get(i).stack();
                    boolean hovered = smx >= rX && smx < rX + SLOT_SIZE
                            && smy >= rY && smy < rY + SLOT_SIZE;
                    KineticTheme.itemSlot(
                            g,
                            rX,
                            rY,
                            SLOT_SIZE,
                            4,
                            hovered
                    );

                    ItemBanControl.withSkip(() -> {
                        KineticTheme.item(
                                g,
                                stack,
                                rX,
                                rY,
                                SLOT_SIZE,
                                1.0F,
                                false
                        );
                        return null;
                    });
                }
            }
            g.endScissor();

            rightScroll.render(
                    g,
                    smx,
                    smy,
                    rightX + rightW - 6,
                    gridY,
                    4,
                    gridAreaH,
                    20
            );
        }

    }

    private int getRightCountX() {
        return rightX;
    }

    private boolean isHoveringButton(KineticButton btn, double mx, double my) { return btn != null && btn.controlVisible() && btn.contains(mx, my);
    }

    @Override
    protected void renderTooltips(int smx, int smy) {
        // 原实现按屏幕坐标请求提示并在上方区域下移 15 像素；这里用 GUI 缩放后的指针位置保持一致
        // The old code requested tooltips in screen coordinates, shifted 15 px down above the lists; the GUI-scaled
        // cursor position keeps that behaviour.
        KineticClientRuntime.CursorPosition cursor = KineticClientRuntime.scaledCursorPosition();
        int mx = (int) cursor.x();
        int my = (int) cursor.y();
        int tooltipY = smy < leftY ? my + 15 : my;

        if (isHoveringButton(addBtn, smx, smy)) { KineticOverlays.requestTooltip(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.btn.add"), mx, tooltipY); return;
        }
        if (isHoveringButton(saveBtn, smx, smy)) { KineticOverlays.requestTooltip(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.btn.save"), mx, tooltipY); return;
        }
        if (isHoveringButton(closeBtn, smx, smy)) { KineticOverlays.requestTooltip(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.btn.back"), mx, tooltipY); return;
        }
        if (isHoveringButton(tagFilterBtn, smx, smy)) {
            Set<ItemUnificationHelper.MergeGroup> groups = selectedTarget == null ? Collections.emptySet() : ItemSearchCache.getUnificationGroupsForId(selectedTarget);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.merge_tag_filter.title"));
            tooltip.add(KineticI18n.translatable(
                    groups.isEmpty() ? "gui.itemcontrol.item.banitem.tooltip.merge_tag_filter.none" : "gui.itemcontrol.item.banitem.tooltip.merge_tag_filter.desc",
                    Component.literal(String.valueOf(groups.size()))
            ));
            KineticOverlays.requestTooltip(tooltip, mx, tooltipY);
            return;
        }

        if (isCreatingRule || selectedTarget != null) {
            int countX = getRightCountX();
            Component countText = KineticI18n.translatable(
                    "gui.itemcontrol.item.banitem.merge_count",
                    rightDisplayList.size(),
                    allItemsCache.size()
            );
            if (smx >= countX && smx < countX + Math.min(KineticText.width(countText), rightInfoRight - countX - TEXT_GAP) && smy >= rightInfoY && smy < rightInfoY + KineticText.lineHeight()) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.count.title"));
                tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.count.merge.desc"));
                KineticOverlays.requestTooltip(tooltip, mx, tooltipY);
                return;
            }
        }

        if (smx >= leftX && smx < leftX + leftW && smy >= leftY && smy < leftY + leftH) {
            int currentY = leftY - (int) Math.round(leftScroll.smoothOffset());
            for (LeftEntry entry : leftEntries) {
                if (currentY + entry.h >= leftY && currentY <= leftY + leftH) {
                    if (smx >= entry.x && smx < entry.x + entry.w && smy >= currentY && smy < currentY + entry.h) { entry.requestTooltip(mx, my);
                        break; }
                }
                currentY += entry.h + ROW_GAP;
            }
        } else if ((isCreatingRule || selectedTarget != null) && smx >= rightX && smx < rightX + rightW && smy >= rightY && smy < rightY + rightH) {
            int gridX = rightX + 2, gridY = rightY;
            if (smy >= gridY + gridAreaH) return;
            int localX = smx - gridX;
            int localY = (int) Math.floor(smy - gridY + rightScroll.smoothOffset());
            int col = localX / SLOT_PITCH;
            int row = localY / SLOT_PITCH;
            int idx = row * gridCols + col;
            if (col >= 0 && col < gridCols
                    && localX % SLOT_PITCH < SLOT_SIZE
                    && localY % SLOT_PITCH < SLOT_SIZE
                    && idx >= 0 && idx < rightDisplayList.size()) {
                KineticItemSearch.CachedItem ci = rightDisplayList.get(idx);
                List<Component> tt = new ArrayList<>();
                tt.add(ItemCacheHudRenderer.getDisplayNameCustom(ci.stack()));
                tt.add(Component.literal(ci.id()));
                tt.add(Component.empty());
                tt.add(KineticI18n.translatable(isCreatingRule ? "gui.itemcontrol.item.banitem.tooltip.set_target" : "gui.itemcontrol.item.banitem.tooltip.add_source"));
                KineticOverlays.requestTooltip(tt, mx, my);
            }
        }
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 canvasMouseClicked 全部在控件之前处理 / The old canvasMouseClicked handled all of this before controls.
        double smx = input.x();
        double smy = input.y();
        if (leftScroll.beginDrag(
                        smx,
                        smy,
                        input.button(),
                        leftX + leftW + 2,
                        leftY,
                        4,
                        leftH,
                        20,
                        0
                )) {
            return true;
        }

        int gridY = rightY;

        if (rightScroll.beginDrag(
                        smx,
                        smy,
                        input.button(),
                        rightX + rightW - 6,
                        gridY,
                        4,
                        gridAreaH,
                        20,
                        0
                )) {
            return true;
        }

        if (smx >= leftX && smx < leftX + leftW && smy >= leftY && smy < leftY + leftH) {
            int currentY = leftY - (int) Math.round(leftScroll.smoothOffset());
            for (LeftEntry entry : leftEntries) {
                if (currentY + entry.h >= leftY && currentY <= leftY + leftH) {
                    if (smx >= entry.x && smx < entry.x + entry.w && smy >= currentY && smy < currentY + entry.h) {
                        if (entry.mouseClicked(input)) return true;
                    }
                }
                currentY += entry.h + ROW_GAP;
            }
        } else if ((isCreatingRule || selectedTarget != null) && smx >= rightX && smx < rightX + rightW && smy >= rightY && smy < rightY + rightH) {
            int gridX = rightX + 2;
            int localX = (int) (smx - gridX);
            int localY = (int) (smy - gridY + rightScroll.smoothOffset());
            int col = localX / SLOT_PITCH;
            int row = localY / SLOT_PITCH;
            int idx = row * gridCols + col;
            if (col >= 0 && col < gridCols
                    && localX % SLOT_PITCH < SLOT_SIZE
                    && localY % SLOT_PITCH < SLOT_SIZE
                    && idx >= 0 && idx < rightDisplayList.size()) {
                if (input.isLeft()) {
                    String id = rightDisplayList.get(idx).id();
                    if (isCreatingRule) { tempRules.putIfAbsent(id, new ArrayList<>()); selectedTarget = id; isCreatingRule = false; targetTagFilterActive = false; expandedTargets.add(id);
                    }
                    else { tempRules.get(selectedTarget).add(id);
                    }
                    updateLeftEntries(); updateRightPanel(); return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        // 两个滚动条都要结束拖动（非短路或）/ Both scrollbars must end their drag (non-short-circuit or).
        return leftScroll.release(input.button())
                | rightScroll.release(input.button());
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double smy = input.y();
        if (leftScroll.drag(
                smy,
                leftY,
                leftH,
                20
        )) {
            return true;
        }

        if (rightScroll.drag(
                smy,
                rightY,
                gridAreaH,
                20
        )) {
            return true;
        }

        return false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double smx = input.x();
        double smy = input.y();
        double d = input.deltaY();
        if (smx >= leftX
                && smx <= leftX + leftW
                && smy >= leftY
                && smy <= leftY + leftH
                && leftScroll.scroll(d, 10D)) {
            return true;
        }

        if (smx >= rightX
                && smx <= rightX + rightW
                && smy >= rightY
                && smy <= rightY + rightH
                && rightScroll.scroll(d, SLOT_PITCH)) {
            return true;
        }

        return false;
    }

    abstract static class LeftEntry { int x, y, w, h;
        abstract void render(KineticGraphics g, int mx, int my); abstract boolean mouseClicked(MouseInput input);
        abstract void requestTooltip(int mx, int my); }

    class TargetEntry extends LeftEntry {
        String id;
        ItemStack stack; int count;
        TargetEntry(String id, int count) { this.id = id; this.stack = BanItemConfig.parseItemStack(id); this.count = count;
        }
        void render(KineticGraphics g, int mx, int my) {
            boolean selected = id.equals(selectedTarget), hover = mx >= x && mx < x + w && my >= y && my < y + h;
            KineticTheme.stateSurface(g, x, y, w, h, KineticTheme.Surface.PANEL_ALT, selected, hover, false);
            KineticTheme.itemSlot(g, x + ROW_INSET, y + ROW_INSET, SLOT_SIZE, 4, hover);
            ItemBanControl.withSkip(() -> { KineticTheme.item(g, stack, x + ROW_INSET, y + ROW_INSET, SLOT_SIZE, 1.0F, false); return null; });
            int nameX = x + ROW_NAME_OFFSET;
            int countX = x + w - ROW_COUNT_WIDTH;
            int expandX = countX - ROW_EXPAND_WIDTH;
            g.scrollingText(ItemCacheHudRenderer.getDisplayNameCustom(stack), nameX, y + (TARGET_ROW_H - 8) / 2, expandX - nameX - TEXT_GAP, 0xFFFFFF, false);
            g.scrollingText(KineticI18n.translatable("gui.itemcontrol.item.common.count_parentheses", Component.literal(String.valueOf(count))), countX, y + (TARGET_ROW_H - 8) / 2, ROW_COUNT_WIDTH - TEXT_GAP, 0xFFFFFF, false);
            g.text(KineticI18n.translatable(expandedTargets.contains(id) ? "gui.itemcontrol.item.common.collapse" : "gui.itemcontrol.item.common.expand"), expandX, y + (TARGET_ROW_H - 8) / 2, 0xFFFFFF, false);
        }
        boolean mouseClicked(MouseInput input) {
            if (KineticClientRuntime.shiftModifierDown() && input.isLeft()) { openNbtEditor(id, 2, "", stack);
                return true; }
            if (input.isLeft()) { if (expandedTargets.contains(id)) expandedTargets.remove(id);
            else expandedTargets.add(id); selectedTarget = id; isCreatingRule = false; targetTagFilterActive = false; updateLeftEntries(); updateRightPanel(); return true;
            }
            else if (input.isRight()) { tempRules.remove(id);
                if (id.equals(selectedTarget)) { selectedTarget = null; targetTagFilterActive = false; } expandedTargets.remove(id); updateLeftEntries(); updateRightPanel(); return true;
            }
            return false;
        }
        void requestTooltip(int mx, int my) {
            List<Component> tt = new ArrayList<>();
            tt.add(ItemCacheHudRenderer.getDisplayNameCustom(stack)); tt.add(Component.literal(id));
            tt.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.shift_edit_nbt"));
            tt.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.target_del"));
            KineticOverlays.requestTooltip(tt, mx, my);
        }
    }

    class SourceEntry extends LeftEntry {
        String targetId, sourceId;
        ItemStack stack;
        SourceEntry(String t, String s) { targetId = t; sourceId = s; stack = BanItemConfig.parseItemStack(s);
        }
        void render(KineticGraphics g, int mx, int my) {
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
            KineticTheme.stateSurface(g, x, y, w, h, KineticTheme.Surface.PANEL_ALT, false, hover, false);
            KineticTheme.itemSlot(g, x + SOURCE_INDENT, y + ROW_INSET, SLOT_SIZE, 3, hover);
            ItemBanControl.withSkip(() -> { KineticTheme.item(g, stack, x + SOURCE_INDENT, y + ROW_INSET, SLOT_SIZE, 1.0F, false); return null; });
            int nameX = x + SOURCE_INDENT + SLOT_SIZE + TEXT_GAP;
            g.scrollingText(ItemCacheHudRenderer.getDisplayNameCustom(stack), nameX, y + (SOURCE_ROW_H - 8) / 2,
                    Math.max(0, x + w - TEXT_GAP - nameX), 0xFFAAAAAA, false);
        }
        boolean mouseClicked(MouseInput input) {
            if (KineticClientRuntime.shiftModifierDown() && input.isLeft()) { openNbtEditor(sourceId, 3, targetId, stack);
                return true; }
            if (input.isRight()) { tempRules.get(targetId).remove(sourceId);
                updateLeftEntries(); updateRightPanel(); return true; }
            return false;
        }
        void requestTooltip(int mx, int my) {
            List<Component> tt = new ArrayList<>();
            tt.add(ItemCacheHudRenderer.getDisplayNameCustom(stack)); tt.add(Component.literal(sourceId));
            tt.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.shift_edit_nbt"));
            tt.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.source_del"));
            KineticOverlays.requestTooltip(tt, mx, my);
        }
    }
}
