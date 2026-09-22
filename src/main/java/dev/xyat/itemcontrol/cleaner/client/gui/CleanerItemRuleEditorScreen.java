package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public final class CleanerItemRuleEditorScreen extends KineticScreen {
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

    private final Screen parent;
    private final Mode mode;
    private final Function<List<String>, Boolean> saveHandler;
    private final List<RuleDraft> rules = new ArrayList<>();
    private final GridScrollController gridScroll = new GridScrollController();

    public CleanerItemRuleEditorScreen(
            Screen parent,
            Mode mode,
            List<String> initialRules,
            Function<List<String>, Boolean> saveHandler
    ) {
        super(Component.translatable(mode.titleKey));
        this.parent = parent;
        setParentScreen(parent);
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
        useCanvas(640F, 360F, 6);
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
    protected void buildUi() {
        updateScrollRange();

        addButton(
                34, 328, 104,
                Component.translatable("gui.itemcontrol.cleaner.item_rule_editor.add"),
                null,
                this::openItemSelector
        );

        addButton(
                432, 328, 80,
                Component.translatable("gui.itemcontrol.cleaner.item_rule_editor.save"),
                null,
                this::save
        );

        addButton(
                522, 328, 80,
                Component.translatable("gui.itemcontrol.cleaner.item_rule_editor.back"),
                null,
                this::closeToParent
        );
    }

    private void openItemSelector() {
        if (minecraft == null) return;
        KineticSelectors.openItemSelector(this, selection -> {
            if (selection == null) return;
            String value = selectionValue(selection);
            if (value.isBlank()) {
                KineticOverlays.toast(Component.translatable("msg.itemcontrol.cleaner.item_rule_editor.invalid_selection"));
                return;
            }
            if (!mode.groupRules && !selection.isItem()) {
                KineticOverlays.toast(Component.translatable("msg.itemcontrol.cleaner.item_rule_editor.item_only"));
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
            KineticOverlays.toast(Component.translatable("msg.itemcontrol.cleaner.item_rule_editor.area_tool_required"));
            return;
        }
        List<String> values = new ArrayList<>(rules.size());
        for (RuleDraft rule : rules) {
            values.add(rule.value);
        }
        try {
            if (!Boolean.TRUE.equals(saveHandler.apply(values))) {
                KineticOverlays.toast(Component.translatable("msg.itemcontrol.cleaner.item_rule_editor.save_failed"));
            } else {
                commitDraft();
            }
        } catch (Throwable throwable) {
            KineticOverlays.toast(Component.translatable("msg.itemcontrol.cleaner.item_rule_editor.save_failed"));
        }
    }

    private void closeToParent() {
        if (minecraft != null) navigateBack();
    }

    @Override
    protected boolean handleCloseRequest() {
        closeToParent();
        return true;
    }

    private void updateScrollRange() {
        int rows = (rules.size() + GRID_COLS - 1) / GRID_COLS;
        gridScroll.update(rows, GRID_ROWS);
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, canvasWidth(), canvasHeight(), 0xFF171717, 0xFF0E0E0E);
        GuiTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        GuiTheme.panel(graphics, GRID_X - 6, GRID_Y - 6, GRID_W + 12, GRID_H + 12);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(font, title, canvasWidth() / 2, 11, 0xFFFFFF);
        renderGrid(graphics, mouseX, mouseY);
        if (rules.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(mode.emptyKey), canvasWidth() / 2, 164, 0xFFFFFF);
        }
        graphics.drawString(font, Component.translatable(
                mode.groupRules
                        ? "gui.itemcontrol.cleaner.item_rule_editor.hint.rules"
                        : "gui.itemcontrol.cleaner.item_rule_editor.hint.item"
        ), 34, 35, 0xFFFFFF, false);
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int firstRow = gridScroll.smoothIndexOffset();
        int shift = gridScroll.visualShift(CELL_SIZE);
        int start = firstRow * GRID_COLS;
        int end = Math.min(start + (GRID_ROWS + (shift > 0 ? 1 : 0)) * GRID_COLS, rules.size());
        enableCanvasScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
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
            GuiTheme.itemSlot(graphics, x, y, SLOT_SIZE, 4, hovered);
            GuiTheme.item(graphics, font, stack, x, y, SLOT_SIZE, 1.0F, true);
            }
        } finally {
            disableCanvasScissor(graphics);
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
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        int index = gridIndexAt(scaledMouseX, scaledMouseY);
        if (index >= 0) {
            RuleDraft rule = rules.get(index);
            if (rule.value.startsWith("#") || rule.value.startsWith("@")) {
                KineticOverlays.requestTooltip(List.of(
                                rule.preview().getHoverName(),
                                Component.literal(rule.value),
                                Component.translatable(rule.value.startsWith("#")
                                        ? "gui.itemcontrol.cleaner.item_rule_editor.type.tag"
                                        : "gui.itemcontrol.cleaner.item_rule_editor.type.mod")
                        ), mouseX, mouseY);
            } else {
                KineticOverlays.requestItemTooltip(rule.preview(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (KineticMouseButtons.isPrimary(button) && gridScroll.beginDrag(mouseX, mouseY, SCROLL_X, GRID_Y, 4, GRID_H, 18, 0)) {
            return true;
        }
        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0 && KineticMouseButtons.isSecondary(button)) {
            rules.remove(index);
            updateScrollRange();
            return true;
        }
        return super.canvasMouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        if (gridScroll.release(button)) return true;
        return super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (gridScroll.drag(mouseY, GRID_Y, GRID_H, 18)) return true;
        return super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (contains(mouseX, mouseY, GRID_X, GRID_Y, GRID_W + 14, GRID_H) && gridScroll.scroll(delta)) {
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
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
