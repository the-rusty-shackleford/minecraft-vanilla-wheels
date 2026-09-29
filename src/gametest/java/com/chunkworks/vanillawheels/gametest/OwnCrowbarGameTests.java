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
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.OwnCrowbars;
import com.chunkworks.vanillawheels.ToolboxMenu;
import com.chunkworks.vanillawheels.VanillaWheelsMod;
import com.chunkworks.vanillawheels.Vehicle;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Every vehicle's own crowbar (D-0023), on a real server with real server
 * players. Partitions. Birth: built, and saved before 1.10.0 then loaded. The
 * toolbox: opened by a crouching empty hand, hands its crowbar out, takes back
 * only its own, holds the vehicle while open. Going home: tossed, spilled at a
 * death, left in a chest (at once), left in an ender chest (when it closes),
 * handed to an item frame (refused), taken by a decorated pot (given up at the
 * tick's end); kept in a bag the player carries. Packing: pried with its own
 * crowbar it travels inside; with a spare the spare stays in hand; lost while
 * the vehicle is packed, it waits and is claimed when the vehicle is placed.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnCrowbarGameTests {
    private static final ResourceLocation CAR = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_car");

    public OwnCrowbarGameTests() {}

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static Vehicle car(GameTestHelper helper) {
        floor(helper);
        Vehicle vehicle = Vehicle.create(helper.getLevel(), CAR, helper.absoluteVec(new Vec3(7.5, 2, 7.5)), 0);
        helper.getLevel().addFreshEntity(vehicle);
        return vehicle;
    }

    /** effects: a survival server player standing in front of the car, facing it */
    private static ServerPlayer player(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "crowbar-tester"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(7.5, 2, 11.0));
        player.teleportTo(helper.getLevel(), at.x, at.y, at.z, 180.0f, 20.0f);
        return player;
    }

    private static void done(GameTestHelper helper, ServerPlayer player) {
        player.connection.disconnect(Component.literal("test complete"));
        helper.succeed();
    }

    /** effects: takes the car's own crowbar out of its toolbox into the player's hand */
    private static ItemStack takeOut(Vehicle car, ServerPlayer player) {
        ItemStack crowbar = car.ownCrowbar();
        car.setOwnCrowbar(ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, crowbar);
        return crowbar;
    }

    private static int loose(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> OwnCrowbars.isOwn(e.getItem())).size();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aVehicleIsBornWithItsOwnCrowbarAndOneSavedBeforeGetsItsAtLoad(GameTestHelper helper) {
        Vehicle car = car(helper);
        helper.assertTrue(car.vehicleId() != null, "an id at birth");
        helper.assertTrue(OwnCrowbars.belongsTo(car.ownCrowbar(), car.vehicleId()), "and its own crowbar in its toolbox");
        // As 1.9.5 saved it: no id, no crowbar.
        CompoundTag old = new CompoundTag();
        car.save(old);
        old.remove("VehicleId");
        old.remove("OwnCrowbar");
        old.remove("UUID");
        Vehicle loaded = (Vehicle) EntityType.create(old, helper.getLevel()).orElseThrow();
        helper.assertTrue(loaded.vehicleId() == null, "the old save has no id");
        helper.getLevel().addFreshEntity(loaded);
        helper.assertTrue(loaded.vehicleId() != null && !loaded.vehicleId().equals(car.vehicleId()), "it gets its own id at load");
        helper.assertTrue(OwnCrowbars.belongsTo(loaded.ownCrowbar(), loaded.vehicleId()), "and its own crowbar");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void theToolboxOpensToACrouchingEmptyHandHandsItsCrowbarOutAndTakesBackOnlyItsOwn(GameTestHelper helper) {
        Vehicle car = car(helper);
        Vehicle other = Vehicle.create(helper.getLevel(), CAR, helper.absoluteVec(new Vec3(2.5, 2, 2.5)), 0);
        helper.getLevel().addFreshEntity(other);
        ServerPlayer p = player(helper);
        p.setShiftKeyDown(true);
        helper.assertTrue(car.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND).consumesAction(), "the crouching empty-handed click is taken");
        helper.assertTrue(p.containerMenu instanceof ToolboxMenu, "and opens the toolbox: " + p.containerMenu);
        ToolboxMenu menu = (ToolboxMenu) p.containerMenu;
        helper.assertTrue(OwnCrowbars.belongsTo(menu.getSlot(ToolboxMenu.SLOT).getItem(), car.vehicleId()), "its crowbar is in the slot");
        helper.assertTrue(!menu.getSlot(ToolboxMenu.SLOT).mayPlace(new ItemStack(ModContent.CROWBAR.get())), "a spare crowbar is refused");
        helper.assertTrue(!menu.getSlot(ToolboxMenu.SLOT).mayPlace(other.ownCrowbar()), "another vehicle's own crowbar is refused");
        menu.quickMoveStack(p, ToolboxMenu.SLOT);
        helper.assertTrue(car.ownCrowbar().isEmpty(), "shift-clicked out");
        helper.assertTrue(p.getInventory().items.stream().anyMatch(s -> OwnCrowbars.belongsTo(s, car.vehicleId())), "into the inventory");
        helper.assertTrue(menu.getSlot(ToolboxMenu.SLOT).mayPlace(p.getInventory().items.stream().filter(s -> OwnCrowbars.belongsTo(s, car.vehicleId())).findFirst().orElseThrow()), "its own goes back in");
        p.setShiftKeyDown(false);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.CROWBAR.get()));
        car.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(!car.isRemoved(), "an open toolbox holds the vehicle, as an open chest does");
        p.closeContainer();
        car.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(car.isRemoved(), "closed, the crowbar pries it loose");
        done(helper, p);
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void anOwnCrowbarTossedOrSpilledAtADeathGoesHome(GameTestHelper helper) {
        Vehicle car = car(helper);
        ServerPlayer p = player(helper);
        ItemStack crowbar = takeOut(car, p);
        p.drop(p.getInventory().removeFromSelected(true), true);
        helper.assertValueEqual(loose(helper), 0, "nothing lies on the ground");
        helper.assertTrue(OwnCrowbars.belongsTo(car.ownCrowbar(), car.vehicleId()), "tossed, it is home");
        takeOut(car, p);
        p.kill();
        helper.assertTrue(p.isDeadOrDying(), "the player died holding it");
        helper.assertValueEqual(loose(helper), 0, "nothing lies on the ground");
        helper.assertTrue(OwnCrowbars.belongsTo(car.ownCrowbar(), car.vehicleId()), "spilled at the death, it is home");
        helper.assertTrue(crowbar != null, "(the first)");
        done(helper, p);
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void anOwnCrowbarLeftInAChestGoesHomeAtOnceAndInAnEnderChestWhenItCloses(GameTestHelper helper) {
        Vehicle car = car(helper);
        ServerPlayer p = player(helper);
        BlockPos chestAt = new BlockPos(3, 2, 3);
        helper.setBlock(chestAt, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestAt);
        takeOut(car, p);
        p.openMenu(chest);
        AbstractContainerMenu menu = p.containerMenu;
        menu.getSlot(0).set(p.getMainHandItem().copy());
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        menu.broadcastChanges();
        helper.assertTrue(chest.getItem(0).isEmpty(), "the chest does not keep it, even open");
        helper.assertTrue(OwnCrowbars.belongsTo(car.ownCrowbar(), car.vehicleId()), "it is home at once");
        p.closeContainer();
        // The ender chest is the player's own store, not a block's: looked at when it closes.
        takeOut(car, p);
        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv, pl.getEnderChestInventory()), Component.literal("ender")));
        p.containerMenu.getSlot(0).set(p.getMainHandItem().copy());
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.containerMenu.broadcastChanges();
        helper.assertTrue(OwnCrowbars.isOwn(p.getEnderChestInventory().getItem(0)), "in the ender chest while it is open");
        p.closeContainer();
        helper.assertTrue(p.getEnderChestInventory().getItem(0).isEmpty(), "gone from it when it closes");
        helper.assertTrue(OwnCrowbars.belongsTo(car.ownCrowbar(), car.vehicleId()), "and home");
        done(helper, p);
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void anOwnCrowbarInABagThePlayerCarriesStaysThere(GameTestHelper helper) {
        Vehicle car = car(helper);
        ServerPlayer p = player(helper);
        ItemStack crowbar = car.ownCrowbar().copy();
        car.setOwnCrowbar(ItemStack.EMPTY);
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        p.getInventory().setItem(20, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("backpacksplus:basic_backpack"))));
        Carried.giveOrDrop(p, crowbar);
        helper.assertTrue(Carried.has(p, s -> OwnCrowbars.belongsTo(s, car.vehicleId())), "into the pocketed bag");
        BlockPos chestAt = new BlockPos(3, 2, 3);
        helper.setBlock(chestAt, Blocks.CHEST);
        p.openMenu((ChestBlockEntity) helper.getBlockEntity(chestAt));
        p.containerMenu.broadcastChanges();
        p.closeContainer();
        helper.assertTrue(Carried.has(p, s -> OwnCrowbars.belongsTo(s, car.vehicleId())), "a bag the player carries keeps it");
        helper.assertTrue(car.ownCrowbar().isEmpty(), "it did not go home");
        done(helper, p);
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void anOwnCrowbarIsNotHandedToAnItemFrameAndADecoratedPotGivesItUp(GameTestHelper helper) {
        Vehicle car = car(helper);
        ServerPlayer p = player(helper);
        takeOut(car, p);
        BlockPos wall = new BlockPos(3, 2, 3);
        helper.setBlock(wall, Blocks.STONE);
        ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(wall.south()), Direction.SOUTH);
        helper.getLevel().addFreshEntity(frame);
        p.interactOn(frame, InteractionHand.MAIN_HAND);
        helper.assertTrue(frame.getItem().isEmpty(), "the frame is refused it");
        helper.assertTrue(OwnCrowbars.isOwn(p.getMainHandItem()), "it stays in hand");
        BlockPos potAt = new BlockPos(5, 2, 3);
        helper.setBlock(potAt, Blocks.DECORATED_POT);
        BlockPos pot = helper.absolutePos(potAt);
        p.gameMode.useItemOn(p, helper.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pot), Direction.UP, pot, false));
        var potEntity = (net.minecraft.world.level.block.entity.DecoratedPotBlockEntity) helper.getBlockEntity(potAt);
        helper.assertTrue(OwnCrowbars.isOwn(potEntity.getTheItem()) && p.getMainHandItem().isEmpty(), "the pot took it, as a pot takes anything: " + potEntity.getTheItem());
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!OwnCrowbars.isOwn(potEntity.getTheItem()), "at the tick's end the pot gave it up: " + potEntity.getTheItem());
            helper.assertTrue(OwnCrowbars.belongsTo(car.ownCrowbar(), car.vehicleId()), "and it is home");
            done(helper, p);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void priedWithItsOwnCrowbarItTravelsInsideAndASpareStaysInHand(GameTestHelper helper) {
        Vehicle car = car(helper);
        ServerPlayer p = player(helper);
        UUID id = car.vehicleId();
        // A disc in its radio too: a packed vehicle carries both as components (HeldStack).
        p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MUSIC_DISC_CAT));
        car.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
        helper.assertTrue(car.disc().is(Items.MUSIC_DISC_CAT), "a disc in the radio");
        takeOut(car, p);
        car.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(car.isRemoved(), "pried loose");
        helper.assertTrue(!Carried.has(p, s -> OwnCrowbars.isOwn(s)), "its own crowbar left the player");
        ItemStack packed = p.getInventory().items.stream().filter(s -> s.is(ModContent.VEHICLE_ITEM.get())).findFirst().orElseThrow();
        helper.assertTrue(packed.has(ModContent.OWN_CROWBAR.get()) && OwnCrowbars.belongsTo(packed.get(ModContent.OWN_CROWBAR.get()).copy(), id), "and travels inside");
        helper.assertTrue(packed.has(ModContent.DISC.get()) && packed.get(ModContent.DISC.get()).copy().is(Items.MUSIC_DISC_CAT), "with the disc in its radio");
        helper.assertValueEqual(packed.get(ModContent.VEHICLE_ID.get()), id, "with the vehicle's id");
        BlockPos floor = helper.absolutePos(new BlockPos(7, 1, 7));
        p.setItemInHand(InteractionHand.MAIN_HAND, packed);
        ModContent.VEHICLE_ITEM.get().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
        Vehicle placed = helper.getLevel().getEntitiesOfClass(Vehicle.class, helper.getBounds().inflate(4)).stream().findFirst().orElseThrow();
        helper.assertValueEqual(placed.vehicleId(), id, "placed, the same vehicle");
        helper.assertTrue(OwnCrowbars.belongsTo(placed.ownCrowbar(), id), "with its crowbar in its toolbox");
        helper.assertTrue(placed.disc().is(Items.MUSIC_DISC_CAT), "and its disc in its radio");
        ItemStack spare = new ItemStack(ModContent.CROWBAR.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, spare);
        placed.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(placed.isRemoved(), "a spare pries too");
        helper.assertTrue(p.getMainHandItem().is(ModContent.CROWBAR.get()) && !OwnCrowbars.isOwn(p.getMainHandItem()), "and stays in hand");
        done(helper, p);
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void anOwnCrowbarLostWhileItsVehicleIsPackedWaitsAndIsClaimedWhenItIsPlaced(GameTestHelper helper) {
        Vehicle car = car(helper);
        ServerPlayer p = player(helper);
        UUID id = car.vehicleId();
        ItemStack own = car.ownCrowbar().copy();
        car.setOwnCrowbar(ItemStack.EMPTY);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.CROWBAR.get()));
        car.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(car.isRemoved(), "pried loose with a spare, its own crowbar out");
        ItemStack packed = p.getInventory().items.stream().filter(s -> s.is(ModContent.VEHICLE_ITEM.get())).findFirst().orElseThrow();
        helper.assertTrue(!packed.has(ModContent.OWN_CROWBAR.get()), "packed without it");
        p.getInventory().setItem(8, own);
        p.getInventory().selected = 8;
        p.drop(p.getInventory().removeFromSelected(true), true);
        helper.assertValueEqual(loose(helper), 0, "dropped, nothing lies on the ground");
        helper.assertTrue(OwnCrowbars.get(p.server).waitingFor(id), "it waits for its packed vehicle");
        BlockPos floor = helper.absolutePos(new BlockPos(7, 1, 7));
        p.getInventory().selected = 0;
        p.setItemInHand(InteractionHand.MAIN_HAND, packed);
        ModContent.VEHICLE_ITEM.get().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
        Vehicle placed = helper.getLevel().getEntitiesOfClass(Vehicle.class, helper.getBounds().inflate(4)).stream().findFirst().orElseThrow();
        helper.assertTrue(OwnCrowbars.belongsTo(placed.ownCrowbar(), id), "placed, it claims its crowbar");
        helper.assertTrue(!OwnCrowbars.get(p.server).waitingFor(id), "which no longer waits");
        done(helper, p);
    }
}
