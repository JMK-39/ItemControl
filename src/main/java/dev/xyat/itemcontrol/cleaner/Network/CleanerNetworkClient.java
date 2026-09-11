package dev.xyat.itemcontrol.cleaner.Network;

import dev.xyat.itemcontrol.cleaner.client.CleanerAreaClientState;
import dev.xyat.itemcontrol.cleaner.client.gui.CleanerMenu;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CleanerNetworkClient {
    public static void handleSyncCounts(CleanerNetwork.SyncTrashBinCounts packet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu.containerId == packet.containerId() && player.containerMenu instanceof CleanerMenu cleanerMenu) {
            for (int i = 0; i < packet.counts().length && i < 54; i++) {
                cleanerMenu.slots.get(i).getItem().setCount(packet.counts()[i]);
            }
        }
    }

    public static void handleSyncAreas(CleanerNetwork.SyncCleanerAreas packet) {
        CleanerAreaClientState.setAreas(packet.areas());
    }

    public static void handleNotifyToast(CleanerNetwork.NotifyToast packet) {
        GuiOverlay.toast("cleaner_area", packet.message());
    }

    public static void handleClearAreaSelection() {
        CleanerAreaClientState.clearSelection();
    }
}
