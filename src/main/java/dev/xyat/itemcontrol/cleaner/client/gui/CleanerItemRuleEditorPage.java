package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// NeoForge 26.1 no longer strips @OnlyIn members and warns about the annotation; this class is only used on the client.
//? if <26.1
@OnlyIn(Dist.CLIENT)
public final class CleanerItemRuleEditorPage extends KineticPage {
    public enum Mode {
        CLEANER_WHITELIST(
                "gui.itemcontrol.cleaner.item_rule_editor.whitelist.title",
                "gui.itemcontrol.cleaner.item_rule_editor.whitelist.empty",
                true,
                false
        ),
        TRASH_BLACKLIST(
                "gui.itemcontrol.cleaner.item_rule_editor.blacklist.title",
                "gui.itemcontrol.cleaner.item_rule_editor.blacklist.empty",
                true,
                false
        ),
        AREA_TOOL(
                "gui.itemcontrol.cleaner.item_rule_editor.area_tool.title",
                "gui.itemcontrol.cleaner.item_rule_editor.area_tool.empty",
                false,
                true
        );

        private final String titleKey;
        private final String emptyKey;
        private final boolean groupRules;
        private final boolean singleItem;

        Mode(String titleKey, String emptyKey, boolean groupRules, boolean singleItem) {
            this.titleKey = titleKey;
            this.emptyKey = emptyKey;
            this.groupRules = groupRules;
            this.singleItem = singleItem;
        }
    }

    private static final int SLOT_SIZE = 22;
    private static final int SLOT_GAP = 2;
    private static final int CELL_SIZE = SLOT_SIZE + SLOT_GAP;
    // Keep the original list envelope; use fewer complete slots rather than smaller icons.
    private static final int GROUP_COLS = (569 + SLOT_GAP) / CELL_SIZE;
    private static final int MIN_ROWS = 3;
    private static final int MAX_ROWS = (246 + SLOT_GAP) / CELL_SIZE;
    private static final int PANEL_PAD = 16;
    private static final int MIN_SINGLE_PANEL_W = 316;
    private static final int MAX_PANEL_W = 604;

    // The window fits its content: the area tool shows one slot, rule lists show the rows their rules need plus one
    // free row, and the whole block is centred in the canvas.
    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, gridW, gridH, gridCols, gridRows, scrollX;

    private final Mode mode;
    private final Function<List<String>, Boolean> saveHandler;
    private final List<RuleDraft> rules = new ArrayList<>();
    private final KineticScrollController gridScroll = new KineticScrollController();

    public CleanerItemRuleEditorPage(
            Mode mode,
            List<String> initialRules,
            Function<List<String>, Boolean> saveHandler
    ) {
        super(KineticI18n.translatable(mode.titleKey));
        this.mode = mode;
        this.saveHandler = saveHandler;
        if (initialRules != null) {
            for (String raw : initialRules) {
                String value = raw == null ? "" : raw.trim();
                if (!value.isEmpty() && rules.stream().noneMatch(rule -> rule.value.equals(value))) {
                    rules.add(new RuleDraft(value));
                }
            }
        }
        useCanvas(640, 360, 6);
        configureStandaloneDraft(
                this::ruleValues,
                this::restoreRuleValues
        );
    }

    private List<String> ruleValues() {
        List<String> values = new ArrayList<>(rules.size());
        for (RuleDraft rule : rules) values.add(rule.value);
        return values;
    }

