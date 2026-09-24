package dev.xyat.itemcontrol.item;

import com.mojang.logging.LogUtils;
import dev.xyat.itemcontrol.item.command.ItemCommandExtension;
import dev.xyat.itemcontrol.item.client.ItemClientProxy;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.config.ItemProtectionConfig;
import dev.xyat.itemcontrol.item.config.ItemProtectionConfigGui;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfig;
import dev.xyat.itemcontrol.item.config.ItemPropertyConfigGui;
import dev.xyat.itemcontrol.item.event.ItemProtectionHandler;
import dev.xyat.itemcontrol.item.event.VoidItemEventHandler;
import dev.xyat.itemcontrol.item.event.WorldLoadEventHandler;
import dev.xyat.itemcontrol.item.network.ItemNetwork;
import dev.xyat.itemcontrol.item.property.ItemPropertyEventHandler;
import dev.xyat.itemcontrol.item.property.ItemPropertyOverrides;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.config.server.KTServerConfigSpec;
import org.slf4j.Logger;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ItemModule {
    public static final String MODID = "itemcontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ItemModule() {
        InitItems.register();
        BanItemConfig.load();
        ItemProtectionConfig.load();
        ItemPropertyConfig.load();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ItemPropertyOverrides::onCommonSetup);
        KTServerConfigApi.registerActionPage(ItemPropertyConfigGui.PAGE_ID);
        KTServerConfigApi.register(KTServerConfigSpec.builder("itemcontrol:item_protection")
                .booleanValue("enable_item_protection", () -> ItemProtectionConfig.enableItemProtection, value -> ItemProtectionConfig.enableItemProtection = value)
                .booleanValue("enable_void_salvage", () -> ItemProtectionConfig.enableVoidSalvage, value -> ItemProtectionConfig.enableVoidSalvage = value)
                .stringList("indestructible_items", () -> new java.util.ArrayList<>(ItemProtectionConfig.indestructibleItemsRaw), ItemProtectionConfig::setProtectionRules)
                .booleanValue("global_damage_immunity_enabled", () -> ItemProtectionConfig.enableGlobalItemDamageImmunity, value -> ItemProtectionConfig.enableGlobalItemDamageImmunity = value)
                .stringList("global_damage_immunity", () -> new java.util.ArrayList<>(ItemProtectionConfig.globalItemDamageImmunityRaw), ItemProtectionConfig::setDamageSources)
                .booleanValue("global_direct_entity_immunity_enabled", () -> ItemProtectionConfig.enableGlobalDirectEntityImmunity, value -> ItemProtectionConfig.enableGlobalDirectEntityImmunity = value)
                .stringList("global_direct_entity_immunity", () -> new java.util.ArrayList<>(ItemProtectionConfig.globalDirectEntityImmunityRaw), ItemProtectionConfig::setDirectEntitySources)
                .onSave(() -> {
                    ItemProtectionConfig.save();
                    BanItemConfig.save();
                })
                .afterSave(server -> ItemNetwork.syncServerConfigToAllPlayers())
                .build());
        ItemNetwork.register();
        ItemProtectionHandler.register();
        ItemPropertyEventHandler.register();
        VoidItemEventHandler.register();
        WorldLoadEventHandler.register();
        ItemCommandExtension.install();

        KineticPlatform.runOnClient(() -> () -> {
            ItemClientProxy.install();
            ItemProtectionConfigGui.load();
            ItemPropertyConfigGui.load();
        });
    }
}
