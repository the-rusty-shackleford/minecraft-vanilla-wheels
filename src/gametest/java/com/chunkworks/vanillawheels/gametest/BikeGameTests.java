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
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Input;
import com.chunkworks.vanillawheels.domain.Jump;
import com.chunkworks.vanillawheels.domain.Linkage;
import com.chunkworks.vanillawheels.domain.Vec;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A two-wheeler on a real server (D-0047): the box bike, the box car's mesh on two wheels in a line,
 * jumping as a horse does and leaning as a bike does. A full bar clears a two-block wall and a tap
 * does not; a jump asked for in the air is made on landing; landing from a jump, off flat ground or
 * off a ledge, hurts nobody aboard; the server takes a jumping driver's moves as their client made
 * them, the whole arc, without a snap back or a kick; it leans into a turn each way and onto its
 * stand when parked; a car does not jump. The profile's ride block parses into its linkage.
 *
 * <p>The runway is 48 long, 9 high and 15 wide, a dirt floor laid 4 deep.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BikeGameTests {
    private static final int FLOOR = 4;
    private static final ResourceLocation BOX_BIKE = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_bike");
    private static final ResourceLocation BOX_CAR = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_car");
    private static final Input GAS = new Input(1, 0, false, true, true);
    /** The wall's near face, x; the bikes start at x 3 and reach full speed well before it. */
    private static final int WALL = 30;

    public BikeGameTests() {}

    private static void layFloor(GameTestHelper helper) {
        int length = (int) helper.getBounds().getXsize();
        int width = (int) helper.getBounds().getZsize();
        for (int x = 0; x < length; x++) {
            for (int z = 0; z < width; z++) {
                for (int y = 0; y < FLOOR; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }
    }

    /** effects: returns a fuelled vehicle of {@code profile} at (x, y, z), facing +x, in the level */
    private static Vehicle spawn(GameTestHelper helper, ResourceLocation profile, double x, double y, double z) {
        Vehicle v = Vehicle.create(helper.getLevel(), profile, helper.absoluteVec(new Vec3(x, y, z)), -90.0f);
        helper.assertTrue(v != null, profile + " is registered");
        v.setFuel(5 * 1600);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    /** effects: returns a villager seated at {@code v}'s controls: the server then holds the wheel, and drives by script */
    private static Villager rider(GameTestHelper helper, Vehicle v) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(villager != null, "a villager");
        villager.moveTo(v.getX(), v.getY(), v.getZ());
        helper.getLevel().addFreshEntity(villager);
        helper.assertTrue(villager.startRiding(v, true) && v.getControllingPassenger() == villager, "the villager at the controls");
        return villager;
    }

    /** effects: returns how far above the floor {@code v} stands */
    private static double height(GameTestHelper helper, Vehicle v) {
        return v.getY() - helper.absoluteVec(new Vec3(0, FLOOR, 0)).y;
    }

    /** effects: returns the front of {@code v}'s body, x in the test's frame */
    private static double nose(GameTestHelper helper, Vehicle v) {
        return helper.relativeVec(v.position()).x + v.profile().body().length() / 2.0;
    }

    @GameTest(template = "arena")
    public void theRideBlockParsesIntoItsLinkage(GameTestHelper helper) {
        layFloor(helper);
        Vehicle bike = spawn(helper, BOX_BIKE, 4.5, FLOOR, 7.5);
        VehicleProfile p = bike.profile();
        helper.assertTrue(p != null && p.jump().isPresent() && p.jump().get().strength() == Jump.AVERAGE_HORSE, "an empty jump block is an average horse's");
        helper.assertTrue(p.lean().isPresent() && Math.abs(p.lean().get().parked() - Math.toRadians(-8)) < 1e-9, "the lean and its parked angle");
        Linkage links = p.linkage();
        helper.assertTrue(links.joints().size() == 4 && links.joints().get(1).parent() == 0, "four joints, the front wheel within the fork");
        helper.assertValueEqual(p.wheelParts(), 2, "on the lift it takes a wheel for each part that rolls");
        helper.assertValueEqual(spawn(helper, BOX_CAR, 10.5, FLOOR, 4.5).profile().wheelParts(), 4, "a car one for each drawn wheel");
        helper.assertValueEqual(spawn(helper, GameTestMod.BOX_SKIDS, 10.5, FLOOR, 11.5).profile().wheelParts(), 0, "skids none");
        helper.assertTrue(links.joints().get(1).pivot().near(new Vec(0, 6 / 16.0, 13 / 16.0), 1e-9), "in blocks: " + links.joints().get(1).pivot());
        helper.assertTrue(links.joints().get(1).amount() == 6 / 16.0 && links.joints().get(1).axis().x() == 1.0, "the wheel's radius in blocks, its axle flipped left");
        helper.assertTrue(bike.canJump(), "a bike jumps");
        helper.assertTrue(!spawn(helper, BOX_CAR, 4.5, FLOOR, 11.5).canJump(), "a car does not");
        helper.succeed();
    }

    /**
     * Two bikes ride at a two-block wall, each asking to jump when its nose is four ticks of travel
     * from the wall: at that point a full bar is two blocks up as the nose reaches the wall. The full
     * bar clears it and lands beyond; the tap rises a little more than half a block and is stopped.
     */
    @GameTest(template = "runway", timeoutTicks = 200)
    public void aFullBarClearsATwoBlockWallAndATapDoesNot(GameTestHelper helper) {
        layFloor(helper);
        for (int z = 0; z < 15; z++) {
            helper.setBlock(new BlockPos(WALL, FLOOR, z), Blocks.STONE);
            helper.setBlock(new BlockPos(WALL, FLOOR + 1, z), Blocks.STONE);
        }
        Vehicle full = spawn(helper, BOX_BIKE, 3.5, FLOOR, 4.5);
        Vehicle tap = spawn(helper, BOX_BIKE, 3.5, FLOOR, 10.5);
        rider(helper, full);
        rider(helper, tap);
        full.setScriptedInput(GAS);
        tap.setScriptedInput(GAS);
        boolean[] asked = new boolean[2];
        double[] highest = new double[2];
        double[] sunk = {0.0};
        StringBuilder trace = new StringBuilder();
        helper.onEachTick(() -> {
            if (height(helper, full) > 0.5) sunk[0] = Math.max(sunk[0], Math.abs(full.suspension(1.0f).lift()));
            if (asked[0] && trace.length() < 1500) trace.append(String.format(" [%.2f h%.2f v%.3f]", helper.relativeVec(full.position()).x, height(helper, full), full.speed()));
            Vehicle[] bikes = {full, tap};
            for (int i = 0; i < 2; i++) {
                Vehicle b = bikes[i];
                highest[i] = Math.max(highest[i], height(helper, b));
                if (!asked[i] && WALL - nose(helper, b) <= 4.0 * Math.max(0.2, Math.abs(b.speed()))) {
                    b.onPlayerJump(i == 0 ? 100 : 0);
                    asked[i] = true;
                }
            }
        });
        helper.runAfterDelay(120, () -> {
            double past = helper.relativeVec(full.position()).x;
            double held = helper.relativeVec(tap.position()).x;
            helper.assertTrue(asked[0] && asked[1], "both asked to jump");
            helper.assertTrue(past > WALL + 1 + full.profile().body().length() / 2.0,
                    "the full bar cleared the wall and landed beyond it: x " + past + ", highest " + highest[0] + trace);
            helper.assertTrue(height(helper, full) < 0.05, "and stands on the floor again: " + height(helper, full));
            helper.assertTrue(held < WALL, "the tap was stopped at the wall: x " + held + ", highest " + highest[1]);
            helper.assertTrue(highest[1] < 1.0 + 1e-3, "a tap rises no higher than a step: " + highest[1]);
            helper.assertTrue(highest[0] > 3.0 && highest[0] < 3.2, "a full bar rises as an average horse does, 3.12: " + highest[0]);
            helper.assertTrue(sunk[0] < 1.0e-6, "and in the air it is drawn where it is, not on the ground under it: lifted " + sunk[0]);
            helper.succeed();
        });
    }

    /** A jump asked for in the air waits for the ground, as a horse's does: dropped from three blocks, it jumps as it lands. */
    @GameTest(template = "arena", timeoutTicks = 80)
    public void aJumpAskedForInTheAirIsMadeOnLanding(GameTestHelper helper) {
        layFloor(helper);
        Vehicle bike = spawn(helper, BOX_BIKE, 6.5, FLOOR + 3, 7.5);
        rider(helper, bike);
        bike.setScriptedInput(Input.NONE);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(height(helper, bike) > 2.5, "in the air: " + height(helper, bike));
            bike.onPlayerJump(100);
        });
        double[] lowestAfterAsk = {Double.MAX_VALUE};
        double[] highestAfterLanding = {0.0};
        helper.onEachTick(() -> {
            if (helper.getTick() < 2) return;
            double h = height(helper, bike);
            if (lowestAfterAsk[0] > 0.01) {
                lowestAfterAsk[0] = Math.min(lowestAfterAsk[0], h);
            } else {
                highestAfterLanding[0] = Math.max(highestAfterLanding[0], h);
            }
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(lowestAfterAsk[0] <= 0.01, "it came down first: " + lowestAfterAsk[0]);
            helper.assertTrue(highestAfterLanding[0] > 3.0, "then jumped the full bar from the floor: " + highestAfterLanding[0]);
            helper.assertTrue(!bike.jumpPending(), "and the jump is spent");
            helper.succeed();
        });
    }

    /** Landing from a full-bar jump hurts nobody aboard: off the flat, and off a three-block ledge (a fall of six). The tall runway, 16 high: the arc off the ledge would meet the runway's ceiling. */
    @GameTest(template = "tall", timeoutTicks = 160)
    public void landingFromAJumpHurtsNobodyAboard(GameTestHelper helper) {
        layFloor(helper);
        // A ledge three blocks high from x 0 to 12 in the far lane; the floor beyond.
        for (int x = 0; x <= 12; x++) {
            for (int z = 9; z < 13; z++) {
                for (int y = FLOOR; y < FLOOR + 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        Vehicle flat = spawn(helper, BOX_BIKE, 4.5, FLOOR, 4.5);
        Vehicle ledge = spawn(helper, BOX_BIKE, 4.5, FLOOR + 3, 10.5);
        Villager a = rider(helper, flat);
        Villager b = rider(helper, ledge);
        int condition = flat.condition();
        flat.setScriptedInput(GAS);
        ledge.setScriptedInput(GAS);
        boolean[] asked = new boolean[2];
        double[] highest = new double[2];
        helper.onEachTick(() -> {
            highest[0] = Math.max(highest[0], height(helper, flat));
            highest[1] = Math.max(highest[1], height(helper, ledge));
            if (!asked[0] && helper.getTick() >= 20) {
                flat.onPlayerJump(100);
                asked[0] = true;
            }
            if (!asked[1] && 12.0 - nose(helper, ledge) <= 0.5) {
                ledge.onPlayerJump(100);
                asked[1] = true;
            }
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(asked[0] && asked[1], "both jumped");
            helper.assertTrue(highest[0] > 3.0 && highest[1] > 6.0, "a full jump each, the ledge's from over six up: " + highest[0] + ", " + highest[1]);
            helper.assertTrue(height(helper, ledge) < 0.05, "the ledge bike is down on the floor: " + height(helper, ledge));
            helper.assertTrue(a.getHealth() == a.getMaxHealth() && b.getHealth() == b.getMaxHealth(),
                    "nobody hurt: " + a.getHealth() + ", " + b.getHealth());
            helper.assertTrue(a.getVehicle() == flat && b.getVehicle() == ledge, "and still aboard");
            helper.assertTrue(flat.condition() == condition, "nor the bike: " + flat.condition());
            helper.succeed();
        });
    }

    /** effects: returns a real player, logged in through the player list, at (x, FLOOR, z) */
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
     * The driver's client jumps: it tells the server so, then reports every tick of the arc, a full
     * bar's, moving forward. Through the game's own handlers, with the driver's real connection: the
     * server takes each move as reported (no snap back, D-0007's re-run stood on the ground only where
     * the ground is), never kicks them as floating, and the landing hurts them not at all.
     */
    @GameTest(template = "runway", timeoutTicks = 80)
    public void theServerKeepsADriversJump(GameTestHelper helper) {
        layFloor(helper);
        Vehicle bike = spawn(helper, BOX_BIKE, 6.5, FLOOR, 7.5);
        ServerPlayer driver = player(helper, "jumping-driver", 6.5, 7.5);
        helper.assertTrue(driver.startRiding(bike, true) && bike.getControllingPassenger() == driver, "the driver at the controls");
        Vehicle client = Vehicle.create(helper.getLevel(), BOX_BIKE, bike.position(), -90.0f);
        double[] vy = {Jump.HORSE.launch(100)};
        double floorY = helper.absoluteVec(new Vec3(0, FLOOR, 0)).y;
        double[] worst = {0.0};
        int[] reported = {0};
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    acceptTeleport(driver);
                    driver.connection.handlePlayerCommand(new ServerboundPlayerCommandPacket(driver, ServerboundPlayerCommandPacket.Action.START_RIDING_JUMP, 100));
                })
                .thenExecuteFor(30, () -> {
                    // A tick of the driver's client: forward a third of a block, up or down the arc, never through the floor.
                    Vec3 from = client.position();
                    double y = Math.max(floorY, from.y + vy[0]);
                    vy[0] = y == floorY ? 0.0 : Jump.airborne(vy[0]);
                    client.absMoveTo(from.x + 0.3, y, from.z, -90.0f, 0.0f);
                    driver.connection.tick();
                    driver.connection.handleMoveVehicle(new ServerboundMoveVehiclePacket(client));
                    worst[0] = Math.max(worst[0], bike.position().distanceTo(client.position()));
                    reported[0]++;
                })
                .thenExecute(() -> {
                    helper.assertTrue(reported[0] == 30, "thirty moves reported");
                    helper.assertTrue(worst[0] < 1e-6, "the server took every move of the arc as reported; the worst was " + worst[0] + " off");
                    helper.assertTrue(Math.abs(bike.getY() - floorY) < 1e-6, "and stands landed where the client landed it");
                    helper.assertTrue(!driver.hasDisconnected(), "the driver was not kicked");
                    helper.assertTrue(driver.getHealth() == driver.getMaxHealth(), "nor hurt landing: " + driver.getHealth());
                    driver.connection.disconnect(net.minecraft.network.chat.Component.literal("test complete"));
                })
                .thenSucceed();
    }

    /** It leans into a turn, right for right and left for left, upright going straight, and onto its stand parked. */
    @GameTest(template = "runway", timeoutTicks = 200)
    public void itLeansIntoTurnsAndOntoItsStandParked(GameTestHelper helper) {
        layFloor(helper);
        Vehicle ridden = spawn(helper, BOX_BIKE, 10.5, FLOOR, 7.5);
        Vehicle parked = spawn(helper, BOX_BIKE, 40.5, FLOOR, 7.5);
        rider(helper, ridden);
        double[] roll = new double[3];
        helper.startSequence()
                .thenExecute(() -> ridden.setScriptedInput(GAS))
                .thenIdle(20)
                .thenExecute(() -> roll[0] = ridden.suspension(1.0f).roll())
                .thenExecute(() -> ridden.setScriptedInput(new Input(1, 1, false, true, true)))
                .thenIdle(15)
                .thenExecute(() -> roll[1] = ridden.suspension(1.0f).roll())
                .thenExecute(() -> ridden.setScriptedInput(new Input(1, -1, false, true, true)))
                .thenIdle(25)
                .thenExecute(() -> roll[2] = ridden.suspension(1.0f).roll())
                .thenExecute(() -> {
                    helper.assertTrue(Math.abs(roll[0]) < Math.toRadians(1), "upright going straight: " + Math.toDegrees(roll[0]));
                    helper.assertTrue(roll[1] > Math.toRadians(10), "leaning right into a right turn: " + Math.toDegrees(roll[1]));
                    helper.assertTrue(roll[2] < -Math.toRadians(10), "leaning left into a left turn: " + Math.toDegrees(roll[2]));
                    helper.assertTrue(Math.abs(Math.toDegrees(roll[1])) <= 35.0 + 1e-6 && Math.abs(Math.toDegrees(roll[2])) <= 35.0 + 1e-6, "never past the most");
                    double stand = parked.suspension(1.0f).roll();
                    helper.assertTrue(Math.abs(stand - Math.toRadians(-8)) < Math.toRadians(0.5), "the parked bike leans onto its stand: " + Math.toDegrees(stand));
                })
                .thenSucceed();
    }

    /** A car's jump key never jumps it: the game's jump is a horse's, and only a profile that says so has one. */
    @GameTest(template = "arena", timeoutTicks = 40)
    public void aCarDoesNotJump(GameTestHelper helper) {
        layFloor(helper);
        Vehicle car = spawn(helper, BOX_CAR, 6.5, FLOOR, 7.5);
        rider(helper, car);
        car.setScriptedInput(Input.NONE);
        helper.runAfterDelay(2, () -> car.onPlayerJump(100));
        double[] highest = {0.0};
        helper.onEachTick(() -> highest[0] = Math.max(highest[0], height(helper, car)));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(highest[0] < 0.05, "it never left the floor: " + highest[0]);
            helper.assertTrue(!car.jumpPending(), "nothing waits to jump");
            helper.succeed();
        });
    }
}
