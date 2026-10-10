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

import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.itemcontrol.item.util.ItemBanControl;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BannedItemPage extends KineticPage {

    private KineticTextField searchBox;
    private KineticButton ruleBtn, saveBtn, viewBtn, closeBtn;
    private static int viewMode = 0;
    private static int rememberedScrollOffset = 0;
    private static String lastSearchQuery = "";
    private List<KineticItemSearch.CachedItem> currentSourceList = new ArrayList<>();
    private List<KineticItemSearch.CachedItem> displayList = new ArrayList<>();

    private boolean isAutoCompleteMode = false;
    private List<String> autoCompleteList = new ArrayList<>();
    private final List<String> allTags = new ArrayList<>();
    private final List<String> allMods = new ArrayList<>();

    private static final int SLOT_SIZE = 22;
    private static final int SLOT_PITCH = SLOT_SIZE + 2;
    // Leave a gap between self-drawn text and the next control or panel edge.
    private static final int TEXT_GAP = 4;
    private final KineticScrollController gridScroll = new KineticScrollController();
    private int gridX, gridY, gridCols, contentW, contentH, gridW;
    private boolean compactToolbar;
    private int infoY;
    private int totalH = 0;

    public BannedItemPage() {
        super(KineticI18n.translatable("gui.itemcontrol.item.banitem.title"));
        useCanvas(
                640,
                360,
                4
        );

        configureDraft(BanItemConfig::snapshotData, this::restoreBanSnapshot);
    }

    private void restoreBanSnapshot(BanItemConfig.Data snapshot) {
        BanItemConfig.restoreDataSnapshot(snapshot);
        ItemSearchCache.markRulesChanged();
        if (searchBox != null) updateSearch(searchBox.textValue());
    }

    @Override
    protected void build(KineticUi ui) {
        allMods.clear();
        allMods.addAll(ItemSearchCache.getAllMods());

        allTags.clear();
        allTags.addAll(ItemSearchCache.getAllTags());

        int sidePadding =
                switch (layoutMetrics().level()) {
                    case LARGE -> 14;
                    case NORMAL -> 10;
                    case SMALL -> 8;
                    case COMPACT -> 6;
                };

        compactToolbar =
                isPortraitLayout()
                        || isCompactLayout()
                        || width() < 520;

        int searchY = 5;
        int spacing = 5;
        int ruleBtnW = 60;

        gridY =
                compactToolbar
                        ? 76
                        : 30;

        int availableWidth =
                Math.max(
                        SLOT_SIZE,
                        width()
                                - sidePadding * 2
                                - 12
                );

        gridCols =
                Math.max(
                        1,
                        (availableWidth + SLOT_PITCH - SLOT_SIZE) / SLOT_PITCH
                );

        contentW =
                gridCols * SLOT_PITCH - (SLOT_PITCH - SLOT_SIZE);

        gridW = contentW;

        gridX =
                Math.max(
                        sidePadding,
                        (
                                width()
                                        - contentW
                                        - 8
                        ) / 2
                );

        contentH =
                Math.max(
                        SLOT_SIZE,
                        Math.max(
                                1,
                                (Math.max(SLOT_SIZE, height() - gridY - 8) + SLOT_PITCH - SLOT_SIZE) / SLOT_PITCH
                        ) * SLOT_PITCH - (SLOT_PITCH - SLOT_SIZE)
                );

        int rightEdge =
                gridX + contentW;

        int btnW =
                compactToolbar
                        ? Math.max(
                                42,
                                (
                                        contentW
                                                - spacing * 2
                                ) / 3
                        )
                        : 80;

        int searchW =
                compactToolbar
                        ? Math.max(
                                80,
                                contentW
                                        - ruleBtnW
                                        - 2 - btnW - spacing
                        )
                        : 120;

        searchBox = ui().textField(gridX + btnW + spacing, searchY, searchW).build();
        searchBox.setPlaceholder(KineticI18n.translatable("gui.itemcontrol.item.banitem.search.hint"));
        searchBox.setTextValue(lastSearchQuery);
        searchBox.setDefaultText(lastSearchQuery);
        searchBox.onTextChange(this::updateSearch);

        ruleBtn = ui().button(searchBox.controlX() + searchBox.controlWidth() + 2, searchY, ruleBtnW).text(Component.empty()).tooltip(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.btn.rule_desc")).onClick(() -> {
                    String query = searchBox.textValue().trim().toLowerCase(Locale.ROOT);
                    if (BanItemConfig.isProtected(query)) return;
                    if (query.startsWith("@") || query.startsWith("#")) {
                        if (BanItemConfig.data.bannedItems.contains(query)) {
                            BanItemConfig.data.bannedItems.remove(query);
                        } else {
                            BanItemConfig.data.bannedItems.add(query);
                        }
                        BanItemConfig.rebuildCache();
                        ItemSearchCache.markRulesChanged();
                        updateSearch(searchBox.textValue());
                    }
                }).build();
        ruleBtn.setControlVisible(false);

        int buttonY =
                compactToolbar
                        ? 31
                        : searchY;

        int saveX;
        int viewX;
        int closeX;

        if (compactToolbar) {
            saveX = gridX;
            viewX = saveX + btnW + spacing;
            closeX = gridX;
        } else {
            closeX = gridX;
            viewX = rightEdge - btnW;
            saveX = viewX - spacing - btnW;
        }
        saveBtn = ui().button(saveX, buttonY, btnW).text(KineticI18n.translatable("gui.itemcontrol.item.banitem.btn.save")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.btn.save")).onClick(() -> {
                    String jsonData = BanItemConfig.GSON.toJson(BanItemConfig.data);
                    ItemNetwork.CHANNEL.sendToServer(
                            new ItemNetwork.SaveBanConfigPacket(ItemNetwork.EDITOR_BAN_ITEM, jsonData)
                    );
                }).build();

        viewBtn = ui().button(viewX, buttonY, btnW).text(getViewModeText()).tooltip(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.btn.view")).onClick(() -> {
                    viewMode = (viewMode + 1) % 3;
                    viewBtn.setText(getViewModeText());
                    updateSearch(searchBox.textValue());
                }).build();

        closeBtn = ui().button(closeX, searchY, btnW).text(KineticI18n.translatable("gui.itemcontrol.item.banitem.btn.back")).tooltip(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.btn.back")).onClick(this::close).build();

        // Keep the permanent toolbar controls explicit through the public Kinetic contract.
        // The rule button is the only conditional control in this row.
        saveBtn.setText(KineticI18n.translatable("gui.itemcontrol.item.banitem.btn.save"));
        saveBtn.setControlVisible(true);
        saveBtn.setEnabled(true);
        viewBtn.setText(getViewModeText());
        viewBtn.setControlVisible(true);
        viewBtn.setEnabled(true);
        closeBtn.setText(KineticI18n.translatable("gui.itemcontrol.item.banitem.btn.back"));
        closeBtn.setControlVisible(true);
        closeBtn.setEnabled(true);

        infoY =
                compactToolbar
                        ? 57
                        : 11;

        updateSearch(lastSearchQuery);

        ItemSearchCache.prepareCache(() -> {
            if (!isOpen()) return;
            allMods.clear();
            allMods.addAll(ItemSearchCache.getAllMods());
            allTags.clear();
            allTags.addAll(ItemSearchCache.getAllTags());
            updateSearch(searchBox == null ? lastSearchQuery : searchBox.textValue());
        });
    }

    public void applySaveResult(boolean success) {
        if (success) commitDraft();
    }

    private Component getViewModeText() {
        if (viewMode == 0) return KineticI18n.translatable("gui.itemcontrol.item.banitem.view.all");
        if (viewMode == 1) return KineticI18n.translatable("gui.itemcontrol.item.banitem.view.banned");
        return KineticI18n.translatable("gui.itemcontrol.item.banitem.view.inventory");
    }

    private List<KineticItemSearch.CachedItem> getInventoryItems() {
        return ItemSearchCache.getInventoryItems();
    }

    private void updateSearch(String text) {
        String query = text.trim().toLowerCase(Locale.ROOT);
        boolean changed = !query.equals(lastSearchQuery);
        lastSearchQuery = query;

        if (query.startsWith("@")) {
            if (allMods.contains(query) && !query.equals("@")) {
                isAutoCompleteMode = false;
                ruleBtn.setControlVisible(true);
                boolean isRuleBanned = BanItemConfig.data.bannedItems.contains(query);
                ruleBtn.setText(KineticI18n.translatable(isRuleBanned ? "gui.itemcontrol.item.banitem.rule.unban" : "gui.itemcontrol.item.banitem.rule.ban"));
                updateDisplayListForExactMatch(query);
            } else {
                isAutoCompleteMode = true;
                ruleBtn.setControlVisible(false);
                autoCompleteList = ItemSearchCache.searchStrings("ban_mod", allMods, query);
                totalH = autoCompleteList.size() * SLOT_PITCH;
            }
        } else if (query.startsWith("#")) {
            if (allTags.contains(query) && !query.equals("#")) {
                isAutoCompleteMode = false;
                ruleBtn.setControlVisible(true);
                boolean isRuleBanned = BanItemConfig.data.bannedItems.contains(query);
                ruleBtn.setText(KineticI18n.translatable(isRuleBanned ? "gui.itemcontrol.item.banitem.rule.unban" : "gui.itemcontrol.item.banitem.rule.ban"));
                updateDisplayListForExactMatch(query);
            } else {
                isAutoCompleteMode = true;
                ruleBtn.setControlVisible(false);
                autoCompleteList = ItemSearchCache.searchStrings("ban_tag", allTags, query);
                totalH = autoCompleteList.size() * SLOT_PITCH;
            }
        } else {
            isAutoCompleteMode = false;
            ruleBtn.setControlVisible(false);
            updateDisplayListForExactMatch(query);
        }

        if (!isAutoCompleteMode) {
            int totalRows = (int) Math.ceil((double) displayList.size() / gridCols);
            totalH = totalRows * SLOT_PITCH;
        }

        gridScroll.update(totalH, contentH);

        if (changed) {
            gridScroll.reset();
        } else {
            gridScroll.setOffset(rememberedScrollOffset);
        }

        rememberedScrollOffset = gridScroll.offset();
    }

    private void updateDisplayListForExactMatch(String query) {
        if (viewMode == 0) {
            currentSourceList = ItemSearchCache.getAllItems();
        } else if (viewMode == 2) {
            currentSourceList = getInventoryItems();
        } else {
            currentSourceList = ItemSearchCache.getBannedSourceList();
        }

        int sourceHash = viewMode == 0 ? ItemSearchCache.getAllItemsHash() : ItemSearchCache.hashCachedItems(currentSourceList);
        displayList = ItemSearchCache.searchItems("ban_display_" + viewMode, currentSourceList, query, sourceHash);
    }

    //? if >=1.21 {
