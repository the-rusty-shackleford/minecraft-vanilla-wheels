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
package com.chunkworks.vanillawheels.domain;

import org.junit.jupiter.api.Test;

import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The terrain pose over synthetic ground, the scenarios of nfx's offline
 * replay ({@code slopesim.py}): a body driving straight along +z at a steady
 * speed, its physics y resting on the highest block under its box, its
 * drawn pose stepped every tick. What is asserted is what the eye sees in
 * first person: no jumps in height, one steady angle up a staircase, a
 * level body against a wall, a tail that never goes under the floor.
 */
class TerrainTest {

    /** A pickup-ish shape: wheels 0.5 out and 1.4 fore and aft, radius 0.4, climbs one block, 4.6 long. */
    private static final Terrain.Shape TRUCK = new Terrain.Shape(0.4, 1.0, 4.6, 1.0,
            new double[] {-0.5, 0.5, -0.5, 0.5}, new double[] {1.4, 1.4, -1.4, -1.4});

    /** Ground as a function of world z alone (flat across x): block tops, so the terrain is per column. */
    private static Terrain.Columns columns(DoubleUnaryOperator groundAtZ, double bodyY) {
        return (x, z, lo, hi) -> {
            double top = groundAtZ.applyAsDouble(Math.floor(z)) - bodyY;
            return top >= lo - 1.0 && top <= hi + 1.0 ? top : Double.NEGATIVE_INFINITY;
        };
    }

    /** One drive: the body at z advancing {@code speed} a tick for {@code ticks}, returning the poses and the physics ys. */
    private static double[][] drive(DoubleUnaryOperator ground, double speed, int ticks, double startZ) {
        double halfWidth = TRUCK.track() / 2.0;
        double[][] out = new double[ticks][];   // z, y, height, pitch, roll
        Terrain.Pose pose = Terrain.Pose.level(ground.applyAsDouble(Math.floor(startZ)));
        for (int t = 0; t < ticks; t++) {
            double z = startZ + speed * t;
            double y = Math.max(ground.applyAsDouble(Math.floor(z + halfWidth)), ground.applyAsDouble(Math.floor(z - halfWidth)));
            Terrain.Frame frame = new Terrain.Frame(0.0, z, 0.0);
            pose = Terrain.step(columns(ground, y), frame, TRUCK, y, pose);
            out[t] = new double[] {z, y, pose.height(), pose.pitch(), pose.roll()};
        }
        return out;
    }

    @Test
    void flatGroundLeavesTheBodyLevelOnItsWheels() {
        double[][] run = drive(z -> 0.0, 0.5, 40, 0.0);
        for (double[] r : run) {
            assertEquals(0.0, r[2], 1e-9, "height on the ground");
            assertEquals(0.0, r[3], 1e-9, "no pitch");
            assertEquals(0.0, r[4], 1e-9, "no roll");
        }
    }

    @Test
    void aStaircaseIsOneSteadyAngleAndTheHeightNeverJumps() {
        // Flat, then a 1:1 staircase from z = 4: the physics y jumps a block per riser; the drawn height must not.
        DoubleUnaryOperator ground = z -> Math.max(0.0, z - 3);
        double[][] run = drive(ground, 0.5, 60, 0.0);
        double worstJump = 0.0;
        for (int t = 1; t < run.length; t++) {
            worstJump = Math.max(worstJump, Math.abs(run[t][2] - run[t - 1][2]));
        }
        assertTrue(worstJump < 0.6, "the drawn height climbs at most a little over the speed a tick, never a riser: " + worstJump);
        // On the stairs the nose is up (negative pitch) near 45 degrees, and stays there.
        double late = run[run.length - 1][3], earlier = run[run.length - 10][3];
        assertTrue(late < Math.toRadians(-35) && late > -Terrain.TILT_LIMIT - 1e-9, "nose up near the staircase's angle: " + Math.toDegrees(late));
        assertEquals(late, earlier, Math.toRadians(3), "and steady, not nodding per riser");
    }

    @Test
    void aWallTallerThanTheClimbFlattensTheFit() {
        // A two-block kerb ahead of a truck that climbs one: the walk stops at the wall face, so the body stays level.
        DoubleUnaryOperator ground = z -> z >= 6 ? 2.0 : 0.0;
        double[][] run = drive(ground, 0.0, 40, 3.5);   // parked with its nose 0.2 short of the kerb
        double[] last = run[run.length - 1];
        assertEquals(0.0, last[3], Math.toRadians(2), "level against the wall: " + Math.toDegrees(last[3]));
        assertEquals(0.0, last[2], 0.05, "and on the ground");
    }

