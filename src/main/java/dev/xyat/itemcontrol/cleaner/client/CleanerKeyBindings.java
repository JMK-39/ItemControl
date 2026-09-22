package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;

public final class CleanerKeyBindings {
    private static KineticKeyBindings.Binding cleanerKey;

    private CleanerKeyBindings() {
    }

    public static void register() {
        if (cleanerKey != null) {
            return;
        }
        cleanerKey = KineticKeyBindings.builder("key.itemcontrol.cleaner")
                .category("key.itemcontrol.category")
                .context(KineticKeyBindings.Context.IN_GAME)
                .keyboard(KineticKeyBindings.Key.DELETE)
                .onPressed(CleanerKeyHandler::handleCleanerPress)
                .register();
    }
}
