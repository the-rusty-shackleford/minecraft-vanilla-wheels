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

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.domain.Footprint;
import com.chunkworks.vanillawheels.domain.Footprint.Cell;
import com.chunkworks.vanillawheels.domain.LiftMotion;
import com.chunkworks.vanillawheels.domain.LiftStatus;
import com.chunkworks.vanillawheels.lift.LiftBlockEntity;
import com.chunkworks.vanillawheels.lift.LiftControllerBlock;
import com.chunkworks.vanillawheels.lift.LiftMenu;
import com.chunkworks.vanillawheels.lift.LiftPartBlock;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.chunkworks.vanillawheels.VanillaWheelsMod;

/**
 * The Mechanic Lift on a headless server, placed through the real
 * BlockItem by a mock player and worked through its real menu: placement
 * facing each way lays the whole footprint and refuses a blocked cell, an
 * unsupported deck cell and an entity in the way; breaking any part drops
 * one lift and removes all, in creative none, by setblock none; the menu
 * opens at the controller from a far post; Build spawns the box car facing
 * the front and takes exactly its parts, and refuses a missing engine, a
 * deck already holding a vehicle, and a running job; Paint colours the
 * vehicle on the deck and takes one dye, and refuses an empty deck, a
 * vehicle beside the deck, and no dye; closing the menu hands the parts
 * back; the job survives a save.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LiftGameTests {
    private static final int SIZE = 15;
    private static final int FLOOR = 2;
    private static final ResourceLocation BOX_CAR = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_car");
    /** The controller's cell for a lift facing north: the deck runs south from it, centred in the arena. */
    private static final BlockPos CONTROLLER = new BlockPos(7, FLOOR, 3);

    public LiftGameTests() {}

    private static void layFloor(GameTestHelper helper) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 0; y < FLOOR; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    /** A server player with a connection that goes nowhere, so menus and block breaking take the real path. */
    private static ServerPlayer player(GameTestHelper helper, GameType mode) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "lift-tester"), false);
        ServerPlayer sp = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, sp, cookie);
        sp.setGameMode(mode);
        // Beside every lift these tests place, within reach of the controller, off every deck.
        Vec3 at = helper.absoluteVec(new Vec3(11.5, FLOOR, 6.5));
        sp.teleportTo(at.x, at.y, at.z);
        return sp;
    }

    /** effects: has {@code sp} place a lift item on the floor under {@code controller} looking {@code facing}'s way, the real BlockItem path */
    private static InteractionResult place(GameTestHelper helper, ServerPlayer sp, BlockPos controller, Direction facing) {
        sp.setYRot(facing.getOpposite().toYRot());
        ItemStack lift = new ItemStack(ModContent.LIFT_ITEM.get());
        sp.setItemInHand(InteractionHand.MAIN_HAND, lift);
        BlockPos floor = helper.absolutePos(controller).below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        return ModContent.LIFT_ITEM.get().place(new BlockPlaceContext(helper.getLevel(), sp, InteractionHand.MAIN_HAND, lift, hit));
    }

    private static LiftBlockEntity lift(GameTestHelper helper, BlockPos controller) {
        return (LiftBlockEntity) helper.getBlockEntity(controller);
    }

    private static int liftItemsDropped(GameTestHelper helper) {
        AABB arena = new AABB(helper.absoluteVec(new Vec3(0, 0, 0)), helper.absoluteVec(new Vec3(SIZE, 9, SIZE)));
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, arena, e -> e.getItem().is(ModContent.LIFT_ITEM.get())).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static int liftBlocks(GameTestHelper helper) {
        int n = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = FLOOR; y < FLOOR + 4; y++) {
                    BlockState s = helper.getBlockState(new BlockPos(x, y, z));
                    if (s.is(ModContent.LIFT_CONTROLLER.get()) || s.is(ModContent.LIFT_PART.get())) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static LiftMenu open(GameTestHelper helper, ServerPlayer sp, BlockPos at) {
        BlockState s = helper.getBlockState(at);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(at)), Direction.UP, helper.absolutePos(at), false);
        InteractionResult r = s.useWithoutItem(helper.getLevel(), sp, hit);
        helper.assertTrue(r.consumesAction(), "the click opened something: " + r);
        helper.assertTrue(sp.containerMenu instanceof LiftMenu, "the lift's menu: " + sp.containerMenu);
        return (LiftMenu) sp.containerMenu;
    }

    private static void load(LiftMenu menu, int chassis, int wheels, int engine, int dye) {
        menu.getSlot(LiftMenu.CHASSIS).set(chassis > 0 ? ModContent.chassisStack(BOX_CAR) : ItemStack.EMPTY);
        menu.getSlot(LiftMenu.WHEELS).set(wheels > 0 ? new ItemStack(ModContent.WHEEL.get(), wheels) : ItemStack.EMPTY);
        menu.getSlot(LiftMenu.ENGINE).set(engine > 0 ? new ItemStack(ModContent.ENGINE.get()) : ItemStack.EMPTY);
        menu.getSlot(LiftMenu.DYE).set(dye > 0 ? new ItemStack(Items.RED_DYE, dye) : ItemStack.EMPTY);
        menu.broadcastChanges();
    }

    // --- placement -----------------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 100)
    public void placingLaysTheWholeFootprintFacingEachWayAndSetblockAirTakesItAll(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        BlockPos centre = new BlockPos(7, FLOOR, 7);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // The controller is three cells in front of the arena's centre so the deck ends at the centre's far side.
            BlockPos controller = centre.relative(facing, 3);
            InteractionResult r = place(helper, sp, controller, facing);
            helper.assertTrue(r.consumesAction(), "placed facing " + facing + ": " + r);
            BlockState c = helper.getBlockState(controller);
            helper.assertTrue(c.is(ModContent.LIFT_CONTROLLER.get()), "the controller facing " + facing);
            helper.assertValueEqual(c.getValue(LiftControllerBlock.FACING), facing, "its facing");
            for (int i = 1; i <= Footprint.PARTS; i++) {
                BlockPos p = LiftControllerBlock.at(controller, facing, Footprint.cell(i));
                BlockState s = helper.getBlockState(p);
                helper.assertTrue(s.is(ModContent.LIFT_PART.get()), "part " + i + " facing " + facing + " at " + p + ": " + s);
                helper.assertValueEqual(s.getValue(LiftPartBlock.INDEX), i, "its index");
                helper.assertValueEqual(LiftPartBlock.controller(s, helper.absolutePos(p)), helper.absolutePos(controller), "it finds its controller");
            }
            helper.assertValueEqual(liftBlocks(helper), Footprint.PARTS + 1, "the whole lift and nothing else");
            helper.assertTrue(sp.getMainHandItem().isEmpty(), "the item was used");
            // setblock air on a far part: everything goes, nothing drops.
            helper.setBlock(LiftControllerBlock.at(controller, facing, Footprint.cell(Footprint.PARTS)), Blocks.AIR);
            helper.assertValueEqual(liftBlocks(helper), 0, "gone after a setblock facing " + facing);
            helper.assertValueEqual(liftItemsDropped(helper), 0, "no drop from a setblock");
        }
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void placementRefusesABlockedCellAnUnsupportedDeckCellAndAnEntityInTheWay(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        // A block in the back-right corner post's cell.
        BlockPos post = LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(2, 1, 5));
        helper.setBlock(post, Blocks.STONE);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH) == InteractionResult.FAIL, "refused with a block in a post cell");
        helper.assertValueEqual(liftBlocks(helper), 0, "nothing placed");
        helper.assertTrue(sp.getMainHandItem().is(ModContent.LIFT_ITEM.get()), "the item stays");
        helper.setBlock(post, Blocks.AIR);
        // A hole under a deck cell.
        BlockPos hole = LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(-1, 0, 4)).below();
        helper.setBlock(hole, Blocks.AIR);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH) == InteractionResult.FAIL, "refused over a hole");
        helper.assertValueEqual(liftBlocks(helper), 0, "nothing placed over the hole");
        helper.setBlock(hole, Blocks.STONE);
        // A cow standing on the deck-to-be.
        Cow cow = EntityType.COW.create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(7.5, FLOOR, 6.5));
        cow.setPos(at.x, at.y, at.z);
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH) == InteractionResult.FAIL, "refused with a cow in the way");
        cow.discard();
        // Grass in a cell is replaceable and no obstacle.
        helper.setBlock(LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(1, 0, 2)), Blocks.SHORT_GRASS);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed over grass");
        helper.assertValueEqual(liftBlocks(helper), Footprint.PARTS + 1, "the whole lift");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void breakingAnyPartOrTheControllerDropsOneLiftAndTakesAllAndCreativeDropsNone(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        BlockPos farPost = LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(-2, 1, 5));
        helper.assertTrue(sp.gameMode.destroyBlock(helper.absolutePos(farPost)), "broke a post");
        helper.assertValueEqual(liftBlocks(helper), 0, "the lift is gone");
        helper.assertValueEqual(liftItemsDropped(helper), 1, "one lift dropped");
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CONTROLLER)).inflate(16)).forEach(ItemEntity::discard);

        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed again");
        helper.assertTrue(sp.gameMode.destroyBlock(helper.absolutePos(CONTROLLER)), "broke the controller");
        helper.assertValueEqual(liftBlocks(helper), 0, "the lift is gone again");
        helper.assertValueEqual(liftItemsDropped(helper), 1, "one lift dropped again");
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CONTROLLER)).inflate(16)).forEach(ItemEntity::discard);

        ServerPlayer creative = player(helper, GameType.CREATIVE);
        helper.assertTrue(place(helper, creative, CONTROLLER, Direction.NORTH).consumesAction(), "placed by a creative player");
        BlockPos deck = LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(0, 0, 3));
        helper.assertTrue(creative.gameMode.destroyBlock(helper.absolutePos(deck)), "broke a deck cell in creative");
        helper.assertValueEqual(liftBlocks(helper), 0, "the lift is gone in creative");
        helper.assertValueEqual(liftItemsDropped(helper), 0, "and nothing dropped");
        helper.succeed();
    }

    // --- the menu ------------------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 100)
    public void theMenuOpensAtTheControllerFromAFarPostAndHandsThePartsBackOnClose(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftMenu menu = open(helper, sp, LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(2, 1, 5)));
        helper.assertTrue(menu.stillValid(sp), "valid for the player who opened it");
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.NO_RECIPE, "empty: nothing to build");
        helper.assertValueEqual(menu.paintStatus(), LiftStatus.Paint.NO_VEHICLE, "empty: nothing to paint");
        load(menu, 1, 3, 1, 2);
        sp.closeContainer();
        int wheels = 0, chassis = 0, engines = 0, dyes = 0;
        for (ItemStack s : sp.getInventory().items) {
            if (s.is(ModContent.WHEEL.get())) wheels += s.getCount();
            if (s.is(ModContent.CHASSIS.get())) chassis += s.getCount();
            if (s.is(ModContent.ENGINE.get())) engines += s.getCount();
            if (s.is(Items.RED_DYE)) dyes += s.getCount();
        }
        helper.assertValueEqual(wheels, 3, "the wheels came back");
        helper.assertValueEqual(chassis, 1, "the chassis came back");
        helper.assertValueEqual(engines, 1, "the engine came back");
        helper.assertValueEqual(dyes, 2, "the dye came back");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public void buildSpawnsTheCarFacingTheFrontTakesExactlyItsPartsAndRefusesTheRest(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftMenu menu = open(helper, sp, CONTROLLER);
        load(menu, 1, 4, 0, 0);
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.NO_RECIPE, "no engine, no car");
        helper.assertTrue(!menu.clickMenuButton(sp, LiftMenu.BUILD_BUTTON), "the click is refused");
        load(menu, 1, 3, 1, 0);
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.NO_RECIPE, "a wheel short");
        load(menu, 1, 5, 1, 0);
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.NO_RECIPE, "a wheel over is refused too");
        load(menu, 1, 4, 1, 0);
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.READY, "the box car's parts");
        helper.assertTrue(menu.clickMenuButton(sp, LiftMenu.BUILD_BUTTON), "built");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        List<Vehicle> cars = helper.getLevel().getEntitiesOfClass(Vehicle.class, lift.deckBox());
        helper.assertValueEqual(cars.size(), 1, "one car on the deck");
        Vehicle car = cars.get(0);
        helper.assertValueEqual(car.profileId(), BOX_CAR, "the chassis' vehicle");
        helper.assertValueEqual(car.getYRot(), Footprint.Heading.NORTH.yaw(), "facing the front");
        helper.assertTrue(car.position().distanceTo(lift.spawn()) < 0.01, "on the deck's centre: " + car.position() + " vs " + lift.spawn());
        helper.assertTrue(menu.getSlot(LiftMenu.CHASSIS).getItem().isEmpty(), "the chassis was used");
        helper.assertTrue(menu.getSlot(LiftMenu.WHEELS).getItem().isEmpty(), "all four wheels were used");
        helper.assertTrue(menu.getSlot(LiftMenu.ENGINE).getItem().isEmpty(), "the engine was used");
        helper.assertTrue(lift.busy(), "the rams are working");
        menu.broadcastChanges();
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.BUSY, "busy while the job runs");
        load(menu, 1, 4, 1, 0);
        helper.assertTrue(!menu.clickMenuButton(sp, LiftMenu.BUILD_BUTTON), "refused while busy");
        helper.runAtTickTime(LiftMotion.JOB + 2, () -> {
            helper.assertTrue(!lift.busy(), "the job ended");
            menu.broadcastChanges();
            helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.OCCUPIED, "the deck holds the car");
            helper.assertTrue(!menu.clickMenuButton(sp, LiftMenu.BUILD_BUTTON), "refused while occupied");
            helper.assertValueEqual(helper.getLevel().getEntitiesOfClass(Vehicle.class, lift.deckBox()).size(), 1, "still one car");
            car.discard();
            menu.broadcastChanges();
            helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.READY, "ready once the deck is clear");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public void paintColoursTheCarOnTheDeckTakesOneDyeAndRefusesAnEmptyDeckACarBesideItAndNoDye(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        LiftMenu menu = open(helper, sp, CONTROLLER);
        load(menu, 0, 0, 0, 2);
        helper.assertValueEqual(menu.paintStatus(), LiftStatus.Paint.NO_VEHICLE, "nothing on the deck");
        // A car beside the deck does not count.
        Vehicle beside = Vehicle.create(helper.getLevel(), BOX_CAR, helper.absoluteVec(new Vec3(12.5, FLOOR, 7.5)), 0.0f);
        helper.getLevel().addFreshEntity(beside);
        menu.broadcastChanges();
        helper.assertValueEqual(menu.paintStatus(), LiftStatus.Paint.NO_VEHICLE, "a car beside the deck is not on it");
        helper.assertTrue(!menu.clickMenuButton(sp, LiftMenu.PAINT_BUTTON), "refused");
        Vehicle car = Vehicle.create(helper.getLevel(), BOX_CAR, lift.spawn(), Footprint.Heading.NORTH.yaw());
        helper.getLevel().addFreshEntity(car);
        load(menu, 0, 0, 0, 0);
        helper.assertValueEqual(menu.paintStatus(), LiftStatus.Paint.NO_DYE, "a car but no dye");
        load(menu, 0, 0, 0, 2);
        helper.assertValueEqual(menu.paintStatus(), LiftStatus.Paint.READY, "a car and a dye");
        helper.assertTrue(menu.clickMenuButton(sp, LiftMenu.PAINT_BUTTON), "painted");
        helper.assertValueEqual(car.paint(), DyeColor.RED, "the car is red");
        helper.assertTrue(beside.paint() != DyeColor.RED, "the car beside is not");
        helper.assertValueEqual(menu.getSlot(LiftMenu.DYE).getItem().getCount(), 1, "one dye taken");
        helper.assertTrue(lift.busy(), "the rams are working");
        helper.assertTrue(lift.raise(0.0f) >= 0.0, "the deck is rising: " + lift.raise(0.0f));
        // The job survives a save.
        var tag = lift.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertValueEqual(tag.getInt("JobTicks"), LiftMotion.JOB, "the job is saved");
        helper.runAtTickTime(LiftMotion.JOB / 2, () -> helper.assertTrue(lift.raise(0.0f) > LiftMotion.LIFT * 0.9, "up mid-job: " + lift.raise(0.0f)));
        helper.runAtTickTime(LiftMotion.JOB + 2, () -> {
            helper.assertTrue(!lift.busy(), "the job ended");
            helper.assertValueEqual(lift.raise(0.0f), 0.0, "the deck is down");
            helper.succeed();
        });
    }
}
