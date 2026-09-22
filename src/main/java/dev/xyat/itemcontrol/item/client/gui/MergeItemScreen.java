package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.itemcontrol.item.util.ItemBanControl;
import dev.xyat.kineticcore.api.client.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.widget.KineticControl;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MergeItemScreen extends KineticScreen {
    private final Screen parent;
    private KineticEditBox searchBox;
    private KineticEditBox leftSearchBox;
    private StateButton addBtn, saveBtn, closeBtn, tagFilterBtn;
    private final GridScrollController leftScroll = new GridScrollController();
    private final GridScrollController rightScroll = new GridScrollController();
    private int totalRightH = 0;
    private String selectedTarget = null;
    private boolean isCreatingRule = false;
    private boolean targetTagFilterActive = false;
    private final Set<String> expandedTargets = new HashSet<>();

    private final List<KineticItemSearch.CachedItem> allItemsCache;
    private final Map<String, List<String>> tempRules = new HashMap<>();
    private final List<LeftEntry> leftEntries = new ArrayList<>();
    private List<KineticItemSearch.CachedItem> rightDisplayList = new ArrayList<>();

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_PITCH = 19;
    private static String rememberedLeftSearch = "";
    private static String rememberedRightSearch = "";
    private int leftX, leftY, leftW, leftH;
    private int rightX, rightY, rightW, rightH;
    private int gridCols;
    private int gridAreaH;
    private boolean compactLayout;
    private int rightInfoY;

    public MergeItemScreen() {
        this(null);
    }

    public MergeItemScreen(Screen parent) {
        super(Component.translatable("gui.itemcontrol.item.banitem.merge_overview_title"));
        this.parent = parent;
        useCanvas(
                640f,
                360f,
                4
        );

        this.allItemsCache = ItemSearchCache.getAllItems();
        if (BanItemConfig.data != null && BanItemConfig.data.mergedItems != null) {
            for (Map.Entry<String, List<String>> entry : BanItemConfig.data.mergedItems.entrySet()) {
                this.tempRules.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        setParentScreen(parent);
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
    protected void buildUi() {
        int sidePadding =
                switch (layoutLevel()) {
                    case LARGE -> 14;
                    case NORMAL -> 10;
                    case SMALL -> 8;
                    case COMPACT -> 6;
                };

        int gap = 6;
        int buttonHeight = 20;

        compactLayout =
                isPortraitLayout()
                        || isCompactLayout()
                        || canvasWidth() < 540;

        if (compactLayout) {
            initCompactLayout(sidePadding, gap, buttonHeight);
        } else {
            initWideLayout(sidePadding, gap, buttonHeight);
        }

        updateLeftEntries();
        updateRightPanel();
    }

    private void initWideLayout(int sidePadding, int gap, int buttonHeight) {
        int searchY = 5;
        int panelY = 30;
        int rightGridTop = panelY + 28;
        leftW = Math.max(120, Math.min(170, canvasWidth() / 4));
        leftX = sidePadding;
        leftY = panelY;
        leftH = Math.max(80, canvasHeight() - panelY - 8);
        rightX = leftX + leftW + gap;
        rightY = rightGridTop;
        rightW = Math.max(SLOT_SIZE + 10, canvasWidth() - sidePadding - rightX);
        rightH = Math.max(SLOT_PITCH * 2 - (SLOT_PITCH - SLOT_SIZE), canvasHeight() - rightY - 8);
        gridCols = Math.max(1, (rightW - 10 + SLOT_PITCH - SLOT_SIZE) / SLOT_PITCH);
        gridAreaH = rightH;

        leftSearchBox = createLeftSearchBox(leftX, searchY, leftW);

        int buttonWidth = 60;
        int closeX = rightX + rightW - buttonWidth;
        int saveX = closeX - gap - buttonWidth;
        int addX = saveX - gap - buttonWidth;
        int rightSearchWidth = Math.max(80, Math.min(150, addX - gap - rightX));

        searchBox = createRightSearchBox(rightX, searchY, rightSearchWidth);
        tagFilterBtn = createTagFilterButton(rightX + rightSearchWidth + 4, searchY, 76);
        addBtn = createAddButton(addX, searchY, buttonWidth, buttonHeight);
        saveBtn = createSaveButton(saveX, searchY, buttonWidth, buttonHeight);
        closeBtn = createCloseButton(closeX, searchY, buttonWidth, buttonHeight);
        rightInfoY = panelY + 3;
    }

    private void initCompactLayout(int sidePadding, int gap, int buttonHeight) {
        int contentW = Math.max(120, canvasWidth() - sidePadding * 2);
        leftX = sidePadding;
        leftW = contentW;
        leftSearchBox = createLeftSearchBox(leftX, 5, leftW);

        int buttonY = 30;
        int buttonWidth = Math.max(42, (contentW - gap * 2) / 3);
        addBtn = createAddButton(leftX, buttonY, buttonWidth, buttonHeight);
        saveBtn = createSaveButton(leftX + buttonWidth + gap, buttonY, buttonWidth, buttonHeight);
        closeBtn = createCloseButton(leftX + (buttonWidth + gap) * 2, buttonY, buttonWidth, buttonHeight);

        leftY = 56;
        int minimumRightHeight = SLOT_PITCH * 4 - (SLOT_PITCH - SLOT_SIZE);
        int reservedForRight = 20 + 4 + font.lineHeight + 4 + minimumRightHeight + 8;
        int availableForLeft = canvasHeight() - leftY - reservedForRight;
        leftH = Math.max(56, Math.min(120, availableForLeft));

        int rightSearchY = leftY + leftH + 4;
        rightX = sidePadding;
        rightW = contentW;
        int tagWidth = 76;
        int rightSearchWidth = Math.max(80, rightW - tagWidth - 4);
        searchBox = createRightSearchBox(rightX, rightSearchY, rightSearchWidth);
        tagFilterBtn = createTagFilterButton(rightX + rightSearchWidth + 4, rightSearchY, tagWidth);
        rightInfoY = rightSearchY + 25;
        rightY = rightInfoY + font.lineHeight + 4;
        rightH = Math.max(SLOT_PITCH * 2 - (SLOT_PITCH - SLOT_SIZE), canvasHeight() - rightY - 8);
        gridCols = Math.max(1, (rightW - 10 + SLOT_PITCH - SLOT_SIZE) / SLOT_PITCH);
        gridAreaH = rightH;
    }

    private KineticEditBox createLeftSearchBox(int x, int y, int width) {
        KineticEditBox box = addTextField(x, y, width, Component.empty());
        box.setPlaceholder(Component.translatable("gui.itemcontrol.item.banitem.search.hint"));
        box.setResponder(query -> {
            rememberedLeftSearch = query == null ? "" : query;
            updateLeftEntries();
        });
        box.setValue(rememberedLeftSearch);
        return box;
    }

    private KineticEditBox createRightSearchBox(int x, int y, int width) {
        KineticEditBox box = addTextField(x, y, width, Component.empty());
        box.setPlaceholder(Component.translatable("gui.itemcontrol.item.banitem.search.hint"));
        box.setResponder(query -> {
            rememberedRightSearch = query == null ? "" : query;
            updateRightPanel();
        });
        box.setValue(rememberedRightSearch);
        return box;
    }

    private StateButton createTagFilterButton(int x, int y, int width) {
        StateButton button = addButton(
                x, y, width,
                Component.translatable("gui.itemcontrol.item.banitem.merge_tag_filter"),
                null,
                () -> {
                    targetTagFilterActive = !targetTagFilterActive;
                    updateRightPanel();
                }
        );
        ((KineticControl) button).setVisible(false);
        return button;
    }

    private StateButton createAddButton(int x, int y, int width, int height) {
        return addButton(
                x, y, width,
                Component.translatable("gui.itemcontrol.item.banitem.merge_add"),
                null,
                () -> {
                    isCreatingRule = true;
                    selectedTarget = null;
                    targetTagFilterActive = false;
                    updateLeftEntries();
                    updateRightPanel();
                }
        );
    }

    private StateButton createSaveButton(int x, int y, int width, int height) {
        return addButton(
                x, y, width,
                Component.translatable("gui.itemcontrol.item.banitem.btn.save"),
                null,
                () -> {
                    BanItemConfig.Data data = new BanItemConfig.Data();
                    data.bannedItems = new ArrayList<>(BanItemConfig.data.bannedItems);
                    data.mergedItems.putAll(buildMergedRulesForSave());
                    ItemNetwork.CHANNEL.sendToServer(
                            new ItemNetwork.SaveBanConfigPacket(ItemNetwork.EDITOR_MERGE_ITEM, BanItemConfig.GSON.toJson(data))
                    );
                }
        );
    }

    public void applySaveResult(boolean success) {
        if (success) commitDraft();
    }

    private StateButton createCloseButton(int x, int y, int width, int height) {
        return addButton(
                x, y, width,
                Component.translatable("gui.itemcontrol.item.banitem.btn.back"),
                null,
                this::onClose
        );
    }

    private String getSearchDataForId(String idStr) {
        return ItemSearchCache.getSearchDataForId(idStr);
    }

    private void updateLeftEntries() {
        leftEntries.clear();
        String leftQuery = leftSearchBox != null ? leftSearchBox.getValue().toLowerCase(Locale.ROOT).trim() : "";

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
        for (LeftEntry e : leftEntries) { e.h = (e instanceof TargetEntry) ? 22 : 12; totalLeftH += e.h + 1; }
        leftScroll.update(totalLeftH, leftH);
    }

    private void updateRightPanel() {
        if (searchBox == null) return;
        ((KineticControl) searchBox).setVisible(isCreatingRule || selectedTarget != null);
        ((KineticControl) searchBox).setEnabled(((KineticControl) searchBox).isVisible());
        updateTagFilterButton();

        if (!((KineticControl) searchBox).isVisible()) {
            rightDisplayList = new ArrayList<>();
            totalRightH = 0;
            rightScroll.reset();
            rightScroll.update(0, gridAreaH);
            return;
        }

        String query = searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        Set<String> excluded = buildExcludedIdentifiers();
        Set<ItemUnificationHelper.MergeGroup> targetGroups = targetTagFilterActive && selectedTarget != null ? ItemSearchCache.getUnificationGroupsForId(selectedTarget) : Collections.emptySet();
        int sourceHash = 31 * ItemSearchCache.getAllItemsHash() + ItemSearchCache.hashStrings(excluded);
        if (targetTagFilterActive) sourceHash = 31 * sourceHash + hashGroups(targetGroups);

        rightDisplayList = new ArrayList<>(ItemSearchCache.searchItems(targetTagFilterActive ? "merge_right_unify" : "merge_right", allItemsCache, query, c -> {
            if (excluded.contains(c.id()) || excluded.contains(getBaseIdentifier(c.id()))) return false;
            return !targetTagFilterActive || ItemSearchCache.hasAnyUnificationGroup(c.stack(), targetGroups);
        }, sourceHash));

        int totalRightRows = (int) Math.ceil((double) rightDisplayList.size() / gridCols);
        totalRightH = totalRightRows * SLOT_PITCH;
        rightScroll.update(totalRightH, gridAreaH);
    }

    private void updateTagFilterButton() {
        if (tagFilterBtn == null) return;
        boolean show = selectedTarget != null && !isCreatingRule;
        Set<ItemUnificationHelper.MergeGroup> groups = show ? ItemSearchCache.getUnificationGroupsForId(selectedTarget) : Collections.emptySet();
        ((KineticControl) tagFilterBtn).setVisible(show);
        ((KineticControl) tagFilterBtn).setEnabled(show && !groups.isEmpty());
        if (!((KineticControl) tagFilterBtn).isEnabled()) targetTagFilterActive = false;
        ((KineticControl) tagFilterBtn).setText(Component.translatable(targetTagFilterActive ? "gui.itemcontrol.item.banitem.merge_tag_filter_on" : "gui.itemcontrol.item.banitem.merge_tag_filter"));
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
        int bracket = idStr.indexOf('{');
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

        if (this.minecraft != null) {
            KineticSelectors.openNbtEditor(this, initNbt, (savedNbt) -> {
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

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int smx, int smy, float pt) {
        GuiTheme.canvasBackground(g, canvasWidth(), canvasHeight());
        GuiTheme.panel(g, leftX, leftY, leftW, leftH);
        GuiTheme.panel(g, rightX, rightY, rightW, rightH);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int smx, int smy, float pt) {
        int curY = leftY - (int) Math.round(leftScroll.smoothOffset());
        enableCanvasScissor(g, leftX, leftY, leftX + leftW, leftY + leftH);
        for (LeftEntry e : leftEntries) {
            if (curY + e.h >= leftY && curY <= leftY + leftH) {
                e.x = leftX + 2;
                e.y = curY; e.w = leftW - 4; e.render(g, smx, smy);
            }
            curY += e.h + 1;
        }
        disableCanvasScissor(g);

        GuiTheme.scrollbar(
                leftScroll,
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
            Component countText = Component.translatable(
                    "gui.itemcontrol.item.banitem.merge_count",
                    rightDisplayList.size(),
                    allItemsCache.size()
            );
            g.drawString(font, countText, countX, rightInfoY, 0xFFFFFF, false);

            int gridX = rightX + 2;
            int gridY = rightY;
            enableCanvasScissor(g, rightX, gridY, rightX + rightW, gridY + gridAreaH);
            for (int i = 0; i < rightDisplayList.size(); i++) {
                int col = i % gridCols;
                int row = i / gridCols;
                int rX = gridX + col * SLOT_PITCH;
                int rY = gridY + row * SLOT_PITCH - (int) Math.round(rightScroll.smoothOffset());
                if (rY + SLOT_SIZE > gridY && rY < gridY + gridAreaH) {
                    ItemStack stack = rightDisplayList.get(i).stack();
                    boolean hovered = smx >= rX && smx < rX + SLOT_SIZE
                            && smy >= rY && smy < rY + SLOT_SIZE;
                    GuiTheme.itemSlot(
                            g,
                            rX,
                            rY,
                            SLOT_SIZE,
                            4,
                            hovered
                    );

                    ItemBanControl.withSkip(() -> {
                        GuiTheme.item(
                                g,
                                font,
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
            disableCanvasScissor(g);

            GuiTheme.scrollbar(
                    rightScroll,
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

    private boolean isHoveringButton(StateButton btn, double mx, double my) { return btn != null && ((KineticControl) btn).isVisible() && mx >= btn.getX() && mx < btn.getX() + btn.getWidth() && my >= btn.getY() && my < btn.getY() + btn.getHeight();
    }

    @Override
    protected void renderTooltips(@NotNull GuiGraphics g, int smx, int smy, int mx, int my) {
        int tooltipY = smy < leftY ? my + 15 : my;

        if (isHoveringButton(addBtn, smx, smy)) { KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.add"), mx, tooltipY); return;
        }
        if (isHoveringButton(saveBtn, smx, smy)) { KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.save"), mx, tooltipY); return;
        }
        if (isHoveringButton(closeBtn, smx, smy)) { KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.back"), mx, tooltipY); return;
        }
        if (isHoveringButton(tagFilterBtn, smx, smy)) {
            Set<ItemUnificationHelper.MergeGroup> groups = selectedTarget == null ? Collections.emptySet() : ItemSearchCache.getUnificationGroupsForId(selectedTarget);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.merge_tag_filter.title"));
            tooltip.add(Component.translatable(
                    groups.isEmpty() ? "gui.itemcontrol.item.banitem.tooltip.merge_tag_filter.none" : "gui.itemcontrol.item.banitem.tooltip.merge_tag_filter.desc",
                    Component.literal(String.valueOf(groups.size())).withStyle(ChatFormatting.AQUA)
            ));
            KineticOverlays.requestTooltip(tooltip, mx, tooltipY);
            return;
        }

        if (isCreatingRule || selectedTarget != null) {
            int countX = getRightCountX();
            Component countText = Component.translatable(
                    "gui.itemcontrol.item.banitem.merge_count",
                    rightDisplayList.size(),
                    allItemsCache.size()
            );
            if (smx >= countX && smx < countX + font.width(countText) && smy >= rightInfoY && smy < rightInfoY + font.lineHeight) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.count.title"));
                tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.count.merge.desc"));
                KineticOverlays.requestTooltip(tooltip, mx, tooltipY);
                return;
            }
        }

        if (smx >= leftX && smx < leftX + leftW && smy >= leftY && smy < leftY + leftH) {
            int currentY = leftY - (int) Math.round(leftScroll.smoothOffset());
            for (LeftEntry entry : leftEntries) {
                if (currentY + entry.h >= leftY && currentY <= leftY + leftH) {
                    if (smx >= entry.x && smx < entry.x + entry.w && smy >= currentY && smy < currentY + entry.h) { entry.requestTooltip(g, mx, my);
                        break; }
                }
                currentY += entry.h + 1;
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
                tt.add(Component.translatable(isCreatingRule ? "gui.itemcontrol.item.banitem.tooltip.set_target" : "gui.itemcontrol.item.banitem.tooltip.add_source"));
                KineticOverlays.requestTooltip(tt, mx, my);
            }
        }
    }

    @Override
    protected boolean canvasMouseClicked(double smx, double smy, int btn) {
        if (KineticMouseButtons.isPrimary(btn)
                && leftScroll.beginDrag(
                        smx,
                        smy,
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

        if (KineticMouseButtons.isPrimary(btn)
                && rightScroll.beginDrag(
                        smx,
                        smy,
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
                        if (entry.mouseClicked(smx, smy, btn)) return true;
                    }
                }
                currentY += entry.h + 1;
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
                if (KineticMouseButtons.isPrimary(btn)) {
                    String id = rightDisplayList.get(idx).id();
                    if (isCreatingRule) { tempRules.putIfAbsent(id, new ArrayList<>()); selectedTarget = id; isCreatingRule = false; targetTagFilterActive = false; expandedTargets.add(id);
                    }
                    else { tempRules.get(selectedTarget).add(id);
                    }
                    updateLeftEntries(); updateRightPanel(); return true;
                }
            }
        }
        return super.canvasMouseClicked(smx, smy, btn);
    }

    @Override
    protected boolean canvasMouseReleased(double smx, double smy, int btn) {
        boolean released =
                leftScroll.release(btn)
                        | rightScroll.release(btn);

        return released
                || super.canvasMouseReleased(smx, smy, btn);
    }

    @Override
    protected boolean canvasMouseDragged(double smx, double smy, int btn, double dx, double dy) {
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

        return super.canvasMouseDragged(smx, smy, btn, dx, dy);
    }

    @Override
    protected boolean canvasMouseScrolled(double smx, double smy, double d) {
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

        return super.canvasMouseScrolled(smx, smy, d);
    }

    abstract static class LeftEntry { int x, y, w, h;
        abstract void render(GuiGraphics g, int mx, int my); abstract boolean mouseClicked(double mx, double my, int btn);
        abstract void requestTooltip(GuiGraphics g, int mx, int my); }

    class TargetEntry extends LeftEntry {
        String id;
        ItemStack stack; int count;
        TargetEntry(String id, int count) { this.id = id; this.stack = BanItemConfig.parseItemStack(id); this.count = count;
        }
        void render(GuiGraphics g, int mx, int my) {
            boolean selected = id.equals(selectedTarget), hover = mx >= x && mx < x + w && my >= y && my < y + h;
            GuiTheme.stateSurface(g, x, y, w, h, GuiTheme.Surface.PANEL_ALT, selected, hover, false);
            GuiTheme.itemSlot(g, x, y + 1, 20, 4, hover);
            ItemBanControl.withSkip(() -> { GuiTheme.item(g, font, stack, x, y + 1, 20, 1.0F, false); return null; });
            g.drawString(font, ItemCacheHudRenderer.getDisplayNameCustom(stack), x + 24, y + 7, 0xFFFFFF, false);
            g.drawString(font, Component.translatable("gui.itemcontrol.item.common.count_parentheses", Component.literal(String.valueOf(count)).withStyle(ChatFormatting.YELLOW)), x + w - 24, y + 7, 0xFFFFFF, false);
            g.drawString(font, Component.translatable(expandedTargets.contains(id) ? "gui.itemcontrol.item.common.collapse" : "gui.itemcontrol.item.common.expand"), x + w - 36, y + 7, 0xFFFFFF, false);
        }
        boolean mouseClicked(double mx, double my, int btn) {
            if (KineticClientRuntime.shiftModifierDown() && KineticMouseButtons.isPrimary(btn)) { openNbtEditor(id, 2, "", stack);
                return true; }
            if (KineticMouseButtons.isPrimary(btn)) { if (expandedTargets.contains(id)) expandedTargets.remove(id);
            else expandedTargets.add(id); selectedTarget = id; isCreatingRule = false; targetTagFilterActive = false; updateLeftEntries(); updateRightPanel(); return true;
            }
            else if (KineticMouseButtons.isSecondary(btn)) { tempRules.remove(id);
                if (id.equals(selectedTarget)) { selectedTarget = null; targetTagFilterActive = false; } expandedTargets.remove(id); updateLeftEntries(); updateRightPanel(); return true;
            }
            return false;
        }
        void requestTooltip(GuiGraphics g, int mx, int my) {
            List<Component> tt = new ArrayList<>();
            tt.add(ItemCacheHudRenderer.getDisplayNameCustom(stack)); tt.add(Component.literal(id));
            tt.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.shift_edit_nbt"));
            tt.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.target_del"));
            KineticOverlays.requestTooltip(tt, mx, my);
        }
    }

    class SourceEntry extends LeftEntry {
        String targetId, sourceId;
        ItemStack stack;
        SourceEntry(String t, String s) { targetId = t; sourceId = s; stack = BanItemConfig.parseItemStack(s);
        }
        void render(GuiGraphics g, int mx, int my) {
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
            GuiTheme.stateSurface(g, x, y, w, h, GuiTheme.Surface.PANEL_ALT, false, hover, false);
            GuiTheme.itemSlot(g, x + 10, y, 12, 3, hover);
            ItemBanControl.withSkip(() -> { GuiTheme.item(g, font, stack, x + 10, y, 12, 0.5F, false); return null; });
            g.pose().pushPose(); g.pose().translate(x + 24, y + 2.5f, 0); g.pose().scale(0.8f, 0.8f, 1.0f); g.drawString(font, ItemCacheHudRenderer.getDisplayNameCustom(stack), 0, 0, 0xFFAAAAAA, false); g.pose().popPose();
        }
        boolean mouseClicked(double mx, double my, int btn) {
            if (KineticClientRuntime.shiftModifierDown() && KineticMouseButtons.isPrimary(btn)) { openNbtEditor(sourceId, 3, targetId, stack);
                return true; }
            if (KineticMouseButtons.isSecondary(btn)) { tempRules.get(targetId).remove(sourceId);
                updateLeftEntries(); updateRightPanel(); return true; }
            return false;
        }
        void requestTooltip(GuiGraphics g, int mx, int my) {
            List<Component> tt = new ArrayList<>();
            tt.add(ItemCacheHudRenderer.getDisplayNameCustom(stack)); tt.add(Component.literal(sourceId));
            tt.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.shift_edit_nbt"));
            tt.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.source_del"));
            KineticOverlays.requestTooltip(tt, mx, my);
        }
    }
}
