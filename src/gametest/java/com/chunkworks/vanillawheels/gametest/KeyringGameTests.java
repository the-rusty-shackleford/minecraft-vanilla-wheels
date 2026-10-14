/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.vanillawheels.gametest;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.vanillawheels.KeyFobs;
import com.chunkworks.vanillawheels.Keyring;
import com.chunkworks.vanillawheels.KeyringItem;
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.RecoveryData;
import com.chunkworks.vanillawheels.VanillaWheelsMod;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.domain.Whereabouts;
import com.chunkworks.vanillawheels.keyring.KeyringMenu;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The keyring (D-0041) and the keys on it (D-0026), through the real interactions: a blank used
 * at a vehicle, the drop key, menus, a death, the ring's own screen. A player made here has a
 * connection that goes nowhere, so menus and inventories take the real path.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KeyringGameTests {
    private static final ResourceLocation CAR = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_car");

    public KeyringGameTests() {}

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static Vehicle car(GameTestHelper helper, double x, double z) {
        Vehicle vehicle = Vehicle.create(helper.getLevel(), CAR, helper.absoluteVec(new Vec3(x, 2, z)), 0);
        helper.getLevel().addFreshEntity(vehicle);
        vehicle.setFuel(vehicle.tank().capacity());
        return vehicle;
    }

    private static ServerPlayer player(GameTestHelper helper, GameProfile profile) {
        var cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(12.5, 2, 12.5));
        player.teleportTo(at.x, at.y, at.z);
        return player;
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return player(helper, new GameProfile(UUID.randomUUID(), name));
    }

    /**
     * effects: {@code p} uses a blank key on {@code v}, the real interaction, from the last hotbar slot: a
     * ring made by the first key lands in the first empty slot, which is then not the hand the next blank
     * goes into. Returns the key as it sits on their ring, or EMPTY.
     */
    private static ItemStack pair(ServerPlayer p, Vehicle v) {
        p.getInventory().selected = 8;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.KEY_FOB.get()));
        v.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        List<ItemStack> keys = ringKeys(p);
        return keys.isEmpty() ? ItemStack.EMPTY : keys.getLast().copy();
    }

    private static ItemStack ring(ServerPlayer p) {
        Carried.Place place = Keyring.ringOf(p);
        return place == null ? ItemStack.EMPTY : place.stack();
    }

    private static List<ItemStack> ringKeys(ServerPlayer p) {
        return Keyring.keys(ring(p));
    }

    private static UUID token(ItemStack key) {
        return key.get(ModContent.KEY_TOKEN.get());
    }

    private static int ringsAndKeysOnTheGround(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8),
                e -> e.getItem().is(ModContent.KEY_FOB.get()) || Keyring.isRing(e.getItem())).size();
    }

    private static String nameOf(ItemStack key) {
        Component name = key.get(DataComponents.ITEM_NAME);
        return name == null ? "" : name.getString();
    }

    private static Predicate<ItemStack> ringOf(ServerPlayer p) {
        return s -> Keyring.isRing(s) && p.getUUID().equals(Keyring.ownerOf(s));
    }

    private static void done(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer p : players) p.connection.disconnect(Component.literal("test complete"));
        helper.succeed();
    }

    // --- pairing, the mark, and recall from the ring ----------------------------

    @GameTest(template = "arena", timeoutTicks = 140)
    public void pairingMakesTheRingPutsEachMarkedKeyOnItAndTheRingRecallsEachVehicle(GameTestHelper helper) {
        floor(helper);
        Vehicle a = car(helper, 4.5, 4.5);
        a.setPaint(DyeColor.RED);
        Vehicle b = car(helper, 10.5, 4.5);
        b.setCustomName(Component.literal("Hauler"));
        Vehicle c = car(helper, 4.5, 10.5);
        ServerPlayer p = player(helper, "collector");
        helper.assertTrue(Keyring.ringOf(p) == null, "no ring before the first key");
        ItemStack keyA = pair(p, a);
        helper.assertTrue(p.getMainHandItem().isEmpty(), "the blank was used up");
        helper.assertTrue(Keyring.ringOf(p) != null, "the first key made the ring");
        helper.assertValueEqual(Keyring.ownerOf(ring(p)), p.getUUID(), "the ring is the player's");
        ItemStack keyB = pair(p, b);
        helper.assertValueEqual(ringKeys(p).size(), 2, "both keys on the one ring");
        RecoveryData data = RecoveryData.get(p.server);
        helper.assertValueEqual(data.ownerOf(a.binding()), p.getUUID(), "the first pairing stands");
        helper.assertValueEqual(data.ownerOf(b.binding()), p.getUUID(), "beside the second");
        helper.assertTrue(data.isCurrent(token(keyA)) && data.isCurrent(token(keyB)), "both keys work");
        helper.assertValueEqual(nameOf(keyB), "Key to Hauler", "a key is named for its vehicle");
        helper.assertValueEqual(keyA.get(ModContent.KEY_COLOUR.get()), Vehicle.colourOf(DyeColor.RED, a.profile()) & 0xFFFFFF, "and banded in its paint");
        helper.assertTrue(KeyFobs.onPerson(p, token(keyA)) && KeyFobs.onPerson(p, token(keyB)), "a key on the ring is on the person");

        // The ring itself at a vehicle: its own key refreshes the mark; a vehicle it has no key to is not paired by it.
        Carried.Place place = Keyring.ringOf(p);
        p.getInventory().setItem(place.cell(), ItemStack.EMPTY);
        p.setItemInHand(InteractionHand.MAIN_HAND, place.stack().copy());
        a.setPaint(DyeColor.BLUE);
        a.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        ItemStack refreshed = Keyring.keyOn(ring(p), token(keyA));
        helper.assertTrue(refreshed != null, "A's key is still on the ring");
        helper.assertValueEqual(refreshed.get(ModContent.KEY_COLOUR.get()), Vehicle.colourOf(DyeColor.BLUE, a.profile()) & 0xFFFFFF, "its own vehicle refreshes the band");
        helper.assertTrue(!data.bind(p, c, p.getMainHandItem()), "the ring at a vehicle it has no key to is refused");
        helper.assertValueEqual(data.notice(p).getString(), Component.translatable("vanillawheels.key.ring_no_key").getString(), "and says so");
        c.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(c.binding() == null, "the ring pairs nothing: that is a blank's job");
        helper.assertValueEqual(ringKeys(p).size(), 2, "and the ring is as it was");

        ServerPlayer stranger = player(helper, "stranger");
        pair(stranger, a);
        helper.assertValueEqual(data.ownerOf(a.binding()), p.getUUID(), "another player's blank does not take it");
        helper.assertTrue(!stranger.getMainHandItem().has(ModContent.KEY_TOKEN.get()) && Keyring.ringOf(stranger) == null, "and stays blank, no ring made");

        // Recall B from the ring: the screen open, Recall, then Confirm once the quote is up.
        KeyringItem.open(p);
        helper.assertTrue(p.containerMenu instanceof KeyringMenu, "the ring's screen is open");
        helper.assertTrue(data.beginFromRing(p, b.binding()), "Recall started");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(data.progress(p) != null && data.progress(p).quote() != null, "quoted"))
                .thenExecute(() -> data.confirm(p));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(b.isRemoved(), "B's key recalls B");
            helper.assertTrue(!a.isRemoved(), "and leaves A where it is");
            helper.assertTrue(p.getInventory().items.stream().anyMatch(s -> s.is(ModContent.VEHICLE_ITEM.get())), "B is packed in the inventory");
            done(helper, p, stranger);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aBlankForAPairedVehicleRetiresTheOldKeyOffTheRing(GameTestHelper helper) {
        floor(helper);
        Vehicle a = car(helper, 4.5, 4.5);
        ServerPlayer p = player(helper, "rekeyer");
        ItemStack first = pair(p, a).copy();
        ItemStack second = pair(p, a).copy();
        RecoveryData data = RecoveryData.get(p.server);
        helper.assertTrue(!data.isCurrent(token(first)) && data.isCurrent(token(second)), "the new key works; the old does not");
        helper.assertValueEqual(ringKeys(p).size(), 1, "the retired key left the ring");
        helper.assertValueEqual(token(ringKeys(p).getFirst()), token(second), "the new one is on it");
        // A retired key is an ordinary item: in hand, it can be dropped.
        p.getInventory().setItem(5, first);
        p.getInventory().selected = 5;
        helper.assertTrue(p.drop(true), "the drop key drops it");
        helper.assertValueEqual(ringsAndKeysOnTheGround(helper), 1, "and it lies on the ground");
        done(helper, p);
    }

    // --- never lost -----------------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 80)
    public void theRingCannotBeDroppedAndComesBackFromAChestAStrangerAFrameAndABag(GameTestHelper helper) {
        floor(helper);
        Vehicle a = car(helper, 4.5, 4.5);
        ServerPlayer p = player(helper, "owner");
        UUID t = token(pair(p, a));
        int slot = Keyring.ringOf(p).cell();
        p.getInventory().selected = slot;
        helper.assertTrue(!p.drop(true), "the drop key does nothing");
        helper.assertTrue(Keyring.isRing(p.getInventory().getItem(slot)), "the ring is still in its slot");
        helper.assertValueEqual(ringsAndKeysOnTheGround(helper), 0, "nothing lies on the ground");
        ItemStack thrown = p.getInventory().removeItem(slot, 1);
        p.drop(thrown, true);
        helper.runAtTickTime(2, () -> {
            helper.assertValueEqual(ringsAndKeysOnTheGround(helper), 0, "thrown out of a screen, nothing lies on the ground");
            helper.assertTrue(KeyFobs.onPerson(p, t), "it is back on its owner");

            BlockPos chestAt = new BlockPos(2, 2, 12);
            helper.setBlock(chestAt, Blocks.CHEST);
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestAt);
            p.openMenu(chest);
            ItemStack ring = ring(p).copy();
            p.getInventory().removeItem(ring(p));
            p.containerMenu.getSlot(0).set(ring);
            p.containerMenu.broadcastChanges();
            helper.assertTrue(chest.getItem(0).isEmpty(), "a chest does not keep it, even open");
            helper.assertTrue(KeyFobs.onPerson(p, t), "it is back at once");
            p.closeContainer();

            ServerPlayer stranger = player(helper, "stranger");
            stranger.openMenu(chest);
            ring = ring(p).copy();
            p.getInventory().removeItem(ring(p));
            // The stranger's own inventory, as the chest's menu shows it: its first main slot after the chest's 27.
            stranger.containerMenu.getSlot(27).set(ring);
            stranger.containerMenu.broadcastChanges();
            helper.assertTrue(!Carried.has(stranger, ringOf(p)), "a stranger does not keep it");
            helper.assertTrue(KeyFobs.onPerson(p, t), "it went home to its owner");
            stranger.closeContainer();

            ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(new BlockPos(6, 2, 12)), Direction.SOUTH);
            helper.setBlock(new BlockPos(6, 2, 11), Blocks.STONE);
            helper.getLevel().addFreshEntity(frame);
            int at = Keyring.ringOf(p).cell();
            p.getInventory().selected = at < 9 ? at : 0;
            if (at >= 9) {
                ItemStack moved = p.getInventory().removeItem(at, 1);
                p.getInventory().setItem(0, moved);
            }
            p.interactOn(frame, InteractionHand.MAIN_HAND);
            helper.assertTrue(frame.getItem().isEmpty(), "a frame is refused it");
            helper.assertTrue(KeyFobs.onPerson(p, t), "and it stays on its owner");

            // A bag refuses the ring (it fits inside no container item), so with the inventory full but for a bag
            // a ring coming home has nowhere to go: it waits, and comes within a second of room being made.
            ItemStack theRing = ring(p).copy();
            p.getInventory().removeItem(ring(p));
            for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(net.minecraft.world.item.Items.DIRT, 64));
            p.getInventory().setItem(20, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("backpacksplus:basic_backpack"))));
            KeyFobs.sendHome(p.server, theRing);
            helper.assertTrue(!KeyFobs.onPerson(p, t), "the bag did not take it");
            helper.assertValueEqual(ringsAndKeysOnTheGround(helper), 0, "and it lies nowhere");
            helper.assertTrue(KeyFobs.get(p.server).waitingFor(t), "no room on its owner: it waits");
            p.getInventory().setItem(3, ItemStack.EMPTY);
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(KeyFobs.onPerson(p, t), "room made, the ring came to its owner within a second");
            helper.assertTrue(!KeyFobs.get(p.server).waitingFor(t), "and waits no longer");
            helper.assertValueEqual(Carried.count(p, ringOf(p)), 1, "one ring, not two");
            done(helper, p);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void theRingComesBackAtTheRespawnWithEveryKeyAndALooseKeyJoinsItAtLogin(GameTestHelper helper) {
        floor(helper);
        Vehicle a = car(helper, 4.5, 4.5);
        Vehicle b = car(helper, 10.5, 4.5);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "mortal");
        ServerPlayer p = player(helper, profile);
        UUID ta = token(pair(p, a));
        UUID tb = token(pair(p, b));
        p.kill();
        helper.assertTrue(p.isDeadOrDying(), "the owner died with the ring");
        helper.assertValueEqual(ringsAndKeysOnTheGround(helper), 0, "it does not lie where they fell");
        KeyFobs fobs = KeyFobs.get(p.server);
        helper.assertTrue(fobs.waitingFor(ta) && fobs.waitingFor(tb), "both keys wait, on the ring, for the respawn");
        ServerPlayer back = p.server.getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
        helper.assertTrue(KeyFobs.onPerson(back, ta) && KeyFobs.onPerson(back, tb), "respawned, the owner has both");
        helper.assertValueEqual(Carried.count(back, ringOf(back)), 1, "on one ring");

        // A loose key from before the ring: on the person at login, it joins the ring.
        ItemStack loose = Keyring.keyOn(ring(back), ta).copy();
        Keyring.drop(back, ta);
        helper.assertTrue(!KeyFobs.onPerson(back, ta), "A's key is off the ring");
        back.getInventory().setItem(7, loose);
        helper.assertTrue(KeyFobs.onPerson(back, ta) && Keyring.keyOn(ring(back), ta) == null, "loose in the inventory");
        back.server.getPlayerList().remove(back);
        helper.assertTrue(back.server.getPlayerList().getPlayer(profile.getId()) == null, "the owner is away");
        ServerPlayer again = player(helper, profile);
        helper.assertTrue(Keyring.keyOn(ring(again), ta) != null, "logged in, the loose key is on the ring");
        helper.assertTrue(!Carried.has(again, s -> s.is(ModContent.KEY_FOB.get())), "and no key lies loose");
        done(helper, again);
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aBlankInTheAirIsRefusedWhileEveryKeyIsOnTheRingAndReIssuesOneAfterAClear(GameTestHelper helper) {
        floor(helper);
        Vehicle a = car(helper, 4.5, 4.5);
        a.setCustomName(Component.literal("Rover"));
        Vehicle b = car(helper, 10.5, 4.5);
        ServerPlayer p = player(helper, "forgetful");
        UUID ta = token(pair(p, a));
        UUID tb = token(pair(p, b));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.KEY_FOB.get()));
        helper.assertTrue(!ModContent.KEY_FOB.get().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(), "every key on the ring: the blank is refused");
        helper.assertTrue(!p.getMainHandItem().isEmpty(), "and stays in hand");
        Keyring.drop(p, ta);   // A's key gone, as a /clear leaves it
        helper.assertTrue(ModContent.KEY_FOB.get().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(), "the blank re-issued the key");
        helper.assertTrue(p.getMainHandItem().isEmpty(), "and was used up");
        RecoveryData data = RecoveryData.get(p.server);
        helper.assertTrue(!data.isCurrent(ta), "the lost one no longer works");
        helper.assertTrue(data.isCurrent(tb), "B's key is untouched");
        helper.assertValueEqual(ringKeys(p).size(), 2, "two keys on the ring again");
        ItemStack replacement = ringKeys(p).getLast();
        helper.assertTrue(data.isCurrent(token(replacement)), "the new key works");
        helper.assertValueEqual(nameOf(replacement), "Key to Rover", "the replacement wears A's mark");
        done(helper, p);
    }

    // --- the ring's screen -----------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 120)
    public void theRowsSayWhereEachVehicleIsAndARecallWaitsForConfirmAndCancelChargesNothing(GameTestHelper helper) {
        floor(helper);
        Vehicle near = car(helper, 7.5, 7.5);
        near.setCustomName(Component.literal("Near"));
        Vehicle wreck = car(helper, 4.5, 4.5);
        wreck.setCustomName(Component.literal("Wreck"));
        ServerPlayer p = player(helper, "reader");
        pair(p, near);
        pair(p, wreck);
        wreck.kill();   // packed on the ground
        RecoveryData data = RecoveryData.get(p.server);
        List<RecoveryData.Row> rows = data.rows(p);
        helper.assertValueEqual(rows.size(), 2, "a row a pairing, in pairing order");
        helper.assertValueEqual(rows.get(0).mark().name().getString(), "Near", "named");
        helper.assertValueEqual(rows.get(0).whereabouts().kind(), Whereabouts.Kind.DEPLOYED, "deployed");
        helper.assertTrue(rows.get(0).whereabouts().sameDimension() && rows.get(0).whereabouts().about() == 7, "about seven blocks away: " + rows.get(0).whereabouts());
        helper.assertTrue(rows.get(0).onRing(), "its key on the ring");
        helper.assertValueEqual(rows.get(1).whereabouts().kind(), Whereabouts.Kind.ON_THE_GROUND, "the wreck lies on the ground");
        helper.assertTrue(rows.get(0).whereabouts().share() == 0.01, "a recall from here costs the floor: " + rows.get(0).whereabouts().share());

        KeyringItem.open(p);
        helper.assertTrue(p.containerMenu instanceof KeyringMenu, "the ring's screen is open");
        p.containerMenu.broadcastChanges();
        KeyringMenu menu = (KeyringMenu) p.containerMenu;
        helper.assertValueEqual(menu.shown().rows().size(), 2, "the screen was sent both rows");
        helper.assertTrue(menu.clickMenuButton(p, KeyringMenu.RECALL + 0), "Recall on the first row");
        int fuel = near.tank().ticks();
        helper.runAtTickTime(30, () -> {
            RecoveryData.Progress progress = data.progress(p);
            helper.assertTrue(progress != null && progress.quote() != null, "quoted, and waiting");
            helper.assertTrue(!near.isRemoved() && near.tank().ticks() == fuel, "nothing moves and nothing is charged before Confirm");
            p.containerMenu.broadcastChanges();
            helper.assertTrue(menu.shown().request().quoted(), "the screen shows the quote");
            helper.assertTrue(menu.clickMenuButton(p, KeyringMenu.CANCEL), "Cancel");
            helper.assertTrue(data.progress(p) == null, "the request is gone");
            helper.assertValueEqual(near.tank().ticks(), fuel, "and nothing was charged");
            helper.assertTrue(menu.clickMenuButton(p, KeyringMenu.RECALL + 0), "Recall again");
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(menu.clickMenuButton(p, KeyringMenu.CONFIRM), "Confirm");
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(near.isRemoved(), "confirmed, the vehicle is recalled");
            helper.assertTrue(p.getInventory().items.stream().anyMatch(s -> s.is(ModContent.VEHICLE_ITEM.get())), "into the inventory");
            helper.assertTrue(fuel - RecoveryData.get(p.server).rows(p).size() >= 0, "rows still read");
            helper.assertValueEqual(data.rows(p).get(0).whereabouts().kind(), Whereabouts.Kind.COLLECTED, "and the row says so");
            done(helper, p);
        });
    }
}
