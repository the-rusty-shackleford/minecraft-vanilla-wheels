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
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Docking;
import com.chunkworks.vanillawheels.domain.Footprint;
import com.chunkworks.vanillawheels.domain.Footprint.Cell;
import com.chunkworks.vanillawheels.domain.Input;
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
import net.minecraft.nbt.CompoundTag;
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
    private static final ResourceLocation BOX_TRAILER = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_trailer");
    /** The box car's body stretched to ten blocks: longer than any lift takes. */
    private static final ResourceLocation BOX_LONG = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_long");
    /** The long box 3.8 wide, as the Chinook: wider than the deck between its posts. */
    private static final ResourceLocation BOX_WIDE = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_wide");
    /** The controller's cell for a lift facing north: its eight-long deck runs south from it, x 5..9, z 3..10. */
    private static final BlockPos CONTROLLER = new BlockPos(7, FLOOR, 3);
    /** A second lift's controller, facing east: its deck runs west from it along the arena's south side, x 6..13, z 9..13. */
    private static final BlockPos EAST_CONTROLLER = new BlockPos(13, FLOOR, 11);
    /** The lift the item places since 1.18.0 (D-0042). */
    private static final Footprint PLACED = Footprint.LONG;
    /** The deck's top, where a vehicle's feet stand. */
    private static final double DECK = FLOOR + 1;
    private static final Input STICK_RIGHT = new Input(0, 1, false, true, true);
    private static final Input GAS = new Input(1, 0, false, true, true);

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

    private static double bodyLength(GameTestHelper helper, ResourceLocation id) {
        return VanillaWheels.profile(helper.getLevel().registryAccess(), id).orElseThrow().value().body().length();
    }

    /** effects: stands a fuelled box car on {@code lift}'s spot, docked as Build leaves one, and returns it */
    private static Vehicle parked(GameTestHelper helper, LiftBlockEntity lift) {
        Vehicle car = Vehicle.create(helper.getLevel(), BOX_CAR, lift.spot(bodyLength(helper, BOX_CAR)), lift.heading().yaw());
        car.setFuel(car.tank().capacity());
        car.dock(lift, false);
        helper.getLevel().addFreshEntity(car);
        return car;
    }

    /** effects: stands a fuelled, free {@code id} at {@code at} in the arena facing {@code yaw}, and returns it */
    private static Vehicle stand(GameTestHelper helper, ResourceLocation id, Vec3 at, float yaw) {
        Vehicle v = Vehicle.create(helper.getLevel(), id, helper.absoluteVec(at), yaw);
        helper.assertTrue(v != null, id + " is registered");
        v.setFuel(v.tank().capacity());
        helper.getLevel().addFreshEntity(v);
        return v;
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
        // Off every deck this test lays (they reach x 3..11 and z 3..11): a body in a cell keeps a lift from standing.
        Vec3 corner = helper.absoluteVec(new Vec3(1.5, FLOOR, 12.5));
        sp.teleportTo(corner.x, corner.y, corner.z);
        BlockPos centre = new BlockPos(7, FLOOR, 7);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // The controller is three cells in front of the arena's centre, so the eight-long deck runs four past it.
            BlockPos controller = centre.relative(facing, 3);
            InteractionResult r = place(helper, sp, controller, facing);
            helper.assertTrue(r.consumesAction(), "placed facing " + facing + ": " + r);
            BlockState c = helper.getBlockState(controller);
            helper.assertTrue(c.is(ModContent.LIFT_CONTROLLER.get()), "the controller facing " + facing);
            helper.assertValueEqual(c.getValue(LiftControllerBlock.FACING), facing, "its facing");
            helper.assertTrue(c.getValue(LiftControllerBlock.LONG), "the item places the long lift");
            helper.assertValueEqual(lift(helper, controller).footprint(), PLACED, "eight long");
            for (int i = 1; i <= PLACED.parts(); i++) {
                BlockPos p = LiftControllerBlock.at(controller, facing, PLACED.cell(i));
                BlockState s = helper.getBlockState(p);
                helper.assertTrue(s.is(ModContent.LIFT_PART.get()), "part " + i + " facing " + facing + " at " + p + ": " + s);
                helper.assertValueEqual(s.getValue(LiftPartBlock.INDEX), i, "its index");
                helper.assertTrue(s.getValue(LiftPartBlock.LONG), "its shape");
                helper.assertValueEqual(LiftPartBlock.controller(s, helper.absolutePos(p)), helper.absolutePos(controller), "it finds its controller");
            }
            helper.assertValueEqual(liftBlocks(helper), PLACED.parts() + 1, "the whole lift and nothing else");
            helper.assertTrue(sp.getMainHandItem().isEmpty(), "the item was used");
            // setblock air on a far part: everything goes, nothing drops.
            helper.setBlock(LiftControllerBlock.at(controller, facing, PLACED.cell(PLACED.parts())), Blocks.AIR);
            helper.assertValueEqual(liftBlocks(helper), 0, "gone after a setblock facing " + facing);
            helper.assertValueEqual(liftItemsDropped(helper), 0, "no drop from a setblock");
        }
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void placementRefusesABlockedCellAnUnsupportedDeckCellAndAnEntityInTheWay(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        // A block in the back-right corner post's cell: the eighth row's, two rows past where a short lift's would be.
        BlockPos post = LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(2, 1, 7));
        helper.setBlock(post, Blocks.STONE);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH) == InteractionResult.FAIL, "refused with a block in a post cell");
        helper.assertValueEqual(liftBlocks(helper), 0, "nothing placed");
        helper.assertTrue(sp.getMainHandItem().is(ModContent.LIFT_ITEM.get()), "the item stays");
        helper.setBlock(post, Blocks.AIR);
        // A hole under a deck cell of the seventh row, which only the long lift has.
        BlockPos hole = LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(-1, 0, 6)).below();
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
        helper.assertValueEqual(liftBlocks(helper), PLACED.parts() + 1, "the whole lift");
        helper.succeed();
    }

    /**
     * effects: removes the dropped lift items inside this test's own bounds. Only those, and only
     * there: a sweep sixteen blocks round the controller once reached into the neighbouring
     * runway and took the chest test's spilled apples, whenever that test had spilled first.
     */
    private static void sweepLiftItems(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), e -> e.getItem().is(ModContent.LIFT_ITEM.get())).forEach(ItemEntity::discard);
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void breakingAnyPartOrTheControllerDropsOneLiftAndTakesAllAndCreativeDropsNone(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        BlockPos farPost = LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(-2, 1, 7));
        helper.assertTrue(sp.gameMode.destroyBlock(helper.absolutePos(farPost)), "broke a post");
        helper.assertValueEqual(liftBlocks(helper), 0, "the lift is gone");
        helper.assertValueEqual(liftItemsDropped(helper), 1, "one lift dropped");
        sweepLiftItems(helper);

        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed again");
        helper.assertTrue(sp.gameMode.destroyBlock(helper.absolutePos(CONTROLLER)), "broke the controller");
        helper.assertValueEqual(liftBlocks(helper), 0, "the lift is gone again");
        helper.assertValueEqual(liftItemsDropped(helper), 1, "one lift dropped again");
        sweepLiftItems(helper);

        ServerPlayer creative = player(helper, GameType.CREATIVE);
        helper.assertTrue(place(helper, creative, CONTROLLER, Direction.NORTH).consumesAction(), "placed by a creative player");
        BlockPos deck = LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(0, 0, 3));
        helper.assertTrue(creative.gameMode.destroyBlock(helper.absolutePos(deck)), "broke a deck cell in creative");
        helper.assertValueEqual(liftBlocks(helper), 0, "the lift is gone in creative");
        helper.assertValueEqual(liftItemsDropped(helper), 0, "and nothing dropped");
        helper.succeed();
    }

    // --- the menu ------------------------------------------------------------

    /** Partitions: empty/healthy/damaged/wreck; wrong/short/exact/excess material; idle/busy; survival/creative. */
    @GameTest(template = "arena", timeoutTicks = 160)
    public void repairsOnlyDamagedMountedVehiclesAtProportionalCostAndPreservesCargo(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "lift placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        LiftMenu menu = open(helper, sp, CONTROLLER);
        menu.broadcastChanges();
        helper.assertTrue(!menu.repairVisible() && !menu.getSlot(LiftMenu.REPAIR).isActive(), "no repair section on an empty deck");
        Vehicle car = parked(helper, lift);
        car.setFuel(1234); car.setItem(0, new ItemStack(Items.DIAMOND, 7));
        menu.broadcastChanges();
        helper.assertTrue(!menu.repairVisible(), "healthy vehicle hides repair section");
        car.setCondition(7499); menu.broadcastChanges();
        helper.assertTrue(menu.repairVisible(), "damage reveals repair section");
        helper.assertValueEqual(menu.repairCost(), 6, "25.01 percent damage rounds 5.002 steel up to six");
        menu.getSlot(LiftMenu.REPAIR).set(new ItemStack(Items.DIRT, 64)); menu.broadcastChanges();
        helper.assertTrue(!menu.repairReady() && !menu.clickMenuButton(sp, LiftMenu.REPAIR_BUTTON), "wrong material rejected");
        ItemStack steel = menu.repairMaterial(); steel.setCount(5);
        var repairItem = steel.getItem();
        menu.getSlot(LiftMenu.REPAIR).set(steel); menu.broadcastChanges();
        helper.assertTrue(!menu.clickMenuButton(sp, LiftMenu.REPAIR_BUTTON), "insufficient material rejected");
        helper.assertValueEqual(steel.getCount(), 5, "refused repair consumed nothing");
        steel.setCount(8); menu.broadcastChanges();
        helper.assertTrue(menu.repairReady() && menu.clickMenuButton(sp, LiftMenu.REPAIR_BUTTON), "repair accepted");
        helper.assertValueEqual(car.condition(), 10000, "fully repaired");
        helper.assertTrue(menu.getSlot(LiftMenu.REPAIR).getItem().isEmpty(), "hidden repair slot holds nothing");
        helper.assertValueEqual(sp.getInventory().items.stream().filter(s -> s.is(repairItem)).mapToInt(ItemStack::getCount).sum(), 2, "two spare ingots returned immediately");
        helper.assertValueEqual(car.tank().ticks(), 1234, "repair does not refill fuel");
        helper.assertValueEqual(car.getItem(0).getCount(), 7, "cargo is intact");
        helper.assertTrue(!menu.repairVisible(), "healthy car hides repair section again");
        car.setCondition(0); menu.getSlot(LiftMenu.REPAIR).set(menu.repairMaterial()); menu.broadcastChanges();
        helper.assertTrue(!menu.clickMenuButton(sp, LiftMenu.REPAIR_BUTTON), "busy lift refuses another job");
        helper.runAtTickTime(LiftMotion.JOB + 2, () -> {
            menu.broadcastChanges();
            helper.assertValueEqual(menu.repairCost(), 20, "full wreck costs twenty steel");
            ItemStack full = menu.repairMaterial(); full.setCount(20); menu.getSlot(LiftMenu.REPAIR).set(full);
            helper.assertTrue(menu.clickMenuButton(sp, LiftMenu.REPAIR_BUTTON), "wreck repaired through real menu");
            helper.assertTrue(car.hasFuel(), "repaired engine works again");
            helper.assertTrue(menu.getSlot(LiftMenu.REPAIR).getItem().isEmpty(), "full repair consumed twenty");
            sp.closeContainer(); helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void creativeRepairsRequireAndConsumeNoMaterials(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.CREATIVE);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "lift placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        Vehicle car = parked(helper, lift);
        car.setCondition(0);
        LiftMenu menu = open(helper, sp, CONTROLLER); menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(LiftMenu.REPAIR).getItem().isEmpty(), "no repair material supplied");
        helper.assertTrue(menu.repairReady() && menu.clickMenuButton(sp, LiftMenu.REPAIR_BUTTON), "creative repairs without material");
        helper.assertValueEqual(car.condition(), 10000, "repaired");
        sp.closeContainer(); helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void theMenuOpensAtTheControllerFromAFarPostAndHandsThePartsBackOnClose(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftMenu menu = open(helper, sp, LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(2, 1, 7)));
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
        Vec3 spot = lift.spot(bodyLength(helper, BOX_CAR));
        helper.assertTrue(car.position().distanceTo(spot) < 0.01, "on its spot, backed up to the wall: " + car.position() + " vs " + spot);
        helper.assertTrue(car.docked(), "born docked");
        helper.assertValueEqual(lift.dockedVehicle(), car, "the lift's vehicle");
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

    @GameTest(template = "arena", timeoutTicks = 60)
    public void buildMakesAProfileWhoseWheelsAreNotDrawnFromItsChassisAndEngineAloneAsTheKindThatClaimsIt(GameTestHelper helper) {
        // The box skids: the box car with its wheels not drawn (skids in the body, D-0030), claimed by the tests' own kind.
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftMenu menu = open(helper, sp, CONTROLLER);
        menu.getSlot(LiftMenu.CHASSIS).set(ModContent.chassisStack(GameTestMod.BOX_SKIDS));
        menu.getSlot(LiftMenu.WHEELS).set(new ItemStack(ModContent.WHEEL.get(), 4));
        menu.getSlot(LiftMenu.ENGINE).set(new ItemStack(ModContent.ENGINE.get()));
        menu.broadcastChanges();
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.NO_RECIPE, "four wheels its skids have no place for");
        menu.getSlot(LiftMenu.WHEELS).set(ItemStack.EMPTY);
        menu.broadcastChanges();
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.READY, "a chassis and an engine build it");
        helper.assertTrue(menu.clickMenuButton(sp, LiftMenu.BUILD_BUTTON), "built");
        List<Vehicle> built = helper.getLevel().getEntitiesOfClass(Vehicle.class, lift(helper, CONTROLLER).deckBox());
        helper.assertValueEqual(built.size(), 1, "one vehicle on the deck");
        helper.assertTrue(built.get(0) instanceof SkidVehicle, "made by the kind that claims it: " + built.get(0));
        helper.assertTrue(menu.getSlot(LiftMenu.CHASSIS).getItem().isEmpty() && menu.getSlot(LiftMenu.ENGINE).getItem().isEmpty(), "the chassis and the engine were used");
        helper.succeed();
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
        Vehicle car = parked(helper, lift);
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

    // --- docking (D-0040) ---------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 120)
    public void aVehicleLeftStandingOnTheDeckIsTakenToItsSpotFacingTheFront(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        // Off centre, askew, nobody aboard: the deck runs z 3..10, x 5..9.
        Vehicle car = stand(helper, BOX_CAR, new Vec3(6.3, DECK, 4.6), 135.0f);
        Vec3 spot = lift.spot(bodyLength(helper, BOX_CAR));
        Vec3 start = car.position();
        helper.assertTrue(start.distanceTo(spot) > 1.0, "it starts well off the spot");
        helper.assertValueEqual(lift.dockStatus(), LiftStatus.Dock.TAKING, "about to be taken");
        helper.runAtTickTime(Docking.REST - 2, () -> helper.assertTrue(!car.docked() && !lift.busy(), "not before it has stood still for REST ticks"));
        int taken = Docking.REST + 2;
        helper.runAtTickTime(taken, () -> {
            helper.assertTrue(car.docked(), "the lift has taken it");
            helper.assertTrue(lift.busy(), "the rams run");
            helper.assertValueEqual(lift.dockStatus(), LiftStatus.Dock.TAKING, "taking");
            helper.assertTrue(car.position().distanceTo(start) < 0.01, "still where it stood through the rise");
        });
        helper.runAtTickTime(taken + LiftMotion.UP + LiftMotion.HOLD / 2, () -> {
            helper.assertTrue(lift.raise(0.0f) > LiftMotion.LIFT * 0.9, "the deck is up while it slides: " + lift.raise(0.0f));
            double gone = car.position().distanceTo(start), left = car.position().distanceTo(spot);
            helper.assertTrue(gone > 0.1 && left > 0.1, "half way through the hold it is between: gone " + gone + ", left " + left);
            helper.assertValueEqual(car.getY(), spot.y, "on the deck's top throughout");
            // The drawn suspension carries the deck's raise while the job runs; the body's own lift is what is left.
            var s = car.suspension(1.0f);
            double own = s.lift() - car.deckRaise(1.0f);
            helper.assertTrue(Math.abs(own) < 0.3 && Math.abs(s.pitch()) < 0.1 && Math.abs(s.roll()) < 0.1,
                    "posed level on the deck as it slides: lift " + own + " pitch " + s.pitch() + " roll " + s.roll());
        });
        helper.runAtTickTime(taken + LiftMotion.JOB + 2, () -> {
            helper.assertTrue(!lift.busy(), "the job ended");
            helper.assertTrue(car.position().distanceTo(spot) < 0.01, "on its spot: " + car.position() + " vs " + spot);
            helper.assertValueEqual(car.getYRot(), Footprint.Heading.NORTH.yaw(), "facing the front");
            helper.assertTrue(car.docked(), "and held");
            helper.assertValueEqual(lift.dockStatus(), LiftStatus.Dock.DOCKED, "docked");
            helper.assertValueEqual(lift.dockedVehicle(), car, "the lift's vehicle");
            // Flat on the deck: the wall behind its tail must not lift or tilt the drawn body (Rusty saw a docked car float).
            var held = car.suspension(1.0f);
            helper.assertTrue(held.lift() == 0.0 && held.pitch() == 0.0 && held.roll() == 0.0,
                    "drawn flat on the deck: lift " + held.lift() + " pitch " + held.pitch() + " roll " + held.roll());
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public void aVehicleBackedInIsTurnedToFaceTheFrontOnALiftFacingEast(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, EAST_CONTROLLER, Direction.EAST).consumesAction(), "placed facing east");
        LiftBlockEntity lift = lift(helper, EAST_CONTROLLER);
        // The deck runs west from the controller: x 6..13, z 9..13. The front looks east (yaw -90); backed in looks west.
        Vehicle car = stand(helper, BOX_CAR, new Vec3(10.4, DECK, 11.9), 90.0f);
        Vec3 spot = lift.spot(bodyLength(helper, BOX_CAR));
        helper.runAtTickTime(Docking.REST + 2 + LiftMotion.JOB + 2, () -> {
            helper.assertTrue(car.docked(), "taken");
            helper.assertTrue(car.position().distanceTo(spot) < 0.01, "on its spot: " + car.position() + " vs " + spot);
            helper.assertValueEqual(car.getYRot(), Footprint.Heading.EAST.yaw(), "spun half a turn to face the front");
            // Centred (D-0043): the nose as far from the front edge (the controller's east face) as the tail from the
            // wall's face, which is BAR_MIN into the deck's last cell to the west.
            double front = helper.absolutePos(EAST_CONTROLLER).getX() + 1;
            double wall = front - lift.footprint().wallFace();
            double half = bodyLength(helper, BOX_CAR) / 2;
            double noseMargin = front - (car.getX() + half), tailMargin = (car.getX() - half) - wall;
            helper.assertValueEqual(Math.round(noseMargin * 1000) / 1000.0, Math.round(tailMargin * 1000) / 1000.0, "the same margin at the nose and the tail");
            helper.assertTrue(tailMargin >= Docking.GAP, "at least the gap off the wall: " + tailMargin);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void aDriverAboardKeepsTheLiftWaitingUntilTheyStepOut(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        Vehicle car = stand(helper, BOX_CAR, new Vec3(6.5, DECK, 5.5), 180.0f);
        ServerPlayer driver = player(helper, GameType.SURVIVAL);
        // Three ticks on the deck first, so the server has stood it on the ground as a drive onto the deck would.
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(car.onGround(), "stood on the deck");
            helper.assertTrue(driver.startRiding(car, true), "the driver boards");
            helper.assertValueEqual(car.getControllingPassenger(), driver, "at the wheel");
        });
        helper.runAtTickTime(3 + Docking.REST + 5, () -> {
            helper.assertTrue(!car.docked() && !lift.busy(), "not taken with a driver aboard");
            helper.assertValueEqual(lift.dockStatus(), LiftStatus.Dock.DRIVER, "step out");
            driver.stopRiding();
        });
        helper.runAtTickTime(3 + Docking.REST + 5 + Docking.REST + 3, () -> {
            helper.assertTrue(car.docked() && lift.busy(), "taken once they stepped out");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public void aHeldVehicleIgnoresTheStickAndTheThrottleLetsItGo(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        Vehicle car = parked(helper, lift);
        Vec3 spot = car.position();
        car.setScriptedInput(STICK_RIGHT);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(car.docked(), "still docked with the stick held");
            helper.assertTrue(car.position().distanceTo(spot) < 0.001, "not an inch: " + car.position());
            helper.assertValueEqual(car.getYRot(), Footprint.Heading.NORTH.yaw(), "not a degree");
            helper.assertTrue(!lift.busy(), "a parked car needs no job");
            car.setScriptedInput(GAS);
        });
        helper.runAtTickTime(22, () -> helper.assertTrue(!car.docked(), "the throttle let it go"));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(car.getZ() < spot.z - 0.5, "driven off toward the front: " + car.position() + " from " + spot);
            helper.assertTrue(!car.docked(), "and not taken back while it moves");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public void aSecondVehicleOnATakenDeckIsLeftAlone(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        Vehicle car = parked(helper, lift);
        // On the deck's front-left corner, clear of the docked car.
        Vehicle other = stand(helper, BOX_CAR, new Vec3(5.9, DECK, 4.0), 90.0f);
        Vec3 start = other.position();
        helper.runAtTickTime(Docking.REST + LiftMotion.JOB + 5, () -> {
            helper.assertTrue(!other.docked(), "the deck is the first car's");
            helper.assertTrue(other.position().distanceTo(start) < 0.05, "left where it stood: " + other.position());
            helper.assertValueEqual(other.getYRot(), 90.0f, "and as it stood");
            helper.assertTrue(car.docked() && car.position().distanceTo(lift.spot(bodyLength(helper, BOX_CAR))) < 0.01, "the first car holds its spot");
            helper.assertTrue(!lift.busy(), "no job ran");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aDockedVehicleItsPlayersClientTakesOffTheSpotIsLetGoOnTheServer(GameTestHelper helper) {
        // A pilot's client lets go of the lift on the climb alone and flies off; the server, which a
        // player's vehicle only mirrors, heard of no throttle and kept it docked (D-0046).
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.CREATIVE);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftMenu menu = open(helper, sp, CONTROLLER);
        load(menu, 1, 4, 1, 0);
        helper.assertTrue(menu.clickMenuButton(sp, LiftMenu.BUILD_BUTTON), "built");
        sp.closeContainer();
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        Vehicle car = lift.dockedVehicle();
        helper.assertTrue(car != null, "born docked");
        helper.runAtTickTime(LiftMotion.JOB + 2, () -> {
            helper.assertTrue(sp.startRiding(car, true) && car.getControllingPassenger() == sp, "the player takes the wheel");
            helper.assertTrue(car.docked(), "still docked with the player aboard");
            // What the player's client reports as it rises off the deck: a move, and no throttle.
            car.absMoveTo(car.getX(), car.getY() + 3.0, car.getZ(), car.getYRot(), car.getXRot());
        });
        helper.runAtTickTime(LiftMotion.JOB + 5, () -> {
            helper.assertTrue(!car.docked(), "let go once off its spot");
            helper.assertTrue(lift.dockedVehicle() == null, "the lift holds nothing");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aBodyWiderThanTheDeckIsBuiltThroughTheLiftsOwnPostsButNotThroughAnythingElse(GameTestHelper helper) {
        // The Chinook could not be built from 1.17.0 on: its spot runs through the front posts (D-0046).
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.CREATIVE);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        LiftMenu menu = open(helper, sp, CONTROLLER);
        menu.getSlot(LiftMenu.CHASSIS).set(ModContent.chassisStack(BOX_WIDE));
        menu.broadcastChanges();
        VehicleProfile wide = lift.profile(BOX_WIDE);
        helper.assertTrue(wide != null && wide.body().width() > 3.7, "the wide box is wider than the deck between its posts");
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.READY, "the lift's own posts do not refuse it");
        BlockPos ahead = new BlockPos(7, FLOOR + 1, 1);
        helper.setBlock(ahead, Blocks.STONE);
        menu.broadcastChanges();
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.OCCUPIED, "a block in its spot off the lift still does");
        helper.setBlock(ahead, Blocks.AIR);
        menu.broadcastChanges();
        helper.assertTrue(menu.clickMenuButton(sp, LiftMenu.BUILD_BUTTON), "built");
        helper.assertValueEqual(helper.getLevel().getEntitiesOfClass(Vehicle.class, lift.deckBox()).size(), 1, "one wide box on the deck");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void aBodyTooLongForTheDeckIsNeverTaken(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        helper.assertTrue(bodyLength(helper, BOX_LONG) > Docking.longest(lift.footprint()), "the long box is longer than the lift takes");
        Vehicle bus = stand(helper, BOX_LONG, new Vec3(7.5, DECK, 6.0), 180.0f);
        Vec3 start = bus.position();
        helper.assertValueEqual(lift.dockStatus(), LiftStatus.Dock.TOO_BIG, "too big");
        helper.runAtTickTime(Docking.REST + LiftMotion.JOB + 5, () -> {
            helper.assertTrue(!bus.docked() && !lift.busy(), "never taken");
            helper.assertTrue(bus.position().distanceTo(start) < 0.05, "left where it stood");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160)
    public void aTowingVehicleWaitsAndOneWhoseSpotIsBlockedWaitsAndBothAreTakenOnceFree(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        // The car facing the front with the trailer hitched close behind it, both on the deck and both across the
        // middle where the two spots lie (the car's z 5.16..8.16, the trailer's 4.91..8.41): each stands where the
        // other's spot is, so neither is clear.
        Vehicle car = stand(helper, BOX_CAR, new Vec3(7.5, DECK, 5.0), 180.0f);
        Vehicle trailer = stand(helper, BOX_TRAILER, new Vec3(7.5, DECK, 8.3), 180.0f);
        car.hitch(trailer);
        helper.assertValueEqual(lift.dockStatus(), LiftStatus.Dock.TOWING, "unhitch first");
        helper.runAtTickTime(Docking.REST + 5, () -> {
            helper.assertTrue(!car.docked() && !trailer.docked() && !lift.busy(), "neither is taken while hitched");
            trailer.unhitch();
        });
        helper.runAtTickTime(Docking.REST + 5 + Docking.REST + 5, () -> {
            // Unhitched, the trailer stands where the car's spot is: the car is blocked, and the trailer, standing
            // where its own spot is not, is blocked by the car in turn; nothing moves.
            helper.assertTrue(!car.docked() && !trailer.docked() && !lift.busy(), "nothing is taken while the other stands in the way");
            helper.assertTrue(lift.dockStatus() == LiftStatus.Dock.BLOCKED, "blocked: " + lift.dockStatus());
            trailer.discard();
        });
        helper.runAtTickTime(Docking.REST + 5 + Docking.REST + 5 + Docking.REST + 3 + LiftMotion.JOB, () -> {
            helper.assertTrue(car.docked(), "the car is taken once the trailer is gone");
            helper.assertTrue(car.position().distanceTo(lift.spot(bodyLength(helper, BOX_CAR))) < 0.01, "to its spot");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void aTrailerAloneDocksLikeACar(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        Vehicle trailer = stand(helper, BOX_TRAILER, new Vec3(6.8, DECK, 5.0), 160.0f);
        helper.runAtTickTime(Docking.REST + 2 + LiftMotion.JOB + 2, () -> {
            helper.assertTrue(trailer.docked(), "taken");
            helper.assertTrue(trailer.position().distanceTo(lift.spot(bodyLength(helper, BOX_TRAILER))) < 0.01, "on its own spot, by its own length");
            helper.assertValueEqual(trailer.getYRot(), Footprint.Heading.NORTH.yaw(), "facing the front");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void breakingTheLiftFreesTheDockedVehicleAndASaveKeepsItDocked(GameTestHelper helper) {
        layFloor(helper);
        ServerPlayer sp = player(helper, GameType.SURVIVAL);
        helper.assertTrue(place(helper, sp, CONTROLLER, Direction.NORTH).consumesAction(), "placed");
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        Vehicle car = parked(helper, lift);
        CompoundTag tag = new CompoundTag();
        car.saveWithoutId(tag);
        Vehicle again = Vehicle.create(helper.getLevel(), BOX_CAR, car.position(), 180.0f);
        again.load(tag);
        helper.assertTrue(again.docked(), "loaded docked");
        helper.assertValueEqual(again.dockedAt(), helper.absolutePos(CONTROLLER), "on its lift");
        helper.setBlock(CONTROLLER, Blocks.AIR);
        helper.runAtTickTime(3, () -> {
            helper.assertValueEqual(liftBlocks(helper), 0, "the lift is gone");
            helper.assertTrue(!car.docked(), "the car is free");
            helper.assertTrue(car.dockedAt() == null, "and belongs to no lift");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public void aShortLiftCentresByItsOwnWallABlockNearerThanALongOne(GameTestHelper helper) {
        layFloor(helper);
        // A six-long lift whole, as the box's garage lifts are: laid by commands, every part flagged short.
        Footprint old = Footprint.SHORT;
        for (int i = old.parts(); i >= 1; i--) {
            helper.setBlock(LiftControllerBlock.at(CONTROLLER, Direction.NORTH, old.cell(i)),
                    ModContent.LIFT_PART.get().defaultBlockState().setValue(LiftPartBlock.FACING, Direction.NORTH).setValue(LiftPartBlock.INDEX, i).setValue(LiftPartBlock.LONG, false));
        }
        helper.setBlock(CONTROLLER, ModContent.LIFT_CONTROLLER.get().defaultBlockState().setValue(LiftControllerBlock.FACING, Direction.NORTH).setValue(LiftControllerBlock.LONG, false));
        LiftBlockEntity lift = lift(helper, CONTROLLER);
        helper.assertValueEqual(lift.footprint(), old, "six long");
        helper.assertValueEqual(lift.deckBox().maxZ - helper.absolutePos(CONTROLLER).getZ(), 6.0, "its deck box ends at the sixth row");
        Vehicle car = stand(helper, BOX_CAR, new Vec3(7.5, DECK, 5.0), 180.0f);
        Vec3 spot = lift.spot(bodyLength(helper, BOX_CAR));
        double depth = spot.z - helper.absolutePos(CONTROLLER).getZ();
        helper.assertValueEqual(Math.round(depth * 1e4) / 1e4, Math.round(Docking.depth(old, bodyLength(helper, BOX_CAR)) * 1e4) / 1e4, "the spot in the short deck's middle");
        helper.assertValueEqual(Math.round((Docking.depth(Footprint.LONG, bodyLength(helper, BOX_CAR)) - depth) * 1e4) / 1e4, 1.0, "a block nearer than on a long one: half the two rows");
        helper.assertTrue(bodyLength(helper, BOX_LONG) > Docking.longest(old), "the long box fits neither");
        helper.runAtTickTime(Docking.REST + 2 + LiftMotion.JOB + 2, () -> {
            helper.assertTrue(car.docked(), "taken");
            helper.assertTrue(car.position().distanceTo(spot) < 0.01, "on the short deck's spot: " + car.position() + " vs " + spot);
            helper.assertValueEqual(car.getYRot(), Footprint.Heading.NORTH.yaw(), "facing the front");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aLiftFromBeforeTheWallGrowsOneOnLoadWhereItsCellsAreFreeAndStaysSixLong(GameTestHelper helper) {
        layFloor(helper);
        // Laid by commands as 1.16.0 placed it: the controller and parts 1..33 in the short shape's order, no wall and no
        // shape flag, as every lift saved before 1.18.0 reads. Stone stands in the wall's middle cell.
        Footprint old = Footprint.SHORT;
        BlockPos stone = LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(0, 1, 5));
        helper.setBlock(stone, Blocks.STONE);
        for (int i = 33; i >= 1; i--) {
            helper.setBlock(LiftControllerBlock.at(CONTROLLER, Direction.NORTH, old.cell(i)),
                    ModContent.LIFT_PART.get().defaultBlockState().setValue(LiftPartBlock.FACING, Direction.NORTH).setValue(LiftPartBlock.INDEX, i));
        }
        helper.setBlock(CONTROLLER, ModContent.LIFT_CONTROLLER.get().defaultBlockState().setValue(LiftControllerBlock.FACING, Direction.NORTH));
        helper.assertValueEqual(liftBlocks(helper), 34, "the old lift, whole");
        helper.assertValueEqual(lift(helper, CONTROLLER).footprint(), old, "it keeps the shape it was laid as (D-0042)");
        helper.runAtTickTime(2, () -> {
            for (int right : new int[] {-1, 1}) {
                BlockPos at = LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(right, 1, 5));
                BlockState s = helper.getBlockState(at);
                helper.assertTrue(s.is(ModContent.LIFT_PART.get()), "a wall cell grew at " + right + ": " + s);
                helper.assertValueEqual(s.getValue(LiftPartBlock.INDEX), old.indexOf(new Cell(right, 1, 5)), "with its index");
                helper.assertValueEqual(s.getValue(LiftPartBlock.FACING), Direction.NORTH, "facing the lift's way");
                helper.assertTrue(!s.getValue(LiftPartBlock.LONG), "in the short shape");
            }
            helper.assertTrue(helper.getBlockState(stone).is(Blocks.STONE), "the stone is left to itself");
            helper.assertValueEqual(liftBlocks(helper), 36, "two cells grew");
            for (int back = 6; back <= 7; back++) {
                BlockPos row = LiftControllerBlock.at(CONTROLLER, Direction.NORTH, new Cell(0, 0, back));
                helper.assertTrue(helper.getBlockState(row).isAir(), "no deck grew past the sixth row: " + helper.getBlockState(row));
            }
            helper.setBlock(CONTROLLER, Blocks.AIR);
        });
        helper.runAtTickTime(4, () -> {
            helper.assertValueEqual(liftBlocks(helper), 0, "the grown cells go with the lift");
            helper.succeed();
        });
    }
}
