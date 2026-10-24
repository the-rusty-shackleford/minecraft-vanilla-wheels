/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Chunkworks
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

import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.VanillaWheelsMod;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A vehicle's crew across leaving it and logging out (D-0038), on real players logged in and out
 * through the player list, as a server does. OtatopMalloy died on the box on 2026-10-11 when he and
 * Bobandy_ timed out of a hovering Huey a quarter of a second apart: the game keeps a vehicle in a
 * player's save only while he is the one player aboard, so the pilot was saved standing in the air
 * and the Huey left in Bobandy_'s save; he logged back in to nothing and fell. Here: whoever leaves
 * frees only their own seat; a rider who logs out with others aboard is seated again on logging in;
 * one who logs in to the vehicle gone is stood on the ground under them, or at the surface over
 * them under water, or beside a harm; a lone rider still takes the vehicle along, as the game does.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrewGameTests {
    /** The floor's top over the test's bottom: one layer of stone. */
    private static final int FLOOR = 1;
    /** Where the vehicles hover, over the test's bottom: six blocks over the floor, a fall that hurts. */
    private static final double HIGH = 7.0;

    public CrewGameTests() {}

    /** effects: lays one layer of {@code block} over the test's whole bottom */
    private static void layFloor(GameTestHelper helper, Block block) {
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 15; z++) {
                helper.setBlock(new BlockPos(x, 0, z), block);
            }
        }
    }

    /** effects: returns the box skids, made by the tests' kind at (7.5, y, 7.5) of the test's bounds, in the level */
    private static SkidVehicle skids(GameTestHelper helper, double y) {
        Vehicle made = Vehicle.create(helper.getLevel(), GameTestMod.BOX_SKIDS, helper.absoluteVec(new Vec3(7.5, y, 7.5)), 0.0f);
        helper.assertTrue(made instanceof SkidVehicle, "the kind makes it");
        helper.getLevel().addFreshEntity(made);
        return (SkidVehicle) made;
    }

    /** effects: returns {@code profile} logged in through the player list, as the server logs in anyone: from their save, if they have one */
    private static ServerPlayer logIn(GameTestHelper helper, GameProfile profile) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    /** effects: returns a new player in survival, logged in for the first time and standing at {@code at} of the test's bounds */
    private static ServerPlayer newPlayer(GameTestHelper helper, String name, Vec3 at) {
        ServerPlayer player = logIn(helper, new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        Vec3 abs = helper.absoluteVec(at);
        player.teleportTo(abs.x, abs.y, abs.z);
        return player;
    }

    /** effects: logs {@code player} out as a dropped connection does, and checks they are gone */
    private static void logOut(GameTestHelper helper, ServerPlayer player) {
        player.connection.disconnect(Component.literal("logged out"));
        helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == null, player.getScoreboardName() + " is logged out");
    }

    /** effects: boards {@code riders} in order, the first into the driver's seat */
    private static void board(GameTestHelper helper, Vehicle vehicle, ServerPlayer... riders) {
        for (ServerPlayer r : riders) {
            helper.assertTrue(r.startRiding(vehicle, true), r.getScoreboardName() + " boards");
        }
        helper.assertTrue(vehicle.getControllingPassenger() == riders[0], "the first aboard drives");
    }

    /** effects: returns how far apart {@code a} and {@code b} are across the ground: a seat's height moves with the body's pose, its place across does not */
    private static double across(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** effects: returns the floor's top in the level's heights */
    private static double floorTop(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(0, FLOOR, 0)).getY();
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void whoeverLeavesFreesOnlyTheirOwnSeatAndTheControlsAreTakenOnlyByTheKey(GameTestHelper helper) {
        layFloor(helper, Blocks.STONE);
        SkidVehicle skids = skids(helper, FLOOR);
        ServerPlayer pilot = newPlayer(helper, "seat-pilot", new Vec3(7.5, FLOOR, 7.5));
        ServerPlayer crew = newPlayer(helper, "seat-crew", new Vec3(7.5, FLOOR, 7.5));
        board(helper, skids, pilot, crew);
        AtomicReference<Vec3> pilotSeat = new AtomicReference<>(), crewSeat = new AtomicReference<>();
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    pilotSeat.set(pilot.position());
                    crewSeat.set(crew.position());
                    helper.assertTrue(across(pilotSeat.get(), crewSeat.get()) > 0.5, "two seats apart: " + across(pilotSeat.get(), crewSeat.get()));
                    pilot.stopRiding();
                    helper.assertTrue(skids.getControllingPassenger() == null, "nobody is handed the controls: " + skids.getControllingPassenger());
                    helper.assertTrue(crew.getVehicle() == skids, "the crew stays aboard");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(across(crew.position(), crewSeat.get()) < 0.05,
                            "the crew still in their own seat, not moved up into the pilot's: " + across(crew.position(), crewSeat.get()) + " from it");
                    helper.assertTrue(skids.getControllingPassenger() == null && skids.seatOf(crew) == 1, "and still nobody at the controls");
                    skids.takeControls(crew);
                    helper.assertTrue(skids.getControllingPassenger() == crew && skids.seatOf(crew) == 0, "the take-the-controls key: into the driver's seat");
                    helper.assertTrue(pilot.startRiding(skids, true), "the pilot boards again");
                    helper.assertTrue(skids.seatOf(pilot) == 1 && skids.getControllingPassenger() == crew, "into the free seat; the crew keeps the controls");
                    skids.takeControls(pilot);
                    helper.assertTrue(skids.getControllingPassenger() == crew && skids.seatOf(pilot) == 1, "the key does nothing with someone at the controls");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(across(crew.position(), pilotSeat.get()) < 0.05, "the crew sits in the driver's seat: " + across(crew.position(), pilotSeat.get()) + " from it");
                    helper.assertTrue(across(pilot.position(), crewSeat.get()) < 0.05, "the pilot in the other");
                    logOut(helper, pilot);
                    logOut(helper, crew);
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void aRiderWhoLogsOutWithOthersAboardIsSeatedAgainOnLoggingIn(GameTestHelper helper) {
        layFloor(helper, Blocks.STONE);
        SkidVehicle skids = skids(helper, HIGH);
        ServerPlayer pilot = newPlayer(helper, "back-pilot", new Vec3(7.5, FLOOR, 7.5));
        ServerPlayer crew = newPlayer(helper, "back-crew", new Vec3(7.5, FLOOR, 7.5));
        GameProfile profile = pilot.getGameProfile();
        board(helper, skids, pilot, crew);
        AtomicReference<ServerPlayer> back = new AtomicReference<>();
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    logOut(helper, pilot);
                    helper.assertTrue(!skids.isRemoved() && crew.getVehicle() == skids, "two aboard: the game leaves the vehicle in the world with the crew");
                })
                // Nobody at the controls: the server steps it, and a car with nobody holding it up comes down.
                .thenIdle(40)
                .thenExecute(() -> {
                    ServerPlayer p = logIn(helper, profile);
                    back.set(p);
                    helper.assertTrue(p.getVehicle() == skids, "logged back in aboard, wherever it is now: " + p.getVehicle() + " at " + p.position());
                    helper.assertTrue(skids.getControllingPassenger() == p, "in their own seat, the driver's");
                    helper.assertTrue(crew.getVehicle() == skids, "and the crew where they were");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(back.get().getHealth() == back.get().getMaxHealth(), "unhurt: " + back.get().getHealth());
                    logOut(helper, back.get());
                    logOut(helper, crew);
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void aRiderWhoLogsInToTheVehicleGoneIsStoodOnTheGroundUnderThem(GameTestHelper helper) {
        // OtatopMalloy's death, six blocks up: the pilot drops, then the crew, who takes the vehicle along.
        layFloor(helper, Blocks.STONE);
        SkidVehicle skids = skids(helper, HIGH);
        skids.hover = true;
        ServerPlayer pilot = newPlayer(helper, "gone-pilot", new Vec3(7.5, FLOOR, 7.5));
        ServerPlayer crew = newPlayer(helper, "gone-crew", new Vec3(7.5, FLOOR, 7.5));
        GameProfile pilotProfile = pilot.getGameProfile(), crewProfile = crew.getGameProfile();
        board(helper, skids, pilot, crew);
        AtomicReference<ServerPlayer> back = new AtomicReference<>();
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    // The game saves a rider at their own height over the vehicle's middle.
                    Vec3 left = new Vec3(skids.getX(), pilot.getY(), skids.getZ());
                    logOut(helper, pilot);
                    logOut(helper, crew);
                    helper.assertTrue(skids.isRemoved() && skids.getRemovalReason() == Entity.RemovalReason.UNLOADED_WITH_PLAYER,
                            "the last one aboard took it along, as the game does: " + skids.getRemovalReason());
                    ServerPlayer p = logIn(helper, pilotProfile);
                    back.set(p);
                    helper.assertTrue(p.getVehicle() == null, "nothing to sit in");
                    helper.assertTrue(Math.abs(p.getY() - floorTop(helper)) < 1e-6,
                            "stood on the ground under them, not " + (left.y - floorTop(helper)) + " blocks up: at " + (p.getY() - floorTop(helper)));
                    helper.assertTrue(Math.abs(p.getX() - left.x) < 1e-6 && Math.abs(p.getZ() - left.z) < 1e-6, "straight down from where they were saved");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    ServerPlayer p = back.get();
                    helper.assertTrue(p.getHealth() == p.getMaxHealth() && Math.abs(p.getY() - floorTop(helper)) < 1e-6, "unhurt and still there: " + p.getHealth());
                    ServerPlayer c = logIn(helper, crewProfile);
                    helper.assertTrue(c.getVehicle() instanceof SkidVehicle, "the crew comes back in the vehicle they took along");
                    logOut(helper, c);
                    logOut(helper, p);
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aRiderWhoLogsInToTheVehicleGoneOverAHarmIsStoodBesideIt(GameTestHelper helper) {
        layFloor(helper, Blocks.STONE);
        SkidVehicle skids = skids(helper, HIGH);
        skids.hover = true;
        ServerPlayer pilot = newPlayer(helper, "harm-pilot", new Vec3(7.5, FLOOR, 7.5));
        ServerPlayer crew = newPlayer(helper, "harm-crew", new Vec3(7.5, FLOOR, 7.5));
        GameProfile pilotProfile = pilot.getGameProfile();
        board(helper, skids, pilot, crew);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    BlockPos under = BlockPos.containing(pilot.getX(), floorTop(helper), pilot.getZ());
                    helper.getLevel().setBlockAndUpdate(under, Blocks.MAGMA_BLOCK.defaultBlockState());
                    logOut(helper, pilot);
                    logOut(helper, crew);
                    ServerPlayer p = logIn(helper, pilotProfile);
                    helper.assertTrue(Math.abs(p.getY() - floorTop(helper)) < 1e-6, "on the floor: at " + (p.getY() - floorTop(helper)));
                    helper.assertTrue(!p.blockPosition().equals(under) && p.blockPosition().distManhattan(under) <= 2, "beside the magma, not on it: " + p.blockPosition() + " by " + under);
                    logOut(helper, p);
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aRiderWhoLogsInUnderWaterToTheVehicleGoneComesUpAtTheSurface(GameTestHelper helper) {
        // A submarine's crew: the vehicle held three blocks down in water whose surface is at seven.
        layFloor(helper, Blocks.STONE);
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 15; z++) {
                for (int y = FLOOR; y < 7; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        SkidVehicle skids = skids(helper, 3.0);
        skids.hover = true;
        ServerPlayer pilot = newPlayer(helper, "deep-pilot", new Vec3(7.5, FLOOR, 7.5));
        ServerPlayer crew = newPlayer(helper, "deep-crew", new Vec3(7.5, FLOOR, 7.5));
        GameProfile pilotProfile = pilot.getGameProfile();
        board(helper, skids, pilot, crew);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    logOut(helper, pilot);
                    logOut(helper, crew);
                    ServerPlayer p = logIn(helper, pilotProfile);
                    double surface = helper.absolutePos(new BlockPos(0, 7, 0)).getY();
                    helper.assertTrue(Math.abs(p.getY() - surface) < 1e-6, "at the surface, not under it: " + (p.getY() - surface) + " from it");
                    logOut(helper, p);
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aLoneRiderWhoLogsOutTakesTheVehicleAlongAndComesBackInIt(GameTestHelper helper) {
        layFloor(helper, Blocks.STONE);
        SkidVehicle skids = skids(helper, HIGH);
        skids.hover = true;
        ServerPlayer pilot = newPlayer(helper, "lone-pilot", new Vec3(7.5, FLOOR, 7.5));
        GameProfile profile = pilot.getGameProfile();
        board(helper, skids, pilot);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    double y = pilot.getY();
                    logOut(helper, pilot);
                    helper.assertTrue(skids.getRemovalReason() == Entity.RemovalReason.UNLOADED_WITH_PLAYER, "gone with them");
                    ServerPlayer p = logIn(helper, profile);
                    helper.assertTrue(p.getVehicle() instanceof SkidVehicle v && v.getControllingPassenger() == p, "back in it, at the controls");
                    helper.assertTrue(Math.abs(p.getY() - y) < 0.01, "where they left: " + (p.getY() - y));
                    logOut(helper, p);
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 20)
    public void aRidersClickOnTheirOwnVehicleIsTheirHandsWithASeatFree(GameTestHelper helper) {
        // From a seat the crosshair lands on the vehicle's own hull. A free seat beside the rider took
        // the click as boarding, so nobody could eat, drink or open a map aboard a Huey (D-0045).
        layFloor(helper, Blocks.STONE);
        SkidVehicle skids = skids(helper, FLOOR);
        ServerPlayer pilot = newPlayer(helper, "hand-pilot", new Vec3(7.5, FLOOR, 7.5));
        board(helper, skids, pilot);
        helper.assertTrue(skids.hasSeatFor(newPlayer(helper, "hand-other", new Vec3(3.5, FLOOR, 3.5))), "a seat is free");
        pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
        helper.assertTrue(!skids.interact(pilot, InteractionHand.MAIN_HAND).consumesAction(), "the click is passed to the bread in hand");
        helper.assertTrue(pilot.getVehicle() == skids && skids.getControllingPassenger() == pilot, "still at the controls");
        pilot.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(!skids.interact(pilot, InteractionHand.MAIN_HAND).consumesAction(), "an empty hand's click is passed on too");
        helper.succeed();
    }
}
