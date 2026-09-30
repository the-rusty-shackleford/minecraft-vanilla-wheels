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
import com.chunkworks.vanillawheels.VehicleCargo;
import com.chunkworks.vanillawheels.domain.Condition;
import com.chunkworks.vanillawheels.domain.HandRepair;
import com.chunkworks.vanillawheels.domain.Knocks;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Punch to pack, right-click to repair (D-0025), on a real server with real server players: a
 * punch through {@code Player.attack}, a right-click as the server takes one (at the point, then
 * at the entity). Partitions. Punches: fewer than six in a row; six in a row (packs, as it is);
 * a pause longer than the gap (a new row); creative (one packs); someone aboard (refused); paired
 * to another player's key (refused), to the puncher's (packs), creative stranger (packs); a trailer
 * hitched behind a paired car (the car's owner only). The right-click: damaged (a step, hunger by
 * the profile's job), repaired to whole (then it seats), a wreck (it drives after one click), an
 * empty hunger bar (refused), creative (whole at once, no hunger), a gas can in hand (the can's).
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PunchAndRepairGameTests {
    private static final ResourceLocation CAR = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_car");
    private static final ResourceLocation TRAILER = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_cargo_trailer");

    public PunchAndRepairGameTests() {}

    private static Vehicle car(GameTestHelper helper) {
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        Vehicle vehicle = Vehicle.create(helper.getLevel(), CAR, helper.absoluteVec(new Vec3(7.5, 2, 7.5)), 0);
        helper.getLevel().addFreshEntity(vehicle);
        vehicle.setFuel(vehicle.tank().capacity());
        return vehicle;
    }

    /** effects: a server player in {@code mode} in front of the car, facing it, empty-handed */
    private static ServerPlayer player(GameTestHelper helper, GameType mode, String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        Vec3 at = helper.absoluteVec(new Vec3(7.5, 2, 11.0));
        player.teleportTo(helper.getLevel(), at.x, at.y, at.z, 180.0f, 20.0f);
        return player;
    }

    /** effects: {@code n} punches from {@code p} at {@code v}, through the game's own attack */
    private static void punch(ServerPlayer p, Vehicle v, int n) {
        for (int i = 0; i < n; i++) {
            p.attack(v);
            p.resetAttackStrengthTicker();
        }
    }

    /** effects: a right-click at the vehicle as the server takes one: at the point, then, passed, at the entity */
    private static InteractionResult rightClick(ServerPlayer p, Vehicle v) {
        InteractionResult r = v.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        return r.consumesAction() ? r : p.interactOn(v, InteractionHand.MAIN_HAND);
    }

    private static ItemStack packed(ServerPlayer p) {
        return p.getInventory().items.stream().filter(s -> s.is(ModContent.VEHICLE_ITEM.get())).findFirst().orElse(ItemStack.EMPTY);
    }

    private static void pair(ServerPlayer owner, Vehicle v) {
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.KEY_FOB.get()));
        v.interactAt(owner, Vec3.ZERO, InteractionHand.MAIN_HAND);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void done(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer p : players) p.connection.disconnect(Component.literal("test complete"));
        helper.succeed();
    }

    // --- punches -------------------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 80)
    public void sixPunchesInARowPackItAsItIsAndAPauseStartsTheRowAgain(GameTestHelper helper) {
        Vehicle v = car(helper);
        v.setCondition(7300);
        v.setItem(0, new ItemStack(Items.APPLE, 7));
        ServerPlayer p = player(helper, GameType.SURVIVAL, "puncher");
        punch(p, v, Knocks.TO_PACK - 1);
        helper.assertTrue(!v.isRemoved(), "five punches leave it standing");
        helper.assertValueEqual(v.condition(), 7300, "and are not wear");
        helper.assertTrue(v.getHurtTime() > 0, "they rock it");
        helper.runAtTickTime(5 + Knocks.GAP + 1, () -> {
            punch(p, v, 1);
            helper.assertTrue(!v.isRemoved(), "after a pause, the sixth starts a new row");
            punch(p, v, Knocks.TO_PACK - 2);
            helper.assertTrue(!v.isRemoved(), "five in the new row");
            punch(p, v, 1);
            helper.assertTrue(v.isRemoved(), "the sixth in a row packs it");
            ItemStack item = packed(p);
            helper.assertTrue(!item.isEmpty(), "into the puncher's inventory");
            helper.assertValueEqual(item.get(ModContent.CONDITION.get()), 7300, "at the condition it had");
            helper.assertValueEqual(VehicleCargo.unpack(item.get(ModContent.CARGO.get())).get(0).getCount(), 7, "with its cargo");
            done(helper, p);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void creativePacksWithOnePunchAndNobodyPacksAVehicleSomeoneRides(GameTestHelper helper) {
        Vehicle v = car(helper);
        ServerPlayer rider = player(helper, GameType.SURVIVAL, "rider");
        helper.assertTrue(rider.startRiding(v, true), "a rider boards");
        ServerPlayer p = player(helper, GameType.SURVIVAL, "puncher");
        punch(p, v, Knocks.TO_PACK + 2);
        helper.assertTrue(!v.isRemoved(), "punches do nothing while someone rides it");
        ServerPlayer c = player(helper, GameType.CREATIVE, "creative");
        punch(c, v, 1);
        helper.assertTrue(!v.isRemoved(), "not even in creative");
        rider.stopRiding();
        punch(c, v, 1);
        helper.assertTrue(v.isRemoved(), "empty, one creative punch packs it");
        helper.assertTrue(!packed(c).isEmpty(), "into the creative player's inventory");
        done(helper, rider, p, c);
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aPairedVehicleAndTheTrailerBehindItPackOnlyForItsKeysOwner(GameTestHelper helper) {
        Vehicle v = car(helper);
        Vehicle trailer = Vehicle.create(helper.getLevel(), TRAILER, v.position().add(0, 0, -4), 0);
        helper.getLevel().addFreshEntity(trailer);
        v.hitch(trailer);
        helper.assertTrue(v.trailer() == trailer, "hitched");
        ServerPlayer owner = player(helper, GameType.SURVIVAL, "owner");
        pair(owner, v);
        helper.assertTrue(v.binding() != null, "paired");
        ServerPlayer stranger = player(helper, GameType.SURVIVAL, "stranger");
        punch(stranger, trailer, Knocks.TO_PACK);
        helper.assertTrue(!trailer.isRemoved(), "a stranger cannot pack the trailer behind a paired car");
        punch(stranger, v, Knocks.TO_PACK);
        helper.assertTrue(!v.isRemoved(), "nor the car");
        helper.assertValueEqual(v.condition(), Condition.MAX, "and the refused punches did nothing");
        punch(owner, trailer, Knocks.TO_PACK);
        helper.assertTrue(trailer.isRemoved(), "its owner packs the trailer");
        punch(owner, v, Knocks.TO_PACK);
        helper.assertTrue(v.isRemoved(), "and the car");
        ItemStack car = owner.getInventory().items.stream()
                .filter(s -> s.is(ModContent.VEHICLE_ITEM.get()) && CAR.equals(s.get(ModContent.VEHICLE.get()))).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(car.has(ModContent.BINDING.get()), "the packed car keeps its pairing");
        Vehicle other = Vehicle.create(helper.getLevel(), CAR, helper.absoluteVec(new Vec3(3.5, 2, 3.5)), 0);
        helper.getLevel().addFreshEntity(other);
        pair(owner, other);
        ServerPlayer creative = player(helper, GameType.CREATIVE, "admin");
        punch(creative, other, 1);
        helper.assertTrue(other.isRemoved(), "a creative player packs a paired vehicle: cleanup");
        done(helper, owner, stranger, creative);
    }

    // --- the right-click -----------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aDamagedVehicleRepairsAStepAClickForHungerByItsJobThenTheClickSeats(GameTestHelper helper) {
        Vehicle v = car(helper);
        v.setCondition(9500);
        ServerPlayer p = player(helper, GameType.SURVIVAL, "mechanic");
        float before = p.getFoodData().getExhaustionLevel();
        helper.assertTrue(rightClick(p, v).consumesAction(), "the click is taken");
        helper.assertValueEqual(v.condition(), 9500 + HandRepair.STEP, "a step");
        helper.assertTrue(p.getVehicle() == null, "and does not seat the player");
        float per = p.getFoodData().getExhaustionLevel() - before;
        helper.assertTrue(Math.abs(per - 0.5f) < 1.0e-4f, "a twenty-ingot job tires half a point a click: " + per);
        rightClick(p, v);
        helper.assertValueEqual(v.condition(), Condition.MAX, "whole");
        rightClick(p, v);
        helper.assertTrue(p.getVehicle() == v, "whole, the same click seats the player");
        p.stopRiding();

        v.setCondition(0);
        helper.assertTrue(!v.hasFuel(), "a wreck cannot drive");
        rightClick(p, v);
        helper.assertValueEqual(v.condition(), HandRepair.STEP, "a wreck takes a step");
        helper.assertTrue(v.hasFuel(), "and drives");

        p.getFoodData().setFoodLevel(0);
        rightClick(p, v);
        helper.assertValueEqual(v.condition(), HandRepair.STEP, "too hungry to work on it");
        helper.assertTrue(p.getVehicle() == null, "and not seated either");

        p.getFoodData().setFoodLevel(20);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.GAS_CAN.get()));
        v.interactAt(p, Vec3.ZERO, InteractionHand.MAIN_HAND);
        p.interactOn(v, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(v.condition(), HandRepair.STEP, "a gas can's click is the can's, not a repair");
        done(helper, p);
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void creativeRepairsWholeAtOnceAndTiresNobody(GameTestHelper helper) {
        Vehicle v = car(helper);
        v.setCondition(0);
        ServerPlayer p = player(helper, GameType.CREATIVE, "creative-mechanic");
        float before = p.getFoodData().getExhaustionLevel();
        rightClick(p, v);
        helper.assertValueEqual(v.condition(), Condition.MAX, "whole at once");
        helper.assertValueEqual(p.getFoodData().getExhaustionLevel(), before, "no hunger spent");
        done(helper, p);
    }
}