    private void restoreRuleValues(List<String> values) {
        rules.clear();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) rules.add(new RuleDraft(value));
            }
        }
        refreshLayout();
    }

    @Override
    protected void build(KineticUi ui) {
        layoutWindow();
        updateScrollRange();

        int buttonY = panelY + panelH - 8 - 20;
        ui.button(panelX + PANEL_PAD + 90, buttonY, 104)
                .text(KineticI18n.translatable("gui.itemcontrol.cleaner.item_rule_editor.add"))
                .onClick(this::openItemSelector)
                .build();

        ui.button(panelX + panelW - 100, buttonY, 80)
                .text(KineticI18n.translatable("gui.itemcontrol.cleaner.item_rule_editor.save"))
                .onClick(this::save)
                .build();

        ui.button(panelX + PANEL_PAD, buttonY, 80)
                .text(KineticI18n.translatable("gui.itemcontrol.cleaner.item_rule_editor.back"))
                .onClick(this::closeToParent)
                .build();
    }

    // Title, hint, grid and buttons all sit inside one panel. The area tool shows only its one slot (name, id and
    // hint are in the slot's tooltip); rule lists show the rows their rules need plus one free row.
    private void layoutWindow() {
        if (mode.singleItem) {
            gridCols = 1;
            gridRows = 1;
            panelW = MIN_SINGLE_PANEL_W;
            gridW = SLOT_SIZE;
        } else {
            gridCols = GROUP_COLS;
            gridRows = wantedRows();
            gridW = gridCols * CELL_SIZE - SLOT_GAP;
            panelW = MAX_PANEL_W;
        }
        gridH = gridRows * CELL_SIZE - SLOT_GAP;
        int gridTop = mode.singleItem ? 26 : 40;
        // Grid top, the grid and its frame, the button row and the margins around it.
        panelH = gridTop + gridH + 6 + 8 + 20 + 8;
        panelX = (width() - panelW) / 2;
        panelY = Math.max(0, (height() - panelH) / 2);
        gridX = mode.singleItem ? panelX + (panelW - SLOT_SIZE) / 2 : panelX + PANEL_PAD;
        gridY = panelY + gridTop;
        scrollX = gridX + gridW + 3;
    }

    private int wantedRows() {
        int contentRows = (rules.size() + GROUP_COLS - 1) / GROUP_COLS;
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, contentRows + 1));
    }

    // Rule lists grow and shrink with their rules; the window is laid out again when the row count changes.
    private void refreshLayout() {
        if (!mode.singleItem && isAttached() && wantedRows() != gridRows) rebuild();
        else updateScrollRange();
    }

    private Component hintText() {
        return KineticI18n.translatable(mode.groupRules
                ? "gui.itemcontrol.cleaner.item_rule_editor.hint.rules"
                : "gui.itemcontrol.cleaner.item_rule_editor.hint.item");
    }

    private void openItemSelector() {
        if (!isAttached()) return;
        KineticSelectors.openItemSelector(selection -> {
            if (selection == null) return;
            String value = selectionValue(selection);
            if (value.isBlank()) {
                KineticOverlays.toast(KineticI18n.translatable("msg.itemcontrol.cleaner.item_rule_editor.invalid_selection"));
                return;
            }
            if (!mode.groupRules && !selection.isItem()) {
                KineticOverlays.toast(KineticI18n.translatable("msg.itemcontrol.cleaner.item_rule_editor.item_only"));
                return;
            }
            if (mode.singleItem) {
                rules.clear();
                rules.add(new RuleDraft(value));
            } else if (rules.stream().noneMatch(rule -> rule.value.equals(value))) {
                rules.add(new RuleDraft(value));
            }
            refreshLayout();
            int row = Math.max(0, rules.size() - 1) / gridCols;
            if (row >= gridScroll.offset() + gridRows) {
                gridScroll.setOffset(row - gridRows + 1);
            }
        });
    }

    private String selectionValue(KineticSelectors.ItemSelection selection) {
        if (selection.isTag()) return "#" + selection.value().trim();
        if (selection.isMod()) return "@" + selection.value().trim();
        if (!selection.isItem()) return "";
        ResourceLocation id = KineticRegistries.items().id(selection.stack().getItem());
        return id == null ? "" : id.toString();
    }

    private void save() {
        if (mode.singleItem && rules.isEmpty()) {
            KineticOverlays.toast(KineticI18n.translatable("msg.itemcontrol.cleaner.item_rule_editor.area_tool_required"));
            return;
        }
        List<String> values = new ArrayList<>(rules.size());
        for (RuleDraft rule : rules) {
            values.add(rule.value);
        }
        try {
            if (!Boolean.TRUE.equals(saveHandler.apply(values))) {
                KineticOverlays.toast(KineticI18n.translatable("msg.itemcontrol.cleaner.item_rule_editor.save_failed"));
            } else {
                commitDraft();
            }
        } catch (Throwable throwable) {
            KineticOverlays.toast(KineticI18n.translatable("msg.itemcontrol.cleaner.item_rule_editor.save_failed"));
        }
    }

    private void closeToParent() {
        if (isAttached()) navigateBack();
    }

    @Override
    protected boolean onCloseRequested() {
        closeToParent();
        return true;
    }

    private void updateScrollRange() {
        int rows = (rules.size() + gridCols - 1) / gridCols;
        gridScroll.update(rows, gridRows);
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The world stays visible behind a light shade; only the panel itself is dark.
        KineticTheme.shadow(graphics, width(), height());
        KineticTheme.panel(graphics, panelX, panelY, panelW, panelH);
        // Rule lists keep their scroll bar inside the grid frame, 3 px right of the slots.
        int frameW = mode.singleItem ? gridW + 12 : scrollX + 4 + 4 - (gridX - 6);
        KineticTheme.panel(graphics, gridX - 6, gridY - 6, frameW, gridH + 12);
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.scrollingTextCentered(title(), panelX + panelW / 2, panelY + 8, panelW - 2 * PANEL_PAD, 0xFFFFFF, true);
        renderGrid(graphics, mouseX, mouseY);
        if (mode.singleItem) {
            if (rules.isEmpty()) {
                KineticTheme.itemSlot(graphics, gridX, gridY, SLOT_SIZE, 4, contains(mouseX, mouseY, gridX, gridY, SLOT_SIZE, SLOT_SIZE));
            }
            return;
        }
        if (rules.isEmpty()) {
            graphics.scrollingTextCentered(KineticI18n.translatable(mode.emptyKey), gridX + gridW / 2, gridY + (gridH - 8) / 2, gridW - 4, 0xFFFFFF, true);
        }
        graphics.scrollingText(hintText(), gridX, panelY + 22, gridW - 4, 0xFFFFFF, false);
    }

    private void renderGrid(KineticGraphics graphics, int mouseX, int mouseY) {
        int firstRow = gridScroll.smoothIndexOffset();
        int shift = gridScroll.visualShift(CELL_SIZE);
        int start = firstRow * gridCols;
        int end = Math.min(start + (gridRows + (shift > 0 ? 1 : 0)) * gridCols, rules.size());
        graphics.scissor(gridX, gridY, gridX + gridW, gridY + gridH);
        try {
            for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local % gridCols;
            int row = local / gridCols;
            int x = gridX + col * CELL_SIZE;
            int y = gridY + row * CELL_SIZE - shift;
            if (y + SLOT_SIZE <= gridY || y >= gridY + gridH) continue;
            boolean hovered = contains(mouseX, mouseY, gridX, gridY, gridW, gridH)
                    && contains(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE);
            ItemStack stack = rules.get(index).preview();
            KineticTheme.itemSlot(graphics, x, y, SLOT_SIZE, 4, hovered);
            KineticTheme.item(graphics, stack, x, y, SLOT_SIZE, 1.0F, true);
            }
        } finally {
            graphics.endScissor();
        }

        updateScrollRange();
        if (!mode.singleItem) gridScroll.render(
                graphics,
                mouseX,
                mouseY,
                scrollX,
                gridY,
                4,
                gridH,
                18
        );
    }

    @Override
    protected void renderTooltips(int mouseX, int mouseY) {
        if (mode.singleItem) {
            // The area tool's name, id and the editing hint live in the slot's tooltip instead of a text row.
            if (!contains(mouseX, mouseY, gridX, gridY, SLOT_SIZE, SLOT_SIZE)) return;
            if (rules.isEmpty()) {
                showTooltip(List.of(KineticI18n.translatable(mode.emptyKey), hintText()));
            } else {
                RuleDraft rule = rules.get(0);
                showTooltip(List.of(rule.preview().getHoverName(), Component.literal(rule.value), hintText()));
            }
            return;
        }
        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0) {
            RuleDraft rule = rules.get(index);
            if (rule.value.startsWith("#") || rule.value.startsWith("@")) {
                showTooltip(List.of(
                                rule.preview().getHoverName(),
                                Component.literal(rule.value),
                                KineticI18n.translatable(rule.value.startsWith("#")
                                        ? "gui.itemcontrol.cleaner.item_rule_editor.type.tag"
                                        : "gui.itemcontrol.cleaner.item_rule_editor.type.mod")
                ));
            } else {
                showItemTooltip(rule.preview());
            }
        }
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 canvasMouseClicked 在控件之前处理 / The old canvasMouseClicked handled this before controls.
        double mouseX = input.x();
        double mouseY = input.y();
        if (gridScroll.beginDrag(mouseX, mouseY, input.button(), scrollX, gridY, 4, gridH, 18, 0)) {
            return true;
        }
        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0 && input.isRight()) {
            rules.remove(index);
            refreshLayout();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        return gridScroll.release(input.button());
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        return gridScroll.drag(input.y(), gridY, gridH, 18);
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        if (contains(input.x(), input.y(), gridX, gridY, gridW + 14, gridH) && gridScroll.scroll(input.deltaY())) {
            return true;
        }
        return false;
    }

    private int gridIndexAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY, gridX, gridY, gridW, gridH)) return -1;
        int col = (int) ((mouseX - gridX) / CELL_SIZE);
        int shift = gridScroll.visualShift(CELL_SIZE);
        double contentY = mouseY - gridY + shift;
        int row = (int) Math.floor(contentY / CELL_SIZE);
        if (col < 0 || col >= gridCols || row < 0 || row > gridRows) return -1;
        double localX = mouseX - gridX - col * CELL_SIZE;
        double localY = contentY - row * CELL_SIZE;
        if (localX >= SLOT_SIZE || localY >= SLOT_SIZE) return -1;
        int index = (gridScroll.smoothIndexOffset() + row) * gridCols + col;
        return index >= 0 && index < rules.size() ? index : -1;
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static final class RuleDraft {
        private final String value;
        private ItemStack cachedPreview;

        private RuleDraft(String value) {
            this.value = value;
        }

        private ItemStack preview() {
            if (cachedPreview == null) cachedPreview = createPreview(value);
            return cachedPreview;
        }

        private static ItemStack createPreview(String value) {
            if (value.startsWith("@")) {
                String namespace = value.substring(1).trim();
                for (Item item : KineticRegistries.items().values()) {
                    ResourceLocation id = KineticRegistries.items().id(item);
                    if (id != null && id.getNamespace().equals(namespace) && item != Items.AIR) {
                        return new ItemStack(item);
                    }
                }
                return new ItemStack(Items.BARRIER);
            }
            if (value.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(value.substring(1).trim());
                if (tagId != null) {
                    var tag = ItemTags.create(tagId);
                    for (Item item : KineticRegistries.items().values()) {
                        if (item == Items.AIR) continue;
                        ItemStack stack = new ItemStack(item);
                        if (stack.is(tag)) return stack;
                    }
                }
                return new ItemStack(Items.BARRIER);
            }
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) return new ItemStack(Items.BARRIER);
            Item item = KineticRegistries.items().get(id);
            if (item == null || item == Items.AIR) return new ItemStack(Items.BARRIER);
            return new ItemStack(item);
        }
    }
}