/*private void openNbtEditor(String idStr, boolean isRule, ItemStack fallbackStack) {
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
                String cleanNbt = savedNbt == null ? "" : savedNbt.trim();
                String newIdStr = baseId + cleanNbt;
                if (isRule) removeBanRule(idStr);
                addBanRule(newIdStr);
                viewMode = 1;
                gridScroll.reset();
                rememberedScrollOffset = 0;
                if (viewBtn != null) viewBtn.setText(getViewModeText());
                updateSearch(searchBox == null ? "" : searchBox.textValue());
            });
        }
    }*/
//?} else {
private void openNbtEditor(String idStr, boolean isRule, ItemStack fallbackStack) {
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
                String cleanNbt = savedNbt == null ? "" : savedNbt.trim();
                String newIdStr = baseId + cleanNbt;
                if (isRule) removeBanRule(idStr);
                addBanRule(newIdStr);
                viewMode = 1;
                gridScroll.reset();
                rememberedScrollOffset = 0;
                if (viewBtn != null) viewBtn.setText(getViewModeText());
                updateSearch(searchBox == null ? "" : searchBox.textValue());
            });
        }
    }
//?}


    private String getRuleIdentifier(KineticItemSearch.CachedItem cachedItem) {
        if (cachedItem == null) return "";
        String idStr = cachedItem.id() == null ? "" : cachedItem.id().trim();
//? if >=1.21 {
/*        if (idStr.startsWith("@") || idStr.startsWith("#") || idStr.contains("[")) return idStr;*/
//?} else {
        if (idStr.startsWith("@") || idStr.startsWith("#") || idStr.contains("{")) return idStr;
//?}
        String identifier = BanItemConfig.getItemIdentifier(cachedItem.stack());
        return identifier.isBlank() ? idStr : identifier;
    }

    private String getListedRuleFor(KineticItemSearch.CachedItem cachedItem) {
        if (cachedItem == null || BanItemConfig.data == null || BanItemConfig.data.bannedItems == null) return "";
        String identifier = getRuleIdentifier(cachedItem);
        if (!identifier.isBlank() && BanItemConfig.data.bannedItems.contains(identifier)) return identifier;
        String idStr = cachedItem.id() == null ? "" : cachedItem.id().trim();
        if (!idStr.isBlank() && BanItemConfig.data.bannedItems.contains(idStr)) return idStr;
        String baseId = BanItemConfig.getBaseIdentifier(identifier);
        if (!baseId.isBlank() && BanItemConfig.data.bannedItems.contains(baseId)) return baseId;
        return "";
    }

    private void addBanRule(String rule) {
        if (rule == null || rule.isBlank() || BanItemConfig.isProtected(rule)) return;
//? if >=1.21 {
/*        if (rule.contains("[")) {*/
//?} else {
        if (rule.contains("{")) {
//?}
            String baseId = BanItemConfig.getBaseIdentifier(rule);
            if (!baseId.isBlank()) BanItemConfig.data.bannedItems.remove(baseId);
        }
        if (!BanItemConfig.data.bannedItems.contains(rule)) BanItemConfig.data.bannedItems.add(rule);
        BanItemConfig.rebuildCache();
        ItemSearchCache.markRulesChanged();
    }

    private void removeBanRule(String rule) {
        if (rule == null || rule.isBlank()) return;
        BanItemConfig.data.bannedItems.remove(rule);
        BanItemConfig.rebuildCache();
        ItemSearchCache.markRulesChanged();
    }

    @Override
    protected void renderBackground(KineticGraphics g, int smx, int smy, float pt) {
        KineticTheme.canvasBackground(g, width(), height());
        KineticTheme.panelAlt(g, gridX - 3, gridY - 3, contentW + 12, contentH + 6);
    }

    @Override
    protected void renderForeground(KineticGraphics g, int smx, int smy, float pt) {
        Component countText;

        int countX =
                compactToolbar
                        ? gridX
                        : searchBox.controlX()
                        + searchBox.controlWidth()
                        + 10;

        if (!compactToolbar
                && ruleBtn != null
                && ruleBtn.controlVisible()) {
            countX =
                    ruleBtn.controlX()
                            + ruleBtn.controlWidth()
                            + 10;
        }
        int countWidth = Math.max(0, (compactToolbar ? gridX + contentW : saveBtn.controlX()) - countX - TEXT_GAP);

        if (isAutoCompleteMode) {
            countText = KineticI18n.translatable(
                    "gui.itemcontrol.item.banitem.autocomplete.matches_count",
                    Component.literal(String.valueOf(autoCompleteList.size()))
            );
            g.scrollingText(countText, countX, infoY, countWidth, KineticTheme.current().mutedText(), false);

            g.scissor(gridX,
                    gridY,
                    gridX + contentW,
                    gridY + contentH
            );
            for (int i = 0; i < autoCompleteList.size(); i++) {
                String entry = autoCompleteList.get(i);
                int y = gridY + i * SLOT_PITCH - (int) Math.round(gridScroll.smoothOffset());
                if (y + SLOT_SIZE > gridY && y < gridY + contentH) {
                    boolean hovered = smx >= gridX && smx < gridX + gridW && smy >= y && smy < y + SLOT_SIZE;
                    KineticTheme.stateSurface(
                            g,
                            gridX,
                            y,
                            gridW,
                            SLOT_SIZE,
                            i % 2 == 0 ? KineticTheme.Surface.PANEL : KineticTheme.Surface.PANEL_ALT,
                            false,
                            hovered,
                            false
                    );
                    g.scrollingText(Component.literal(entry), gridX + 5, y + 6, gridW - 5 - TEXT_GAP, 0xFFFFFF, true);
                }
            }
        } else {
            countText = KineticI18n.translatable(
                    viewMode == 1 ? "gui.itemcontrol.item.banitem.count.banned" : "gui.itemcontrol.item.banitem.count.normal",
                    displayList.size(),
                    currentSourceList.size()
            );
            g.scrollingText(countText, countX, infoY, countWidth, KineticTheme.current().mutedText(), false);

            g.scissor(gridX,
                    gridY,
                    gridX + contentW,
                    gridY + contentH
            );
            for (int i = 0; i < displayList.size(); i++) {
                KineticItemSearch.CachedItem ci = displayList.get(i);
                int col = i % gridCols;
                int row = i / gridCols;
                int x = gridX + col * SLOT_PITCH;
                int y = gridY + row * SLOT_PITCH - (int) Math.round(gridScroll.smoothOffset());
                if (y + SLOT_SIZE > gridY && y < gridY + contentH) {
                    boolean hovered = smx >= x && smx < x + SLOT_SIZE
                            && smy >= y && smy < y + SLOT_SIZE;
                    KineticTheme.itemSlot(
                            g,
                            x,
                            y,
                            SLOT_SIZE,
                            4,
                            hovered
                    );

                    ItemBanControl.withSkip(() -> {
                        KineticTheme.item(
                                g,
                                ci.stack(),
                                x,
                                y,
                                SLOT_SIZE,
                                1.0F,
                                true
                        );
                        return null;
                    });
                    boolean isBannedMarker = ci.id().startsWith("@") || ci.id().startsWith("#") || BanItemConfig.isBanned(ci.stack());
                    if (isBannedMarker) {
                        KineticTheme.indicatorFill(g, x + 2, y + SLOT_SIZE - 3, SLOT_SIZE - 3, 2, KineticTheme.Indicator.DANGER);
                    }
                }
            }
        }
        g.endScissor();

        gridScroll.render(
                g,
                smx,
                smy,
                gridX + contentW + 2,
                gridY,
                4,
                contentH,
                20
        );

    }

    private boolean isHoveringButton(KineticButton btn, double mx, double my) {
        return btn != null && btn.controlVisible() && btn.contains(mx, my);
    }

    @Override
    protected void renderTooltips(int smx, int smy) {
        // 原实现按屏幕坐标请求提示并在顶部下移 15 像素；这里用 GUI 缩放后的指针位置保持一致
        // The old code requested tooltips in screen coordinates, shifted 15 px down near the top; the GUI-scaled cursor
        // position keeps that behaviour.
        KineticClientRuntime.CursorPosition cursor = KineticClientRuntime.scaledCursorPosition();
        int mx = (int) cursor.x();
        int my = (int) cursor.y();
        int tooltipY = smy < 30 ? my + 15 : my;

        if (isHoveringButton(saveBtn, smx, smy)) { KineticOverlays.requestTooltip(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.btn.save"), mx, tooltipY); return; }
        if (isHoveringButton(viewBtn, smx, smy)) { KineticOverlays.requestTooltip(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.btn.view"), mx, tooltipY); return; }
        if (isHoveringButton(closeBtn, smx, smy)) { KineticOverlays.requestTooltip(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.btn.back"), mx, tooltipY); return; }
        if (isHoveringButton(ruleBtn, smx, smy)) { KineticOverlays.requestTooltip(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.btn.rule_desc"), mx, tooltipY); return; }

        if (!isAutoCompleteMode) {
            int countX =
                    compactToolbar
                            ? gridX
                            : searchBox.controlX()
                            + searchBox.controlWidth()
                            + 10;

            if (!compactToolbar
                    && ruleBtn != null
                    && ruleBtn.controlVisible()) {
                countX =
                        ruleBtn.controlX()
                                + ruleBtn.controlWidth()
                                + 10;
            }
            String rawStr = displayList.size() + " / " + currentSourceList.size();

            if (smx >= countX && smx < countX + KineticText.width(rawStr) && smy >= infoY && smy < infoY + KineticText.lineHeight()) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.count.title"));
                if (viewMode == 0) tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.count.all_view.desc"));
                else if (viewMode == 1) tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.count.banned_view.desc"));
                else tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.view.inventory"));
                KineticOverlays.requestTooltip(tooltip, mx, tooltipY);
                return;
            }
            if (smx >= gridX && smx < gridX + contentW && smy >= gridY && smy < gridY + contentH) {
                int localX = smx - gridX;
                int localY = (int) Math.floor(smy - gridY + gridScroll.smoothOffset());
                int col = localX / SLOT_PITCH;
                int row = localY / SLOT_PITCH;
                int idx = row * gridCols + col;
                if (col >= 0 && col < gridCols
                        && localX % SLOT_PITCH < SLOT_SIZE
                        && localY % SLOT_PITCH < SLOT_SIZE
                        && idx >= 0 && idx < displayList.size()) {
                    ItemBanControl.withSkip(() -> {
                        KineticItemSearch.CachedItem ci = displayList.get(idx);
                        List<Component> tooltip = new ArrayList<>();
                        if (ci.id().startsWith("@") || ci.id().startsWith("#")) {
                            tooltip.add(Component.literal(ci.id()));
                            tooltip.add(KineticI18n.translatable(ci.id().startsWith("@") ? "gui.itemcontrol.item.banitem.tooltip.mod_rule" : "gui.itemcontrol.item.banitem.tooltip.tag_rule"));
                            tooltip.add(Component.empty());
                            tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.right_unban_rule"));
                        } else {
                            String ruleIdentifier = getRuleIdentifier(ci);
                            String listedRule = getListedRuleFor(ci);
                            boolean isProtected = BanItemConfig.isProtected(ruleIdentifier);
                            tooltip.add(ItemCacheHudRenderer.getDisplayNameCustom(ci.stack()));
                            tooltip.add(Component.literal(ruleIdentifier));
                            tooltip.add(Component.empty());
                            tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.shift_edit_nbt"));
                            if (isProtected) {
                                tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.protected"));
                            } else if (BanItemConfig.isBanned(ci.stack())) {
                                if (!listedRule.isBlank()) tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.right_unban"));
                                else tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.banned_by_rule"));
                            } else tooltip.add(KineticI18n.translatable("gui.itemcontrol.item.banitem.tooltip.left_ban"));
                        }
                        KineticOverlays.requestTooltip(tooltip, mx, my);
                        return null;
                    });
                }
            }
        }
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 canvasMouseClicked 全部在控件之前处理 / The old canvasMouseClicked handled all of this before controls.
        double smx = input.x();
        double smy = input.y();
        if (gridScroll.beginDrag(
                        smx,
                        smy,
                        input.button(),
                        gridX + contentW + 2,
                        gridY,
                        4,
                        contentH,
                        20,
                        0
                )) {
            rememberedScrollOffset = gridScroll.offset();
            return true;
        }
        if (smx >= gridX && smx < gridX + contentW && smy >= gridY && smy < gridY + contentH) {
            if (isAutoCompleteMode) {
                int localY = (int) (smy - gridY + gridScroll.smoothOffset());
                int idx = localY / SLOT_PITCH;
                if (localY % SLOT_PITCH < SLOT_SIZE && idx >= 0 && idx < autoCompleteList.size()) { if (searchBox != null) searchBox.setTextValue(autoCompleteList.get(idx)); return true;
                }
            } else {
                int localX = (int) (smx - gridX);
                int localY = (int) (smy - gridY + gridScroll.smoothOffset());
                int col = localX / SLOT_PITCH;
                int row = localY / SLOT_PITCH;
                int idx = row * gridCols + col;
                if (col >= 0 && col < gridCols
                        && localX % SLOT_PITCH < SLOT_SIZE
                        && localY % SLOT_PITCH < SLOT_SIZE
                        && idx >= 0 && idx < displayList.size()) {
                    KineticItemSearch.CachedItem ci = displayList.get(idx);
                    String ruleIdentifier = getRuleIdentifier(ci);
                    if (BanItemConfig.isProtected(ruleIdentifier)) return true;
                    if (ruleIdentifier.startsWith("@") || ruleIdentifier.startsWith("#")) {
                        if (input.isRight()) {
                            removeBanRule(ruleIdentifier);
                            updateSearch(searchBox.textValue());
                        }
                        return true;
                    }
                    String listedRule = getListedRuleFor(ci);
                    if (KineticClientRuntime.shiftModifierDown() && input.isLeft()) {
                        openNbtEditor(ruleIdentifier, !listedRule.isBlank(), ci.stack());
                        return true;
                    }
                    if (input.isLeft() && listedRule.isBlank() && !BanItemConfig.isBanned(ci.stack())) {
                        addBanRule(ruleIdentifier);
                        updateSearch(searchBox.textValue());
                    } else if (input.isRight() && !listedRule.isBlank()) {
                        removeBanRule(listedRule);
                        updateSearch(searchBox.textValue());
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        if (gridScroll.release(input.button())) {
            rememberedScrollOffset = gridScroll.offset();
            return true;
        }

        return false;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        if (gridScroll.drag(
                input.y(),
                gridY,
                contentH,
                20
        )) {
            rememberedScrollOffset = gridScroll.offset();
            return true;
        }

        return false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        if (gridScroll.scroll(input.deltaY(), SLOT_PITCH)) {
            rememberedScrollOffset = gridScroll.offset();
            return true;
        }

        return false;
    }

}
