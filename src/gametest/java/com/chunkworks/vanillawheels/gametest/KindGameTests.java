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
import com.chunkworks.vanillawheels.VanillaWheelsMod;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Input;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Vehicle kinds (D-0030), with the tests' own kind, {@link SkidVehicle}, claiming the box skids:
 * a claimed profile is made by its kind's entity type wherever a vehicle is made -- created, or
 * set down from its item -- and an unclaimed one by the protocol's; a kind drives through its own
 * hook and its own policy decides whether it runs anything over. (The lift's Build is in
 * {@link LiftGameTests}, beside its helpers.)
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KindGameTests {
    private static final int FLOOR = 4;
    private static final ResourceLocation BOX_CAR = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_car");
    private static final Input GAS = new Input(1, 0, false, true, true);

    public KindGameTests() {}

    /** effects: lays a dirt floor {@code FLOOR} deep over the first {@code length} by fifteen blocks of the test's bounds */
    private static void layFloor(GameTestHelper helper, int length) {
        for (int x = 0; x < length; x++) {
            for (int z = 0; z < 15; z++) {
                for (int y = 0; y < FLOOR; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }
    }

    @GameTest(template = "arena", timeoutTicks = 20)
    public void aProfileSetsItsEngineLoopsNoteAndLoudnessAndOneThatDoesNotKeepsACars(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        VehicleProfile.Sounds skids = VanillaWheels.profile(registries, GameTestMod.BOX_SKIDS).orElseThrow().value().sounds();
        helper.assertValueEqual(skids.pitch(), new VehicleProfile.Span(0.6, 1.05), "the box skids' note, as written");
        helper.assertValueEqual(skids.volume(), new VehicleProfile.Span(0.3, 1.0), "and their loudness");
        helper.assertTrue(Math.abs(skids.pitch().at(0.5) - 0.825) < 1e-9, "halfway is halfway: " + skids.pitch().at(0.5));
        VehicleProfile.Sounds car = VanillaWheels.profile(registries, BOX_CAR).orElseThrow().value().sounds();
        helper.assertValueEqual(car.pitch(), VehicleProfile.Sounds.CAR_PITCH, "a profile naming no note has a car's, 0.75 to 1.6");
        helper.assertValueEqual(car.volume(), VehicleProfile.Sounds.CAR_VOLUME, "and a car's loudness, 0.28 to 0.72");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 20)
    public void aProfileNamesItsThirdPersonCameraDistanceAndOneThatDoesNotGetsALengthsWorth(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        VehicleProfile skids = VanillaWheels.profile(registries, GameTestMod.BOX_SKIDS).orElseThrow().value();
        helper.assertValueEqual(skids.camera(), 9.0, "the box skids' camera, as written");
        VehicleProfile car = VanillaWheels.profile(registries, BOX_CAR).orElseThrow().value();
        helper.assertTrue(car.look().camera().isEmpty(), "the box car names none");
        helper.assertValueEqual(car.camera(), 1.5 + 1.5 * car.body().length(), "so it stands a length's worth back, as before");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aClaimedProfileIsMadeByItsKindsEntityAndAnUnclaimedOneByTheProtocols(GameTestHelper helper) {
        layFloor(helper, 15);
        Vehicle skids = Vehicle.create(helper.getLevel(), GameTestMod.BOX_SKIDS, helper.absoluteVec(new Vec3(4.5, FLOOR, 4.5)), 0.0f);
        helper.assertTrue(skids instanceof SkidVehicle, "the kind's class makes the profile it claims: " + skids);
        helper.assertValueEqual(skids.getType(), GameTestMod.SKID_VEHICLE.get(), "and its entity type");
        Vehicle car = Vehicle.create(helper.getLevel(), BOX_CAR, helper.absoluteVec(new Vec3(10.5, FLOOR, 4.5)), 0.0f);
        helper.assertTrue(car != null && !(car instanceof SkidVehicle), "an unclaimed profile is the protocol's own vehicle");
        helper.assertValueEqual(car.getType(), ModContent.VEHICLE_ENTITY.get(), "of the protocol's own entity type");

        // Set down from its item by a player, through the item's own use.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(7.5, FLOOR, 2.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ModContent.vehicleStack(GameTestMod.BOX_SKIDS));
        BlockPos floor = helper.absolutePos(new BlockPos(7, FLOOR - 1, 10));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        helper.assertTrue(ModContent.VEHICLE_ITEM.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "the item sets it down");
        List<Vehicle> placed = helper.getLevel().getEntitiesOfClass(Vehicle.class, new AABB(floor).inflate(1.0, 2.0, 1.0));
        helper.assertValueEqual(placed.size(), 1, "one vehicle where the item was used");
        helper.assertTrue(placed.get(0) instanceof SkidVehicle, "made by the kind that claims it: " + placed.get(0));
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the item was used up");
        helper.succeed();
    }

    @GameTest(template = "runway", timeoutTicks = 160)
    public void aKindDrivesThroughItsOwnHookAndRunsNothingOverWhenItsPolicySaysSo(GameTestHelper helper) {
        layFloor(helper, 48);
        Vehicle made = Vehicle.create(helper.getLevel(), GameTestMod.BOX_SKIDS, helper.absoluteVec(new Vec3(3.5, FLOOR, 7.5)), -90.0f);
        helper.assertTrue(made instanceof SkidVehicle, "the kind makes it");
        SkidVehicle skids = (SkidVehicle) made;
        skids.setFuel(8000);
        helper.getLevel().addFreshEntity(skids);
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "a cow");
        Vec3 at = helper.absoluteVec(new Vec3(16.5, FLOOR, 7.5));
        cow.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        float health = cow.getHealth();
        skids.setScriptedInput(GAS);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(skids.getX() > at.x + 4.0, "driven past the cow, now at " + skids.position()))
                .thenExecute(() -> {
                    skids.setScriptedInput(null);
                    helper.assertTrue(skids.steps > 20, "it drove through its own hook: " + skids.steps + " ticks at the wheel");
                    helper.assertTrue(Math.abs(skids.speed()) > 0.15, "at more than a walking pace: " + skids.speed());
                    helper.assertValueEqual(cow.getHealth(), health, "the cow, driven through, is unhurt: the kind runs nothing over");
                })
                .thenSucceed();
    }
}
