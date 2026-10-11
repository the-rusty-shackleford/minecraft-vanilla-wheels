/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.gametest;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.VanillaWheelsMod;
import com.chunkworks.vanillawheels.domain.Hatch;
import com.chunkworks.vanillawheels.garage.FloorHatchBlock;
import com.chunkworks.vanillawheels.garage.FloorHatchBlockEntity;
import com.chunkworks.vanillawheels.garage.HatchPlateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Floor Hatch (D-0037) on a real server: a raised floor at y 8 with a three-by-three opening
 * (x 5..7, z 5..7) over an empty shaft, and a row of three housings south of it (z 8) facing north.
 * Partitions: laid over an empty opening; opened by redstone (the guard first, the whole row from one
 * powered housing) and shut again; opened by something rising from below before it arrives, held open
 * while it is in the plate's layer, shut once it has gone back down; someone standing on it while
 * something rises far below and close below; the opening filled (the car at home) with and without
 * power; a housing broken; a plate block without a housing; the depth stepped up and down; a look
 * given.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FloorHatchGameTests {
    private static final int FLOOR = 8;
    private static final int[] ROW = {5, 6, 7};

    /** effects: the ground, the raised floor with its opening, and the row of housings, shut */
    private static void build(GameTestHelper h) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (int x = 2; x <= 12; x++) for (int z = 2; z <= 14; z++) {
            boolean opening = x >= 5 && x <= 7 && z >= 5 && z <= 8;
            if (!opening) h.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE);
        }
        for (int x : ROW) h.setBlock(new BlockPos(x, FLOOR, 8), ModContent.FLOOR_HATCH.get().defaultBlockState().setValue(FloorHatchBlock.FACING, Direction.NORTH));
    }

    private static FloorHatchBlockEntity housing(GameTestHelper h, int x) {
        return (FloorHatchBlockEntity) h.getBlockEntity(new BlockPos(x, FLOOR, 8));
    }

    private static boolean plate(GameTestHelper h, int x, int z) {
        return h.getBlockState(new BlockPos(x, FLOOR, z)).is(ModContent.HATCH_PLATE.get());
    }

    /** effects: returns how many of the opening's nine cells hold a plate block */
    private static int plates(GameTestHelper h) {
        int n = 0;
        for (int x : ROW) for (int z = 5; z <= 7; z++) if (plate(h, x, z)) n++;
        return n;
    }

    private static boolean emptyOpening(GameTestHelper h) {
        for (int x : ROW) for (int z = 5; z <= 7; z++) if (!h.getBlockState(new BlockPos(x, FLOOR, z)).isAir()) return false;
        return true;
    }

    private static double collisionTop(GameTestHelper h, BlockPos at) {
        var abs = h.absolutePos(at);
        return h.getLevel().getBlockState(abs).getCollisionShape(h.getLevel(), abs, CollisionContext.empty()).max(Direction.Axis.Y);
    }

    private static Minecart cart(GameTestHelper h, double y) {
        Minecart cart = EntityType.MINECART.create(h.getLevel());
        cart.setNoGravity(true);
        cart.moveTo(h.absoluteVec(new Vec3(6.5, y, 6.5)));
        h.getLevel().addFreshEntity(cart);
        return cart;
    }

    private static void moveTo(GameTestHelper h, Minecart cart, double y) {
        Vec3 at = h.absoluteVec(new Vec3(6.5, y, 6.5));
        cart.setPos(at.x, at.y, at.z);
        cart.setDeltaMovement(Vec3.ZERO);
    }

    private static double top(GameTestHelper h, Minecart cart) {
        return cart.getBoundingBox().maxY - h.absoluteVec(Vec3.ZERO).y;
    }

    @GameTest(template = "garage_arena", timeoutTicks = 120)
    public void itLaysItsPlateThenOpensGuardFirstWhenPoweredAndShutsAgain(GameTestHelper h) {
        build(h);
        h.runAtTickTime(6, () -> {
            h.assertTrue(plates(h) == 9, "laid over the empty opening: " + plates(h) + " of 9");
            h.assertTrue(collisionTop(h, new BlockPos(6, FLOOR, 6)) == 1.0, "a plate block is a whole block to walk on");
            h.assertTrue(housing(h, 5).guard() == 0 && collisionTop(h, new BlockPos(5, FLOOR, 8)) == 1.0, "shut: no guard");
            // Power only the westmost housing: the whole row opens.
            h.setBlock(new BlockPos(4, FLOOR, 8), Blocks.REDSTONE_BLOCK);
        });
        final int[] gone = {-1};
        for (int t = 7; t < 40; t++) h.runAtTickTime(t, () -> {
            if (gone[0] < 0 && plates(h) < 9) {
                gone[0] = (int) h.getTick();
                h.assertTrue(housing(h, 5).guard() == Hatch.GUARD_TICKS, "the guard is up before the plate goes: " + housing(h, 5).guard());
            }
        });
        h.runAtTickTime(40, () -> {
            h.assertTrue(gone[0] > 0 && emptyOpening(h), "open: the opening is empty");
            for (int x : ROW) h.assertTrue(housing(h, x).travel() == Hatch.travel(3), "housing " + x + " drawn back: " + housing(h, x).travel());
            h.assertTrue(collisionTop(h, new BlockPos(7, FLOOR, 8)) == 2.0, "the guard rail stands a block high on the far housing too");
            h.setBlock(new BlockPos(4, FLOOR, 8), Blocks.AIR);
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(plates(h) == 9, "shut again: " + plates(h) + " of 9");
            h.assertTrue(housing(h, 6).guard() == 0 && collisionTop(h, new BlockPos(6, FLOOR, 8)) == 1.0, "the guard folded away");
            h.succeed();
        });
    }

    /** A cart standing in for a lift's car: it rises from the shaft's floor through the opening a fifth
     * of a block a tick, and goes back down a tenth, lingering in the plate's layer. */
    @GameTest(template = "garage_arena", timeoutTicks = 180)
    public void somethingRisingOpensItBeforeItArrivesAndItShutsWhenItHasGoneBack(GameTestHelper h) {
        build(h);
        final Minecart[] cart = new Minecart[1];
        h.runAtTickTime(8, () -> {
            h.assertTrue(plates(h) == 9, "shut before the cart comes");
            cart[0] = cart(h, 2.0);
        });
        for (int t = 10; t <= 45; t++) {
            final double y = 2.0 + (t - 10) * 0.2;
            h.runAtTickTime(t, () -> moveTo(h, cart[0], y));
        }
        for (int t = 50; t <= 120; t++) {
            final double y = 9.0 - (t - 50) * 0.1;
            h.runAtTickTime(t, () -> moveTo(h, cart[0], y));
        }
        for (int t = 10; t <= 120; t++) h.runAtTickTime(t, () -> {
            if (top(h, cart[0]) > FLOOR) {
                h.assertTrue(emptyOpening(h), "the plate is gone wherever the cart reaches the plate's layer: top " + top(h, cart[0]));
            }
        });
        h.runAtTickTime(121, () -> cart[0].discard());
        h.runAtTickTime(150, () -> {
            h.assertTrue(plates(h) == 9, "shut once the cart went back down: " + plates(h) + " of 9");
            h.assertTrue(housing(h, 6).guard() == 0, "the guard folded");
            h.succeed();
        });
    }

    @GameTest(template = "garage_arena", timeoutTicks = 120)
    public void itWaitsForTheOneStandingOnItUntilWhatRisesIsClose(GameTestHelper h) {
        build(h);
        final Minecart[] cart = new Minecart[1];
        var player = h.makeMockServerPlayerInLevel();
        h.runAtTickTime(8, () -> {
            h.assertTrue(plates(h) == 9, "shut");
            player.moveTo(h.absoluteVec(new Vec3(6.5, FLOOR + 1, 6.5)));
            cart[0] = cart(h, 2.0);
        });
        for (int t = 10; t <= 70; t++) {
            final double y = 2.0 + (t - 10) * 0.1;
            h.runAtTickTime(t, () -> {
                moveTo(h, cart[0], y);
                double gap = FLOOR - top(h, cart[0]);
                if (gap > Hatch.CLOSE + 0.25) {
                    h.assertTrue(plates(h) == 9, "someone stands on it and the cart is " + gap + " below: still shut");
                }
                if (top(h, cart[0]) > FLOOR - 0.1) {
                    h.assertTrue(emptyOpening(h), "open before the cart reaches it, even under a foot");
                }
            });
        }
        h.runAtTickTime(72, () -> {
            h.getLevel().getServer().getPlayerList().remove(player);
            h.succeed();
        });
    }

    /** The car at home fills the opening: the hatch stays drawn back, its guard down, powered or not. */
    @GameTest(template = "garage_arena", timeoutTicks = 120)
    public void aFilledOpeningKeepsItDrawnBackWithTheGuardDown(GameTestHelper h) {
        build(h);
        // A housing laid by an opening already filled (a car at home): it draws back, so it is never drawn over the car.
        var lone = new BlockPos(16, FLOOR, 12);
        for (int z = 9; z <= 11; z++) h.setBlock(new BlockPos(16, FLOOR, z), Blocks.IRON_BLOCK);
        h.setBlock(lone, ModContent.FLOOR_HATCH.get().defaultBlockState().setValue(FloorHatchBlock.FACING, Direction.NORTH));
        h.runAtTickTime(6, () -> h.setBlock(new BlockPos(4, FLOOR, 8), Blocks.REDSTONE_BLOCK));
        h.runAtTickTime(20, () -> {
            var hatch = (FloorHatchBlockEntity) h.getBlockEntity(lone);
            h.assertTrue(hatch.travel() == Hatch.travel(3) && !hatch.plated() && hatch.guard() == 0,
                    "laid by a filled opening: drawn back, no plate, no guard (" + hatch.travel() + ")");
        });
        h.runAtTickTime(30, () -> {
            h.assertTrue(emptyOpening(h), "open");
            for (int x : ROW) for (int z = 5; z <= 7; z++) h.setBlock(new BlockPos(x, FLOOR, z), Blocks.IRON_BLOCK);
            h.setBlock(new BlockPos(4, FLOOR, 8), Blocks.AIR);
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(housing(h, 6).travel() == Hatch.travel(3), "the opening filled: it stays drawn back");
            h.assertTrue(housing(h, 6).guard() == 0 && collisionTop(h, new BlockPos(6, FLOOR, 8)) == 1.0, "no guard over a filled opening");
            h.assertTrue(h.getBlockState(new BlockPos(6, FLOOR, 6)).is(Blocks.IRON_BLOCK), "and nothing of the filling taken");
            for (int x : ROW) for (int z = 5; z <= 7; z++) h.setBlock(new BlockPos(x, FLOOR, z), Blocks.AIR);
        });
        h.runAtTickTime(80, () -> {
            h.assertTrue(plates(h) == 9, "emptied and unpowered, it shuts: " + plates(h) + " of 9");
            h.succeed();
        });
    }

    @GameTest(template = "garage_arena", timeoutTicks = 40)
    public void aBrokenHousingTakesItsPlateAndAStrayPlateGoes(GameTestHelper h) {
        build(h);
        h.runAtTickTime(6, () -> {
            h.assertTrue(plates(h) == 9, "shut");
            h.destroyBlock(new BlockPos(5, FLOOR, 8));
            h.setBlock(new BlockPos(15, FLOOR, 15), ModContent.HATCH_PLATE.get().defaultBlockState().setValue(HatchPlateBlock.DISTANCE, 2));
        });
        h.runAtTickTime(10, () -> {
            for (int z = 5; z <= 7; z++) h.assertTrue(!plate(h, 5, z), "the broken housing's column is open at z " + z);
            for (int z = 5; z <= 7; z++) h.assertTrue(plate(h, 6, z) && plate(h, 7, z), "the other housings keep theirs at z " + z);
            h.assertTrue(h.getBlockState(new BlockPos(15, FLOOR, 15)).isAir(), "a plate block without its housing goes");
            h.succeed();
        });
    }

    @GameTest(template = "garage_arena", timeoutTicks = 40)
    public void aClickStepsTheDepthAndTheHatchWearsAGivenBlock(GameTestHelper h) {
        build(h);
        var lone = new BlockPos(16, FLOOR, 12);
        h.setBlock(lone, ModContent.FLOOR_HATCH.get().defaultBlockState().setValue(FloorHatchBlock.FACING, Direction.NORTH));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(new Vec3(16.5, FLOOR + 1, 14.5)));
        h.runAtTickTime(6, () -> {
            h.assertTrue(plate(h, 16, 9) && h.getBlockState(new BlockPos(16, FLOOR, 8)).isAir(), "three deep to start");
            h.useBlock(lone, player);
        });
        h.runAtTickTime(12, () -> {
            h.assertTrue(((FloorHatchBlockEntity) h.getBlockEntity(lone)).depth() == 4 && plate(h, 16, 8), "a click: four deep, laid again");
            player.setShiftKeyDown(true);
            h.useBlock(lone, player);
            player.setShiftKeyDown(false);
        });
        h.runAtTickTime(18, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(16, FLOOR, 8)).isAir() && plate(h, 16, 9), "sneaking: three deep again");
            var andesite = new ItemStack(Items.ANDESITE);
            player.setItemInHand(InteractionHand.MAIN_HAND, andesite);
            var abs = h.absolutePos(lone);
            h.useBlock(lone, player, new BlockHitResult(Vec3.atCenterOf(abs).add(0, .5, 0), Direction.UP, abs, false));
            var hatch = (FloorHatchBlockEntity) h.getBlockEntity(lone);
            h.assertTrue(hatch.look() != null && hatch.look().is(Blocks.ANDESITE), "it wears the andesite");
            h.assertTrue(h.getBlockState(lone).getValue(FloorHatchBlock.LOOKED), "and is drawn as it");
            h.assertTrue(andesite.getCount() == 1, "using none of it");
            h.succeed();
        });
    }
}
