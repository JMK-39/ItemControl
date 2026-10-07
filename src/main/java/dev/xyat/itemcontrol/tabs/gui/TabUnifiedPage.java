package dev.xyat.itemcontrol.tabs.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.state.DragStateController;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.itemcontrol.tabs.network.TabNetwork;
import dev.xyat.itemcontrol.tabs.TabClientEvents;
import dev.xyat.itemcontrol.tabs.TabConfig;
import dev.xyat.itemcontrol.tabs.TabModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class TabUnifiedPage extends KineticPage {
    private enum ItemState {
        NORMAL,
        ADDED,
        BANNED
    }

    private enum DragType {
        MAIN_TAB,
        MAIN_ITEM,
        RIGHT_ITEM
    }

    private record DisplayItem(
            ItemStack stack,
            ItemState state,
            TabConfig.TabItem ref,
            String ruleStr
    ) {
    }

    private static class TabInfo {
        Component name;
        ItemStack icon;
        ResourceLocation id;
    }

    private static final int MAIN_X = 12;
    private static final int MAIN_W = 468;
    private static final int RIGHT_X = 500;
    private static final int RIGHT_W = 126;

    private static final int ARROW_W = 20;
    // The arrows sit inside the tab strip frame, as far from its side lines as from its top and bottom.
    private static final int ARROW_INSET = 4;
    private static final int TAB_SIZE = 24;
    private static final int TAB_INNER_PADDING = 0;
    private static final int TAB_VIEW_W = MAIN_W - ARROW_W * 2 - TAB_INNER_PADDING * 2;
    private static final int MAIN_TAB_COLS = TAB_VIEW_W / TAB_SIZE;
    private static final int TAB_CONTENT_W = MAIN_TAB_COLS * TAB_SIZE;
    private static final int TAB_CONTENT_X = MAIN_X + ARROW_W + TAB_INNER_PADDING + (TAB_VIEW_W - TAB_CONTENT_W) / 2;

    private static final int SLOT_SIZE = 18;
    private static final int MAIN_ITEM_COLS = 26;
    private static final int RIGHT_ITEM_COLS = 7;

    private static final int TAB_Y = 32;
    private static final int TAB_SCROLL_Y = 60;
    private static final int ITEM_Y = 72;
    private static final int ITEM_H = 234;
    private static final int ITEM_VISIBLE_ROWS = 13;

    private final List<TabInfo> allGameTabs =
            new ArrayList<>();

    private final List<TabInfo> mainTabs =
            new ArrayList<>();

    private final List<DisplayItem> mainItems =
            new ArrayList<>();

    private final List<DisplayItem> rightHiddenItems =
            new ArrayList<>();

    private final KineticScrollController mainTabScroll =
            new KineticScrollController();

    private final KineticScrollController mainItemScroll =
            new KineticScrollController();

    private final KineticScrollController rightItemScroll =
            new KineticScrollController();

    private final DragStateController<DragType> contentDrag =
            new DragStateController<>();

    private int mainSelectedTabIdx;

    private TabInfo hoveredTabTooltip;
    private boolean hoveredTabBanned;
    private DisplayItem hoveredItemTooltip;
    private boolean hoveredItemHiddenPane;

    public TabUnifiedPage() {
        super(KineticI18n.translatable(
                "gui.itemcontrol.tabs.tabs.unified.title"
        ));

        useCanvas(640, 360, 6);

        TabClientEvents.clearNotification();

        for (Map.Entry<ResourceKey<CreativeModeTab>, CreativeModeTab> entry
                : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            ResourceLocation id =
                    entry.getKey().location();

            String idText =
                    id.toString();

            if ("minecraft:inventory".equals(idText)
                    || "minecraft:search".equals(idText)
                    || "minecraft:hotbar".equals(idText)) {
                continue;
            }

            CreativeModeTab tab =
                    entry.getValue();

            TabInfo info =
                    new TabInfo();

            info.name =
                    tab.getDisplayName();

            info.icon =
                    tab.getIconItem();

            info.id =
                    id;

            allGameTabs.add(info);
        }

        allGameTabs.sort(
                Comparator.comparing(
                        info -> info.id.toString()
                )
        );

        refreshData();
        configureStandaloneDraft(this::captureTabSnapshot, this::restoreTabSnapshot);
    }

    private record TabSnapshot(String json) {
    }

    private TabSnapshot captureTabSnapshot() {
        return new TabSnapshot(TabConfig.GSON.toJson(TabConfig.currentEditing));
    }

    private void restoreTabSnapshot(TabSnapshot snapshot) {
        if (snapshot == null || snapshot.json() == null || snapshot.json().isBlank()) return;
        TabConfig.Data restored = TabConfig.GSON.fromJson(snapshot.json(), TabConfig.Data.class);
        if (restored == null) return;
        TabConfig.currentEditing = restored;
        refreshData();
    }

    private void refreshData() {
        mainTabs.clear();
        mainItems.clear();
        rightHiddenItems.clear();

        List<String> removalRules =
                TabConfig.currentEditing.removals;

        mainTabs.addAll(allGameTabs);

        if (mainSelectedTabIdx >= mainTabs.size()) {
            mainSelectedTabIdx =
                    Math.max(
                            0,
                            mainTabs.size() - 1
                    );
        }

        if (!mainTabs.isEmpty()) {
            buildMainItems(removalRules);
        }

        for (String rule : removalRules) {
            ItemStack stack =
                    TabModule.parseItemStr(rule);

            if (!stack.isEmpty()) {
                rightHiddenItems.add(
                        new DisplayItem(
                                stack,
                                ItemState.BANNED,
                                null,
                                rule
                        )
                );
            }
        }

        refreshScrollRanges();
    }

    private void buildMainItems(
            List<String> removalRules
    ) {
        TabInfo currentTab =
                mainTabs.get(mainSelectedTabIdx);

        Collection<ItemStack> rawItems;

        TabModule.bypassAllModifications = true;

        try {
            //? if >=26.1 {
            /*CreativeModeTab minecraftTab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(currentTab.id);
            *///?} else {
            CreativeModeTab minecraftTab =
                    BuiltInRegistries.CREATIVE_MODE_TAB
                            .get(currentTab.id);
            //?}

            rawItems =
                    minecraftTab == null
                            ? List.of()
                            : minecraftTab.getDisplayItems();
        } finally {
            TabModule.bypassAllModifications = false;
        }

        List<ItemStack> nativeItems =
                new ArrayList<>();

        for (ItemStack stack : rawItems) {
            if (stack.isEmpty()) {
                continue;
            }

            String rule =
                    TabModule.buildRule(stack);

            if (!TabModule.INJECTED_ITEMS.contains(
                    currentTab.id + "|" + rule
            )) {
                nativeItems.add(stack);
            }
        }

        List<DisplayItem> customItems =
                new ArrayList<>();

        for (TabConfig.TabAddition addition
                : TabConfig.currentEditing.additions) {
            if (!addition.tabId.equals(
                    currentTab.id.toString()
            )) {
                continue;
            }

            for (TabConfig.TabItem item : addition.items) {
                if (item == null) {
                    continue;
                }

                ItemStack stack =
                        item.getStack();

                if (!stack.isEmpty()) {
                    customItems.add(
                            new DisplayItem(
                                    stack,
                                    ItemState.ADDED,
                                    item,
                                    TabModule.buildRule(stack)
                            )
                    );
                }
            }
        }

        for (ItemStack stack : nativeItems) {
            boolean custom =
                    customItems.stream()
                            .anyMatch(item ->
                                    ItemStack.isSameItemSameTags(
                                            item.stack,
                                            stack
                                    )
                            );

            if (custom) {
                continue;
            }

            String rule =
                    TabModule.buildRule(stack);

            ItemState state =
                    removalRules.contains(rule)
                            ? ItemState.BANNED
                            : ItemState.NORMAL;

            mainItems.add(
                    new DisplayItem(
                            stack,
                            state,
                            null,
                            rule
                    )
            );
        }

        mainItems.addAll(customItems);
    }

    private void refreshScrollRanges() {
        mainTabScroll.update(
                mainTabs.size(),
                MAIN_TAB_COLS
        );

        mainItemScroll.update(
                rowCount(
                        mainItems.size(),
                        MAIN_ITEM_COLS
                ),
                ITEM_VISIBLE_ROWS
        );

        rightItemScroll.update(
                rowCount(
                        rightHiddenItems.size(),
                        RIGHT_ITEM_COLS
                ),
                ITEM_VISIBLE_ROWS
        );
    }

    private static int rowCount(
            int size,
            int columns
    ) {
        return (size + columns - 1) / columns;
    }

    @Override
    protected void build(KineticUi ui) {
        int bottomY =
                height() - 22;

        ui.button(MAIN_X + ARROW_INSET, TAB_Y + ARROW_INSET, ARROW_W)
                .text(Component.literal("<"))
                .onClick(() -> mainTabScroll.setOffset(mainTabScroll.offset() - 1))
                .build();
        ui.button(MAIN_X + MAIN_W - ARROW_W - ARROW_INSET, TAB_Y + ARROW_INSET, ARROW_W)
                .text(Component.literal(">"))
                .onClick(() -> mainTabScroll.setOffset(mainTabScroll.offset() + 1))
                .build();

        ui.button(MAIN_X, bottomY, 160).text(KineticI18n.translatable("gui.itemcontrol.tabs.tabs.unified.btn_add_item")).onClick(this::openItemSelector).build();

        ui.button(width() / 2 - 40, bottomY, 80).text(KineticI18n.translatable("gui.itemcontrol.tabs.tabs.btn.back")).onClick(this::returnToParent).build();

        ui.button(RIGHT_X, bottomY, RIGHT_W).text(KineticI18n.translatable("gui.itemcontrol.tabs.tabs.btn.save_apply")).onClick(() -> {
                    String json = TabConfig.GSON.toJson(TabConfig.currentEditing);
                    TabNetwork.saveTabs(json);
                    commitDraft();
                }).build();
    }

    private void openItemSelector() {
        if (!isAttached()
                || mainTabs.isEmpty()) {
            return;
        }

        String tabId =
                mainTabs.get(
                        mainSelectedTabIdx
                ).id.toString();

        KineticSelectors.openItemSelector(selection -> {
                            if (!selection.isItem()) {
                                return;
                            }

                            ItemStack selectedStack =
                                    selection.stack();

                            ResourceLocation selectedId =
                                    KineticRegistries.items().id(
                                            selectedStack.getItem()
                                    );

                            if (selectedId == null) {
                                return;
                            }

                            String id =
                                    selectedId.toString();

//? if >=1.21 {
/*                            String nbt = dev.xyat.itemcontrol.item.data.ItemData.format(selectedStack);*/
//?} else {
                            String nbt =
                                    selectedStack.hasTag()
                                            && selectedStack.getTag() != null
                                            ? selectedStack.getTag().toString()
                                            : "{}";


//?}
                            boolean exists =
                                    mainItems.stream()
                                            .anyMatch(item -> {
                                                ResourceLocation currentId =
                                                        KineticRegistries.items().id(
                                                                item.stack.getItem()
                                                        );

//? if >=1.21 {
/*                                                String currentNbt = dev.xyat.itemcontrol.item.data.ItemData.format(item.stack);*/
//?} else {
                                                String currentNbt =
                                                        item.stack.getTag() != null
                                                                ? item.stack.getTag().toString()
                                                                : "{}";


//?}
                                                return currentId != null
                                                        && currentId.toString().equals(id)
                                                        && currentNbt.equals(nbt);
                                            });

                            if (exists) {
                                TabNetwork.requestNotification(
                                        "gui.itemcontrol.tabs.tabs.notify.duplicate"
                                );
                                return;
                            }

                            TabConfig.TabAddition addition =
                                    TabConfig.currentEditing.additions.stream()
                                            .filter(value ->
                                                    value.tabId.equals(tabId)
                                            )
                                            .findFirst()
                                            .orElseGet(() -> {
                                                TabConfig.TabAddition created =
                                                        new TabConfig.TabAddition();

                                                created.tabId =
                                                        tabId;

                                                TabConfig.currentEditing.additions.add(
                                                        created
                                                );

                                                return created;
                                            });

                            addition.items.add(
                                    new TabConfig.TabItem(
                                            id,
                                            nbt
                                    )
                            );

                            refreshData();
                }
        );
    }

    @Override
    protected void renderBackground(KineticGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        hoveredTabTooltip = null;
        hoveredItemTooltip = null;

        KineticTheme.panel(graphics, 0, 0, width(), height());

        KineticTheme.surface(graphics, 0, height() - 30, width(), 30, KineticTheme.Surface.PANEL_ALT);

        graphics.scrollingText(KineticI18n.translatable(
                        "gui.itemcontrol.tabs.tabs.unified.left_title.colored"
                ), MAIN_X, 8, MAIN_W - 4, 0xFFFFFF, true);

        graphics.scrollingText(KineticI18n.translatable(
                        "gui.itemcontrol.tabs.tabs.unified.right_title.colored"
                ), RIGHT_X, 8, RIGHT_W - 4, 0xFFFFFF, true);

        renderMainPane(
                graphics,
                mouseX,
                mouseY
        );

        renderRightPane(
                graphics,
                mouseX,
                mouseY
        );
    }

    private void renderMainPane(
            KineticGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        KineticTheme.panel(graphics, MAIN_X, TAB_Y, MAIN_W, TAB_SIZE);

        int firstTab = mainTabScroll.smoothIndexOffset();
        int tabShift = mainTabScroll.visualShift(TAB_SIZE);
        graphics.scissor(TAB_CONTENT_X, TAB_Y, TAB_CONTENT_X + TAB_CONTENT_W, TAB_Y + TAB_SIZE);
        for (int i = 0;
             i < MAIN_TAB_COLS + 1;
             i++) {
            int index =
                    firstTab + i;

            if (index >= mainTabs.size()) {
                break;
            }

            TabInfo info =
                    mainTabs.get(index);

            int tabX =
                    TAB_CONTENT_X + i * TAB_SIZE - tabShift;

            boolean selected =
                    index == mainSelectedTabIdx;

            boolean banned =
                    TabConfig.currentEditing.hiddenTabs.contains(
                            info.id.toString()
                    );

            boolean hovered =
                    KineticTheme.hovering(
                            mouseX,
                            mouseY,
                            tabX,
                            TAB_Y,
                            TAB_SIZE - 1,
                            TAB_SIZE - 1
                    );

            KineticTheme.itemSlot(graphics, tabX, TAB_Y, TAB_SIZE, TAB_SIZE, 4,
                    selected, hovered && !selected, false);

            if (banned) {
                KineticTheme.indicatorFill(
                        graphics,
                        tabX + 1,
                        TAB_Y + 1,
                        TAB_SIZE - 2,
                        TAB_SIZE - 2,
                        KineticTheme.Indicator.DANGER,
                        0.27F
                );
            }

            KineticTheme.item(
                    graphics,
                    info.icon,
                    tabX,
                    TAB_Y,
                    TAB_SIZE,
                    1.0F,
                    false
            );

            if (hovered
                    && !contentDrag.isActive()) {
                hoveredTabTooltip = info;
                hoveredTabBanned = banned;
            }
        }

        graphics.endScissor();

        mainTabScroll.renderHorizontal(
                graphics,
                mouseX,
                mouseY,
                TAB_CONTENT_X,
                TAB_SCROLL_Y,
                TAB_CONTENT_W,
                4,
                20
        );

        KineticTheme.panel(graphics, MAIN_X - 2, ITEM_Y - 2, MAIN_W + 4, ITEM_H + 4);

        KineticTheme.surface(graphics, MAIN_X, ITEM_Y, MAIN_W, ITEM_H, KineticTheme.Surface.PANEL_ALT);

        renderItemGrid(
                graphics,
                mainItems,
                mainItemScroll,
                MAIN_X,
                MAIN_ITEM_COLS,
                false,
                mouseX,
                mouseY
        );

        mainItemScroll.render(
                graphics,
                mouseX,
                mouseY,
                MAIN_X + MAIN_W + 4,
                ITEM_Y,
                4,
                ITEM_H,
                20
        );
    }

    private void renderRightPane(
            KineticGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        KineticTheme.panel(graphics, RIGHT_X - 2, ITEM_Y - 2, RIGHT_W + 4, ITEM_H + 4);

        KineticTheme.surface(graphics, RIGHT_X, ITEM_Y, RIGHT_W, ITEM_H, KineticTheme.Surface.PANEL_ALT);

        renderItemGrid(
                graphics,
                rightHiddenItems,
                rightItemScroll,
                RIGHT_X,
                RIGHT_ITEM_COLS,
                true,
                mouseX,
                mouseY
        );

        rightItemScroll.render(
                graphics,
                mouseX,
                mouseY,
                RIGHT_X + RIGHT_W + 4,
                ITEM_Y,
                4,
                ITEM_H,
                20
        );
    }

    private void renderItemGrid(
            KineticGraphics graphics,
            List<DisplayItem> items,
            KineticScrollController scroll,
            int gridX,
            int columns,
            boolean hiddenPane,
            int mouseX,
            int mouseY
    ) {
        int firstRow = scroll.smoothIndexOffset();
        int itemShift = scroll.visualShift(SLOT_SIZE);
        int startIndex =
                firstRow * columns;

        int endIndex =
                Math.min(
                        startIndex
                                + (ITEM_VISIBLE_ROWS + 1) * columns,
                        items.size()
                );

        graphics.scissor(gridX, ITEM_Y, gridX + columns * SLOT_SIZE, ITEM_Y + ITEM_H);
        for (int i = startIndex;
             i < endIndex;
             i++) {
            DisplayItem item =
                    items.get(i);

            int localIndex =
                    i - startIndex;

            int column =
                    localIndex % columns;

            int row =
                    localIndex / columns;

            int x =
                    gridX
                            + column * SLOT_SIZE;

            int y =
                    ITEM_Y
                            + row * SLOT_SIZE
                            - itemShift;

            boolean hovered =
                    KineticTheme.hovering(
                            mouseX,
                            mouseY,
                            x,
                            y,
                            SLOT_SIZE - 1,
                            SLOT_SIZE - 1
                    );

            KineticTheme.itemSlot(
                    graphics,
                    x,
                    y,
                    SLOT_SIZE,
                    4,
                    hovered
            );

            KineticTheme.item(
                    graphics,
                    item.stack,
                    x,
                    y,
                    SLOT_SIZE,
                    1.0F,
                    !hiddenPane
            );

            int itemBarY =
                    y + SLOT_SIZE - 2;

            if (item.state == ItemState.ADDED) {
                KineticTheme.indicatorFill(graphics, x + 2, itemBarY, SLOT_SIZE - 4, 1, KineticTheme.Indicator.SUCCESS);
            } else if (item.state == ItemState.BANNED) {
                KineticTheme.indicatorFill(graphics, x + 2, itemBarY, SLOT_SIZE - 4, 1, KineticTheme.Indicator.DANGER);
            }

            if (hovered
                    && !contentDrag.isActive()) {
                hoveredItemTooltip = item;
                hoveredItemHiddenPane = hiddenPane;
            }
        }
        graphics.endScissor();
    }

    private void renderTabTooltip(
            TabInfo info,
            boolean banned) {
        List<Component> tooltip =
                new ArrayList<>();

        tooltip.add(
                KineticI18n.translatable(
                        banned
                                ? "gui.itemcontrol.tabs.format.red"
                                : "gui.itemcontrol.tabs.format.white",
                        info.name.getString()
                )
        );

        if (banned) {
            tooltip.add(
                    KineticI18n.translatable(
                            "gui.itemcontrol.tabs.tabs.unified.right_tabs_title.colored"
                    )
            );

            tooltip.add(
                    KineticI18n.translatable(
                            "gui.itemcontrol.tabs.tabs.unified.tooltip.right_tab.1.colored"
                    )
            );

            tooltip.add(
                    KineticI18n.translatable(
                            "gui.itemcontrol.tabs.tabs.unified.tooltip.right_tab.2.colored"
                    )
            );

            tooltip.add(
                    KineticI18n.translatable(
                            "gui.itemcontrol.tabs.tabs.unified.tooltip.right_tab.3.colored"
                    )
            );
        } else {
            tooltip.add(
                    KineticI18n.translatable(
                            "gui.itemcontrol.tabs.tabs.unified.tooltip.left_tab.1.colored"
                    )
            );

            tooltip.add(
                    KineticI18n.translatable(
                            "gui.itemcontrol.tabs.tabs.unified.tooltip.left_tab.2.colored"
                    )
            );

            tooltip.add(
                    KineticI18n.translatable(
                            "gui.itemcontrol.tabs.tabs.unified.tooltip.left_tab.3.colored"
                    )
            );
        }

        renderRawTooltip(
                tooltip);
    }

    private void renderItemTooltip(
            DisplayItem item,
            boolean hiddenPane) {
        List<Component> tooltip =
                new ArrayList<>();

        tooltip.add(
                item.stack.getHoverName()
        );

        if (hiddenPane) {
            tooltip.add(
                    KineticI18n.translatable(
                            "gui.itemcontrol.tabs.tabs.unified.right_items_title.colored"
                    )
            );

            tooltip.add(
                    KineticI18n.translatable(
                            "gui.itemcontrol.tabs.tabs.unified.tooltip.right_item.1.colored"
                    )
            );

            tooltip.add(
                    KineticI18n.translatable(
                            "gui.itemcontrol.tabs.tabs.unified.tooltip.right_item.2.colored"
                    )
            );
        } else {
            if (item.state == ItemState.ADDED) {
                tooltip.add(
                        KineticI18n.translatable(
                                "gui.itemcontrol.tabs.tabs.unified.tip_added_item.colored"
                        )
                );

                tooltip.add(
                        KineticI18n.translatable(
                                item.ref.matchNbt
                                        ? "gui.itemcontrol.tabs.tabs.unified.tooltip.nbt_mode.on.colored"
                                        : "gui.itemcontrol.tabs.tabs.unified.tooltip.nbt_mode.off.colored"
                        )
                );

                tooltip.add(
                        KineticI18n.translatable(
                                "gui.itemcontrol.tabs.tabs.unified.tooltip.left_item_nbt_toggle.colored"
                        )
                );
            } else if (item.state == ItemState.BANNED) {
                tooltip.add(
                        KineticI18n.translatable(
                                "gui.itemcontrol.tabs.tabs.unified.right_items_title.colored"
                        )
                );

                tooltip.add(
                        KineticI18n.translatable(
                                "gui.itemcontrol.tabs.tabs.unified.tooltip.right_item.1.colored"
                        )
                );
            }

            if (item.state != ItemState.BANNED) {
                tooltip.add(
                        KineticI18n.translatable(
                                "gui.itemcontrol.tabs.tabs.unified.tooltip.left_item.1.colored"
                        )
                );

                tooltip.add(
                        KineticI18n.translatable(
                                "gui.itemcontrol.tabs.tabs.unified.tooltip.left_item.2.colored"
                        )
                );
            }
        }

        renderRawTooltip(
                tooltip);
    }

    private void renderRawTooltip(
            List<Component> tooltip) {
        showTooltip(tooltip);
    }

    @Override
    protected void renderForeground(KineticGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (contentDrag.isActive()) {
            Object payload =
                    contentDrag.payload();

            if (payload instanceof TabInfo tabInfo) {
                KineticTheme.item(
                        graphics,
                        tabInfo.icon,
                        mouseX - 11,
                        mouseY - 11,
                        TAB_SIZE,
                        1.0F,
                        false
                );
            } else if (payload instanceof DisplayItem item) {
                KineticTheme.item(
                        graphics,
                        item.stack,
                        mouseX - 9,
                        mouseY - 9,
                        SLOT_SIZE,
                        1.0F,
                        false
                );
            }
        }

        TabClientEvents.renderNotificationScaled(
                graphics,
                width()
        );
    }

    @Override
    protected void renderTooltips(
            int scaledMouseX,
            int scaledMouseY
    ) {
        if (contentDrag.isActive()) {
            return;
        }

        if (hoveredItemTooltip != null) {
            renderItemTooltip(
                    hoveredItemTooltip,
                    hoveredItemHiddenPane);
            return;
        }

        if (hoveredTabTooltip != null) {
            renderTabTooltip(
                    hoveredTabTooltip,
                    hoveredTabBanned);
            return;
        }

        int bottomY =
                height() - 22;

        if (KineticTheme.hovering(
                scaledMouseX,
                scaledMouseY,
                MAIN_X,
                bottomY,
                160,
                18
        )) {
            renderRawTooltip(
                    List.of(
                            KineticI18n.translatable(
                                    "gui.itemcontrol.tabs.tabs.unified.btn.add_item.tooltip"
                            )
                    ));
            return;
        }

        if (KineticTheme.hovering(
                scaledMouseX,
                scaledMouseY,
                RIGHT_X,
                bottomY,
                RIGHT_W,
                18
        )) {
            renderRawTooltip(
                    List.of(
                            KineticI18n.translatable(
                                    "gui.itemcontrol.tabs.tabs.unified.btn.save.tooltip"
                            )
                    ));
        }
    }

    private void returnToParent() {
        if (isAttached()) {
            navigateBack();
        }
    }

    @Override
    protected boolean onCloseRequested() {
        returnToParent();
        return true;
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        if (input.isLeft()) {
            // v2 横向拖动无命中余量参数（固定 1px，原为 0）/ v2 horizontal drag has no hit-padding parameter (fixed 1 px, was 0).
            if (mainTabScroll.beginHorizontalDrag(
                    mouseX,
                    mouseY,
                    input.button(),
                    TAB_CONTENT_X,
                    TAB_SCROLL_Y,
                    TAB_CONTENT_W,
                    4,
                    20
            )) {
                return true;
            }

            if (mainItemScroll.beginDrag(
                    mouseX,
                    mouseY,
                    input.button(),
                    MAIN_X + MAIN_W + 4,
                    ITEM_Y,
                    4,
                    ITEM_H,
                    20,
                    0
            )) {
                return true;
            }

            if (rightItemScroll.beginDrag(
                    mouseX,
                    mouseY,
                    input.button(),
                    RIGHT_X + RIGHT_W + 4,
                    ITEM_Y,
                    4,
                    ITEM_H,
                    20,
                    0
            )) {
                return true;
            }
        }

        if (mouseY >= TAB_Y
                && mouseY < TAB_Y + TAB_SIZE) {
            return handleTabClick(
                    mouseX,
                    input
            );
        }

        if (mouseY >= ITEM_Y
                && mouseY < ITEM_Y + ITEM_H) {
            return handleItemClick(
                    mouseX,
                    mouseY,
                    input
            );
        }

        return false;
    }

    private boolean handleTabClick(
            double mouseX,
            MouseInput input
    ) {
        if (mouseX < TAB_CONTENT_X
                || mouseX >= TAB_CONTENT_X + TAB_CONTENT_W) {
            return false;
        }

        int tabShift = mainTabScroll.visualShift(TAB_SIZE);
        int index =
                mainTabScroll.smoothIndexOffset()
                        + (int) (
                        (mouseX - TAB_CONTENT_X + tabShift)
                                / TAB_SIZE
                );

        if (index < 0
                || index >= mainTabs.size()) {
            return false;
        }

        TabInfo tabInfo =
                mainTabs.get(index);

        String tabId =
                tabInfo.id.toString();

        if (input.isLeft()) {
            if (KineticClientRuntime.controlModifierDown()) {
                contentDrag.start(
                        DragType.MAIN_TAB,
                        tabInfo
                );
            } else {
                mainSelectedTabIdx =
                        index;

                refreshData();
            }

            return true;
        }

        if (input.isRight()) {
            if (KineticClientRuntime.shiftModifierDown()) {
                TabConfig.currentEditing.hiddenTabs.remove(
                        tabId
                );
            } else if (!TabConfig.currentEditing.hiddenTabs.contains(
                    tabId
            )) {
                TabConfig.currentEditing.hiddenTabs.add(
                        tabId
                );
            }

            refreshData();
            return true;
        }

        return false;
    }

    private boolean handleItemClick(
            double mouseX,
            double mouseY,
            MouseInput input
    ) {
        if (mouseX >= MAIN_X
                && mouseX < MAIN_X + MAIN_W) {
            int index =
                    itemIndexAt(
                            mainItemScroll,
                            MAIN_X,
                            MAIN_ITEM_COLS,
                            mouseX,
                            mouseY
                    );

            if (index < 0
                    || index >= mainItems.size()) {
                return false;
            }

            DisplayItem item =
                    mainItems.get(index);

            if (input.isLeft()) {
                if (item.state == ItemState.BANNED) {
                    unhideItem(item);
                    refreshData();
                } else if (KineticClientRuntime.controlModifierDown()) {
                    contentDrag.start(
                            DragType.MAIN_ITEM,
                            item
                    );
                }

                return true;
            }

            if (input.isRight()) {
                if (item.state == ItemState.ADDED
                        && KineticClientRuntime.shiftModifierDown()) {
                    item.ref.matchNbt =
                            !item.ref.matchNbt;
                } else {
                    hideItem(item);
                }

                refreshData();
                return true;
            }

            return false;
        }

        if (mouseX >= RIGHT_X
                && mouseX < RIGHT_X + RIGHT_W) {
            int index =
                    itemIndexAt(
                            rightItemScroll,
                            RIGHT_X,
                            RIGHT_ITEM_COLS,
                            mouseX,
                            mouseY
                    );

            if (index < 0
                    || index >= rightHiddenItems.size()) {
                return false;
            }

            DisplayItem item =
                    rightHiddenItems.get(index);

            if (input.isLeft()) {
                if (KineticClientRuntime.controlModifierDown()) {
                    contentDrag.start(
                            DragType.RIGHT_ITEM,
                            item
                    );
                } else {
                    unhideItem(item);
                    refreshData();
                }

                return true;
            }

            if (input.isRight()) {
                unhideItem(item);
                refreshData();
                return true;
            }
        }

        return false;
    }

    private static int itemIndexAt(
            KineticScrollController scroll,
            int gridX,
            int columns,
            double mouseX,
            double mouseY
    ) {
        int itemShift = scroll.visualShift(SLOT_SIZE);
        int row =
                (int) ((mouseY - ITEM_Y + itemShift) / SLOT_SIZE);

        int column =
                (int) ((mouseX - gridX) / SLOT_SIZE);

        return scroll.smoothIndexOffset() * columns
                + row * columns
                + column;
    }

    private void hideItem(
            DisplayItem item
    ) {
        if (item.state == ItemState.BANNED) {
            return;
        }

        if (item.state == ItemState.ADDED) {
            String currentTabId =
                    mainTabs.get(
                            mainSelectedTabIdx
                    ).id.toString();

            TabConfig.currentEditing.additions.forEach(
                    addition -> {
                        if (addition.tabId.equals(
                                currentTabId
                        )) {
                            addition.items.removeIf(
                                    candidate ->
                                            candidate.id.equals(
                                                    item.ref.id
                                            )
                                                    && candidate.nbt.equals(
                                                    item.ref.nbt
                                            )
                            );
                        }
                    }
            );

            TabConfig.currentEditing.additions.removeIf(
                    addition ->
                            addition.items.isEmpty()
            );
        } else if (!TabConfig.currentEditing.removals.contains(
                item.ruleStr
        )) {
            TabConfig.currentEditing.removals.add(
                    item.ruleStr
            );
        }
    }

    private void unhideItem(
            DisplayItem item
    ) {
        TabConfig.currentEditing.removals.remove(
                item.ruleStr
        );
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        if (mainTabScroll.dragHorizontal(
                mouseX,
                TAB_CONTENT_X,
                TAB_CONTENT_W,
                20
        )) {
            return true;
        }

        if (mainItemScroll.drag(
                mouseY,
                ITEM_Y,
                ITEM_H,
                20
        )) {
            return true;
        }

        if (rightItemScroll.drag(
                mouseY,
                ITEM_Y,
                ITEM_H,
                20
        )) {
            return true;
        }

        if (contentDrag.isActive()) {
            return true;
        }

        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        double mouseX = input.x();
        boolean releasedScroll =
                mainTabScroll.release(input.button())
                        | mainItemScroll.release(input.button())
                        | rightItemScroll.release(input.button());

        if (releasedScroll) {
            return true;
        }

        if (input.isLeft()
                && contentDrag.isActive()) {
            Object payload =
                    contentDrag.payload();

            boolean dropRight =
                    mouseX >= RIGHT_X
                            && mouseX <= RIGHT_X + RIGHT_W;

            boolean dropMain =
                    mouseX >= MAIN_X
                            && mouseX <= MAIN_X + MAIN_W;

            if (contentDrag.type() == DragType.MAIN_ITEM
                    && payload instanceof DisplayItem item
                    && dropRight) {
                hideItem(item);
            } else if (contentDrag.type() == DragType.RIGHT_ITEM
                    && payload instanceof DisplayItem item
                    && dropMain) {
                unhideItem(item);
            }

            contentDrag.clear();
            refreshData();
            return true;
        }

        contentDrag.clear();

        return false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        double delta = input.deltaY();
        if (mouseX >= MAIN_X
                && mouseX <= MAIN_X + MAIN_W) {
            if (mouseY >= TAB_Y
                    && mouseY <= TAB_SCROLL_Y + 8) {
                mainTabScroll.scroll(delta);
            } else {
                mainItemScroll.scroll(delta);
            }

            return true;
        }

        if (mouseX >= RIGHT_X
                && mouseX <= RIGHT_X + RIGHT_W) {
            rightItemScroll.scroll(delta);
            return true;
        }

        return false;
    }
}
