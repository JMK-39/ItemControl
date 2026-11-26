//? if <1.21 {
package dev.xyat.itemcontrolvalidation;

import dev.xyat.itemcontrol.item.InitItems;
import dev.xyat.itemcontrol.item.config.BanItemConfig;
import dev.xyat.itemcontrol.item.util.ItemBanControl;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.slf4j.LoggerFactory;

/** Opt-in actual mixin checks. Uses detached containers and never writes user config or player inventory. */
public final class ItemBanRuntimeChecks {
    private static boolean done;
    private static BanItemConfig.Data before;
    private static boolean enabled;
    private static byte[] disk;
    private static java.util.concurrent.CompletableFuture<Void> clientCheck;
    private static int waitTicks;

    public static void install() {
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END, ItemBanRuntimeChecks::run);
    }

    private static void run(MinecraftServer server) {
        if (done) return;
        if (clientCheck != null) {
            if (!clientCheck.isDone() && ++waitTicks < 1200) return;
            if (!clientCheck.isDone()) clientCheck.completeExceptionally(new AssertionError("client display check did not run within 1200 server ticks"));
            Throwable error = null;
            try { clientCheck.join(); } catch (Throwable failure) { error = failure; }
            finish(server, error);
            return;
        }
        before = BanItemConfig.snapshotData();
        enabled = ItemBanControl.isReplacementEnabled();
        Throwable failure = null;
        try {
            disk = java.nio.file.Files.exists(BanItemConfig.PATH) ? java.nio.file.Files.readAllBytes(BanItemConfig.PATH) : null;
            BanItemConfig.restoreDataSnapshot(new BanItemConfig.Data());
            ItemBanControl.setReplacementEnabled(true);
            var legacyItem = InitItems.VOID_PLACEHOLDER.get();
            require(new ItemStack(legacyItem).isEmpty(), "legacy placeholder is empty at server construction, even with no ban rules");

            CompoundTag saved = new CompoundTag();
            saved.putString("id", BanItemConfig.VOID_ID);
            saved.putByte("Count", (byte) 1);
            require(ItemStack.of(saved).isEmpty(), "legacy saved NBT becomes empty at first load");

            ItemStack[] old = new ItemStack[1];
            ItemBanControl.withSkip(() -> { old[0] = new ItemStack(legacyItem); return null; });
            require(!old[0].isEmpty(), "skip scope can reproduce old persisted stacks");
            require(BanItemConfig.isVoided(old[0]), "world-entry cleanup recognizes a preexisting placeholder with no bans");
            var contents = new SimpleContainer(27);
            contents.setItem(0, old[0]);
            contents.setItem(1, new ItemStack(Items.BARRIER));
            contents.setItem(2, new ItemStack(Items.STICK, 3));
            ServerPlayer player = new net.minecraftforge.common.util.FakePlayer(server.overworld(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ItemBanCheck"));
            var menu = ChestMenu.threeRows(1, player.getInventory(), contents);
            var init = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(ServerPlayer.class, "m_143399_", AbstractContainerMenu.class);
            init.invoke(player, menu);
            require(contents.getItem(0).isEmpty(), "opening an old chest clears the placeholder without a Slot#set operation");
            require(contents.getItem(1).getItem() == Items.BARRIER && contents.getItem(2).getCount() == 3,
                    "opening a chest preserves intentional barriers and allowed stacks");
            menu.removed(player);

            var rules = new BanItemConfig.Data();
            rules.bannedItems.add("minecraft:diamond");
            BanItemConfig.restoreDataSnapshot(rules);
            var table = LootTable.lootTable()
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(legacyItem)))
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(Items.DIAMOND)))
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(Items.BARRIER)))
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(Items.STICK)))
                    .build();
            var params = new LootParams.Builder(server.overworld()).create(LootContextParamSets.EMPTY);
            // Vanilla can retain empty stacks in the intermediate list; only nonempty rewards occupy chest slots.
            var loot = table.getRandomItems(params).stream().filter(stack -> !stack.isEmpty()).toList();
            require(loot.size() == 2 && loot.stream().anyMatch(stack -> stack.getItem() == Items.BARRIER)
                    && loot.stream().anyMatch(stack -> stack.getItem() == Items.STICK), "generated chest loot contains only allowed rewards, including intentional barrier: " + loot);
            var generatedChest = new SimpleContainer(27);
            table.fill(generatedChest, params, 42L);
            int occupied = 0;
            for (int slot = 0; slot < generatedChest.getContainerSize(); slot++) {
                var stack = generatedChest.getItem(slot);
                if (stack.isEmpty()) continue;
                occupied++;
                require(stack.getItem() == Items.BARRIER || stack.getItem() == Items.STICK, "first generated chest has no banned or legacy reward");
            }
            require(occupied == 2, "first generated chest retains both allowed rewards");
            ItemBanControl.withSkip(() -> { require(!new ItemStack(Items.DIAMOND).isEmpty(), "editor skip scope preserves banned item display"); return null; });
            if (dev.xyat.kineticcore.api.runtime.KineticPlatform.isClient()) {
                clientCheck = new java.util.concurrent.CompletableFuture<>();
                dev.xyat.kineticcore.api.runtime.KineticClientRuntime.execute(() -> {
                    try {
                        require(!new ItemStack(Items.DIAMOND).isEmpty(), "client ban editor keeps banned items displayable");
                        require(!new ItemStack(legacyItem).isEmpty(), "client legacy icon display remains available");
                        clientCheck.complete(null);
                    } catch (Throwable error) { clientCheck.completeExceptionally(error); }
                });
                return;
            }
        } catch (Throwable error) {
            failure = error;
        }
        finish(server, failure);
    }

    private static void finish(MinecraftServer server, Throwable failure) {
        done = true;
        try {
            try { BanItemConfig.restoreDataSnapshot(before); }
            finally { ItemBanControl.setReplacementEnabled(enabled); }
            require(BanItemConfig.GSON.toJson(before).equals(BanItemConfig.GSON.toJson(BanItemConfig.snapshotData())), "restore all original rules");
            byte[] after = java.nio.file.Files.exists(BanItemConfig.PATH) ? java.nio.file.Files.readAllBytes(BanItemConfig.PATH) : null;
            require(java.util.Arrays.equals(disk, after), "disk config stays byte-for-byte unchanged");
        } catch (Throwable restoreError) {
            if (failure == null) failure = restoreError;
            else failure.addSuppressed(restoreError);
        }
        var log = LoggerFactory.getLogger(ItemBanRuntimeChecks.class);
        if (failure == null) log.info("ITEM_BAN_RUNTIME_PASS legacy construction/NBT, old chest, loot, allowed barrier, skip scope; clientDisplay={} config restored diskExact=true",
                clientCheck == null ? "not-applicable-dedicated" : "PASS");
        else log.error("ITEM_BAN_RUNTIME_FAIL after config restoration", failure);
        if (Boolean.getBoolean("itemcontrol.banValidation.stopAfter")) {
            if (dev.xyat.kineticcore.api.runtime.KineticPlatform.isDedicatedServer()) server.halt(false);
            else dev.xyat.kineticcore.api.runtime.KineticClientRuntime.execute(dev.xyat.kineticcore.api.runtime.KineticClientRuntime::stopClient);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
//?}
