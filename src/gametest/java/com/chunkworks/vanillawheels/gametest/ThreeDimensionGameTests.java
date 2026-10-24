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
import com.chunkworks.vanillawheels.domain.Condition;
import com.chunkworks.vanillawheels.domain.Crash;
import com.chunkworks.vanillawheels.domain.Hull;
import com.chunkworks.vanillawheels.domain.Input;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A body that moves in three dimensions (D-0031), through the tests' own kind with one facility
 * switched on at a time, beside one with it off: vertical controls keep a Shift-holding rider aboard
 * and let them out by the get-out key, where a car drops them; a hull stops the body short of a
 * block ahead of it and above it that its square box never touches; a reported move that lost more
 * than the body's own model can shed is charged once as a crash, a teleport never, and a car never;
 * and an engine at a quarter of the rate burns a quarter of the fuel.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThreeDimensionGameTests {
    private static final int FLOOR = 4;
    private static final Input GAS = new Input(1, 0, false, true, true);
    /** A box a block across and a block tall from a block behind the origin to four ahead: a long nose, past the body's square. */
    private static final double[] NOSE = Hull.points(List.of(new Hull.Box(-0.5, 0.0, -1.0, 0.5, 1.0, 4.0)));

    public ThreeDimensionGameTests() {}

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

    /** effects: returns the box skids, made by the tests' kind at (x, FLOOR, z) of the test's bounds facing {@code yaw}, in the level */
    private static SkidVehicle skids(GameTestHelper helper, double x, double z, float yaw) {
        Vehicle made = Vehicle.create(helper.getLevel(), GameTestMod.BOX_SKIDS, helper.absoluteVec(new Vec3(x, FLOOR, z)), yaw);
        helper.assertTrue(made instanceof SkidVehicle, "the kind makes it");
        helper.getLevel().addFreshEntity(made);
        return (SkidVehicle) made;
    }

    /** effects: returns a real player, logged in through the player list so the game ticks them as it ticks anyone, at (x, FLOOR, z) */
    private static ServerPlayer player(GameTestHelper helper, String name, double x, double z) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(x, FLOOR, z));
        player.teleportTo(at.x, at.y, at.z);
        return player;
    }

    /** effects: answers {@code player}'s pending teleport as their client would, so the server takes their moves again */
    private static void acceptTeleport(ServerPlayer player) {
        try {
            java.lang.reflect.Field id = ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");
            id.setAccessible(true);
            player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id.getInt(player.connection)));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the teleport id is unreadable", e);
        }
    }

    /**
     * The pilot's client turns, then moves (every vehicle's tick does), and reports where it went and
     * its new heading. The game re-runs the move on the server before it takes the heading from the
     * report, so a hull that turns with the body was tested a tick's turn behind: a long nose that
     * clears a block at the new heading met it at the old one, the server cut the move to nothing and
     * put the body back, "moved wrongly" (the Huey on the box, 2026-10-11). Through the game's own
     * handler, with the pilot's real connection.
     */
    @GameTest(template = "arena", timeoutTicks = 40)
    public void aTurningPilotsReportedMoveIsReRunAtTheHeadingItWasFlownAt(GameTestHelper helper) {
        layFloor(helper, 15);
        // In the air, as an aircraft uses its hull: the game's re-run asks a millionth lower than the
        // report, and a hull resting its bottom on a floor would read that as into the floor.
        SkidVehicle hulled = skids(helper, 7.5, 4.5, 0.0f);
        hulled.setPos(hulled.getX(), hulled.getY() + 2.0, hulled.getZ());
        hulled.hull = NOSE;
        hulled.hover = true;
        helper.setBlock(new BlockPos(6, FLOOR + 2, 8), Blocks.STONE);
        ServerPlayer pilot = player(helper, "turning-pilot", 7.5, 4.5);
        helper.assertTrue(pilot.startRiding(hulled, true) && hulled.getControllingPassenger() == pilot, "the pilot at the controls");
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    // A tick of the pilot's client: a 45-degree turn, then 0.6 forward along the new heading.
                    float turned = 45.0f;
                    Vec3 delta = new Vec3(-Math.sin(Math.toRadians(turned)), 0.0, Math.cos(Math.toRadians(turned))).scale(0.6);
                    Vec3 start = hulled.position();
                    Vehicle made = Vehicle.create(helper.getLevel(), GameTestMod.BOX_SKIDS, start, turned);
                    helper.assertTrue(made instanceof SkidVehicle, "the client's copy");
                    SkidVehicle client = (SkidVehicle) made;
                    client.hull = NOSE;
                    helper.assertTrue(!hulled.blockedAt(0.0, 0.0, 0.0), "the nose clears the stone where it starts");
                    helper.assertTrue(client.clampOf(delta).distanceTo(delta) < 1e-9, "turned, the client's nose clears it the whole move");
                    helper.assertTrue(hulled.blockedAt(delta.x, delta.y, delta.z), "unturned, the nose would end in it");
                    client.absMoveTo(start.x + delta.x, start.y, start.z + delta.z, turned, 0.0f);
                    acceptTeleport(pilot);
                    pilot.connection.tick();   // the listener takes the vehicle it validates and where it stands
                    pilot.connection.handleMoveVehicle(new ServerboundMoveVehiclePacket(client));
                    helper.assertTrue(hulled.position().distanceTo(client.position()) < 1e-6,
                            "the server took the pilot's move: " + hulled.position().subtract(start) + " of " + delta);
                    helper.assertTrue(Math.abs(Mth.wrapDegrees(hulled.getYRot() - turned)) < 1e-3, "and its heading: " + hulled.getYRot());
                    pilot.connection.disconnect(net.minecraft.network.chat.Component.literal("test complete"));
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void verticalControlsKeepAShiftHoldingRiderAboardAndTheGetOutKeyLetsThemOutWhereACarDropsThem(GameTestHelper helper) {
        layFloor(helper, 15);
        SkidVehicle vertical = skids(helper, 4.5, 4.5, 0.0f);
        vertical.vertical = true;
        SkidVehicle car = skids(helper, 10.5, 4.5, 0.0f);
        ServerPlayer pilot = player(helper, "pilot", 4.5, 4.5);
        ServerPlayer driver = player(helper, "driver", 10.5, 4.5);
        helper.assertTrue(pilot.startRiding(vertical, true) && driver.startRiding(car, true), "both aboard");
        pilot.setShiftKeyDown(true);
        driver.setShiftKeyDown(true);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(pilot.getVehicle() == vertical, "Shift held, still aboard: it is \"down\" here");
                    helper.assertTrue(pilot.getPose() != Pose.CROUCHING, "and not crouching in the seat");
                    helper.assertTrue(driver.getVehicle() == null, "a car's driver holding Shift gets out, as from a boat");
                    pilot.setShiftKeyDown(false);
                    vertical.getOut(pilot);
                    helper.assertTrue(pilot.getVehicle() == null, "the get-out key lets them out");
                    pilot.connection.disconnect(net.minecraft.network.chat.Component.literal("test complete"));
                    driver.connection.disconnect(net.minecraft.network.chat.Component.literal("test complete"));
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aHullStopsTheBodyShortOfABlockAheadThatItsSquareBoxNeverTouches(GameTestHelper helper) {
        layFloor(helper, 15);
        SkidVehicle hulled = skids(helper, 7.5, 3.5, 0.0f);
        hulled.hull = NOSE;
        SkidVehicle car = skids(helper, 2.5, 3.5, 0.0f);
        helper.setBlock(new BlockPos(7, FLOOR, 9), Blocks.STONE);
        helper.setBlock(new BlockPos(2, FLOOR, 9), Blocks.STONE);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    double z0 = hulled.getZ(), c0 = car.getZ();
                    // The nose's tip is at +4: a block and a half short of the stone, which the body's
                    // square (a block and a half across, three-quarters either side) is far from.
                    hulled.move(MoverType.SELF, new Vec3(0.0, 0.0, 3.0));
                    car.move(MoverType.SELF, new Vec3(0.0, 0.0, 3.0));
                    double moved = hulled.getZ() - z0;
                    helper.assertTrue(moved > 1.35 && moved < 1.5, "the nose stopped short of the stone: moved " + moved);
                    helper.assertTrue(car.getZ() - c0 > 2.99, "without a hull the square box went the whole way: " + (car.getZ() - c0));
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aHullStopsTheBodyShortOfABlockOverItsNose(GameTestHelper helper) {
        layFloor(helper, 15);
        SkidVehicle hulled = skids(helper, 7.5, 3.5, 0.0f);
        hulled.hull = NOSE;
        // Two over the floor and two and a half ahead: over the nose, clear of the body's square.
        helper.setBlock(new BlockPos(7, FLOOR + 2, 6), Blocks.STONE);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    double y0 = hulled.getY();
                    hulled.move(MoverType.SELF, new Vec3(0.0, 1.8, 0.0));
                    double rose = hulled.getY() - y0;
                    helper.assertTrue(rose > 0.9 && rose < 1.0, "the nose's top stopped under the stone: rose " + rose);
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aHullMetAxisByAxisSlidesAlongTheFloorItPressesIntoWhereAsOneMoveItStops(GameTestHelper helper) {
        layFloor(helper, 15);
        SkidVehicle axes = skids(helper, 3.5, 4.5, -90.0f);
        axes.hull = NOSE;
        axes.axes = true;
        SkidVehicle whole = skids(helper, 3.5, 10.5, -90.0f);
        whole.hull = NOSE;
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    double a0 = axes.getX(), w0 = whole.getX(), y0 = axes.getY();
                    // Along, and pressing down into the floor it rests on: a submarine on the seabed, its planes down.
                    axes.move(MoverType.SELF, new Vec3(1.0, -0.1, 0.0));
                    whole.move(MoverType.SELF, new Vec3(1.0, -0.1, 0.0));
                    helper.assertTrue(axes.getX() - a0 > 0.95, "axis by axis it slides along the floor: " + (axes.getX() - a0));
                    helper.assertTrue(Math.abs(axes.getY() - y0) < 1e-6, "and stays on it: " + (axes.getY() - y0));
                    helper.assertTrue(whole.getX() - w0 < 0.05, "as one move, the floor stops all of it: " + (whole.getX() - w0));
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aReportedMoveThatLostMoreThanItsOwnModelCanShedIsACrashChargedOnce(GameTestHelper helper) {
        layFloor(helper, 15);
        Crash crash = new Crash(0.25, 0.3, 1.5);
        SkidVehicle judged = skids(helper, 3.5, 4.5, -90.0f);
        judged.crash = crash;
        SkidVehicle car = skids(helper, 3.5, 10.5, -90.0f);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    for (SkidVehicle v : new SkidVehicle[] {judged, car}) {
                        v.move(MoverType.PLAYER, new Vec3(0.9, 0.0, 0.0));   // the first report: nothing to judge it against
                        v.move(MoverType.PLAYER, Vec3.ZERO);                 // stopped dead: what the world took
                        v.move(MoverType.PLAYER, Vec3.ZERO);                 // still stopped: the same impact, not charged again
                    }
                    int once = Condition.MAX - crash.crash(0.9);
                    helper.assertValueEqual(judged.condition(), once, "worn by a crash at 0.9, once");
                    helper.assertValueEqual(car.condition(), Condition.MAX, "a car's crash costs it speed, not condition");
                    judged.move(MoverType.PLAYER, new Vec3(7.0, 0.0, 0.0));  // a recall's jump, not a move
                    judged.move(MoverType.PLAYER, Vec3.ZERO);
                    helper.assertValueEqual(judged.condition(), once, "a teleport is never a crash");
                    judged.move(MoverType.PLAYER, new Vec3(0.01, 0.0, 0.0)); // a change its own model could make
                    helper.assertValueEqual(judged.condition(), once, "and a gentle change no crash");
                })
                .thenSucceed();
    }

    @GameTest(template = "runway", timeoutTicks = 120)
    public void anEngineAtAQuarterOfTheRateBurnsAQuarterOfTheFuel(GameTestHelper helper) {
        layFloor(helper, 48);
        SkidVehicle full = skids(helper, 3.5, 4.5, -90.0f);
        SkidVehicle quarter = skids(helper, 3.5, 10.5, -90.0f);
        quarter.rate = 0.25;
        int start = 8000;
        full.setFuel(start);
        quarter.setFuel(start);
        full.setScriptedInput(GAS);
        quarter.setScriptedInput(GAS);
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    full.setScriptedInput(null);
                    quarter.setScriptedInput(null);
                    int one = start - full.tank().ticks(), q = start - quarter.tank().ticks();
                    helper.assertTrue(one >= 38, "at full rate, a tick of fuel a tick of throttle: " + one);
                    helper.assertTrue(Math.abs(one - 4 * q) <= 4, "a quarter as much at a quarter of the rate: " + q + " of " + one);
                })
                .thenSucceed();
    }
}
