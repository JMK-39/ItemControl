package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.page.KineticContainerPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.render.KineticTexture;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import java.util.HashMap;
import java.util.Map;

public class CleanerPage extends KineticContainerPage<CleanerMenu> {
    private static final KineticTexture TEXTURE = KineticTexture.of("minecraft", "textures/gui/container/generic_54.png");
    // 原界面覆盖 renderLabels 且不绘制原版标题与“物品栏”文字；页面宿主会绘制它们，因此移出可见区域
    // The old screen overrode renderLabels and never drew the vanilla title / "Inventory" labels; the page host
    // draws them, so they are moved out of the visible area.
    private static final int HIDDEN_LABEL_POS = -10000;
    // Leave room for the title and history text even when navigation labels are long.
    private static final int MIN_HEADER_WIDTH = 70;

    private final KineticScrollController scroller = new KineticScrollController();
    private int lastSyncedRow = -1;
    private int lastSyncedHistoryIndex = -1;
    private int historyIndex;
    private final int maxHistory;
    private KineticButton previousPageButton;
    private KineticButton nextPageButton;
    private int headerRightX;
    private final Map<Slot, Integer> originalCounts = new HashMap<>();

    public CleanerPage(CleanerMenu cleanerMenu, Component title) {
        super(cleanerMenu, title);
        setImageSize(CleanerMenu.IMAGE_WIDTH, CleanerMenu.IMAGE_HEIGHT);
        setTitleLabelPosition(HIDDEN_LABEL_POS, HIDDEN_LABEL_POS);
        setInventoryLabelPosition(HIDDEN_LABEL_POS, HIDDEN_LABEL_POS);
        this.maxHistory = CleanerConfig.trashBinHistorySize;
    }

    @Override
    protected void build(KineticUi ui) {
        // Vanilla container initialization may reset the inventory label after construction.
        setTitleLabelPosition(HIDDEN_LABEL_POS, HIDDEN_LABEL_POS);
        setInventoryLabelPosition(HIDDEN_LABEL_POS, HIDDEN_LABEL_POS);
        Component previous = KineticI18n.translatable("gui.itemcontrol.cleaner.cleaner.prev");
        Component next = KineticI18n.translatable("gui.itemcontrol.cleaner.cleaner.next");
        int gap = 2;
        int maxButtonWidth = (imageWidth() - 16 - MIN_HEADER_WIDTH - gap) / 2;
        // Fixed widths in every language; the labels scroll when longer.
        int previousWidth = Math.min(maxButtonWidth, 40);
        int nextWidth = Math.min(maxButtonWidth, 40);
        int nextX = leftPos() + imageWidth() - 8 - nextWidth;
        int previousX = nextX - gap - previousWidth;
        headerRightX = previousX - 4;
        int y = topPos() + 2;

        previousPageButton = ui.button(previousX, y, previousWidth)
                .compact()
                .text(previous)
                .onClick(this::showPreviousPage)
                .build();
        nextPageButton = ui.button(nextX, y, nextWidth)
                .compact()
                .text(next)
                .onClick(this::showNextPage)
                .build();
        updatePageButtons();
    }

    private String formatCount(int count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return (count / 1000) + "k";
        if (count < 1000000000) return (count / 1000000) + "m";
        return (count / 1000000000) + "g";
    }

    // Right of the trash grid: 8 px left margin + nine 20 px columns - the last gap + 2 px.
    private static final int SCROLL_X = 8 + 9 * CleanerMenu.TRASH_PITCH - 2 + 2;

