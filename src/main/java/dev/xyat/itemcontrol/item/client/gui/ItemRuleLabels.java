package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

final class ItemRuleLabels {
    private ItemRuleLabels() {}
    static Component id(String id, String translation) {
        var label=Component.literal(id).withStyle(ChatFormatting.WHITE);
        if (!KineticClientRuntime.selectedLanguage().startsWith("en_") && KineticI18n.hasTranslation(translation)) label.append(" — ").append(KineticI18n.translatable(translation).withStyle(ChatFormatting.GOLD));
        return label;
    }
    static Component text(String key,Object... values) { return KineticI18n.translatable("gui.itemcontrol.item_property."+key,values); }
}
