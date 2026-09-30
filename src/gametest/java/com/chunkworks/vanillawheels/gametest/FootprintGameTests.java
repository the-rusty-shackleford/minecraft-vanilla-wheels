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
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * The footprint (D-0007, D-0028) against the terrain players build, driven by the box truck: the
 * Trailblazer's body and its climb of 1, which every shipped vehicle has. Each test pins where the
 * truck goes or stops. Partitions: stairs up a flight, and a flight whose top step is the landing's
 * edge; stairs down; a ramp of half slabs; a lintel the hull passes under -- each at speed and
 * creeping from a standstill; a riser taller than the climb; a fence line (post and arms one width);
 * a wall line whose posts are wider than its arms; a fence corner with a nose point in the empty
 * quadrant its bounding box adds. Each stop is logged as a {@code FOOTPRINT} line, so two builds can
 * be compared number for number (D-0028 has the table).
 *
 * <p>The runway template is 48 blocks long and 9 high; a floor of dirt is laid on it to y 4.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FootprintGameTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int LENGTH = 48;
    private static final int WIDTH = 15;
    private static final int FLOOR = 4;
    private static final ResourceLocation TRUCK = ResourceLocation.parse("vanillawheels_gametest:box_truck");
    private static final Input GAS = new Input(1, 0, false, true, true);
    private static final Input BRAKE = new Input(-1, 0, false, true, true);

    public FootprintGameTests() {}

    private static void layFloor(GameTestHelper helper) {
        for (int x = 0; x < LENGTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                for (int y = 0; y < FLOOR; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }
    }

    /** effects: fills the column at {@code x} across the runway from the floor up to (not including) {@code FLOOR + rise} */
    private static void raise(GameTestHelper helper, int x, int rise) {
        for (int z = 0; z < WIDTH; z++) {
            for (int y = FLOOR; y < FLOOR + rise; y++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    /** effects: puts {@code state} across the runway at ({@code x}, {@code y}) */
    private static void across(GameTestHelper helper, int x, int y, BlockState state) {
        for (int z = 0; z < WIDTH; z++) {
            helper.setBlock(new BlockPos(x, y, z), state);
        }
    }

    /**
     * effects: sets each of {@code cells} to the state the game gives {@code block} among its
     * neighbours there. The helper's set places the default state, and a neighbour placed later
     * connects only the older block's side, so a line set one by one is joined on one side only.
     */
    private static void connected(GameTestHelper helper, List<BlockPos> cells, Block block) {
        for (BlockPos c : cells) {
            helper.setBlock(c, block);
        }
        for (BlockPos c : cells) {
            BlockPos at = helper.absolutePos(c);
            helper.getLevel().setBlock(at, Block.updateFromNeighbourShapes(helper.getLevel().getBlockState(at), helper.getLevel(), at), Block.UPDATE_ALL);
        }
    }

    /** A box truck at ({@code x}, {@code y}, {@code z}), facing +x (east: yaw -90), fuelled. */
    private static Vehicle truck(GameTestHelper helper, double x, double y, double z) {
        Vehicle v = Vehicle.create(helper.getLevel(), TRUCK, helper.absoluteVec(new Vec3(x, y, z)), -90.0f);
        helper.assertTrue(v != null, "the box truck profile is registered");
        v.setFuel(5 * 1600);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    private static double relX(GameTestHelper helper, Vehicle v) {
        return v.getX() - helper.absoluteVec(Vec3.ZERO).x;
    }

    private static double relY(GameTestHelper helper, Vehicle v) {
        return v.getY() - helper.absoluteVec(Vec3.ZERO).y;
    }

    /** The creeping truck's move a tick: a driver easing up to and over a step, from a standstill. */
    private static final double CREEP = 0.05;

    /**
     * effects: sends two trucks east over what starts at x 16 and ends at {@code farEdge}, both from
     * height {@code startY}: one on the gas from x 4.5 in the north lane (to tick 60, then braked to a
     * stop), and one creeping {@link #CREEP} a tick from a standstill half a block short of it in the
     * south lane, as a driver's moves arrive. The footprint checks where a move ends, not the way
     * there, so a truck at speed can step over a false wall a few tenths deep that stops a creeping
     * one for good. At tick 300 asserts that both came to rest with their tails past {@code farEdge},
     * standing at {@code y}, and succeeds.
     */
    private static void driveOver(GameTestHelper helper, String name, double startY, double farEdge, double y) {
        Vehicle fast = truck(helper, 4.5, startY, 3.5);
        double half = fast.profile().body().length() / 2.0;
        Vehicle creep = truck(helper, 16.0 - half - 0.5, startY, 11.5);
        fast.setScriptedInput(GAS);
        helper.runAtTickTime(60, () -> fast.setScriptedInput(BRAKE));
        helper.runAtTickTime(100, () -> fast.setScriptedInput(null));
        helper.onEachTick(() -> {
            if (relX(helper, creep) - half <= farEdge + 0.5) {
                creep.move(MoverType.SELF, new Vec3(CREEP, -0.1, 0.0));
            }
        });
        helper.runAtTickTime(300, () -> {
            for (Vehicle v : List.of(fast, creep)) {
                String which = name + (v == fast ? ", at speed" : ", creeping");
                LOG.info("FOOTPRINT {}: x {} y {}", which, String.format("%.4f", relX(helper, v)), String.format("%.4f", relY(helper, v)));
                helper.assertTrue(relX(helper, v) - half > farEdge, which + ": the tail past it: x " + relX(helper, v));
                helper.assertTrue(Math.abs(relY(helper, v) - y) < 0.1, which + ": standing at " + y + ": y " + relY(helper, v));
            }
            helper.succeed();
        });
    }

    /**
     * effects: drives {@code v} east on the gas; at tick 120 asserts that its nose stopped at
     * {@code face} (within a hundredth past it and a tenth short of it), stalled, and succeeds
     */
    private static void driveInto(GameTestHelper helper, Vehicle v, String name, double face) {
        double half = v.profile().body().length() / 2.0;
        v.setScriptedInput(GAS);
        helper.runAtTickTime(120, () -> {
            double nose = relX(helper, v) + half;
            LOG.info("FOOTPRINT {}: nose {} z {}", name, String.format("%.4f", nose), String.format("%.4f", v.getZ() - helper.absoluteVec(Vec3.ZERO).z));
            helper.assertTrue(nose <= face + 0.01, name + ": the nose stops at " + face + ": nose " + nose);
            helper.assertTrue(nose > face - 0.1, name + ": and close to it: nose " + nose);
            helper.assertTrue(Math.abs(v.speed()) < 0.05, name + ": stalled against it: " + v.speed());
            helper.succeed();
        });
    }

    @GameTest(template = "runway", templateNamespace = "vanillawheels_footprint", timeoutTicks = 320)
    public void theTruckClimbsAFlightOfStairs(GameTestHelper helper) {
        layFloor(helper);
        // Three stairs rising east from x = 16, each on the last's riser, then a landing three up.
        for (int k = 0; k < 3; k++) {
            raise(helper, 16 + k, k);
            across(helper, 16 + k, FLOOR + k, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        }
        for (int x = 19; x < LENGTH; x++) {
            raise(helper, x, 3);
        }
        driveOver(helper, "stairs up", FLOOR, 19.0, FLOOR + 3);
    }

    @GameTest(template = "runway", templateNamespace = "vanillawheels_footprint", timeoutTicks = 320)
    public void theTruckClimbsStairsWhoseTopStepIsTheLanding(GameTestHelper helper) {
        layFloor(helper);
        // Two stairs rising east from x = 16, then the landing's edge a full block over the second's
        // top: from a stair's front half, half a block up, the next sample of a walk can reach the
        // landing a block and a half higher, though the box climbs it a block at a time.
        for (int k = 0; k < 2; k++) {
            raise(helper, 16 + k, k);
            across(helper, 16 + k, FLOOR + k, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        }
        for (int x = 18; x < LENGTH; x++) {
            raise(helper, x, 3);
        }
        driveOver(helper, "stairs to a landing", FLOOR, 18.0, FLOOR + 3);
    }

    @GameTest(template = "runway", templateNamespace = "vanillawheels_footprint", timeoutTicks = 320)
    public void theTruckComesDownAFlightOfStairs(GameTestHelper helper) {
        layFloor(helper);
        // A landing three up to x = 15, then three stairs falling east to the floor.
        for (int x = 0; x < 16; x++) {
            raise(helper, x, 3);
        }
        for (int k = 0; k < 3; k++) {
            raise(helper, 16 + k, 2 - k);
            across(helper, 16 + k, FLOOR + 2 - k, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        }
        driveOver(helper, "stairs down", FLOOR + 3, 19.0, FLOOR);
    }

    @GameTest(template = "runway", templateNamespace = "vanillawheels_footprint", timeoutTicks = 320)
    public void theTruckClimbsARampOfHalfSlabs(GameTestHelper helper) {
        layFloor(helper);
        // Half a block a column from x = 16: slab, block, block and slab, ... to three up at x = 21.
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (int k = 0; k < 6; k++) {
            raise(helper, 16 + k, (k + 1) / 2);
            if (k % 2 == 0) {
                across(helper, 16 + k, FLOOR + k / 2, slab);
            }
        }
        for (int x = 22; x < LENGTH; x++) {
            raise(helper, x, 3);
        }
        driveOver(helper, "half slabs up", FLOOR, 22.0, FLOOR + 3);
    }

    @GameTest(template = "runway", templateNamespace = "vanillawheels_footprint", timeoutTicks = 320)
    public void theTruckPassesUnderALintel(GameTestHelper helper) {
        layFloor(helper);
        // A beam two thick across the runway, its underside two over the floor: over the hull (1.73),
        // inside the height the footprint looks through, and not across the climb line.
        for (int x = 20; x < 22; x++) {
            across(helper, x, FLOOR + 2, Blocks.STONE.defaultBlockState());
        }
        driveOver(helper, "lintel", FLOOR, 22.0, FLOOR);
    }

    @GameTest(template = "runway", templateNamespace = "vanillawheels_footprint", timeoutTicks = 160)
    public void theNoseStopsAtARiserTallerThanTheClimb(GameTestHelper helper) {
        layFloor(helper);
        for (int x = 24; x < LENGTH; x++) {
            raise(helper, x, 2);
        }
        driveInto(helper, truck(helper, 4.5, FLOOR, 7.5), "riser", 24.0);
    }

    @GameTest(template = "runway", templateNamespace = "vanillawheels_footprint", timeoutTicks = 160)
    public void theNoseStopsAtAFenceLine(GameTestHelper helper) {
        layFloor(helper);
        // A fence across the runway at x = 24: posts and arms 4/16 wide, from 6/16 in.
        List<BlockPos> line = new java.util.ArrayList<>();
        for (int z = 0; z < WIDTH; z++) {
            line.add(new BlockPos(24, FLOOR, z));
        }
        connected(helper, line, Blocks.OAK_FENCE);
        driveInto(helper, truck(helper, 4.5, FLOOR, 7.5), "fence line", 24.0 + 6.0 / 16.0);
    }

    @GameTest(template = "runway", templateNamespace = "vanillawheels_footprint", timeoutTicks = 160)
    public void theNoseStopsAtAWallPostsFace(GameTestHelper helper) {
        layFloor(helper);
        // A wall across the runway at x = 24, a torch on every even cell raising a post there: posts
        // 8/16 wide from 4/16 in, arms 6/16 wide from 5/16 in. The truck's nose points sit at z
        // 6.08, 7.0 and 7.92: the first in a post cell but beside its post, on the arm.
        List<BlockPos> line = new java.util.ArrayList<>();
        for (int z = 0; z < WIDTH; z++) {
            line.add(new BlockPos(24, FLOOR, z));
        }
        connected(helper, line, Blocks.COBBLESTONE_WALL);
        for (int z = 0; z < WIDTH; z += 2) {
            helper.setBlock(new BlockPos(24, FLOOR + 1, z), Blocks.TORCH);
        }
        helper.runAtTickTime(1, () -> {
            for (int z = 0; z < WIDTH; z++) {
                boolean post = helper.getBlockState(new BlockPos(24, FLOOR, z)).getValue(WallBlock.UP);
                helper.assertTrue(post == (z % 2 == 0), "a post under each torch and none between: z " + z);
            }
        });
        driveInto(helper, truck(helper, 4.5, FLOOR, 7.0), "wall posts", 24.0 + 4.0 / 16.0);
    }

    @GameTest(template = "runway", templateNamespace = "vanillawheels_footprint", timeoutTicks = 160)
    public void theNoseStopsAtAFenceCornersCell(GameTestHelper helper) {
        layFloor(helper);
        // A fence corner at (24, 7): a line north of it to the runway's edge, a stub west of it. Its
        // boxes make an L, and their bounds add the empty north-west quadrant. The truck's nose points
        // sit at z 7.2 (in that quadrant's rows, 0.2 into the cell), 8.12 and 9.04, so the stub and
        // the corner's post lie between two points, and only the first point can meet the corner.
        List<BlockPos> fence = new java.util.ArrayList<>();
        for (int z = 0; z <= 7; z++) {
            fence.add(new BlockPos(24, FLOOR, z));
        }
        fence.add(new BlockPos(23, FLOOR, 7));
        connected(helper, fence, Blocks.OAK_FENCE);
        driveInto(helper, truck(helper, 4.5, FLOOR, 8.12), "fence corner", 24.0);
    }
}
