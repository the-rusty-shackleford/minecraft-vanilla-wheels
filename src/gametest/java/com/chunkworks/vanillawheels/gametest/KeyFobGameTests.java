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
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.RecoveryData;
import com.chunkworks.vanillawheels.VanillaWheelsMod;
import com.chunkworks.vanillawheels.Vehicle;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * One key per vehicle, marked, never lost (D-0026), on a real server with real server players.
 * Partitions. Pairing: two vehicles, two keys, both live; a key at another vehicle (refused); the
 * vehicle's own key (refreshes its mark after a repaint); another owner's vehicle (refused); a
 * blank for a vehicle already paired (the old key retires and becomes an ordinary item). Recall:
 * each key brings its own vehicle. Going home: tossed; put in a chest; inside a bag dropped on the
 * ground; in a stranger's inventory; handed to an item frame (refused); at a death (back at the
 * respawn); to an owner offline (at the login) or with no room (once room is made). Replacement:
 * a key gone (replaced, marked); none gone (refused).
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KeyFobGameTests {
    private static final ResourceLocation CAR = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_car");

    public KeyFobGameTests() {}

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

    /** effects: {@code p} uses a blank key on {@code v}, the real interaction; returns the key in hand after */
    private static ItemStack pairWithBlank(ServerPlayer p, Vehicle v) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.KEY_FOB.get()));
        v.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        return p.getMainHandItem();
    }

    private static UUID token(ItemStack key) {
        return key.get(ModContent.KEY_TOKEN.get());
    }

    private static Predicate<ItemStack> keyOf(UUID token) {
        return s -> s.is(ModContent.KEY_FOB.get()) && token.equals(s.get(ModContent.KEY_TOKEN.get()));
    }

    private static int keysOnTheGround(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(ModContent.KEY_FOB.get())).size();
    }

    private static String nameOf(ItemStack key) {
        Component name = key.get(DataComponents.ITEM_NAME);
        return name == null ? "" : name.getString();
    }

    private static void done(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer p : players) p.connection.disconnect(Component.literal("test complete"));
        helper.succeed();
    }

    // --- pairing and the mark --------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 140)
    public void eachVehicleHasItsOwnMarkedKeyAndEachKeyRecallsItsOwn(GameTestHelper helper) {
        floor(helper);
        Vehicle a = car(helper, 4.5, 4.5);
        a.setPaint(DyeColor.RED);
        Vehicle b = car(helper, 10.5, 4.5);
        b.setCustomName(Component.literal("Hauler"));
        ServerPlayer p = player(helper, "collector");
        ItemStack keyA = pairWithBlank(p, a).copy();
        ItemStack keyB = pairWithBlank(p, b).copy();
        RecoveryData data = RecoveryData.get(p.server);
        helper.assertValueEqual(data.ownerOf(a.binding()), p.getUUID(), "the first pairing stands");
        helper.assertValueEqual(data.ownerOf(b.binding()), p.getUUID(), "beside the second");
        helper.assertTrue(data.isCurrent(token(keyA)) && data.isCurrent(token(keyB)), "both keys work");
        helper.assertValueEqual(nameOf(keyB), "Key to Hauler", "a key is named for its vehicle");
        helper.assertValueEqual(keyA.get(ModContent.KEY_COLOUR.get()), Vehicle.colourOf(DyeColor.RED, a.profile()) & 0xFFFFFF, "and banded in its paint");

        p.setItemInHand(InteractionHand.MAIN_HAND, keyA.copy());
        b.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(token(p.getMainHandItem()), token(keyA), "a key at another vehicle is refused and unchanged");
        helper.assertValueEqual(data.ownerOf(b.binding()), p.getUUID(), "that vehicle keeps its own pairing");

        a.setPaint(DyeColor.BLUE);
        a.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(p.getMainHandItem().get(ModContent.KEY_COLOUR.get()), Vehicle.colourOf(DyeColor.BLUE, a.profile()) & 0xFFFFFF, "its own vehicle refreshes the band");
        helper.assertValueEqual(token(p.getMainHandItem()), token(keyA), "without retiring the key");

        ServerPlayer stranger = player(helper, "stranger");
        pairWithBlank(stranger, a);
        helper.assertValueEqual(data.ownerOf(a.binding()), p.getUUID(), "another player's blank does not take it");
        helper.assertTrue(!stranger.getMainHandItem().has(ModContent.KEY_TOKEN.get()), "and stays blank");

        p.setItemInHand(InteractionHand.MAIN_HAND, keyB.copy());
        ModContent.KEY_FOB.get().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(b.isRemoved(), "B's key recalls B");
            helper.assertTrue(!a.isRemoved(), "and leaves A where it is");
            done(helper, p, stranger);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aBlankForAPairedVehicleRetiresTheOldKeyIntoAnOrdinaryItem(GameTestHelper helper) {
        floor(helper);
        Vehicle a = car(helper, 4.5, 4.5);
        ServerPlayer p = player(helper, "rekeyer");
        ItemStack first = pairWithBlank(p, a).copy();
        ItemStack second = pairWithBlank(p, a).copy();
        RecoveryData data = RecoveryData.get(p.server);
        helper.assertTrue(!data.isCurrent(token(first)) && data.isCurrent(token(second)), "the new key works; the old does not");
        p.getInventory().setItem(5, first);
        p.getInventory().selected = 5;
        p.drop(p.getInventory().removeFromSelected(true), true);
        helper.assertValueEqual(keysOnTheGround(helper), 1, "a retired key is an ordinary item: it can be dropped");
        done(helper, p);
    }

    // --- never lost -----------------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 80)
    public void aKeyTossedPutInAChestDroppedInABagOrHandedToAStrangerComesHome(GameTestHelper helper) {
        floor(helper);
        Vehicle a = car(helper, 4.5, 4.5);
        ServerPlayer p = player(helper, "owner");
        UUID t = token(pairWithBlank(p, a));
        p.getInventory().selected = 0;
        p.drop(p.getInventory().removeFromSelected(true), true);
        helper.assertValueEqual(keysOnTheGround(helper), 0, "tossed, nothing lies on the ground");
        helper.assertTrue(KeyFobs.onPerson(p, t), "it is back on its owner");

        BlockPos chestAt = new BlockPos(2, 2, 12);
        helper.setBlock(chestAt, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestAt);
        p.openMenu(chest);
        ItemStack key = p.getInventory().items.stream().filter(keyOf(t)).findFirst().orElseThrow();
        p.containerMenu.getSlot(0).set(key.copy());
        p.getInventory().removeItem(key);
        p.containerMenu.broadcastChanges();
        helper.assertTrue(chest.getItem(0).isEmpty(), "a chest does not keep it, even open");
        helper.assertTrue(KeyFobs.onPerson(p, t), "it is back at once");
        p.closeContainer();

        ServerPlayer stranger = player(helper, "stranger");
        stranger.openMenu(chest);
        key = p.getInventory().items.stream().filter(keyOf(t)).findFirst().orElseThrow();
        // The stranger's own inventory, as the chest's menu shows it: its first main slot after the chest's 27.
        stranger.containerMenu.getSlot(27).set(key.copy());
        p.getInventory().removeItem(key);
        stranger.containerMenu.broadcastChanges();
        helper.assertTrue(!Carried.has(stranger, keyOf(t)), "a stranger does not keep it");
        helper.assertTrue(KeyFobs.onPerson(p, t), "it went home to its owner");
        stranger.closeContainer();

        // In a bag: the inventory full, the key put through Carried lands in the bag.
        key = p.getInventory().items.stream().filter(keyOf(t)).findFirst().orElseThrow();
        p.getInventory().removeItem(key);
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        p.getInventory().setItem(20, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("backpacksplus:basic_backpack"))));
        Carried.give(p, key.copy());
        helper.assertTrue(KeyFobs.onPerson(p, t), "in the bag, on its owner");
        ItemStack bag = p.getInventory().getItem(20).copy();
        p.getInventory().setItem(20, new ItemStack(Items.DIRT, 64));
        p.drop(bag, true);
        helper.assertValueEqual(keysOnTheGround(helper), 0, "the bag dropped: its key did not go with it");
        helper.assertTrue(KeyFobs.get(p.server).waitingFor(t), "no room on its owner: it waits");
        ItemEntity lying = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(bag.getItem())).stream().findFirst().orElseThrow();
        var cells = lying.getItem().getOrDefault(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
        helper.assertTrue(cells.stream().noneMatch(s -> s.is(ModContent.KEY_FOB.get())), "the bag on the ground is without it");
        p.getInventory().setItem(3, ItemStack.EMPTY);
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(KeyFobs.onPerson(p, t), "room made, it came to its owner within a second");
            helper.assertTrue(!KeyFobs.get(p.server).waitingFor(t), "and waits no longer");

            ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(new BlockPos(6, 2, 12)), Direction.SOUTH);
            helper.setBlock(new BlockPos(6, 2, 11), Blocks.STONE);
            helper.getLevel().addFreshEntity(frame);
            ItemStack inHand = p.getInventory().items.stream().filter(keyOf(t)).findFirst().orElseThrow();
            p.getInventory().removeItem(inHand);
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, inHand);
            p.interactOn(frame, InteractionHand.MAIN_HAND);
            helper.assertTrue(frame.getItem().isEmpty(), "a frame is refused it");
            done(helper, p, stranger);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aKeyComesBackAtTheRespawnAndToAnOwnerWhoWasAway(GameTestHelper helper) {
        floor(helper);
        Vehicle a = car(helper, 4.5, 4.5);
        Vehicle b = car(helper, 10.5, 4.5);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "mortal");
        ServerPlayer p = player(helper, profile);
        UUID ta = token(pairWithBlank(p, a));
        p.getInventory().setItem(8, p.getMainHandItem().copy());   // A's key aside, before B's blank takes the hand
        UUID tb = token(pairWithBlank(p, b));
        p.kill();
        helper.assertTrue(p.isDeadOrDying(), "the owner died with both keys");
        helper.assertValueEqual(keysOnTheGround(helper), 0, "neither lies where they fell");
        KeyFobs fobs = KeyFobs.get(p.server);
        helper.assertTrue(fobs.waitingFor(ta) && fobs.waitingFor(tb), "both wait for the respawn");
        ServerPlayer back = p.server.getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
        helper.assertTrue(KeyFobs.onPerson(back, ta) && KeyFobs.onPerson(back, tb), "respawned, the owner has both");

        ItemStack away = back.getInventory().items.stream().filter(keyOf(ta)).findFirst().orElseThrow().copy();
        back.getInventory().removeItem(back.getInventory().items.stream().filter(keyOf(ta)).findFirst().orElseThrow());
        // Logged off as the server logs a player off (its disconnect handler's call); the test's
        // embedded connection does not reach that handler on its own.
        back.server.getPlayerList().remove(back);
        helper.assertTrue(back.server.getPlayerList().getPlayer(profile.getId()) == null, "the owner is away");
        KeyFobs.sendHome(helper.getLevel().getServer(), away);
        helper.assertTrue(fobs.waitingFor(ta), "its owner away, it waits");
        ServerPlayer again = player(helper, profile);
        helper.assertTrue(KeyFobs.onPerson(again, ta), "logged in, the owner has it");
        done(helper, again);
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aBlankReplacesAKeyThatIsGoneMarkedAndIsRefusedWhenNoneIs(GameTestHelper helper) {
        floor(helper);
        Vehicle a = car(helper, 4.5, 4.5);
        a.setCustomName(Component.literal("Rover"));
        Vehicle b = car(helper, 10.5, 4.5);
        ServerPlayer p = player(helper, "forgetful");
        UUID ta = token(pairWithBlank(p, a));
        p.getInventory().setItem(8, p.getMainHandItem().copy());
        UUID tb = token(pairWithBlank(p, b));
        p.getInventory().setItem(7, p.getMainHandItem().copy());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.KEY_FOB.get()));
        ModContent.KEY_FOB.get().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
        helper.assertTrue(!p.getMainHandItem().has(ModContent.KEY_TOKEN.get()), "every key with its owner: the blank stays blank");
        p.getInventory().setItem(8, ItemStack.EMPTY);   // A's key gone, as a /clear leaves it
        ModContent.KEY_FOB.get().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
        ItemStack replacement = p.getMainHandItem();
        RecoveryData data = RecoveryData.get(p.server);
        helper.assertTrue(replacement.has(ModContent.KEY_TOKEN.get()) && data.isCurrent(token(replacement)), "the blank became a key");
        helper.assertTrue(!data.isCurrent(ta), "the lost one no longer works");
        helper.assertTrue(data.isCurrent(tb), "B's key is untouched");
        helper.assertValueEqual(nameOf(replacement), "Key to Rover", "the replacement wears A's mark");
        done(helper, p);
    }
}
