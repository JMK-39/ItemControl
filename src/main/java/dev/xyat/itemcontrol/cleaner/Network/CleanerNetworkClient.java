package dev.xyat.itemcontrol.cleaner.Network;

import dev.xyat.itemcontrol.cleaner.client.CleanerAreaClientState;
import dev.xyat.itemcontrol.cleaner.client.gui.CleanerMenu;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.player.LocalPlayer;

public final class CleanerNetworkClient {
    private CleanerNetworkClient() {
    }

    public static void handleSyncCounts(CleanerNetwork.SyncTrashBinCounts packet) {
        LocalPlayer player = KineticClientRuntime.localPlayer();
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
        KineticOverlays.toast("cleaner_area", packet.message());
    }

    public static void handleClearAreaSelection() {
        CleanerAreaClientState.clearSelection();
    }
}
