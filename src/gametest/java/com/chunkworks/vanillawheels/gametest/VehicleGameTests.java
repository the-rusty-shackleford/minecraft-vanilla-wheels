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
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        Player passenger = helper.makeMockPlayer(GameType.SURVIVAL);
        Player third = helper.makeMockPlayer(GameType.SURVIVAL);
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
            helper.assertTrue(v.suspension(1.0f).isSettled(), "the body has settled onto the box: " + v.suspension(1.0f) + " ground " + java.util.Arrays.toString(v.ground()) + " at " + v.position());
            helper.assertTrue(noseUp[0] < -Math.toRadians(15), "the body pitched nose-up on the way: " + Math.toDegrees(noseUp[0]) + " degrees");
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

    @GameTest(template = "runway", timeoutTicks = 100)
    public void theChestSpillsOnAWrenchAndTheItemKeepsPaintAndFuel(GameTestHelper helper) {
        layFloor(helper);
        Vehicle v = car(helper, 4.5, 7.5, true);
        helper.assertValueEqual(v.getContainerSize(), 27, "three rows");
        v.setItem(0, new ItemStack(Items.APPLE, 7));
        v.setItem(26, new ItemStack(Items.STICK, 3));
        v.setPaint(net.minecraft.world.item.DyeColor.RED);
        int fuel = v.tank().ticks();
        Player p = helper.makeMockPlayer(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.WRENCH.get()));
        p.setShiftKeyDown(true);
        v.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(v.isRemoved(), "the vehicle is gone");
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
            List<ItemEntity> spilled = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4.0));
            int apples = spilled.stream().filter(e -> e.getItem().is(Items.APPLE)).mapToInt(e -> e.getItem().getCount()).sum();
            int sticks = spilled.stream().filter(e -> e.getItem().is(Items.STICK)).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertValueEqual(apples, 7, "the apples spilled");
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
}
