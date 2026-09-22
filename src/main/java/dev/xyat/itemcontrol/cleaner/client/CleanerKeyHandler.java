package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.itemcontrol.cleaner.Network.CleanerNetwork;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;

public final class CleanerKeyHandler {
    private static int cooldown = 0;
    private static boolean installed;

    private CleanerKeyHandler() {
    }

    public static void install() {
        if (installed) return;
        installed = true;
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, CleanerKeyHandler::onClientTick);
    }

    private static void onClientTick() {
        if (cooldown > 0) {
            cooldown--;
        }
    }

    public static boolean handleCleanerPress() {
        if (cooldown != 0 || KineticClientRuntime.localPlayer() == null) {
            return false;
        }
        CleanerNetwork.sendToServer(new CleanerNetwork.CleanerRequest());
        cooldown = 20;
        return true;
    }
}
