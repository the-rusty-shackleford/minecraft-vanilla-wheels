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

import com.chunkworks.vanillawheels.VanillaWheelsMod;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.domain.Input;
import com.chunkworks.vanillawheels.domain.Tow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Towing and cargo on a headless server, with the box car and the box
 * trailer: driving the car's hitch onto the trailer's tongue hitches it,
 * the trailer follows straight and through a turn with its tongue on the
 * hitch, a crouching click on the tongue lets go and the trailer rolls to
 * a stop; a lead loads animals through open doors up to the room (an
 * adult is a whole, a calf a half), refuses them through shut doors and
 * past the room, and a click on a door lets them out behind; the tow link
 * survives a save and a load.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TowGameTests {
    private static final int LENGTH = 48;
    private static final int WIDTH = 15;
    private static final int FLOOR = 4;
    private static final ResourceLocation BOX_CAR = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_car");
    private static final ResourceLocation BOX_TRAILER = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_trailer");
    private static final Input GAS = new Input(1, 0, false, true, true);
    private static final Input GAS_RIGHT = new Input(1, 1, false, true, true);

    public TowGameTests() {}

    private static void layFloor(GameTestHelper helper) {
        for (int x = 0; x < LENGTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                for (int y = 0; y < FLOOR; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }
    }

    private static Vehicle spawn(GameTestHelper helper, ResourceLocation id, double x, double z, float yaw) {
        Vehicle v = Vehicle.create(helper.getLevel(), id, helper.absoluteVec(new Vec3(x, FLOOR, z)), yaw);
        helper.assertTrue(v != null, id + " is registered");
        if (id.equals(BOX_CAR)) {
            v.setFuel(v.tank().capacity());
        }
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    /** The car facing east with the trailer behind it, its tongue a little short of the car's hitch. */
    private static Vehicle[] carAndTrailer(GameTestHelper helper) {
        Vehicle car = spawn(helper, BOX_CAR, 8.5, 7.5, -90.0f);
        // The car's hitch is 26 px behind its centre, the trailer's tongue 34 px ahead of its own.
        double gap = 0.3;
        Vehicle trailer = spawn(helper, BOX_TRAILER, 8.5 - 26 / 16.0 - 34 / 16.0 - gap, 7.5, -90.0f);
        return new Vehicle[] {car, trailer};
    }

    private static double yawGap(Vehicle a, Vehicle b) {
        return Math.abs(Math.toDegrees(Tow.wrap(Math.toRadians(a.getYRot() - b.getYRot()))));
    }

    private static Cow cow(GameTestHelper helper, double x, double z, boolean baby) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(x, FLOOR, z));
        cow.setPos(at.x, at.y, at.z);
        cow.setNoAi(true);
        cow.setBaby(baby);
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    @GameTest(template = "runway", timeoutTicks = 200)
    public void drivingTheHitchOntoTheTongueHitchesAndTheTrailerFollowsAndLetsGoOnAClick(GameTestHelper helper) {
        layFloor(helper);
        Vehicle[] pair = carAndTrailer(helper);
        Vehicle car = pair[0];
        Vehicle trailer = pair[1];
        double trailerX0 = trailer.getX();
        helper.assertTrue(trailer.tower() == null, "loose to begin with");
        car.setScriptedInput(GAS);
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(trailer.tower() == car, "hitched: " + trailer.tower());
            helper.assertTrue(car.trailer() == trailer, "and the car knows it");
            helper.assertTrue(trailer.getX() - trailerX0 > 5.0, "the trailer came along: " + (trailer.getX() - trailerX0));
            helper.assertTrue(yawGap(car, trailer) < 3.0, "straight behind: " + yawGap(car, trailer));
            helper.assertTrue(trailer.tongue().distanceTo(car.hitchPoint()) < 0.3, "the tongue is on the hitch: " + trailer.tongue().distanceTo(car.hitchPoint()));
            helper.assertTrue(trailer.speed() > 0.2, "its wheels turn with the car: " + trailer.speed());
            car.setScriptedInput(GAS_RIGHT);
        });
        helper.runAtTickTime(65, () -> {
            double gap = yawGap(car, trailer);
            helper.assertTrue(gap > 3.0 && gap < 60.0, "in the turn the trailer lags the car, and no more than the fold: " + gap);
            helper.assertTrue(trailer.tongue().distanceTo(car.hitchPoint()) < 0.3, "still on the hitch through the turn");
            car.setScriptedInput(null);
        });
        helper.runAtTickTime(170, () -> {
            helper.assertTrue(car.speed() < 0.02, "the car has stopped: " + car.speed());
            helper.assertTrue(trailer.tower() == car, "still hitched at rest");
            // A crouching, empty-handed click on the tongue lets go.
            Player p = helper.makeMockPlayer(GameType.SURVIVAL);
            p.setShiftKeyDown(true);
            Vec3 hit = trailer.tongue().subtract(trailer.position());
            InteractionResult r = trailer.interactAt(p, hit, InteractionHand.MAIN_HAND);
            helper.assertTrue(r.consumesAction(), "the click was taken: " + r);
            helper.assertTrue(trailer.tower() == null, "let go");
            helper.assertTrue(car.trailer() == null, "and the car knows");
            helper.succeed();
        });
    }

    @GameTest(template = "runway", timeoutTicks = 100)
    public void aLeadLoadsAnimalsThroughOpenDoorsUpToTheRoomAndADoorClickLetsThemOut(GameTestHelper helper) {
        layFloor(helper);
        Vehicle trailer = spawn(helper, BOX_TRAILER, 20.5, 7.5, -90.0f);
        Player p = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(16.5, FLOOR, 7.5));
        p.setPos(at.x, at.y, at.z);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        List<Cow> herd = new ArrayList<>();
        herd.add(cow(helper, 15.5, 5.5, false));
        herd.add(cow(helper, 15.5, 6.5, true));
        herd.add(cow(helper, 15.5, 8.5, true));
        herd.add(cow(helper, 15.5, 9.5, true));
        herd.add(cow(helper, 14.5, 7.5, false));
        for (Cow c : herd) {
            c.setLeashedTo(p, true);
        }
        // Shut doors: nobody boards.
        helper.assertTrue(!trailer.doorsOpen(), "the doors start shut");
        InteractionResult shut = trailer.interact(p, InteractionHand.MAIN_HAND);
        helper.assertTrue(shut.consumesAction(), "the lead click was taken: " + shut);
        helper.assertValueEqual(trailer.animals().size(), 0, "nobody boarded through shut doors");
        // A crouching, empty-handed click on a door opens it.
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setShiftKeyDown(true);
        Vec3 door = trailer.rotate(trailer.profile().localBlocks(trailer.profile().doors().get(0).hinge()));
        helper.assertTrue(trailer.interactAt(p, door, InteractionHand.MAIN_HAND).consumesAction(), "the door click was taken");
        helper.assertTrue(trailer.doorsOpen(), "the doors are open");
        // The lead again: an adult and two calves fill it; the third calf and the second adult stay on the lead.
        p.setShiftKeyDown(false);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        helper.assertTrue(trailer.interact(p, InteractionHand.MAIN_HAND).consumesAction(), "the lead click was taken");
        helper.assertValueEqual(trailer.animals().size(), 3, "an adult and two calves aboard");
        helper.assertValueEqual(trailer.cargo().adults(), 1, "one adult");
        helper.assertValueEqual(trailer.cargo().young(), 2, "two calves");
        helper.assertTrue(trailer.cargo().fraction() > 0.999, "full");
        int leashed = 0;
        for (Cow c : herd) {
            if (c.getLeashHolder() == p) {
                leashed++;
                helper.assertTrue(c.getVehicle() == null, "a leashed cow is not aboard");
            } else {
                helper.assertTrue(c.getVehicle() == trailer, "an unleashed cow is aboard");
            }
        }
        helper.assertValueEqual(leashed, 2, "two stayed on the lead");
        int leads = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(Items.LEAD)) {
                leads += s.getCount();
            }
        }
        helper.assertValueEqual(leads, 3 + 1, "three leads came back beside the one in hand");
        // Riders are put in their places by the next tick.
        helper.runAtTickTime(5, () -> {
            for (var c : trailer.animals()) {
                helper.assertTrue(c.position().distanceTo(trailer.position()) < 1.5, "aboard, near the body: " + c.position().distanceTo(trailer.position()));
            }
            // A crouching, empty-handed click on the open door with animals aboard lets them out behind.
            p.setShiftKeyDown(true);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(trailer.interactAt(p, door, InteractionHand.MAIN_HAND).consumesAction(), "the unload click was taken");
            helper.assertValueEqual(trailer.animals().size(), 0, "everyone is off");
            helper.assertTrue(trailer.doorsOpen(), "the doors stay open");
            for (Cow c : herd) {
                if (c.getLeashHolder() != p) {
                    helper.assertTrue(c.getVehicle() == null, "on the ground");
                    helper.assertTrue(c.getX() < trailer.getX() - 1.5, "behind the trailer, which faces east: " + (c.getX() - trailer.getX()));
                }
            }
            // Empty and open, a click shuts them.
            helper.assertTrue(trailer.interactAt(p, door, InteractionHand.MAIN_HAND).consumesAction(), "the shut click was taken");
            helper.assertTrue(!trailer.doorsOpen(), "shut again");
            helper.succeed();
        });
    }

    @GameTest(template = "runway", timeoutTicks = 100)
    public void theTowLinkSurvivesASaveAndALoad(GameTestHelper helper) {
        layFloor(helper);
        Vehicle[] pair = carAndTrailer(helper);
        Vehicle car = pair[0];
        Vehicle trailer = pair[1];
        car.setScriptedInput(GAS);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(trailer.tower() == car, "hitched");
            car.setScriptedInput(null);
            CompoundTag saved = trailer.saveWithoutId(new CompoundTag());
            helper.assertTrue(saved.hasUUID("Tower") && saved.getUUID("Tower").equals(car.getUUID()), "the tower is saved by UUID");
            // The trailer as it would come back from disk: a new entity with a new id, the link only by UUID.
            trailer.discard();
            Vehicle again = Vehicle.create(helper.getLevel(), BOX_TRAILER, trailer.position(), trailer.getYRot());
            again.load(saved);
            helper.getLevel().addFreshEntity(again);
            helper.assertTrue(again.tower() == null, "its id link is gone with the old id");
            helper.runAtTickTime(40, () -> {
                helper.assertTrue(again.tower() == car, "re-linked by UUID: " + again.tower());
                helper.assertTrue(car.trailer() == again, "both ways");
                helper.succeed();
            });
        });
    }
}
