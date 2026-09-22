package dev.xyat.itemcontrol.cleaner.client.gui;

import javax.annotation.Nonnull;

import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.screen.KineticContainerScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class CleanerScreen extends KineticContainerScreen<CleanerMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

    private double scrollTargetRow;
    private final KineticScroll.State scrollState = new KineticScroll.State();
    private int lastSyncedRow = -1;
    private int lastSyncedHistoryIndex = -1;
    private boolean scrolling;
    private int historyIndex;
    private final int maxHistory;
    private StateButton previousPageButton;
    private StateButton nextPageButton;
    private final Map<Slot, Integer> originalCounts = new HashMap<>();

    public CleanerScreen(CleanerMenu cleanerMenu, Inventory inv, Component title) {
        super(cleanerMenu, inv, title);
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
        this.maxHistory = CleanerConfig.trashBinHistorySize;
    }

    @Override
    protected void buildUi() {
        Component previous = Component.translatable("gui.itemcontrol.cleaner.cleaner.prev");
        Component next = Component.translatable("gui.itemcontrol.cleaner.cleaner.next");
        int gap = 2;
        int previousWidth = Math.max(26, this.font.width(previous) + 8);
        int nextWidth = Math.max(26, this.font.width(next) + 8);
        int nextX = this.leftPos + this.imageWidth - 8 - nextWidth;
        int previousX = nextX - gap - previousWidth;
        int y = this.topPos + 2;

        previousPageButton = addCompactButton(
                previousX,
                y,
                previousWidth,
                previous,
                null,
                this::showPreviousPage
        );
        nextPageButton = addCompactButton(
                nextX,
                y,
                nextWidth,
                next,
                null,
                this::showNextPage
        );
        updatePageButtons();
    }

    private String formatCount(int count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return (count / 1000) + "k";
        if (count < 1000000000) return (count / 1000000) + "m";
        return (count / 1000000000) + "g";
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, 176, 222);

        for (Slot slot : this.menu.slots) {
            if (slot.isActive() && slot.container == this.menu.scrollableContainer) {
                GuiTheme.itemSlot(graphics, x + slot.x - 1, y + slot.y - 1);
            }
        }

        int scrollX = x + 172;
        int scrollY = y + 18;
        int hiddenRows = Math.max(0, this.menu.getTotalRows() - 6);
        double visualRow = scrollState.follow(scrollTargetRow, hiddenRows, scrolling);
        GuiTheme.scrollbar(
                graphics,
                mouseX,
                mouseY,
                scrollX - 1,
                scrollY,
                4,
                108,
                15,
                hiddenRows,
                visualRow,
                scrolling
        );

        originalCounts.clear();
        for (Slot slot : this.menu.slots) {
            if (slot.container == this.menu.scrollableContainer && slot.hasItem()) {
                int count = slot.getItem().getCount();
                if (count > 1) {
                    originalCounts.put(slot, count);
                    slot.getItem().setCount(1);
                }
            }
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        for (Map.Entry<Slot, Integer> entry : originalCounts.entrySet()) {
            Slot slot = entry.getKey();
            int realCount = entry.getValue();
            slot.getItem().setCount(realCount);
            String customText = formatCount(realCount);

            graphics.pose().pushPose();
            graphics.pose().translate(slot.x, slot.y, 300.0F);
            float scale = 0.85F;
            graphics.pose().scale(scale, scale, 1.0F);
            int textWidth = this.font.width(customText);
            float textX = (16.0f / scale) - textWidth - 0.1f;
            float textY = (16.0f / scale) - 7.0f;
            graphics.drawString(this.font, customText, (int) textX, (int) textY, 0x55FF55, true);
            graphics.pose().popPose();
        }
        originalCounts.clear();

        Component titleComp = Component.translatable("gui.itemcontrol.cleaner.cleaner.title");
        graphics.drawString(this.font, titleComp, 8, 6, 4210752, false);
        Component pageText = Component.translatable(
                "gui.itemcontrol.cleaner.cleaner.page",
                Component.literal(String.valueOf(historyIndex + 1)).withStyle(ChatFormatting.GREEN),
                Component.literal(String.valueOf(maxHistory)).withStyle(ChatFormatting.YELLOW)
        );
        graphics.drawString(this.font, pageText, 8 + this.font.width(titleComp) + 6, 6, 4210752, false);
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
    protected boolean containerMouseClicked(double mouseX, double mouseY, int button) {
        if (!KineticMouseButtons.isPrimary(button)) return false;
        double rx = mouseX - this.leftPos;
        double ry = mouseY - this.topPos;
        if (rx >= 170 && rx <= 176 && ry >= 18 && ry <= 126) {
            this.scrolling = true;
            return true;
        }
        return false;
    }

    private void resetAndSync() {
        this.scrollTargetRow = 0D;
        this.scrollState.snap(0D, Math.max(0, this.menu.getTotalRows() - 6));
        this.menu.scrollableContainer.clearContent();
        this.syncToServer(true);
    }

    @Override
    protected boolean containerMouseScrolled(double mouseX, double mouseY, double delta) {
        int hiddenRows = this.menu.getTotalRows() - 6;
        if (hiddenRows > 0) {
            this.scrollTargetRow = scrollState.wheel(scrollTargetRow, delta, 1D, hiddenRows);
            this.syncToServer(false);
            return true;
        }
        return false;
    }

    @Override
    protected boolean containerMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.scrolling) return false;
        int hiddenRows = Math.max(0, this.menu.getTotalRows() - 6);
        double normalized = Mth.clamp(
                (mouseY - (this.topPos + 18) - 7.5D) / (108D - 15D),
                0D,
                1D
        );
        this.scrollTargetRow = normalized * hiddenRows;
        this.scrollState.follow(scrollTargetRow, hiddenRows, true);
        this.syncToServer(false);
        return true;
    }

    @Override
    protected boolean containerMouseReleased(double mouseX, double mouseY, int button) {
        if (KineticMouseButtons.isPrimary(button)) {
            this.scrolling = false;
        }
        return false;
    }

    private void syncToServer(boolean force) {
        int hiddenRows = Math.max(0, this.menu.getTotalRows() - 6);
        int currentRow = Mth.clamp((int) Math.round(scrollTargetRow), 0, hiddenRows);
        if (!force && currentRow == lastSyncedRow && historyIndex == lastSyncedHistoryIndex) {
            return;
        }
        lastSyncedRow = currentRow;
        lastSyncedHistoryIndex = historyIndex;
        this.menu.updateState(historyIndex, currentRow);
        CleanerNetwork.sendToServer(new CleanerNetwork.SyncTrashBin(currentRow, historyIndex));
    }
}
