package dev.xyat.itemcontrol.item.client.gui;

import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Banned items in the Core item list editor: green outlines mark banned items, tags and mods; left-click toggles a
 * ban, Shift + left-click edits the item's NBT rule, protected identifiers cannot be banned.
 */
public final class BanRules {
    private BanRules() {
    }

    /** Opens the editor on the current ban list and sends the whole ban config to the server when saved. */
    public static void open() {
        KineticSelectors.openItemListEditor(KineticI18n.translatable("gui.itemcontrol.item.banitem.title"),
                new ArrayList<>(BanItemConfig.data.bannedItems), KineticSelectors.ItemListMode.ITEMS_TAGS_MODS, ADAPTER, BanRules::save);
    }

    private static void save(List<String> rules) {
        BanItemConfig.data.bannedItems = new ArrayList<>(rules);
        BanItemConfig.rebuildCache();
        ItemSearchCache.markRulesChanged();
        ItemNetwork.CHANNEL.sendToServer(new ItemNetwork.SaveBanConfigPacket(ItemNetwork.EDITOR_BAN_ITEM, BanItemConfig.GSON.toJson(BanItemConfig.data)));
    }

    static final KineticSelectors.ItemListRuleAdapter ADAPTER = new KineticSelectors.ItemListRuleAdapter() {
        @Override public boolean acceptsItem(ItemStack stack) { return true; }

        @Override public String itemRule(ItemStack stack) { return BanItemConfig.getItemIdentifier(stack); }

        @Override public boolean matches(String rule, ItemStack stack) { return new BanItemConfig.ItemRule(rule).matches(stack); }

        @Override public boolean acceptsRule(String rule) { return rule != null && !rule.isBlank() && !BanItemConfig.isProtected(rule); }

        @Override public boolean editItem(ItemStack stack, String existingRule, int mouseButton, boolean shift, Consumer<String> replace) {
            if (!shift || mouseButton != 0) return false;
            String rule = existingRule != null ? existingRule : BanItemConfig.getItemIdentifier(stack);
//? if >=1.21 {
            /*int bracket = rule.indexOf('[');
            String baseId = bracket < 0 ? rule : rule.substring(0, bracket);
            String initial = bracket < 0 ? dev.xyat.itemcontrol.item.data.ItemData.format(stack) : rule.substring(bracket);
*///?} else {
            int bracket = rule.indexOf('{');
            String baseId = bracket < 0 ? rule : rule.substring(0, bracket);
            String initial = bracket >= 0 ? rule.substring(bracket) : stack.hasTag() && stack.getTag() != null ? stack.getTag().toString() : "";
//?}
            KineticSelectors.openNbtEditor(initial, saved -> replace.accept(baseId + (saved == null ? "" : saved.trim())));
            return true;
        }
    };
}
