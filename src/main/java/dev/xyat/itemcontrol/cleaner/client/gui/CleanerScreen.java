package dev.xyat.itemcontrol.cleaner.client.gui;

import dev.xyat.itemcontrol.cleaner.config.CleanerConfig;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.Scroll;
import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class CleanerScreen extends AbstractContainerScreen<CleanerMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

    private double scrollTargetRow;
    private final Scroll.State scrollState = new Scroll.State();
    private int lastSyncedRow = -1;
    private int lastSyncedHistoryIndex = -1;
    private boolean scrolling = false;
    private int historyIndex = 0;
    private final int maxHistory;

    // 暂存真实数量，避免渲染逻辑冲突
    private final Map<Slot, Integer> originalCounts = new HashMap<>();

    public CleanerScreen(CleanerMenu cleanerMenu, Inventory inv, Component title) {
        super(cleanerMenu, inv, title);
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
        this.maxHistory = CleanerConfig.trashBinHistorySize;
    }

    // 格式化数字：1000进制，绿色缩小版，无+号
    private String formatCount(int count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return (count / 1000) + "k";
        if (count < 1000000000) return (count / 1000000) + "m";
        return (count / 1000000000) + "g";
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            GuiOverlay.requestItemTooltip(this.hoveredSlot.getItem(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
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

        // --- 视觉欺骗：隐藏原版白色数字 ---
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
        // --- 绘制自定义绿色缩小字体 ---
        for (Map.Entry<Slot, Integer> entry : originalCounts.entrySet()) {
            Slot slot = entry.getKey();
            int realCount = entry.getValue();

            // 还原数量以供 ArmorTooltip 显示
            slot.getItem().setCount(realCount);

            String customText = formatCount(realCount);

            graphics.pose().pushPose();
            // 提高 Z 轴到 300 确保文字在最上层
            graphics.pose().translate(slot.x, slot.y, 300.0F);

            float scale = 0.85F; // 缩小 15%
            graphics.pose().scale(scale, scale, 1.0F);

            int textWidth = this.font.width(customText);
            // textX: 减掉的 0.5f 越小，文字越靠右。0.0f 是理论极限贴边。
            // textY: 减掉的 8.0f 是字高，改为 7.0f 会让文字向下沉约 1 像素。
            float textX = (16.0f / scale) - textWidth - 0.1f;
            float textY = (16.0f / scale) - 7.0f;

            // 0x55FF55: 绿色 | true: 开启阴影
            graphics.drawString(this.font, customText, (int)textX, (int)textY, 0x55FF55, true);

            graphics.pose().popPose();
        }
        originalCounts.clear();

        // 标题
        Component titleComp = Component.translatable("gui.itemcontrol.cleaner.cleaner.title");
        graphics.drawString(this.font, titleComp, 8, 6, 4210752, false);

        // 页码
        Component pageText = Component.translatable(
                "gui.itemcontrol.cleaner.cleaner.page",
                Component.literal(String.valueOf(historyIndex + 1)).withStyle(ChatFormatting.GREEN),
                Component.literal(String.valueOf(maxHistory)).withStyle(ChatFormatting.YELLOW)
        );
        graphics.drawString(this.font, pageText, 8 + this.font.width(titleComp) + 6, 6, 4210752, false);

        renderNavButtons(graphics);
    }

    private void renderNavButtons(GuiGraphics graphics) {
        Component msgPrev = Component.translatable("gui.itemcontrol.cleaner.cleaner.prev");
        Component msgNext = Component.translatable("gui.itemcontrol.cleaner.cleaner.next");

        int nextX = 176 - 8 - this.font.width(msgNext);
        int prevX = nextX - 12 - this.font.width(msgPrev);

        // 简化的渲染逻辑，不使用 substring 裁切颜色代码
        if (historyIndex > 0) {
            graphics.drawString(this.font, msgPrev, prevX, 6, 0xFFFFFF, true);
        } else {
            graphics.drawString(this.font, "◀", prevX, 6, 0xA0A0A0, false);
        }

        if (historyIndex < maxHistory - 1) {
            graphics.drawString(this.font, msgNext, nextX, 6, 0xFFFFFF, true);
        } else {
            graphics.drawString(this.font, "➤", nextX, 6, 0xA0A0A0, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double rx = mouseX - this.leftPos;
            double ry = mouseY - this.topPos;

            if (ry >= 4 && ry <= 16) {
                int nextXStart = 176 - 8 - this.font.width(Component.translatable("gui.itemcontrol.cleaner.cleaner.next"));
                int prevXStart = nextXStart - 12 - this.font.width(Component.translatable("gui.itemcontrol.cleaner.cleaner.prev"));

                if (rx >= prevXStart && rx <= prevXStart + this.font.width(Component.translatable("gui.itemcontrol.cleaner.cleaner.prev"))) {
                    if (historyIndex > 0) { historyIndex--; resetAndSync(); return true; }
                }
                if (rx >= nextXStart && rx <= nextXStart + this.font.width(Component.translatable("gui.itemcontrol.cleaner.cleaner.next"))) {
                    if (historyIndex < maxHistory - 1) { historyIndex++; resetAndSync(); return true; }
                }
            }
            if (rx >= 170 && rx <= 176 && ry >= 18 && ry <= 126) { this.scrolling = true; return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void resetAndSync() {
        this.scrollTargetRow = 0D;
        this.scrollState.snap(0D, Math.max(0, this.menu.getTotalRows() - 6));
        this.menu.scrollableContainer.clearContent();
        this.syncToServer(true);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int hiddenRows = this.menu.getTotalRows() - 6;
        if (hiddenRows > 0) {
            this.scrollTargetRow = scrollState.wheel(scrollTargetRow, delta, 1D, hiddenRows);
            this.syncToServer(false);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrolling) {
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
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
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
