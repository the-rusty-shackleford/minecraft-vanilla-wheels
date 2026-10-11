/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.gametest;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.VanillaWheelsMod;
import com.chunkworks.vanillawheels.domain.Sensing;
import com.chunkworks.vanillawheels.domain.Shutter;
import com.chunkworks.vanillawheels.garage.GarageDoorBlockEntity;
import com.chunkworks.vanillawheels.garage.HiddenDoorBlock;
import com.chunkworks.vanillawheels.garage.VehicleSensorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Vehicle Sensor (D-0035) on a real server.
 * Partitions: a vehicle just inside the range and just outside it, at the default range and a
 * range set by hand; over it a vehicle, a player in a boat, an empty boat, a cow, a ridden horse, a
 * player on foot and an item; a vehicle parked in range, then gone; a sensor set on with nothing
 * near; clicked with an empty hand, sneaking, and with a block in hand; a sensor under a Hidden
 * Door's sill and a car coming.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VehicleSensorGameTests {
    private static final ResourceLocation CAR = ResourceLocation.parse("vanillawheels_gametest:box_car");

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static boolean on(GameTestHelper h, BlockPos pos) {
        var state = h.getBlockState(pos);
        return state.is(ModContent.VEHICLE_SENSOR.get()) && state.getValue(VehicleSensorBlock.POWERED);
    }

    private static void sensor(GameTestHelper h, BlockPos pos, int range) {
        h.setBlock(pos, ModContent.VEHICLE_SENSOR.get().defaultBlockState().setValue(VehicleSensorBlock.RANGE, range));
    }

    /** effects: a box car facing south, centred at (x, z), standing on y {@code base} (held still): with
     * its hood and boot boxes it reaches 0.75 to each side, 1.75 behind its centre and 1.875 ahead */
    private static Vehicle car(GameTestHelper h, double x, int base, double z) {
        var v = Vehicle.create(h.getLevel(), CAR, h.absoluteVec(new Vec3(x, base, z)), 0);
        v.setNoGravity(true);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    /** Two sensors at the default range, 8, side by side in x; a car's boot reaching a quarter-block
     * into one's range, and a car's boot stopping a quarter-block short of the other's. Then a third
     * sensor set to 4 by hand, with a car's hood 6 blocks off. */
    @GameTest(template = "garage_arena", timeoutTicks = 60)
    public void aVehicleWithinRangeTurnsASensorOnAndOneJustOutsideDoesNot(GameTestHelper h) {
        floor(h);
        var near = new BlockPos(3, 1, 2);
        var far = new BlockPos(15, 1, 2);
        var shortRange = new BlockPos(9, 1, 22);
        sensor(h, near, Sensing.DEFAULT_RANGE);
        sensor(h, far, Sensing.DEFAULT_RANGE);
        sensor(h, shortRange, 4);
        // The near sensor's reach in z is -6..11; the car's boot, from 10.75, dips into it.
        car(h, 3.5, 2, 12.5);
        // The far sensor's reach in z is -6..11; the car's boot, from 11.25, stops short.
        car(h, 15.5, 2, 13.0);
        // The hood 6 blocks off a range of four (reach z 18..27): outside; at eight it would be in.
        car(h, 9.5, 2, 14.125);
        h.runAtTickTime(20, () -> {
            h.assertTrue(on(h, near), "a car just inside the range: on");
            h.assertFalse(on(h, far), "a car just outside the range: off");
            h.assertFalse(on(h, shortRange), "a car outside a range set to 4: off");
            h.succeed();
        });
    }

    @GameTest(template = "garage_arena", timeoutTicks = 80)
    public void onlyAVehicleOrSomethingCarryingAPlayerTurnsASensorOn(GameTestHelper h) {
        floor(h);
        var at = new BlockPos(10, 1, 10);
        sensor(h, at, 4);
        Cow cow = h.spawn(EntityType.COW, new BlockPos(10, 2, 12));
        cow.setNoAi(true);
        Horse horse = h.spawn(EntityType.HORSE, new BlockPos(12, 2, 10));
        horse.setNoAi(true);
        var rider = h.makeMockServerPlayerInLevel();
        rider.moveTo(h.absoluteVec(new Vec3(12.5, 2, 10.5)));
        rider.startRiding(horse, true);
        var walker = h.makeMockServerPlayerInLevel();
        walker.moveTo(h.absoluteVec(new Vec3(8.5, 2, 10.5)));
        var item = new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.IRON_INGOT));
        item.moveTo(h.absoluteVec(new Vec3(10.5, 2, 10.5)));
        item.setNoGravity(true);
        h.getLevel().addFreshEntity(item);
        Boat boat = h.spawn(EntityType.BOAT, new BlockPos(10, 2, 8));
        h.runAtTickTime(30, () -> {
            h.assertFalse(on(h, at), "a cow, a ridden horse, a player on foot, an item and an empty boat: off");
            walker.startRiding(boat, true);
        });
        h.runAtTickTime(40, () -> {
            h.assertTrue(on(h, at), "a player in a boat: on");
            walker.stopRiding();
        });
        h.runAtTickTime(60, () -> {
            h.assertFalse(on(h, at), "the boat empty again: off");
            var players = h.getLevel().getServer().getPlayerList();
            players.remove(walker);
            players.remove(rider);
            car(h, 10.5, 2, 13.5);
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(on(h, at), "a car: on");
            h.succeed();
        });
    }

    /** On while a car is parked in range; off within HOLD scans, half a second, of its going. */
    @GameTest(template = "garage_arena", timeoutTicks = 160)
    public void aSensorHoldsWhileAVehicleIsInRangeAndLetsGoHalfASecondAfterItLeaves(GameTestHelper h) {
        floor(h);
        var at = new BlockPos(10, 1, 10);
        sensor(h, at, Sensing.DEFAULT_RANGE);
        var parked = car(h, 10.5, 2, 15.5);
        for (int t : new int[] {10, 40, 80}) {
            h.runAtTickTime(t, () -> h.assertTrue(on(h, at), "on beside a parked car at tick " + h.getTick()));
        }
        h.runAtTickTime(90, parked::discard);
        h.runAtTickTime(97, () -> h.assertTrue(on(h, at), "still on just after the car has gone: it holds"));
        int last = 90 + Sensing.HOLD * Sensing.SCAN_PERIOD + Sensing.SCAN_PERIOD;
        h.runAtTickTime(last, () -> {
            h.assertFalse(on(h, at), "off once the hold has run out");
            h.succeed();
        });
    }

    /** A sensor set on by hand (a command, a copied build) with nothing near lets go. */
    @GameTest(template = "garage_arena", timeoutTicks = 40)
    public void aSensorSetOnWithNothingNearLetsGo(GameTestHelper h) {
        floor(h);
        var at = new BlockPos(10, 1, 10);
        h.setBlock(at, ModContent.VEHICLE_SENSOR.get().defaultBlockState().setValue(VehicleSensorBlock.POWERED, true));
        h.assertTrue(on(h, at), "set on");
        h.runAtTickTime(2 + Sensing.HOLD * Sensing.SCAN_PERIOD + Sensing.SCAN_PERIOD, () -> {
            h.assertFalse(on(h, at), "nothing near: off once the hold ran out");
            h.succeed();
        });
    }

    /** An empty hand steps the range up the ladder, sneaking down; a block in hand is laid against it. */
    @GameTest(template = "garage_arena", timeoutTicks = 20)
    public void clickingASensorStepsItsRangeAndABlockInHandIsLaidAgainstIt(GameTestHelper h) {
        floor(h);
        var at = new BlockPos(10, 2, 10);
        sensor(h, at, Sensing.DEFAULT_RANGE);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(new Vec3(10.5, 2, 12.5)));
        h.useBlock(at, player);
        h.assertTrue(h.getBlockState(at).getValue(VehicleSensorBlock.RANGE) == 12, "a click: 8 to 12");
        h.useBlock(at, player);
        h.assertTrue(h.getBlockState(at).getValue(VehicleSensorBlock.RANGE) == 16, "another: 12 to 16");
        player.setShiftKeyDown(true);
        h.useBlock(at, player);
        h.assertTrue(h.getBlockState(at).getValue(VehicleSensorBlock.RANGE) == 12, "sneaking: 16 to 12");
        player.setShiftKeyDown(false);
        var stone = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        var abs = h.absolutePos(at);
        h.useBlock(at, player, new BlockHitResult(Vec3.atCenterOf(abs).add(0, .5, 0), Direction.UP, abs, false));
        h.assertTrue(h.getBlockState(at).getValue(VehicleSensorBlock.RANGE) == 12, "a block in hand leaves the range alone");
        h.assertBlockPresent(Blocks.STONE, at.above());
        h.succeed();
    }

    /** A sensor under a Hidden Door's sill, its range 8, strongly powers the sill under the door's
     * middle panel, and the door opens for a car coming toward it and shuts when it has gone. */
    @GameTest(template = "garage_arena", timeoutTicks = 120)
    public void aSensorUnderTheSillOpensAHiddenDoorForACarComing(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer(GameType.CREATIVE);
        for (int y = 2; y <= 4; y++) for (int x = 8; x <= 10; x++) {
            var support = h.absolutePos(new BlockPos(x, y - 1, 12));
            var stack = new ItemStack(ModContent.HIDDEN_DOOR_ITEM.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            h.assertTrue(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false))).consumesAction(),
                    "the real item places panel " + x + "," + y);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // The sill under the door's middle panel is the floor at y 1; the sensor under it at y 0.
        sensor(h, new BlockPos(9, 0, 12), Sensing.DEFAULT_RANGE);
        final Vehicle[] car = new Vehicle[1];
        h.runAtTickTime(5, () -> {
            var root = (GarageDoorBlockEntity) h.getBlockEntity(new BlockPos(8, 2, 12));
            h.assertTrue(root.formed() && root.lift() == 0 && h.getBlockState(new BlockPos(8, 2, 12)).getBlock() instanceof HiddenDoorBlock,
                    "a shut three by three hidden door");
            car[0] = car(h, 9.5, 2, 5.5);
        });
        h.runAtTickTime(20, () -> {
            var root = (GarageDoorBlockEntity) h.getBlockEntity(new BlockPos(8, 2, 12));
            h.assertTrue(root.lift() == 3 * Shutter.UNITS, "a car's hood under five blocks off opened it within 15 ticks: " + root.lift());
            car[0].discard();
        });
        h.runAtTickTime(50, () -> {
            var root = (GarageDoorBlockEntity) h.getBlockEntity(new BlockPos(8, 2, 12));
            h.assertTrue(root.lift() == 0, "the car gone and the hold run out, it shut: " + root.lift());
            h.succeed();
        });
    }
}
