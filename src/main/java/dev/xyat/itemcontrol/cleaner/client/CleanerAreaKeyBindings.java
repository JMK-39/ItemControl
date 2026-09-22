package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;
import net.minecraft.network.chat.Component;

public final class CleanerAreaKeyBindings {
    private static KineticKeyBindings.Binding areaActionKey;

    private CleanerAreaKeyBindings() {
    }

    public static void register() {
        if (areaActionKey != null) {
            return;
        }
        areaActionKey = KineticKeyBindings.builder("key.itemcontrol.cleaner.area_action")
                .category("key.itemcontrol.category")
                .context(KineticKeyBindings.Context.IN_GAME)
                .keyboard(KineticKeyBindings.Key.LEFT_SHIFT)
                .register();
    }

    public static boolean isDown() {
        return areaActionKey != null && areaActionKey.isDown();
    }

    public static Component translatedKeyMessage() {
        return areaActionKey == null ? Component.empty() : areaActionKey.translatedKeyMessage();
    }
}