    @Test
    void goingOverADropTheDescendingNoseClearsItsGroundAndTheTailBarelySinks() {
        // Flat, then a one-block drop at z = 6. The nose is the descending end and is kept clear of
        // its ground exactly; the tail, the rising end, may sink a little into the upper floor for a
        // tick while the physics y falls the whole block at once (bounded sink is the design).
        DoubleUnaryOperator ground = z -> z >= 6 ? -1.0 : 0.0;
        double[][] run = drive(ground, 0.5, 40, 0.0);
        double worstTailSink = 0.0;
        for (double[] r : run) {
            double pitch = r[3], lift = r[2] - r[1];
            double half = TRUCK.bodyLength() / 2.0;
            double noseUnderside = Terrain.CLEARANCE * Math.cos(pitch) - half * Math.sin(pitch) + lift;
            double floorAtNose = ground.applyAsDouble(Math.floor(r[0] + half)) - r[1];
            if (pitch > 0.0) {
                assertTrue(noseUnderside >= floorAtNose - 1e-3, "nose over its floor at z=" + r[0] + ": " + noseUnderside + " vs " + floorAtNose);
            }
            double tailUnderside = Terrain.CLEARANCE * Math.cos(pitch) + half * Math.sin(pitch) + lift;
            double floorAtTail = ground.applyAsDouble(Math.floor(r[0] - half)) - r[1];
            worstTailSink = Math.max(worstTailSink, floorAtTail - tailUnderside);
        }
        assertTrue(worstTailSink < 0.3, "the tail's sink at a sudden block drop is a fraction of the bounded sink: " + worstTailSink);
    }

    /** The disc probe's ramp across a step of {@code rise}, sampled every 0.02 blocks: the worst change between neighbours. */
    private static double worstStep(double rise) {
        DoubleUnaryOperator ground = z -> z >= 5 ? rise : 0.0;
        Terrain.Columns cols = columns(ground, 0.0);
        double prev = Double.NaN, worst = 0.0;
        for (double z = 3.0; z <= 6.0; z += 0.02) {
            double h = Terrain.wheelGround(cols, new Terrain.Frame(0.0, 0.0, 0.0), TRUCK, 0.5, z, false, 0.0);
            if (!Double.isNaN(prev)) {
                worst = Math.max(worst, Math.abs(h - prev));
            }
            prev = h;
            if (z < 5.0 - TRUCK.wheelRadius() - 0.05) {
                assertEquals(0.0, h, 1e-9, "flat before the rim reaches the step");
            }
            if (z >= 5.0) {
                assertEquals(rise, h, 1e-9, "on the step once the centre is past the edge");
            }
        }
        return worst;
    }

    @Test
    void aDiscRidesUpAStepLowerThanItsRadiusWithoutAJump() {
        assertTrue(worstStep(0.3) < 0.1, "a low step is a ramp: worst change " + worstStep(0.3));
    }

    @Test
    void aFullBlockStepJumpsByTheBlockLessTheRadiusThenRamps() {
        // The rim meets a block taller than the radius on its face, one radius out: the probe rises
        // by the block less the radius there and ramps the rest. The plane fit and springs smooth that.
        double worst = worstStep(1.0);
        // plus the rim curve's first 0.02 blocks, which is steep at the rim's edge: sqrt(2 r 0.02)
        double rimStart = Math.sqrt(2.0 * TRUCK.wheelRadius() * 0.02);
        assertTrue(worst <= 1.0 - TRUCK.wheelRadius() + rimStart + 0.01 && worst > 0.5, "the jump is the block less the radius: " + worst);
    }

    @Test
    void aTowedBodyIsLevelOnTheFlatAndNosesUpWhenItsAxleDrops() {
        // Coupler 2.5 ahead of the origin, axle 0.8 behind; the ball at its rest height 0.6 over the towed body's y.
        Terrain.Shape trailer = new Terrain.Shape(0.4, 1.0, 3.3, 1.2, new double[] {-0.6, 0.6}, new double[] {-0.8, -0.8});
        Terrain.Ball ball = new Terrain.Ball(0.6, 0.6);
        Terrain.Towed flat = Terrain.towed(columns(z -> 0.0, 0.0), new Terrain.Frame(0.0, 0.0, 0.0), trailer, 0.0, -0.8, 2.5, ball, Terrain.TowedPose.LEVEL);
        assertEquals(0.0, flat.suspension().pitch(), 1e-6, "level on the flat");
        assertEquals(0.0, flat.suspension().lift(), 1e-6);
        // The axle a block lower than the ball's ground: the trailer pitches nose up (negative), the tail down.
        Terrain.Towed dropped = Terrain.towed(columns(z -> z < 0 ? -1.0 : 0.0, 0.0), new Terrain.Frame(0.0, 0.0, 0.0), trailer, 0.0, -0.8, 2.5, ball, Terrain.TowedPose.LEVEL);
        assertTrue(dropped.suspension().pitch() < Math.toRadians(-5), "nose up over a dropped axle: " + Math.toDegrees(dropped.suspension().pitch()));
        assertTrue(dropped.suspension().lift() < 0.0, "the axle sits lower");
    }
}
