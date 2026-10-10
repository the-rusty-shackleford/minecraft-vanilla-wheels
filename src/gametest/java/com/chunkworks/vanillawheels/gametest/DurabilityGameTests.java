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
import com.chunkworks.vanillawheels.domain.Blows;
import com.chunkworks.vanillawheels.domain.Condition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a blow costs a vehicle (D-0034), on a real server: its profile's durability divides every
 * blow but a player's own; a blast and a bolt of lightning, which reach every hit box, are taken
 * once; and the creative tab lists the ground vehicles only. The box truck is the Trailblazer's
 * body and durability, 8; the box car names none, 1.
 * Partitions. Blows: a mob's, an arrow's (divided), a player's own (a knock, unworn), on a
 * durability-8 and a durability-1 profile. A blast: one beside the truck, more than one piece in
 * reach (the largest taken, once); a second blast a tick later (taken again). Lightning: one bolt
 * striking every piece on several ticks (once); a second bolt (again). The tab: a ground vehicle
 * (listed), a profile another kind claims (not listed), the parts (listed).
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DurabilityGameTests {
    private static final ResourceLocation TRUCK = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_truck");
    private static final ResourceLocation CAR = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_car");

    public DurabilityGameTests() {}

    private static Vehicle vehicle(GameTestHelper helper, ResourceLocation profile) {
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        Vehicle v = Vehicle.create(helper.getLevel(), profile, helper.absoluteVec(new Vec3(7.5, 2, 7.5)), 0);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    /** effects: the body and every hit box, as an explosion or a bolt finds them */
    private static List<Entity> pieces(Vehicle v) {
        List<Entity> all = new ArrayList<>();
        all.add(v);
        all.addAll(List.of(v.getParts()));
        return all;
    }

    @GameTest(template = "arena", timeoutTicks = 20)
    public void durabilityDividesEveryBlowButAPlayersOwn(GameTestHelper helper) {
        Vehicle truck = vehicle(helper, TRUCK);
        helper.assertValueEqual(truck.profile().durability(), 8.0, "the box truck's durability");
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "a zombie");
        helper.assertTrue(truck.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0f), "a zombie's blow lands");
        helper.assertValueEqual(truck.condition(), Condition.MAX - 250, "an eighth of the 2000 a point costs");
        Player archer = helper.makeMockPlayer(GameType.SURVIVAL);
        Arrow arrow = new Arrow(helper.getLevel(), archer, new ItemStack(Items.ARROW), null);
        helper.assertTrue(truck.hurt(helper.getLevel().damageSources().arrow(arrow, archer), 6.0f), "a pistol round's 6, as an arrow");
        helper.assertValueEqual(truck.condition(), Condition.MAX - 250 - 1500, "a seventh of a wreck");
        Player puncher = helper.makeMockPlayer(GameType.SURVIVAL);
        puncher.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        puncher.attack(truck);
        helper.assertValueEqual(truck.condition(), Condition.MAX - 1750, "a player's own blow is still a knock (D-0025)");
        Vehicle car = vehicle(helper, CAR);
        helper.assertValueEqual(car.profile().durability(), 1.0, "a profile that names none");
        car.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0f);
        helper.assertValueEqual(car.condition(), Condition.MAX - 2000, "wears as before 1.14.0");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aBlastWearsItOnceAtItsLargestPiece(GameTestHelper helper) {
        Vehicle truck = vehicle(helper, TRUCK);
        helper.runAtTickTime(5, () -> {
            // Beside the truck's front hit box, after a tick has put the boxes in place: the game's own explosion.
            Vec3 at = truck.position().add(1.8, 0.5, 1.0);
            Explosion blast = new Explosion(helper.getLevel(), null, at.x, at.y, at.z, 3.0f, false, Explosion.BlockInteraction.KEEP);
            ExplosionDamageCalculator calc = new ExplosionDamageCalculator();
            int largest = 0, all = 0, reached = 0;
            for (Entity piece : pieces(truck)) {
                if (Math.sqrt(piece.distanceToSqr(at)) / 6.0 > 1.0) continue;
                int wear = Blows.wear(calc.getEntityDamageAmount(blast, piece), 8.0);
                largest = Math.max(largest, wear);
                all += wear;
                reached++;
            }
            helper.assertTrue(reached >= 3, "the blast reaches the body and its boxes: " + reached);
            helper.assertTrue(all >= Condition.MAX, "all of them would wreck it: " + all);
            blast.explode();
            helper.assertValueEqual(truck.condition(), Condition.MAX - largest, "taken once, at the largest piece (of " + all + ")");
            helper.assertTrue(!truck.isRemoved(), "a rocket's blast leaves it standing");
            int after = truck.condition();
            helper.runAtTickTime(6, () -> {
                new Explosion(helper.getLevel(), null, at.x, at.y, at.z, 3.0f, false, Explosion.BlockInteraction.KEEP).explode();
                helper.assertTrue(truck.isRemoved() || truck.condition() < after, "a second blast a tick later is a blast of its own");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aBoltOfLightningWearsItOnce(GameTestHelper helper) {
        Vehicle truck = vehicle(helper, TRUCK);
        helper.runAtTickTime(5, () -> {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
            helper.assertTrue(bolt != null, "a bolt");
            // As the bolt strikes: everything near it, every tick it lives, through each flash.
            for (int tick = 0; tick < 3; tick++) {
                for (Entity piece : pieces(truck)) piece.thunderHit(helper.getLevel(), bolt);
            }
            truck.clearFire();
            int once = Blows.wear(bolt.getDamage(), 8.0);
            helper.assertValueEqual(truck.condition(), Condition.MAX - once, "one bolt, one blow");
            LightningBolt second = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
            for (Entity piece : pieces(truck)) piece.thunderHit(helper.getLevel(), second);
            truck.clearFire();
            helper.assertValueEqual(truck.condition(), Condition.MAX - 2 * once, "a second bolt, a second blow");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 20)
    public void theCreativeTabListsTheGroundVehiclesOnly(GameTestHelper helper) {
        CreativeModeTab tab = ModContent.CREATIVE_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess()));
        Set<ResourceLocation> vehicles = tab.getDisplayItems().stream()
                .filter(s -> s.is(ModContent.VEHICLE_ITEM.get()))
                .map(s -> s.get(ModContent.VEHICLE.get())).collect(Collectors.toSet());
        Set<ResourceLocation> chassis = tab.getDisplayItems().stream()
                .filter(s -> s.is(ModContent.CHASSIS.get()))
                .map(s -> s.get(ModContent.VEHICLE.get())).collect(Collectors.toSet());
        helper.assertTrue(vehicles.contains(CAR) && vehicles.contains(TRUCK), "the ground vehicles: " + vehicles);
        helper.assertTrue(chassis.contains(CAR), "and their chassis: " + chassis);
        helper.assertTrue(!vehicles.contains(GameTestMod.BOX_SKIDS) && !chassis.contains(GameTestMod.BOX_SKIDS),
                "a profile another kind claims is in its own tab: " + vehicles);
        helper.assertTrue(tab.getDisplayItems().stream().anyMatch(s -> s.is(ModContent.WHEEL.get())), "the parts stay");
        helper.succeed();
    }
}
