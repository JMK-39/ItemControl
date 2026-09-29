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
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
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

    private final KineticScrollController scroller = new KineticScrollController();
    private int lastSyncedRow = -1;
    private int lastSyncedHistoryIndex = -1;
    private int historyIndex;
    private final int maxHistory;
    private KineticButton previousPageButton;
    private KineticButton nextPageButton;
    private final Map<Slot, Integer> originalCounts = new HashMap<>();

    public CleanerPage(CleanerMenu cleanerMenu, Component title) {
        super(cleanerMenu, title);
        setImageSize(176, 222);
        setTitleLabelPosition(HIDDEN_LABEL_POS, HIDDEN_LABEL_POS);
        setInventoryLabelPosition(HIDDEN_LABEL_POS, HIDDEN_LABEL_POS);
        this.maxHistory = CleanerConfig.trashBinHistorySize;
    }

    @Override
    protected void build(KineticUi ui) {
        Component previous = KineticI18n.translatable("gui.itemcontrol.cleaner.cleaner.prev");
        Component next = KineticI18n.translatable("gui.itemcontrol.cleaner.cleaner.next");
        int gap = 2;
        int previousWidth = Math.max(26, KineticText.width(previous) + 8);
        int nextWidth = Math.max(26, KineticText.width(next) + 8);
        int nextX = leftPos() + imageWidth() - 8 - nextWidth;
        int previousX = nextX - gap - previousWidth;
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

    @Override
    protected void renderContainerBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = leftPos();
        int y = topPos();
        graphics.texture(TEXTURE, x, y, 0, 0, 176, 222);

        for (Slot slot : menu().slots) {
            if (slot.isActive() && slot.container == menu().scrollableContainer) {
                KineticTheme.itemSlot(graphics, x + slot.x - 1, y + slot.y - 1);
            }
        }

        scroller.update(menu().getTotalRows(), 6);
        scroller.render(graphics, mouseX, mouseY, x + 171, y + 18, 4, 108, 15);

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
        for (Map.Entry<Slot, Integer> entry : originalCounts.entrySet()) {
            Slot slot = entry.getKey();
            int realCount = entry.getValue();
            slot.getItem().setCount(realCount);
            String customText = formatCount(realCount);

            graphics.push();
            graphics.translate(left + slot.x, top + slot.y);
            // 原 translate z=300：抬高一层以盖过物品 / Former z=300 translate: raise one layer above items.
            graphics.raise(1);
            float scale = 0.85F;
            graphics.scale(scale, scale);
            int textWidth = graphics.textWidth(customText);
            float textX = (16.0f / scale) - textWidth - 0.1f;
            float textY = (16.0f / scale) - 7.0f;
            graphics.text(customText, (int) textX, (int) textY, 0x55FF55, true);
            graphics.pop();
        }
        originalCounts.clear();

        Component titleComp = KineticI18n.translatable("gui.itemcontrol.cleaner.cleaner.title");
        graphics.text(titleComp, left + 8, top + 6, 4210752, false);
        Component pageText = KineticI18n.translatable(
                "gui.itemcontrol.cleaner.cleaner.page",
                Component.literal(String.valueOf(historyIndex + 1)),
                Component.literal(String.valueOf(maxHistory))
        );
        graphics.text(pageText, left + 8 + graphics.textWidth(titleComp) + 6, top + 6, 4210752, false);
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
        if (scroller.beginDrag(input.x(), input.y(), input.button(), leftPos() + 171, topPos() + 18, 4, 108, 15, 2)) {
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
        if (!scroller.drag(input.y(), topPos() + 18, 108, 15)) return false;
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
