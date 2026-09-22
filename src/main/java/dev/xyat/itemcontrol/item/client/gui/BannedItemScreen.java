package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BannedItemScreen extends KineticScreen {

    private final Screen parent;
    private KineticEditBox searchBox;
    private StateButton ruleBtn, saveBtn, viewBtn, closeBtn;
    private static int viewMode = 0;
    private static int rememberedScrollOffset = 0;
    private static String lastSearchQuery = "";
    private List<KineticItemSearch.CachedItem> currentSourceList = new ArrayList<>();
    private List<KineticItemSearch.CachedItem> displayList = new ArrayList<>();

    private boolean isAutoCompleteMode = false;
    private List<String> autoCompleteList = new ArrayList<>();
    private final List<String> allTags = new ArrayList<>();
    private final List<String> allMods = new ArrayList<>();

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_PITCH = 19;
    private final GridScrollController gridScroll = new GridScrollController();
    private int gridX, gridY, gridCols, contentW, contentH, gridW;
    private boolean compactToolbar;
    private int infoY;
    private int totalH = 0;

    public BannedItemScreen() {
        this(null);
    }

    public BannedItemScreen(Screen parent) {
        super(Component.translatable("gui.itemcontrol.item.banitem.title"));
        this.parent = parent;
        useCanvas(
                640f,
                360f,
                4
        );

        setParentScreen(parent);
        configureDraft(BanItemConfig::snapshotData, this::restoreBanSnapshot);
    }

    private void restoreBanSnapshot(BanItemConfig.Data snapshot) {
        BanItemConfig.restoreDataSnapshot(snapshot);
        ItemSearchCache.markRulesChanged();
        if (searchBox != null) updateSearch(searchBox.getValue());
    }

    @Override
    protected void buildUi() {
        allMods.clear();
        allMods.addAll(ItemSearchCache.getAllMods());

        allTags.clear();
        allTags.addAll(ItemSearchCache.getAllTags());

        int sidePadding =
                switch (layoutLevel()) {
                    case LARGE -> 14;
                    case NORMAL -> 10;
                    case SMALL -> 8;
                    case COMPACT -> 6;
                };

        compactToolbar =
                isPortraitLayout()
                        || isCompactLayout()
                        || canvasWidth() < 520;

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
                        canvasWidth()
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
                                canvasWidth()
                                        - contentW
                                        - 8
                        ) / 2
                );

        contentH =
                Math.max(
                        SLOT_SIZE,
                        Math.max(
                                1,
                                (Math.max(SLOT_SIZE, canvasHeight() - gridY - 8) + SLOT_PITCH - SLOT_SIZE) / SLOT_PITCH
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
                                        - 2
                        )
                        : 120;

        searchBox = addTextField(gridX, searchY, searchW, Component.empty());
        searchBox.setPlaceholder(Component.translatable("gui.itemcontrol.item.banitem.search.hint"));
        searchBox.setValue(lastSearchQuery);
        searchBox.setResponder(this::updateSearch);

        ruleBtn = addButton(
                searchBox.getX() + searchBox.getWidth() + 2,
                searchY,
                ruleBtnW,
                Component.empty(),
                Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.rule_desc"),
                () -> {
                    String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
                    if (BanItemConfig.isProtected(query)) return;
                    if (query.startsWith("@") || query.startsWith("#")) {
                        if (BanItemConfig.data.bannedItems.contains(query)) {
                            BanItemConfig.data.bannedItems.remove(query);
                        } else {
                            BanItemConfig.data.bannedItems.add(query);
                        }
                        BanItemConfig.rebuildCache();
                        ItemSearchCache.markRulesChanged();
                        updateSearch(searchBox.getValue());
                    }
                }
        );
        ((KineticControl) ruleBtn).setVisible(false);

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
            closeX = viewX + btnW + spacing;
        } else {
            closeX = rightEdge - btnW;
            viewX = closeX - spacing - btnW;
            saveX = viewX - spacing - btnW;
        }
        saveBtn = addButton(
                saveX, buttonY, btnW,
                Component.translatable("gui.itemcontrol.item.banitem.btn.save"),
                Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.save"),
                () -> {
                    String jsonData = BanItemConfig.GSON.toJson(BanItemConfig.data);
                    ItemNetwork.CHANNEL.sendToServer(
                            new ItemNetwork.SaveBanConfigPacket(ItemNetwork.EDITOR_BAN_ITEM, jsonData)
                    );
                }
        );

        viewBtn = addButton(
                viewX, buttonY, btnW,
                getViewModeText(),
                Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.view"),
                () -> {
                    viewMode = (viewMode + 1) % 3;
                    ((KineticControl) viewBtn).setText(getViewModeText());
                    updateSearch(searchBox.getValue());
                }
        );

        closeBtn = addButton(
                closeX, buttonY, btnW,
                Component.translatable("gui.itemcontrol.item.banitem.btn.back"),
                Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.back"),
                this::onClose
        );

        // Keep the permanent toolbar controls explicit through the public Kinetic contract.
        // The rule button is the only conditional control in this row.
        ((KineticControl) saveBtn).setText(Component.translatable("gui.itemcontrol.item.banitem.btn.save"));
        ((KineticControl) saveBtn).setVisible(true);
        ((KineticControl) saveBtn).setEnabled(true);
        ((KineticControl) viewBtn).setText(getViewModeText());
        ((KineticControl) viewBtn).setVisible(true);
        ((KineticControl) viewBtn).setEnabled(true);
        ((KineticControl) closeBtn).setText(Component.translatable("gui.itemcontrol.item.banitem.btn.back"));
        ((KineticControl) closeBtn).setVisible(true);
        ((KineticControl) closeBtn).setEnabled(true);

        infoY =
                compactToolbar
                        ? 57
                        : 11;

        updateSearch(lastSearchQuery);

        ItemSearchCache.prepareCache(() -> {
            if (KineticClientRuntime.currentScreen() != this) return;
            allMods.clear();
            allMods.addAll(ItemSearchCache.getAllMods());
            allTags.clear();
            allTags.addAll(ItemSearchCache.getAllTags());
            updateSearch(searchBox == null ? lastSearchQuery : searchBox.getValue());
        });
    }

    public void applySaveResult(boolean success) {
        if (success) commitDraft();
    }

    private Component getViewModeText() {
        if (viewMode == 0) return Component.translatable("gui.itemcontrol.item.banitem.view.all");
        if (viewMode == 1) return Component.translatable("gui.itemcontrol.item.banitem.view.banned");
        return Component.translatable("gui.itemcontrol.item.banitem.view.inventory");
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
                ((KineticControl) ruleBtn).setVisible(true);
                boolean isRuleBanned = BanItemConfig.data.bannedItems.contains(query);
                ((KineticControl) ruleBtn).setText(Component.translatable(isRuleBanned ? "gui.itemcontrol.item.banitem.rule.unban" : "gui.itemcontrol.item.banitem.rule.ban"));
                updateDisplayListForExactMatch(query);
            } else {
                isAutoCompleteMode = true;
                ((KineticControl) ruleBtn).setVisible(false);
                autoCompleteList = ItemSearchCache.searchStrings("ban_mod", allMods, query);
                totalH = autoCompleteList.size() * SLOT_PITCH;
            }
        } else if (query.startsWith("#")) {
            if (allTags.contains(query) && !query.equals("#")) {
                isAutoCompleteMode = false;
                ((KineticControl) ruleBtn).setVisible(true);
                boolean isRuleBanned = BanItemConfig.data.bannedItems.contains(query);
                ((KineticControl) ruleBtn).setText(Component.translatable(isRuleBanned ? "gui.itemcontrol.item.banitem.rule.unban" : "gui.itemcontrol.item.banitem.rule.ban"));
                updateDisplayListForExactMatch(query);
            } else {
                isAutoCompleteMode = true;
                ((KineticControl) ruleBtn).setVisible(false);
                autoCompleteList = ItemSearchCache.searchStrings("ban_tag", allTags, query);
                totalH = autoCompleteList.size() * SLOT_PITCH;
            }
        } else {
            isAutoCompleteMode = false;
            ((KineticControl) ruleBtn).setVisible(false);
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

        if (this.minecraft != null) {
            KineticSelectors.openNbtEditor(this, initNbt, (savedNbt) -> {
                String cleanNbt = savedNbt == null ? "" : savedNbt.trim();
                String newIdStr = baseId + cleanNbt;
                if (isRule) removeBanRule(idStr);
                addBanRule(newIdStr);
                viewMode = 1;
                gridScroll.reset();
                rememberedScrollOffset = 0;
                if (viewBtn != null) ((KineticControl) viewBtn).setText(getViewModeText());
                updateSearch(searchBox == null ? "" : searchBox.getValue());
            });
        }
    }

    private String getRuleIdentifier(KineticItemSearch.CachedItem cachedItem) {
        if (cachedItem == null) return "";
        String idStr = cachedItem.id() == null ? "" : cachedItem.id().trim();
        if (idStr.startsWith("@") || idStr.startsWith("#") || idStr.contains("{")) return idStr;
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
        if (rule.contains("{")) {
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
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int smx, int smy, float pt) {
        GuiTheme.canvasBackground(g, canvasWidth(), canvasHeight());
        GuiTheme.panelAlt(g, gridX - 3, gridY - 3, contentW + 12, contentH + 6);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int smx, int smy, float pt) {
        Component countText;

        int countX =
                compactToolbar
                        ? gridX
                        : searchBox.getX()
                        + searchBox.getWidth()
                        + 10;

        if (!compactToolbar
                && ruleBtn != null
                && ((KineticControl) ruleBtn).isVisible()) {
            countX =
                    ruleBtn.getX()
                            + ruleBtn.getWidth()
                            + 10;
        }

        if (isAutoCompleteMode) {
            countText = Component.translatable(
                    "gui.itemcontrol.item.banitem.autocomplete.matches_count",
                    Component.literal(String.valueOf(autoCompleteList.size())).withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GRAY);
            g.drawString(font, countText, countX, infoY, 0xFFFFFF, false);

            enableCanvasScissor(
                    g,
                    gridX,
                    gridY,
                    gridX + contentW,
                    gridY + contentH
            );
            for (int i = 0; i < autoCompleteList.size(); i++) {
                String entry = autoCompleteList.get(i);
                int y = gridY + i * SLOT_PITCH - (int) Math.round(gridScroll.smoothOffset());
                if (y + SLOT_SIZE > gridY && y < gridY + contentH) {
                    boolean hovered = smx >= gridX && smx < gridX + gridW && smy >= y && smy < y + SLOT_SIZE;
                    GuiTheme.stateSurface(
                            g,
                            gridX,
                            y,
                            gridW,
                            SLOT_SIZE,
                            i % 2 == 0 ? GuiTheme.Surface.PANEL : GuiTheme.Surface.PANEL_ALT,
                            false,
                            hovered,
                            false
                    );
                    g.drawString(font, entry, gridX + 5, y + 6, 0xFFFFFF);
                }
            }
        } else {
            countText = Component.literal(String.valueOf(displayList.size()))
                    .withStyle(viewMode == 1 ? ChatFormatting.RED : ChatFormatting.GREEN)
                    .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.valueOf(currentSourceList.size())).withStyle(ChatFormatting.YELLOW));
            g.drawString(font, countText, countX, infoY, 0xFFFFFF, false);

            enableCanvasScissor(
                    g,
                    gridX,
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
                    GuiTheme.itemSlot(
                            g,
                            x,
                            y,
                            SLOT_SIZE,
                            4,
                            hovered
                    );

                    ItemBanControl.withSkip(() -> {
                        GuiTheme.item(
                                g,
                                font,
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
                        GuiTheme.indicatorFill(g, x + 2, y + SLOT_SIZE - 3, SLOT_SIZE - 3, 2, GuiTheme.Indicator.DANGER);
                    }
                }
            }
        }
        disableCanvasScissor(g);

        GuiTheme.scrollbar(
                gridScroll,
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

    private boolean isHoveringButton(StateButton btn, double mx, double my) {
        return btn != null && ((KineticControl) btn).isVisible() && mx >= btn.getX() && mx < btn.getX() + btn.getWidth() && my >= btn.getY() && my < btn.getY() + btn.getHeight();
    }

    @Override
    protected void renderTooltips(GuiGraphics g, int smx, int smy, int mx, int my) {
        int tooltipY = smy < 30 ? my + 15 : my;

        if (isHoveringButton(saveBtn, smx, smy)) { KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.save"), mx, tooltipY); return; }
        if (isHoveringButton(viewBtn, smx, smy)) { KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.view"), mx, tooltipY); return; }
        if (isHoveringButton(closeBtn, smx, smy)) { KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.back"), mx, tooltipY); return; }
        if (isHoveringButton(ruleBtn, smx, smy)) { KineticOverlays.requestTooltip(Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.rule_desc"), mx, tooltipY); return; }

        if (!isAutoCompleteMode) {
            int countX =
                    compactToolbar
                            ? gridX
                            : searchBox.getX()
                            + searchBox.getWidth()
                            + 10;

            if (!compactToolbar
                    && ruleBtn != null
                    && ((KineticControl) ruleBtn).isVisible()) {
                countX =
                        ruleBtn.getX()
                                + ruleBtn.getWidth()
                                + 10;
            }
            String rawStr = displayList.size() + " / " + currentSourceList.size();

            if (smx >= countX && smx < countX + font.width(rawStr) && smy >= infoY && smy < infoY + font.lineHeight) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.count.title"));
                if (viewMode == 0) tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.count.all_view.desc"));
                else if (viewMode == 1) tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.count.banned_view.desc"));
                else tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.view.inventory"));
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
                            tooltip.add(Component.translatable(ci.id().startsWith("@") ? "gui.itemcontrol.item.banitem.tooltip.mod_rule" : "gui.itemcontrol.item.banitem.tooltip.tag_rule"));
                            tooltip.add(Component.empty());
                            tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.right_unban_rule"));
                        } else {
                            String ruleIdentifier = getRuleIdentifier(ci);
                            String listedRule = getListedRuleFor(ci);
                            boolean isProtected = BanItemConfig.isProtected(ruleIdentifier);
                            tooltip.add(ItemCacheHudRenderer.getDisplayNameCustom(ci.stack()));
                            tooltip.add(Component.literal(ruleIdentifier));
                            tooltip.add(Component.empty());
                            tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.shift_edit_nbt"));
                            if (isProtected) {
                                tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.protected"));
                            } else if (BanItemConfig.isBanned(ci.stack())) {
                                if (!listedRule.isBlank()) tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.right_unban"));
                                else tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.banned_by_rule"));
                            } else tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.left_ban"));
                        }
                        KineticOverlays.requestTooltip(tooltip, mx, my);
                        return null;
                    });
                }
            }
        }
    }

    @Override
    protected boolean canvasMouseClicked(double smx, double smy, int btn) {
        if (KineticMouseButtons.isPrimary(btn)
                && gridScroll.beginDrag(
                        smx,
                        smy,
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
                if (localY % SLOT_PITCH < SLOT_SIZE && idx >= 0 && idx < autoCompleteList.size()) { if (searchBox != null) searchBox.setValue(autoCompleteList.get(idx)); return true;
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
                        if (KineticMouseButtons.isSecondary(btn)) {
                            removeBanRule(ruleIdentifier);
                            updateSearch(searchBox.getValue());
                        }
                        return true;
                    }
                    String listedRule = getListedRuleFor(ci);
                    if (KineticClientRuntime.shiftModifierDown() && KineticMouseButtons.isPrimary(btn)) {
                        openNbtEditor(ruleIdentifier, !listedRule.isBlank(), ci.stack());
                        return true;
                    }
                    if (KineticMouseButtons.isPrimary(btn) && listedRule.isBlank() && !BanItemConfig.isBanned(ci.stack())) {
                        addBanRule(ruleIdentifier);
                        updateSearch(searchBox.getValue());
                    } else if (KineticMouseButtons.isSecondary(btn) && !listedRule.isBlank()) {
                        removeBanRule(listedRule);
                        updateSearch(searchBox.getValue());
                    }
                    return true;
                }
            }
        }
        return super.canvasMouseClicked(smx, smy, btn);
    }

    @Override
    protected boolean canvasMouseReleased(double smx, double smy, int btn) {
        if (gridScroll.release(btn)) {
            rememberedScrollOffset = gridScroll.offset();
            return true;
        }

        return super.canvasMouseReleased(smx, smy, btn);
    }

    @Override
    protected boolean canvasMouseDragged(double smx, double smy, int btn, double dx, double dy) {
        if (gridScroll.drag(
                smy,
                gridY,
                contentH,
                20
        )) {
            rememberedScrollOffset = gridScroll.offset();
            return true;
        }

        return super.canvasMouseDragged(smx, smy, btn, dx, dy);
    }

    @Override
    protected boolean canvasMouseScrolled(double smx, double smy, double d) {
        if (gridScroll.scroll(d, SLOT_PITCH)) {
            rememberedScrollOffset = gridScroll.offset();
            return true;
        }

        return super.canvasMouseScrolled(smx, smy, d);
    }

}