    /** The vanilla container frame at any size, cut from the chest texture: 4 px corners and edges, plain fill. */
    private static void vanillaPanel(KineticGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0xFFC6C6C6);
        for (int dx = 4; dx < width - 4; dx += 160) {
            int w = Math.min(160, width - 4 - dx);
            graphics.texture(TEXTURE, x + dx, y, 4, 0, w, 4);
            graphics.texture(TEXTURE, x + dx, y + height - 4, 4, 218, w, 4);
        }
        for (int dy = 4; dy < height - 4; dy += 100) {
            int h = Math.min(100, height - 4 - dy);
            graphics.texture(TEXTURE, x, y + dy, 0, 4, 4, h);
            graphics.texture(TEXTURE, x + width - 4, y + dy, 172, 4, 4, h);
        }
        graphics.texture(TEXTURE, x, y, 0, 0, 4, 4);
        graphics.texture(TEXTURE, x + width - 4, y, 172, 0, 4, 4);
        graphics.texture(TEXTURE, x, y + height - 4, 0, 218, 4, 4);
        graphics.texture(TEXTURE, x + width - 4, y + height - 4, 172, 218, 4, 4);
    }

    @Override
    protected void renderContainerBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = leftPos();
        int y = topPos();
        vanillaPanel(graphics, x, y, imageWidth(), imageHeight());
        // The player inventory and hotbar keep the chest's spacing, so they are copied from the chest texture in one
        // piece and look like a chest's inventory under any resource pack.
        graphics.texture(TEXTURE, x + CleanerMenu.INVENTORY_X - 1, y + CleanerMenu.INVENTORY_Y - 1, 7, 139, 162, 3 * 18 + 4 + 18);
        for (Slot slot : menu().slots) {
            if (slot.container != menu().scrollableContainer) continue;
            // Trash slots look exactly like the player inventory below: each takes the inventory sprite of the same
            // column and row parity, so resource packs that draw the inventory as a checkerboard continue it here.
            int column = (slot.x - 8) / CleanerMenu.TRASH_PITCH, row = (slot.y - 18) / CleanerMenu.TRASH_PITCH;
            graphics.texture(TEXTURE, x + slot.x - 1, y + slot.y - 1, 7 + column * 18, 139 + (row % 2) * 18, 18, 18);
        }

        scroller.update(menu().getTotalRows(), 6);
        scroller.render(graphics, mouseX, mouseY, x + SCROLL_X, y + 18, 4, CleanerMenu.TRASH_GRID_HEIGHT, 15);

        originalCounts.clear();
        for (Slot slot : menu().slots) {
            if (slot.container == menu().scrollableContainer && slot.hasItem()) {
                int count = slot.getItem().getCount();
                if (count > 1) {
                    originalCounts.put(slot, count);
                    slot.getItem().setCount(1);
                }
            }
        }
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 原 renderLabels：坐标相对容器区域 / Former renderLabels: coordinates relative to the container area.
        int left = leftPos();
        int top = topPos();
        graphics.push();
        try {
            // The slots already drew the durability bar (at count 1). The count goes on a raised pass above that bar,
            // at the vanilla position, so the bar never covers it.
            graphics.raise(2);
            for (Map.Entry<Slot, Integer> entry : originalCounts.entrySet()) {
                Slot slot = entry.getKey();
                int realCount = entry.getValue();
                slot.getItem().setCount(realCount);
                String text = formatCount(realCount);
                graphics.text(text, left + slot.x + 17 - graphics.textWidth(text), top + slot.y + 9, 0xFF55FF55, true);
            }
        } finally {
            graphics.pop();
        }
        originalCounts.clear();

        Component titleComp = KineticI18n.translatable("gui.itemcontrol.cleaner.cleaner.title");
        Component pageText = KineticI18n.translatable(
                "gui.itemcontrol.cleaner.cleaner.page",
                Component.literal(String.valueOf(historyIndex + 1)),
                Component.literal(String.valueOf(maxHistory))
        );
        int titleX = left + 8;
        int headerWidth = headerRightX - titleX;
        // The title has a fixed area in every language, so the page number after it never moves.
        int titleWidth = Math.min(50, headerWidth / 2);
        graphics.scrollingText(titleComp, titleX, top + 6, titleWidth, 4210752, false);
        int pageX = titleX + titleWidth + 6;
        graphics.scrollingText(pageText, pageX, top + 6, headerRightX - pageX, 4210752, false);
    }

    private void showPreviousPage() {
        if (historyIndex <= 0) return;
        historyIndex--;
        resetAndSync();
        updatePageButtons();
    }

    private void showNextPage() {
        if (historyIndex >= maxHistory - 1) return;
        historyIndex++;
        resetAndSync();
        updatePageButtons();
    }

    private void updatePageButtons() {
        if (previousPageButton != null) {
            previousPageButton.setEnabled(historyIndex > 0);
        }
        if (nextPageButton != null) {
            nextPageButton.setEnabled(historyIndex < maxHistory - 1);
        }
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 containerMouseClicked 在槽位与控件之前运行 / The old containerMouseClicked ran before slots and controls.
        scroller.update(menu().getTotalRows(), 6);
        if (scroller.beginDrag(input.x(), input.y(), input.button(), leftPos() + SCROLL_X, topPos() + 18, 4, CleanerMenu.TRASH_GRID_HEIGHT, 15, 2)) {
            this.syncToServer(false);
            return true;
        }
        return false;
    }

    private void resetAndSync() {
        scroller.update(menu().getTotalRows(), 6);
        scroller.setOffset(0);
        menu().scrollableContainer.clearContent();
        this.syncToServer(true);
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        scroller.update(menu().getTotalRows(), 6);
        if (scroller.canScroll()) {
            scroller.scroll(input.deltaY());
            this.syncToServer(false);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        // 原 containerMouseDragged / Former containerMouseDragged.
        if (!scroller.drag(input.y(), topPos() + 18, CleanerMenu.TRASH_GRID_HEIGHT, 15)) return false;
        this.syncToServer(false);
        return true;
    }

    /** 原 containerMouseReleased / Former containerMouseReleased. */
    @Override
    protected boolean onMouseRelease(MouseInput input) {
        scroller.release(input.button());
        return false;
    }

    private void syncToServer(boolean force) {
        int currentRow = scroller.offset();
        if (!force && currentRow == lastSyncedRow && historyIndex == lastSyncedHistoryIndex) {
            return;
        }
        lastSyncedRow = currentRow;
        lastSyncedHistoryIndex = historyIndex;
        menu().updateState(historyIndex, currentRow);
        CleanerNetwork.sendToServer(new CleanerNetwork.SyncTrashBin(currentRow, historyIndex));
    }
}
