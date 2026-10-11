/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.gametest;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.VanillaWheelsMod;
import com.chunkworks.vanillawheels.domain.Shutter;
import com.chunkworks.vanillawheels.garage.GarageDoorBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Hidden Door (D-0036) on a real server: placed with its real item, powered by a real lever.
 * Partitions: shut, sinking (a row cut by the sheet's edge), open; closing with the doorway clear and
 * with a vehicle in it; a panel given a wearable block, a block it cannot wear, and its own item;
 * a look kept through saving; a door of each kind loaded from its saved panels; a vehicle driven
 * through the open door.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HiddenDoorGameTests {
    private static final BlockPos START = new BlockPos(4, 2, 10);
    private static final BlockPos SWITCH = START.offset(1, 0, -1);   // on the floor in front of a panel
    private static final ResourceLocation CAR = ResourceLocation.parse("vanillawheels_gametest:box_car");
    private static final ResourceLocation TRUCK = ResourceLocation.parse("vanillawheels_gametest:box_truck");

    private static Player prepare(GameTestHelper h) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(new Vec3(6.5, 2, 6.5)));
        player.setYRot(0);
        return player;
    }

    private static boolean place(GameTestHelper h, Player p, BlockPos pos) {
        return place(h, p, pos, ModContent.HIDDEN_DOOR_ITEM.get());
    }

    private static boolean place(GameTestHelper h, Player p, BlockPos pos, Item kind) {
        var item = new ItemStack(kind);
        p.setItemInHand(InteractionHand.MAIN_HAND, item);
        var support = h.absolutePos(pos.below());
        return item.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false))).consumesAction();
    }

    /** effects: a width x height hidden door at START, built panel by panel with the real item, and a lever beside it */
    private static void door(GameTestHelper h, Player p, BlockPos start, int width, int height) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            h.assertTrue(place(h, p, start.offset(x, y, 0)), "the real item places panel " + x + "," + y);
        h.setBlock(SWITCH, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static GarageDoorBlockEntity panel(GameTestHelper h, BlockPos pos) {
        return (GarageDoorBlockEntity) h.getBlockEntity(pos);
    }

    private static boolean full(GarageDoorBlockEntity panel) {
        return !Shapes.joinIsNotEmpty(panel.shape(), Shapes.block(), net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME);
    }

    @GameTest(template = "garage_arena", timeoutTicks = 120)
    public void aHiddenDoorSinksWholeIntoItsSillInFourTenthsOfASecondAndRisesAgain(GameTestHelper h) {
        var p = prepare(h);
        door(h, p, START, 6, 4);
        h.runAtTickTime(4, () -> {
            var root = panel(h, START);
            h.assertTrue(root.formed() && root.width() == 6 && root.height() == 4, "six by four, one door");
            h.assertTrue(root.kind() == Shutter.Kind.SINKING, "a hidden door sinks");
            for (int y = 0; y < 4; y++) for (int x = 0; x < 6; x++)
                h.assertTrue(full(panel(h, START.offset(x, y, 0))), "shut: cell " + x + "," + y + " is a whole block");
            h.useBlock(SWITCH, p);
        });
        // Each tick from the lever on: the first tick it moves, half a block (the top row cut in half,
        // the sill row whole); open eight ticks after it started, 0.4 s.
        final int[] started = {-1};
        for (int t = 5; t <= 20; t++) h.runAtTickTime(t, () -> {
            var root = panel(h, START);
            if (started[0] < 0 && root.lift() > 0) {
                started[0] = (int) h.getTick();
                h.assertTrue(root.lift() == Shutter.Kind.SINKING.step, "half a block in its first tick: " + root.lift());
                var top = panel(h, START.offset(2, 3, 0)).shape();
                h.assertTrue(!top.isEmpty() && Math.abs(top.max(Direction.Axis.Y) - 0.5) < 1e-6, "the top row is cut in half by the sheet's sinking edge");
                h.assertTrue(full(panel(h, START.offset(2, 0, 0))), "the sill row is still whole");
            } else if (started[0] >= 0) {
                int expect = Math.min(4 * Shutter.UNITS, (int) (h.getTick() - started[0] + 1) * Shutter.Kind.SINKING.step);
                h.assertTrue(root.lift() == expect, "tick " + (h.getTick() - started[0]) + " of the sinking: " + root.lift() + ", not " + expect);
            }
        });
        h.runAtTickTime(45, () -> {
            h.assertTrue(started[0] >= 0, "it sank");
            h.assertTrue(panel(h, START).lift() == 4 * Shutter.UNITS, "open: sunk its whole height, " + panel(h, START).lift());
            for (int y = 0; y < 4; y++) for (int x = 0; x < 6; x++)
                h.assertTrue(panel(h, START.offset(x, y, 0)).shape().isEmpty(), "open: nothing left in cell " + x + "," + y);
            h.useBlock(SWITCH, p);
        });
        h.runAtTickTime(85, () -> {
            h.assertTrue(panel(h, START).lift() == 0, "shut again: " + panel(h, START).lift());
            h.assertTrue(full(panel(h, START.offset(5, 3, 0))), "the top row whole again");
            h.succeed();
        });
    }

    @GameTest(template = "garage_arena", timeoutTicks = 140)
    public void closingWaitsForAVehicleInTheDoorway(GameTestHelper h) {
        var p = prepare(h);
        door(h, p, START, 4, 3);
        final Vehicle[] vehicle = new Vehicle[1];
        h.runAtTickTime(4, () -> h.useBlock(SWITCH, p));
        h.runAtTickTime(40, () -> {
            h.assertTrue(panel(h, START).lift() == 3 * Shutter.UNITS, "open");
            vehicle[0] = Vehicle.create(h.getLevel(), CAR, h.absoluteVec(new Vec3(6.0, 2, 10.5)), 0);
            vehicle[0].setNoGravity(true);
            h.getLevel().addFreshEntity(vehicle[0]);
            h.useBlock(SWITCH, p);
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(panel(h, START).lift() == 3 * Shutter.UNITS, "a vehicle in the doorway holds it open");
            vehicle[0].discard();
        });
        h.runAtTickTime(100, () -> {
            h.assertTrue(panel(h, START).lift() == 0, "the doorway clear, it shuts");
            h.succeed();
        });
    }

    @GameTest(template = "garage_arena", timeoutTicks = 30)
    public void aPanelWearsTheBlockItIsGivenAndKeepsItWhenSaved(GameTestHelper h) {
        var p = prepare(h);
        door(h, p, START, 2, 2);
        var at = START.offset(1, 1, 0);
        h.runAtTickTime(3, () -> {
            var abs = h.absolutePos(at);
            var hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
            var andesite = new ItemStack(Items.ANDESITE);
            p.setItemInHand(InteractionHand.MAIN_HAND, andesite);
            h.getLevel().getBlockState(abs).useItemOn(andesite, h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(panel(h, at).look() != null && panel(h, at).look().is(Blocks.ANDESITE), "a panel given andesite is drawn as andesite");
            h.assertTrue(andesite.getCount() == 1, "the block is not used up: it is only a look");
            var glass = new ItemStack(Items.GLASS);
            p.setItemInHand(InteractionHand.MAIN_HAND, glass);
            h.getLevel().getBlockState(abs).useItemOn(glass, h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(panel(h, at).look().is(Blocks.ANDESITE), "glass is not solid: the panel keeps its look");
            h.assertTrue(panel(h, START).look() == null, "the panel beside keeps its own look");
            // Saved and loaded, as a chunk does.
            var saved = panel(h, at).saveWithFullMetadata(h.getLevel().registryAccess());
            var copy = new GarageDoorBlockEntity(abs, h.getLevel().getBlockState(abs));
            copy.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(copy.look() != null && copy.look().is(Blocks.ANDESITE), "the look is saved with the panel");
            h.succeed();
        });
    }

    /** A door read back from its saved panels, as a chunk loads it (a server restart, a player coming
     * back), re-forms and answers redstone: both kinds. */
    @GameTest(template = "garage_arena", timeoutTicks = 80)
    public void aDoorLoadedFromItsSavedPanelsReformsAndOpens(GameTestHelper h) {
        var p = prepare(h);
        record Door(String name, Item item, int left, int open) {}
        var doors = List.of(new Door("garage door", ModContent.GARAGE_DOOR_ITEM.get(), 2, new Shutter.Rectangle(0, 0, 3, 2).fullyRaised()),
                new Door("hidden door", ModContent.HIDDEN_DOOR_ITEM.get(), 12, 2 * Shutter.UNITS));
        for (var d : doors) for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++)
            h.assertTrue(place(h, p, new BlockPos(d.left + x, 2 + y, 10), d.item), "the real item places " + d.name + " panel " + x + "," + y);
        h.runAtTickTime(3, () -> {
            var level = h.getLevel();
            for (var d : doors) for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) {
                var at = h.absolutePos(new BlockPos(d.left + x, 2 + y, 10));
                var old = level.getBlockEntity(at);
                h.assertTrue(old instanceof GarageDoorBlockEntity g && g.formed(), d.name + " formed before the reload");
                var saved = old.saveWithFullMetadata(level.registryAccess());
                level.setBlockEntity(BlockEntity.loadStatic(at, level.getBlockState(at), saved, level.registryAccess()));
            }
        });
        h.runAtTickTime(6, () -> { for (var d : doors) h.setBlock(new BlockPos(d.left - 1, 2, 10), Blocks.REDSTONE_BLOCK); });
        h.runAtTickTime(40, () -> {
            for (var d : doors) {
                var root = panel(h, new BlockPos(d.left, 2, 10));
                h.assertTrue(root.formed() && root.lift() == d.open, d.name + " reloaded and powered: open, " + root.lift() + " of " + d.open);
            }
            h.succeed();
        });
    }

    /** The doors players build for a Trailblazer (the box truck's body) and a car, open, driven through
     * a fifth of a block a tick: nothing of a sunk door stands in the doorway. */
    @GameTest(template = "garage_arena", templateNamespace = "vanillawheels_garage_drive", timeoutTicks = 140)
    public void aTruckDrivesThroughAnOpenHiddenDoor(GameTestHelper h) {
        var p = prepare(h);
        record Run(String name, ResourceLocation vehicle, int left, int width, int height) {}
        var runs = List.of(new Run("truck, 4x4 door", TRUCK, 2, 4, 4), new Run("box car, 3x3 door", CAR, 9, 3, 3),
                new Run("truck, 6x4 door", TRUCK, 15, 6, 4));
        for (var r : runs) for (int y = 0; y < r.height; y++) for (int x = 0; x < r.width; x++)
            h.assertTrue(place(h, p, new BlockPos(r.left + x, 2 + y, 10)), "the real item places " + r.name + " panel " + x + "," + y);
        h.runAtTickTime(2, () -> { for (var r : runs) h.setBlock(new BlockPos(r.left - 1, 2, 10), Blocks.REDSTONE_BLOCK); });
        var vehicles = new Vehicle[runs.size()];
        h.runAtTickTime(45, () -> {
            for (int i = 0; i < runs.size(); i++) {
                var r = runs.get(i);
                var root = panel(h, new BlockPos(r.left, 2, 10));
                h.assertTrue(root.formed() && root.lift() == r.height * Shutter.UNITS, r.name + " open");
                vehicles[i] = Vehicle.create(h.getLevel(), r.vehicle, h.absoluteVec(new Vec3(r.left + r.width / 2.0, 2, 5.5)), 0);
                h.getLevel().addFreshEntity(vehicles[i]);
            }
        });
        for (int t = 47; t < 107; t++) h.runAtTickTime(t, () -> {
            for (var v : vehicles) v.move(MoverType.SELF, new Vec3(0, 0, .2));
        });
        h.runAtTickTime(109, () -> {
            var stopped = new StringBuilder();
            for (int i = 0; i < runs.size(); i++) {
                double z = vehicles[i].getZ() - h.absoluteVec(Vec3.ZERO).z;
                if (z < 15) stopped.append(runs.get(i).name).append(" stopped at z ").append(String.format("%.2f", z)).append("; ");
                h.assertTrue(Math.abs(vehicles[i].getY() - h.absoluteVec(new Vec3(0, 2, 0)).y) < 0.05, runs.get(i).name + " stayed on the floor");
            }
            h.assertTrue(stopped.isEmpty(), "through the open door: " + stopped);
            h.succeed();
        });
    }
}
