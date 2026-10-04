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

    private static final int PANEL_X = 18;
    private static final int PANEL_Y = 28;
    private static final int PANEL_W = 604;
    private static final int PANEL_H = 286;
    private static final int GRID_X = 34;
    private static final int GRID_Y = 52;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 1;
    private static final int CELL_SIZE = SLOT_SIZE + SLOT_GAP;
    private static final int GRID_COLS = 30;
    private static final int GRID_ROWS = 13;
    private static final int GRID_W = GRID_COLS * CELL_SIZE - SLOT_GAP;
    private static final int GRID_H = GRID_ROWS * CELL_SIZE - SLOT_GAP;
    private static final int SCROLL_X = GRID_X + GRID_W + 6;

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
        updateScrollRange();
    }

    @Override
    protected void build(KineticUi ui) {
        updateScrollRange();

        ui.button(34, 328, 104)
                .text(KineticI18n.translatable("gui.itemcontrol.cleaner.item_rule_editor.add"))
                .onClick(this::openItemSelector)
                .build();

        ui.button(432, 328, 80)
                .text(KineticI18n.translatable("gui.itemcontrol.cleaner.item_rule_editor.save"))
                .onClick(this::save)
                .build();

        ui.button(522, 328, 80)
                .text(KineticI18n.translatable("gui.itemcontrol.cleaner.item_rule_editor.back"))
                .onClick(this::closeToParent)
                .build();
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
            updateScrollRange();
            int row = Math.max(0, rules.size() - 1) / GRID_COLS;
            if (row >= gridScroll.offset() + GRID_ROWS) {
                gridScroll.setOffset(row - GRID_ROWS + 1);
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
        int rows = (rules.size() + GRID_COLS - 1) / GRID_COLS;
        gridScroll.update(rows, GRID_ROWS);
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width(), height(), 0xFF171717, 0xFF0E0E0E);
        KineticTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        KineticTheme.panel(graphics, GRID_X - 6, GRID_Y - 6, GRID_W + 12, GRID_H + 12);
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.scrollingTextCentered(title(), width() / 2, 11, PANEL_W - 4, 0xFFFFFF, true);
        renderGrid(graphics, mouseX, mouseY);
        if (rules.isEmpty()) {
            graphics.scrollingTextCentered(KineticI18n.translatable(mode.emptyKey), width() / 2, 164, GRID_W - 4, 0xFFFFFF, true);
        }
        graphics.scrollingText(KineticI18n.translatable(
                mode.groupRules
                        ? "gui.itemcontrol.cleaner.item_rule_editor.hint.rules"
                        : "gui.itemcontrol.cleaner.item_rule_editor.hint.item"
        ), GRID_X, 35, GRID_W - 4, 0xFFFFFF, false);
    }

    private void renderGrid(KineticGraphics graphics, int mouseX, int mouseY) {
        int firstRow = gridScroll.smoothIndexOffset();
        int shift = gridScroll.visualShift(CELL_SIZE);
        int start = firstRow * GRID_COLS;
        int end = Math.min(start + (GRID_ROWS + (shift > 0 ? 1 : 0)) * GRID_COLS, rules.size());
        graphics.scissor(GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        try {
            for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local % GRID_COLS;
            int row = local / GRID_COLS;
            int x = GRID_X + col * CELL_SIZE;
            int y = GRID_Y + row * CELL_SIZE - shift;
            if (y + SLOT_SIZE <= GRID_Y || y >= GRID_Y + GRID_H) continue;
            boolean hovered = contains(mouseX, mouseY, GRID_X, GRID_Y, GRID_W, GRID_H)
                    && contains(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE);
            ItemStack stack = rules.get(index).preview();
            KineticTheme.itemSlot(graphics, x, y, SLOT_SIZE, 4, hovered);
            KineticTheme.item(graphics, stack, x, y, SLOT_SIZE, 1.0F, true);
            }
        } finally {
            graphics.endScissor();
        }

        updateScrollRange();
        gridScroll.render(
                graphics,
                mouseX,
                mouseY,
                SCROLL_X,
                GRID_Y,
                4,
                GRID_H,
                18
        );
    }

    @Override
    protected void renderTooltips(int mouseX, int mouseY) {
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
        if (gridScroll.beginDrag(mouseX, mouseY, input.button(), SCROLL_X, GRID_Y, 4, GRID_H, 18, 0)) {
            return true;
        }
        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0 && input.isRight()) {
            rules.remove(index);
            updateScrollRange();
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
        return gridScroll.drag(input.y(), GRID_Y, GRID_H, 18);
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        if (contains(input.x(), input.y(), GRID_X, GRID_Y, GRID_W + 14, GRID_H) && gridScroll.scroll(input.deltaY())) {
            return true;
        }
        return false;
    }

    private int gridIndexAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY, GRID_X, GRID_Y, GRID_W, GRID_H)) return -1;
        int col = (int) ((mouseX - GRID_X) / CELL_SIZE);
        int shift = gridScroll.visualShift(CELL_SIZE);
        double contentY = mouseY - GRID_Y + shift;
        int row = (int) Math.floor(contentY / CELL_SIZE);
        if (col < 0 || col >= GRID_COLS || row < 0 || row > GRID_ROWS) return -1;
        double localX = mouseX - GRID_X - col * CELL_SIZE;
        double localY = contentY - row * CELL_SIZE;
        if (localX >= SLOT_SIZE || localY >= SLOT_SIZE) return -1;
        int index = (gridScroll.smoothIndexOffset() + row) * GRID_COLS + col;
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
