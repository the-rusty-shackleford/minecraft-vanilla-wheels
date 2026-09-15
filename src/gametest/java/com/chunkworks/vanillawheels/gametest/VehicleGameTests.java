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
import com.chunkworks.vanillawheels.VanillaWheelsMod;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Impact;
import com.chunkworks.vanillawheels.domain.Input;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The protocol on a real server, driving the box car the test mod ships:
 * a profile makes a sized, seated vehicle; the server drives it by a
 * scripted input, forward to a distance and to a stop; it climbs a
 * two-block step and ends level on top; it runs a cow over at speed and
 * not at a walk; a coal fills the tank and an empty tank refuses the
 * throttle; the chest survives a wrench and a placing; a disc goes in and
 * out; the headlights cycle and light up at night; the profile round-trips
 * through its codec.
 *
 * <p>The runway template is 48 blocks long; a floor of dirt is laid on it.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VehicleGameTests {
    private static final int LENGTH = 48;
    private static final int WIDTH = 15;
    private static final int FLOOR = 4;
    private static final ResourceLocation BOX_CAR = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_car");
    private static final Input GAS = new Input(1, 0, false, true, true);

    public VehicleGameTests() {}

    private static void layFloor(GameTestHelper helper) {
        for (int x = 0; x < LENGTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                for (int y = 0; y < FLOOR; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }
    }

    /** A box car at {@code (x, z)} on the floor, facing +x (east: yaw -90), fuelled, unless {@code fuel} is false. */
    private static Vehicle car(GameTestHelper helper, double x, double z, boolean fuel) {
        Vec3 at = helper.absoluteVec(new Vec3(x, FLOOR, z));
        Vehicle v = Vehicle.create(helper.getLevel(), BOX_CAR, at, -90.0f);
        helper.assertTrue(v != null, "the box car profile is registered");
        if (fuel) {
            ItemStack coals = new ItemStack(Items.COAL, 5);
            Player p = helper.makeMockPlayer(GameType.SURVIVAL);
            p.setItemInHand(InteractionHand.MAIN_HAND, coals);
            for (int i = 0; i < 5; i++) {
                v.interact(p, InteractionHand.MAIN_HAND);
            }
        }
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    /**
     * effects: returns a mock player standing at {@code v}, for boarding it. A mock player is
     * made at the world's origin, and a riding player's pickup sweep is the box round itself
     * and its vehicle together: a rider still at the origin on its first tick aboard sweeps
     * the millions of blocks between, every test's runway included, and took the spill test's
     * apples whenever the grid fell on the origin's side of its car.
     */
    private static Player riderAt(GameTestHelper helper, Vehicle v) {
        return riderAt(helper, v, GameType.SURVIVAL);
    }

    private static Player riderAt(GameTestHelper helper, Vehicle v, GameType mode) {
        Player p = helper.makeMockPlayer(mode);
        mode.updatePlayerAbilities(p.getAbilities());   // a mock player's mode sets its answers, not its abilities; the game sets both
        p.setPos(v.getX(), v.getY(), v.getZ());
        return p;
    }

    @GameTest(template = "runway", timeoutTicks = 100)
    public void aProfileMakesASizedSeatedNamedVehicle(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 4.5, 7.5, false);
        VehicleProfile p = v.profile();
        helper.assertTrue(p != null && p.isPowered(), "the profile is there and has an engine");
        helper.assertTrue(Math.abs(v.getBbWidth() - 1.5f) < 1e-5 && Math.abs(v.getBbHeight() - 1.6f) < 1e-5, "sized from the profile: " + v.getBbWidth() + " x " + v.getBbHeight());
        helper.assertTrue(v.getParts().length == Vehicle.MAX_PARTS, "the fixed parts exist");
        helper.assertTrue(v.getParts()[0].getBbWidth() > 1.0f && v.getParts()[2].getBbWidth() < 0.1f, "two parts configured, the rest specks");
        helper.assertValueEqual(v.getName().getString(), "Box Car", "named by its profile");
        Player driver = riderAt(helper, v);
        Player passenger = riderAt(helper, v);
        Player third = riderAt(helper, v);
        helper.assertTrue(driver.startRiding(v), "the first rider boards");
        helper.assertTrue(v.getControllingPassenger() == driver, "and takes the wheel");
        helper.assertTrue(passenger.startRiding(v), "the second boards");
        helper.assertFalse(third.startRiding(v), "two seats, no third");
        helper.assertTrue(Math.abs(v.tuning().wheelBase() - 2.0) < 1e-9, "the wheelbase from the wheels: " + v.tuning().wheelBase());
        helper.succeed();
    }

    @GameTest(template = "runway", timeoutTicks = 200)
    public void theServerDrivesAScriptedCarForwardAndItCoastsToAStop(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 4.5, 7.5, true);
        double x0 = v.getX();
        v.setScriptedInput(GAS);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(v.speed() > 0.3, "up to speed after 40 ticks: " + v.speed());
            helper.assertTrue(v.getX() - x0 > 8.0, "and some way east: " + (v.getX() - x0));
            helper.assertTrue(Math.abs(v.getY() - helper.absoluteVec(new Vec3(0, FLOOR, 0)).y) < 0.1, "on the floor");
            helper.assertTrue(v.tank().ticks() < 8000, "the tank burned: " + v.tank().ticks());
            v.setScriptedInput(null);
        });
        helper.runAtTickTime(180, () -> {
            helper.assertTrue(v.speed() < 0.02, "coasted to a stop: " + v.speed());
            helper.assertTrue(v.getX() - x0 < LENGTH - 3, "and did not leave the runway: " + (v.getX() - x0));
            helper.succeed();
        });
    }

    @GameTest(template = "runway", timeoutTicks = 200)
    public void theCarClimbsATwoBlockStepAndEndsLevelOnTop(GameTestHelper helper) {
        layFloor(helper);
        // A two-block-high shelf from x = 20 to the end.
        for (int x = 20; x < LENGTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                helper.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.STONE);
            }
        }
        Vehicle v = car(helper, 4.5, 7.5, true);
        double floorY = helper.absoluteVec(new Vec3(0, FLOOR, 0)).y;
        // The body should cant: nose up while the front wheels are on the shelf and the rear ones below.
        double[] noseUp = {0.0};
        helper.onEachTick(() -> noseUp[0] = Math.min(noseUp[0], v.suspension(1.0f).pitch()));
        v.setScriptedInput(GAS);
        // Brake once it is up, so it comes to rest on the shelf rather than fifty blocks on.
        helper.runAtTickTime(55, () -> {
            helper.assertTrue(v.getX() > helper.absoluteVec(new Vec3(22, 0, 0)).x, "up the step by now: " + (v.getX() - helper.absoluteVec(new Vec3(0, 0, 0)).x));
            v.setScriptedInput(new Input(-1, 0, false, true, true));
        });
        helper.runAtTickTime(80, () -> v.setScriptedInput(null));
        helper.runAtTickTime(150, () -> {
            helper.assertTrue(v.getX() > helper.absoluteVec(new Vec3(24, 0, 0)).x, "past the step: " + (v.getX() - helper.absoluteVec(new Vec3(0, 0, 0)).x));
            helper.assertTrue(Math.abs(v.getY() - (floorY + 2.0)) < 0.1, "standing two blocks higher: " + (v.getY() - floorY));
            helper.assertTrue(v.suspension(1.0f).isSettled(), "the body has settled onto the box: " + v.suspension(1.0f) + " at " + v.position());
            // The plane fit spreads one riser over the whole footprint and the springs ease into it: a
            // clear cant, not the old per-axle nod.
            helper.assertTrue(noseUp[0] < -Math.toRadians(8), "the body pitched nose-up on the way: " + Math.toDegrees(noseUp[0]) + " degrees");
            helper.succeed();
        });
    }

    @GameTest(template = "runway", timeoutTicks = 200)
    public void runningACowOverHurtsAndShovesItAtSpeedAndNotAtAWalk(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 4.5, 7.5, true);
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "a cow");
        Vec3 at = helper.absoluteVec(new Vec3(30.5, FLOOR, 7.5));
        cow.setPos(at.x, at.y, at.z);
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        float health = cow.getHealth();
        // A mob with no AI is never moved by the game (its travel is skipped),
        // so the shove is read as the impulse it was given, not as distance.
        double[] shove = {0.0};
        helper.onEachTick(() -> shove[0] = Math.max(shove[0], cow.getDeltaMovement().x));
        v.setScriptedInput(GAS);
        helper.runAtTickTime(90, () -> {
            helper.assertTrue(cow.getHealth() < health, "the cow was hurt: " + cow.getHealth() + " of " + health);
            float lost = health - cow.getHealth();
            helper.assertTrue(lost >= Impact.damage(0.5, 0.9, 1.0) && lost <= Impact.damage(0.9, 0.9, 1.0) + 0.01f, "by the speed: lost " + lost);
            helper.assertTrue(shove[0] > Impact.knockback(0.5, 0.9) * 0.5, "shoved down the runway: " + shove[0]);
            helper.assertTrue(v.speed() < 0.9, "the car lost some speed to the cow: " + v.speed());
            helper.succeed();
        });
    }

    @GameTest(template = "runway", timeoutTicks = 100)
    public void aWalkingPaceHurtsNothing(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 4.5, 7.5, true);
        Cow cow = EntityType.COW.create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(8.5, FLOOR, 7.5));
        cow.setPos(at.x, at.y, at.z);
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        float health = cow.getHealth();
        // Two ticks of gas, then coast: the car creeps into the cow well under the pace.
        v.setScriptedInput(GAS);
        helper.runAtTickTime(3, () -> v.setScriptedInput(null));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(v.speed() < Impact.PACE, "creeping: " + v.speed());
            helper.assertValueEqual(cow.getHealth(), health, "the cow's health");
            helper.succeed();
        });
    }

    @GameTest(template = "runway", timeoutTicks = 100)
    public void aCoalFillsTheTankAndAnEmptyTankRefusesTheThrottle(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 4.5, 7.5, false);
        helper.assertTrue(!v.tank().hasFuel(), "born empty");
        v.setScriptedInput(GAS);
        helper.runAtTickTime(20, () -> {
            helper.assertValueEqual(v.speed(), 0.0f, "no fuel, no motion");
            Player p = helper.makeMockPlayer(GameType.SURVIVAL);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 2));
            v.interact(p, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(v.tank().ticks(), 1600, "one coal's burn in the tank");
            helper.assertValueEqual(p.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1, "one coal spent");
            helper.assertTrue(Math.abs(v.fuelFraction() - 1600.0 / 24000.0) < 1e-6, "the gauge reads it: " + v.fuelFraction());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
            v.interact(p, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(v.tank().ticks(), 1600 + 20000, "a lava bucket fits");
            helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET), "and leaves its bucket");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL_BLOCK));
            v.interact(p, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(v.tank().ticks(), 21600, "a coal block (16000) does not fit and is refused");
            helper.assertValueEqual(p.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1, "the block stays in the hand");
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(v.speed() > 0.2, "fuelled, it drives: " + v.speed());
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aCreativeDriverNeedsNoFuelAndBurnsNone(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 7.5, 7.5, false);
        helper.assertTrue(!v.tank().hasFuel(), "born empty");
        Player creative = riderAt(helper, v, GameType.CREATIVE);
        helper.assertTrue(creative.startRiding(v, true), "a creative driver boards");
        helper.assertTrue(creative.hasInfiniteMaterials(), "and has the game's infinite materials");
        helper.assertTrue(!v.fuelRequired(), "fuel is not required of them");
        helper.assertTrue(v.hasFuel(), "the engine may run on the empty tank");
        // Their client reports the throttle down; the server burns nothing for it.
        v.setFuel(100);
        v.onDriveState(0.5f, 0.0f, 1, false, 0.0f);
        helper.runAtTickTime(10, () -> {
            helper.assertValueEqual(v.tank().ticks(), 100, "nothing burned under a creative driver");
            creative.stopRiding();
            Player survival = riderAt(helper, v);
            helper.assertTrue(survival.startRiding(v, true), "a survival driver boards");
            helper.assertTrue(v.fuelRequired(), "fuel is required of them");
            v.onDriveState(0.5f, 0.0f, 1, false, 0.0f);
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(v.tank().ticks() < 100, "the survival driver's throttle burns: " + v.tank().ticks());
            helper.succeed();
        });
    }

    @GameTest(template = "runway", timeoutTicks = 100)
    public void theChestSpillsOnAWrenchAndTheItemKeepsPaintAndFuel(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 4.5, 7.5, true);
        helper.assertValueEqual(v.getContainerSize(), 54, "two chests of three rows");
        v.setItem(0, new ItemStack(Items.APPLE, 7));
        v.setItem(26, new ItemStack(Items.STICK, 3));
        v.setPaint(net.minecraft.world.item.DyeColor.RED);
        int fuel = v.tank().ticks();
        Player p = helper.makeMockPlayer(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.WRENCH.get()));
        p.setShiftKeyDown(true);
        v.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(v.isRemoved(), "the vehicle is gone");
        int atOnce = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4.0)).stream().filter(e -> e.getItem().is(Items.APPLE)).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertValueEqual(atOnce, 7, "the apples spilled at once");
        ItemStack held = null;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(ModContent.VEHICLE_ITEM.get())) {
                held = p.getInventory().getItem(i);
            }
        }
        helper.assertTrue(held != null, "the player holds the vehicle item");
        helper.assertValueEqual(held.get(ModContent.PAINT.get()), net.minecraft.world.item.DyeColor.RED, "its paint");
        helper.assertValueEqual(held.get(ModContent.FUEL.get()), fuel, "its fuel");
        helper.assertValueEqual(VanillaWheels.vehicleOf(held).orElse(null), BOX_CAR, "its vehicle");
        helper.runAtTickTime(5, () -> {
            // Five ticks on, they are still here.
            List<ItemEntity> spilled = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4.0));
            int apples = spilled.stream().filter(e -> e.getItem().is(Items.APPLE)).mapToInt(e -> e.getItem().getCount()).sum();
            int sticks = spilled.stream().filter(e -> e.getItem().is(Items.STICK)).mapToInt(e -> e.getItem().getCount()).sum();
            // Two takers once had them by now: a lift test's sixteen-block sweep (sweepLiftItems) and
            // another test's mock rider still at the world's origin on its first tick aboard (riderAt).
            helper.assertValueEqual(apples, 7, "the apples are still here (sticks " + sticks + ")");
            helper.assertValueEqual(sticks, 3, "the sticks spilled");
            helper.succeed();
        });
    }

    @GameTest(template = "runway", timeoutTicks = 100)
    public void aDiscGoesIntoTheRadioAndComesOutOfIt(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 4.5, 7.5, false);
        Player p = helper.makeMockPlayer(GameType.SURVIVAL);
        p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MUSIC_DISC_CAT));
        v.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(v.disc().is(Items.MUSIC_DISC_CAT), "the disc is in");
        helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "and out of the hand");
        helper.assertTrue(v.song().isPresent(), "the radio knows its song");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MUSIC_DISC_13));
        v.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(v.disc().is(Items.MUSIC_DISC_CAT), "one disc at a time");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        VehicleProfile prof = v.profile();
        Vec3 radio = v.rotate(prof.localBlocks(prof.radio().get().at()));
        v.interactAt(p, radio, InteractionHand.MAIN_HAND);
        helper.assertTrue(v.disc().isEmpty(), "ejected");
        helper.assertTrue(p.getInventory().contains(new ItemStack(Items.MUSIC_DISC_CAT)), "back in the inventory");
        helper.succeed();
    }

    @GameTest(template = "runway", timeoutTicks = 120)
    public void headlightsCycleAndAutoLightsAtNight(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 4.5, 7.5, false);
        helper.assertValueEqual(v.lights(), Vehicle.Lights.OFF, "off to start");
        v.cycleLights();
        helper.assertValueEqual(v.lights(), Vehicle.Lights.ON, "then on");
        v.cycleLights();
        helper.assertValueEqual(v.lights(), Vehicle.Lights.AUTO, "then auto");
        helper.getLevel().setDayTime(18000L);
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(v.lit(), "auto lights at midnight");
            v.cycleLights();
            helper.assertValueEqual(v.lights(), Vehicle.Lights.OFF, "and round to off");
        });
        helper.runAtTickTime(90, () -> {
            helper.assertFalse(v.lit(), "off is off");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void theBoxCarProfileRoundTripsThroughItsCodec(GameTestHelper helper) {
        VehicleProfile p = VanillaWheels.profile(helper.getLevel().registryAccess(), BOX_CAR).orElseThrow().value();
        RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        com.google.gson.JsonElement json = VehicleProfile.CODEC.encodeStart(ops, p).getOrThrow();
        VehicleProfile back = VehicleProfile.CODEC.parse(ops, json).getOrThrow();
        helper.assertValueEqual(back, p, "the profile after a round trip");
        helper.assertTrue(json.getAsJsonObject().has("mesh") && json.getAsJsonObject().has("fuel"), "the JSON is flat: " + json.getAsJsonObject().keySet());
        helper.succeed();
    }

    @GameTest(template = "runway", timeoutTicks = 160)
    public void theNoseStopsAtAWallTheBoxNeverReaches(GameTestHelper helper) {
        layFloor(helper);
        // A wall three blocks high across the runway at x = 24: taller than the climb, and the box is a
        // square of the body's width, so without footprint collision the nose would end up inside it.
        for (int x = 24; x < 26; x++) {
            for (int z = 0; z < WIDTH; z++) {
                for (int y = FLOOR; y < FLOOR + 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        Vehicle v = car(helper, 4.5, 7.5, true);
        double half = v.profile().body().length() / 2.0;
        double wallX = helper.absoluteVec(new Vec3(24, 0, 0)).x;
        v.setScriptedInput(GAS);
        helper.runAtTickTime(120, () -> {
            double nose = v.getX() + half;
            helper.assertTrue(nose <= wallX + 0.01, "the nose stops at the wall's face: nose " + nose + " wall " + wallX);
            helper.assertTrue(nose > wallX - 1.0, "and close to it, not a block short: nose " + nose + " wall " + wallX);
            helper.assertTrue(Math.abs(v.speed()) < 0.05, "stalled against it, not spinning its wheels: " + v.speed());
            helper.succeed();
        });
    }

    @GameTest(template = "runway", timeoutTicks = 200)
    public void aWallMetAtASlantIsSlidAlongNotStoppedAt(GameTestHelper helper) {
        layFloor(helper);
        // A wall three high down the runway's north side from x = 10, and the car aimed twelve
        // degrees into it: the nose corner meets it first, and the body slides east along it.
        for (int x = 10; x < LENGTH; x++) {
            for (int y = 0; y < 3; y++) {
                helper.setBlock(new BlockPos(x, FLOOR + y, 3), Blocks.STONE);
            }
        }
        Vehicle v = car(helper, 4.5, 8.0, true);
        v.setYRot(-102.0f);
        v.yRotO = -102.0f;
        double x0 = v.getX();
        v.setScriptedInput(GAS);
        StringBuilder trace = new StringBuilder();
        int[] tick = {0};
        helper.onEachTick(() -> {
            if (tick[0]++ % 5 == 0) {
                trace.append(String.format(java.util.Locale.ROOT, " t%d:x%.1f,z%.2f,v%.2f,kept%.2f", tick[0], v.getX() - x0, v.getZ() - helper.absoluteVec(new Vec3(0, 0, 0)).z, v.speed(), v.moveKept()));
            }
        });
        // By eighty ticks it has slid some twenty-five blocks; by ninety the nose reaches the barrier
        // the framework stands at the runway's end.
        helper.runAtTickTime(80, () -> {
            double x = v.getX() - x0;
            helper.assertTrue(x > 20.0, "a long way east along the wall: " + x);
            helper.assertTrue(v.speed() > 0.15, "still under way against it: " + v.speed() + trace);
            helper.assertTrue(v.getZ() - helper.absoluteVec(new Vec3(0, 0, 4)).z > 0.6, "the body kept out of the wall: z = " + (v.getZ() - helper.absoluteVec(new Vec3(0, 0, 0)).z));
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void theServerSeatsARiderWithThePoseTheDriverShares(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 7.5, 7.5, true);
        Player p = riderAt(helper, v);
        p.startRiding(v, true);
        helper.runAtTickTime(5, () -> {
            double level = v.getPassengerRidingPosition(p).y;
            // The driver's client reports a nose-up, lifted pose; the server, which computes no pose for a
            // driven vehicle, seats the rider with it from the next tick.
            v.onPose(0.5f, (float) Math.toRadians(-20), 0.0f);
            helper.runAtTickTime(8, () -> {
                double lifted = v.getPassengerRidingPosition(p).y - level;
                helper.assertTrue(lifted > 0.45, "the seat rose with the shared lift and the nose-up pose: " + lifted);
                helper.assertTrue(Math.abs(v.suspension(1.0f).pitch() + Math.toRadians(20)) < 1e-4, "the server draws the shared pitch: " + v.suspension(1.0f).pitch());
                helper.succeed();
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aClickOnAChestOpensItAndAClickBesideItDoesNot(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 7.5, 7.5, true);
        VehicleProfile p = v.profile();
        helper.assertValueEqual(p.storage().orElseThrow().chests().size(), 2, "two chests");
        helper.assertValueEqual(p.storage().orElseThrow().firstSlot(1), 27, "the second chest's slice starts after the first's rows");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // The hit lands on the first chest's lid, in the world's frame relative to the body.
        VehicleProfile.Chest c = p.storage().orElseThrow().chests().get(0);
        Vec3 onChest = v.rotate(p.localBlocks(c.at())).add(0.0, VehicleProfile.Chest.HEIGHT * c.scale() / 2.0, 0.0);
        helper.assertTrue(v.interactAt(player, onChest, InteractionHand.MAIN_HAND).consumesAction(), "the chest takes the click");
        // From outside, the click lands on the hull's box over the chest, the player's eye above it
        // looking down: the ray runs on into the chest, and the chest takes it.
        Vec3 overChest = v.rotate(p.localBlocks(c.at())).add(0.0, p.body().height(), 0.0);
        Vec3 eyeAbove = v.position().add(overChest).add(0.0, 2.0, 0.0);
        player.setPos(eyeAbove.x, eyeAbove.y - player.getEyeHeight(), eyeAbove.z);
        helper.assertTrue(v.interactAt(player, overChest, InteractionHand.MAIN_HAND).consumesAction(), "a click on the hull over the chest, from above, opens it");
        // Beside it, on the bed between the chests, a plain click straight down is not a chest's.
        Vec3 beside = v.rotate(p.localBlocks(new com.chunkworks.vanillawheels.domain.Vec(0, 10, -14))).add(0.0, 0.2, 0.0);
        Vec3 eyeBeside = v.position().add(beside).add(0.0, 2.0, 0.0);
        player.setPos(eyeBeside.x, eyeBeside.y - player.getEyeHeight(), eyeBeside.z);
        helper.assertTrue(!v.interactAt(player, beside, InteractionHand.MAIN_HAND).consumesAction(), "between the chests the click passes to the body");
        helper.succeed();
    }
}
