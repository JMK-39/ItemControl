package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.search.ItemSearchIndex;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.itemcontrol.item.util.ItemBanControl;
import dev.xyat.kineticcore.api.client.selector.NbtEditorScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BannedItemScreen extends KineticScreen {

    private final Screen parent;
    private EditBox searchBox;
    private Button ruleBtn, saveBtn, viewBtn, closeBtn;
    private static int viewMode = 0;
    private static int rememberedScrollOffset = 0;
    private static String lastSearchQuery = "";
    private final List<ItemSearchIndex.CachedItem> allItemsCache;
    private List<ItemSearchIndex.CachedItem> currentSourceList = new ArrayList<>();
    private List<ItemSearchIndex.CachedItem> displayList = new ArrayList<>();

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
        useFluidCanvas(
                640f,
                360f,
                4
        );

        this.allItemsCache = ItemSearchCache.getAllItems();
        dev.xyat.kineticcore.api.client.screen.GuiSession.setParent(this, parent);
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
                        || canvasWidth < 520;

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
                        canvasWidth
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
                                canvasWidth
                                        - contentW
                                        - 8
                        ) / 2
                );

        contentH =
                Math.max(
                        SLOT_SIZE,
                        Math.max(
                                1,
                                (Math.max(SLOT_SIZE, canvasHeight - gridY - 8) + SLOT_PITCH - SLOT_SIZE) / SLOT_PITCH
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

        searchBox =
                new EditBox(
                        font,
                        gridX,
                        searchY,
                        searchW,
                        20,
                        Component.empty()
                );

        searchBox.setValue(lastSearchQuery);
        searchBox.setResponder(this::updateSearch);
        addRenderableWidget(searchBox);
        ruleBtn =
                Button.builder(
                                Component.empty(),
                                button -> {
                                    String query =
                                            searchBox.getValue()
                                                    .trim()
                                                    .toLowerCase(
                                                            Locale.ROOT
                                                    );

                                    if (BanItemConfig.isProtected(query)) {
                                        return;
                                    }

                                    if (query.startsWith("@")
                                            || query.startsWith("#")) {
                                        if (BanItemConfig.data.bannedItems.contains(
                                                query
                                        )) {
                                            BanItemConfig.data.bannedItems.remove(
                                                    query
                                            );
                                        } else {
                                            BanItemConfig.data.bannedItems.add(
                                                    query
                                            );
                                        }

                                        BanItemConfig.rebuildCache();
                                        ItemSearchCache.markRulesChanged();
                                        updateSearch(searchBox.getValue());
                                    }
                                }
                        )
                        .bounds(
                                searchBox.getX()
                                        + searchBox.getWidth()
                                        + 2,
                                searchY,
                                ruleBtnW,
                                20
                        )
                        .build();
        ruleBtn.visible = false;
        addRenderableWidget(ruleBtn);

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
        saveBtn =
                Button.builder(
                                Component.translatable(
                                        "gui.itemcontrol.item.banitem.btn.save"
                                ),
                                button -> {
                                    String jsonData =
                                            BanItemConfig.GSON.toJson(
                                                    BanItemConfig.data
                                            );

                                    ItemNetwork.CHANNEL.sendToServer(
                                            new ItemNetwork.SaveBanConfigPacket(
                                                    ItemNetwork.EDITOR_BAN_ITEM,
                                                    jsonData
                                            )
                                    );
                                }
                        )
                        .bounds(
                                saveX,
                                buttonY,
                                btnW,
                                20
                        )
                        .build();

        addRenderableWidget(saveBtn);

        viewBtn =
                Button.builder(
                                getViewModeText(),
                                button -> {
                                    viewMode =
                                            (viewMode + 1) % 3;

                                    button.setMessage(
                                            getViewModeText()
                                    );

                                    updateSearch(
                                            searchBox.getValue()
                                    );
                                }
                        )
                        .bounds(
                                viewX,
                                buttonY,
                                btnW,
                                20
                        )
                        .build();
        addRenderableWidget(viewBtn);

        closeBtn =
                Button.builder(
                                Component.translatable(
                                        "gui.itemcontrol.item.banitem.btn.back"
                                ),
                                button -> onClose()
                        )
                        .bounds(
                                closeX,
                                buttonY,
                                btnW,
                                20
                        )
                        .build();

        addRenderableWidget(closeBtn);

        infoY =
                compactToolbar
                        ? 57
                        : 11;

        updateSearch(lastSearchQuery);
    }

    public void applySaveResult(boolean success) {
        if (success) commitDraft();
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private Component getViewModeText() {
        if (viewMode == 0) return Component.translatable("gui.itemcontrol.item.banitem.view.all");
        if (viewMode == 1) return Component.translatable("gui.itemcontrol.item.banitem.view.banned");
        return Component.translatable("gui.itemcontrol.item.banitem.view.inventory");
    }

    private List<ItemSearchIndex.CachedItem> getInventoryItems() {
        return ItemSearchCache.getInventoryItems();
    }

    private void updateSearch(String text) {
        String query = text.trim().toLowerCase(Locale.ROOT);
        boolean changed = !query.equals(lastSearchQuery);
        lastSearchQuery = query;

        if (query.startsWith("@")) {
            if (allMods.contains(query) && !query.equals("@")) {
                isAutoCompleteMode = false;
                ruleBtn.visible = true;
                boolean isRuleBanned = BanItemConfig.data.bannedItems.contains(query);
                ruleBtn.setMessage(Component.translatable(isRuleBanned ? "gui.itemcontrol.item.banitem.rule.unban" : "gui.itemcontrol.item.banitem.rule.ban"));
                updateDisplayListForExactMatch(query);
            } else {
                isAutoCompleteMode = true;
                ruleBtn.visible = false;
                autoCompleteList = ItemSearchCache.searchStrings("ban_mod", allMods, query);
                totalH = autoCompleteList.size() * SLOT_PITCH;
            }
        } else if (query.startsWith("#")) {
            if (allTags.contains(query) && !query.equals("#")) {
                isAutoCompleteMode = false;
                ruleBtn.visible = true;
                boolean isRuleBanned = BanItemConfig.data.bannedItems.contains(query);
                ruleBtn.setMessage(Component.translatable(isRuleBanned ? "gui.itemcontrol.item.banitem.rule.unban" : "gui.itemcontrol.item.banitem.rule.ban"));
                updateDisplayListForExactMatch(query);
            } else {
                isAutoCompleteMode = true;
                ruleBtn.visible = false;
                autoCompleteList = ItemSearchCache.searchStrings("ban_tag", allTags, query);
                totalH = autoCompleteList.size() * SLOT_PITCH;
            }
        } else {
            isAutoCompleteMode = false;
            ruleBtn.visible = false;
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
            currentSourceList = allItemsCache;
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
            this.minecraft.setScreen(new NbtEditorScreen(initNbt, (savedNbt) -> {
                String cleanNbt = savedNbt == null ? "" : savedNbt.trim();
                String newIdStr = baseId + cleanNbt;
                if (isRule) removeBanRule(idStr);
                addBanRule(newIdStr);
                viewMode = 1;
                gridScroll.reset();
                rememberedScrollOffset = 0;
                if (viewBtn != null) viewBtn.setMessage(getViewModeText());
                updateSearch(searchBox == null ? "" : searchBox.getValue());
            }, this));
        }
    }

    private String getRuleIdentifier(ItemSearchIndex.CachedItem cachedItem) {
        if (cachedItem == null) return "";
        String idStr = cachedItem.idStr == null ? "" : cachedItem.idStr.trim();
        if (idStr.startsWith("@") || idStr.startsWith("#") || idStr.contains("{")) return idStr;
        String identifier = BanItemConfig.getItemIdentifier(cachedItem.stack);
        return identifier.isBlank() ? idStr : identifier;
    }

    private String getListedRuleFor(ItemSearchIndex.CachedItem cachedItem) {
        if (cachedItem == null || BanItemConfig.data == null || BanItemConfig.data.bannedItems == null) return "";
        String identifier = getRuleIdentifier(cachedItem);
        if (!identifier.isBlank() && BanItemConfig.data.bannedItems.contains(identifier)) return identifier;
        String idStr = cachedItem.idStr == null ? "" : cachedItem.idStr.trim();
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
        g.fillGradient(0, 0, canvasWidth, canvasHeight, 0xFF222222, 0xFF111111);
        g.fill(gridX - 3, gridY - 3, gridX + contentW + 9, gridY + contentH + 3, 0xFF000000);
        g.fill(gridX - 2, gridY - 2, gridX + contentW + 8, gridY + contentH + 2, 0xFF2A2A2A);
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
                && ruleBtn.visible) {
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
                    g.fill(gridX, y, gridX + gridW, y + SLOT_SIZE, hovered ? 0x88FFFFFF : ((i % 2 == 0) ? 0x44FFFFFF : 0x44888888));
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
                ItemSearchIndex.CachedItem ci = displayList.get(i);
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
                                ci.stack,
                                x,
                                y,
                                SLOT_SIZE,
                                1.0F,
                                true
                        );
                        return null;
                    });
                    boolean isBannedMarker = ci.idStr.startsWith("@") || ci.idStr.startsWith("#") || BanItemConfig.isBanned(ci.stack);
                    if (isBannedMarker) g.fill(x + 2, y + SLOT_SIZE - 3, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFFFF3333);
                }
            }
        }
        g.disableScissor();

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

        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            g.drawString(font, Component.translatable("gui.itemcontrol.item.banitem.search.hint"), searchBox.getX() + 6, searchBox.getY() + 6, 0x888888, false);
        }
    }

    private boolean isHoveringButton(Button btn, double mx, double my) {
        return btn != null && btn.visible && mx >= btn.getX() && mx < btn.getX() + btn.getWidth() && my >= btn.getY() && my < btn.getY() + btn.getHeight();
    }

    @Override
    protected void renderTooltips(GuiGraphics g, int smx, int smy, int mx, int my) {
        int tooltipY = smy < 30 ? my + 15 : my;

        if (isHoveringButton(saveBtn, smx, smy)) { GuiOverlay.requestTooltip(Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.save"), mx, tooltipY); return; }
        if (isHoveringButton(viewBtn, smx, smy)) { GuiOverlay.requestTooltip(Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.view"), mx, tooltipY); return; }
        if (isHoveringButton(closeBtn, smx, smy)) { GuiOverlay.requestTooltip(Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.back"), mx, tooltipY); return; }
        if (isHoveringButton(ruleBtn, smx, smy)) { GuiOverlay.requestTooltip(Component.translatable("gui.itemcontrol.item.banitem.tooltip.btn.rule_desc"), mx, tooltipY); return; }

        if (!isAutoCompleteMode) {
            int countX =
                    compactToolbar
                            ? gridX
                            : searchBox.getX()
                            + searchBox.getWidth()
                            + 10;

            if (!compactToolbar
                    && ruleBtn != null
                    && ruleBtn.visible) {
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
                GuiOverlay.requestTooltip(tooltip, mx, tooltipY);
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
                        ItemSearchIndex.CachedItem ci = displayList.get(idx);
                        List<Component> tooltip = new ArrayList<>();
                        if (ci.idStr.startsWith("@") || ci.idStr.startsWith("#")) {
                            tooltip.add(Component.literal(ci.idStr));
                            tooltip.add(Component.translatable(ci.idStr.startsWith("@") ? "gui.itemcontrol.item.banitem.tooltip.mod_rule" : "gui.itemcontrol.item.banitem.tooltip.tag_rule"));
                            tooltip.add(Component.empty());
                            tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.right_unban_rule"));
                        } else {
                            String ruleIdentifier = getRuleIdentifier(ci);
                            String listedRule = getListedRuleFor(ci);
                            boolean isProtected = BanItemConfig.isProtected(ruleIdentifier);
                            tooltip.add(ItemCacheHudRenderer.getDisplayNameCustom(ci.stack));
                            tooltip.add(Component.literal(ruleIdentifier));
                            tooltip.add(Component.empty());
                            tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.shift_edit_nbt"));
                            if (isProtected) {
                                tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.protected"));
                            } else if (BanItemConfig.isBanned(ci.stack)) {
                                if (!listedRule.isBlank()) tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.right_unban"));
                                else tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.banned_by_rule"));
                            } else tooltip.add(Component.translatable("gui.itemcontrol.item.banitem.tooltip.left_ban"));
                        }
                        GuiOverlay.requestTooltip(tooltip, mx, my);
                        return null;
                    });
                }
            }
        }
    }

    @Override
    protected boolean canvasMouseClicked(double smx, double smy, int btn) {
        if (btn == 0
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
                    ItemSearchIndex.CachedItem ci = displayList.get(idx);
                    String ruleIdentifier = getRuleIdentifier(ci);
                    if (BanItemConfig.isProtected(ruleIdentifier)) return true;
                    if (ruleIdentifier.startsWith("@") || ruleIdentifier.startsWith("#")) {
                        if (btn == 1) {
                            removeBanRule(ruleIdentifier);
                            updateSearch(searchBox.getValue());
                        }
                        return true;
                    }
                    String listedRule = getListedRuleFor(ci);
                    if (Screen.hasShiftDown() && btn == 0) {
                        openNbtEditor(ruleIdentifier, !listedRule.isBlank(), ci.stack);
                        return true;
                    }
                    if (btn == 0 && listedRule.isBlank() && !BanItemConfig.isBanned(ci.stack)) {
                        addBanRule(ruleIdentifier);
                        updateSearch(searchBox.getValue());
                    } else if (btn == 1 && !listedRule.isBlank()) {
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
        if (gridScroll.scroll(d, SLOT_PITCH / 3.0D)) {
            rememberedScrollOffset = gridScroll.offset();
            return true;
        }

        return super.canvasMouseScrolled(smx, smy, d);
    }

}
