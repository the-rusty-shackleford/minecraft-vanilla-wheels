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

/**
 * What a crash costs a body that moves in three dimensions (D-0031), in condition points out of
 * {@link Condition#MAX}: nothing up to a safe closing speed, then a share that grows with the
 * square of the speed past it, all of it from the wreck speed on. A wall or a roof is met at its
 * closing speed; the floor at the sink, which has a safe speed of its own (a landing). A car has
 * none of this: its crash costs it speed, not condition. Rotorcraft built this as its {@code Wear}
 * with an aircraft's three speeds fixed (its D-0001); a protocol now names its own.
 *
 * <p>RI: every speed finite; 0 <= safe < wreck; 0 <= touchdownSafe < wreck.
 * AF: AF(safe, touchdownSafe, wreck) = "a body that a wall or a roof met faster than {@code safe}
 *     blocks a tick, or the floor met sinking faster than {@code touchdownSafe}, wears by the
 *     square of the speed past it, and is wrecked outright at {@code wreck}".
 *
 * @param safe          a wall or a roof met no faster than this, blocks a tick, costs nothing: a bump
 * @param touchdownSafe the floor met sinking no faster than this costs nothing: a landing
 * @param wreck         met at this closing speed, the body is wrecked outright
 */
public record Crash(double safe, double touchdownSafe, double wreck) {
    public Crash {
        if (!Double.isFinite(safe) || !Double.isFinite(touchdownSafe) || !Double.isFinite(wreck)
                || safe < 0.0 || touchdownSafe < 0.0 || safe >= wreck || touchdownSafe >= wreck) {
            throw new IllegalArgumentException("a crash's safe speeds are under its wreck speed: " + safe + ", " + touchdownSafe + ", " + wreck);
        }
    }

    /**
     * requires: closing finite
     * effects: returns the condition a crash at {@code closing} blocks a tick into a block costs:
     * 0 up to {@code safe}, then {@code MAX * ((closing - safe) / (wreck - safe))^2}, all of it from
     * {@code wreck} on, to the nearest whole point (rounding up let a hair of floating error in the
     * speed add a point)
     * throws: IllegalArgumentException if closing is not finite
     */
    public int crash(double closing) {
        return past(closing, safe);
    }

    /**
     * requires: sink finite
     * effects: returns the condition meeting the floor at {@code sink} blocks a tick costs: as
     * {@link #crash}, with nothing up to {@code touchdownSafe}
     * throws: IllegalArgumentException if sink is not finite
     */
    public int touchdown(double sink) {
        return past(sink, touchdownSafe);
    }

    private int past(double speed, double safeSpeed) {
        if (!Double.isFinite(speed)) {
            throw new IllegalArgumentException("a crash's speed is finite: " + speed);
        }
        if (speed <= safeSpeed) {
            return 0;
        }
        double share = Math.min(1.0, (speed - safeSpeed) / (wreck - safeSpeed));
        return (int) Math.round(Condition.MAX * share * share);
    }

    /**
     * requires: every argument finite; shed >= 0
     * effects: returns how much the velocity changed between two successive moves, (px, py, pz)
     * to (nx, ny, nz), beyond the {@code shed} the body's own model can change it by in a tick --
     * 0 for any change the model could have made, more for one the world made it
     */
    public static double lost(double px, double py, double pz, double nx, double ny, double nz, double shed) {
        double dx = nx - px, dy = ny - py, dz = nz - pz;
        return Math.max(0.0, Math.sqrt(dx * dx + dy * dy + dz * dz) - shed);
    }

    /**
     * requires: every argument finite
     * effects: returns the closing speed of a collision that took a velocity of (bx, by, bz) to
     * (ax, ay, az): the speed it carried along the direction it lost -- the whole speed into a wall
     * met square, the part into it of one met at a slant, the sink into a floor, the climb into a
     * roof -- however far into the tick it was met; 0 when nothing was lost
     */
    public static double closing(double bx, double by, double bz, double ax, double ay, double az) {
        double lx = bx - ax, ly = by - ay, lz = bz - az;
        double loss = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (loss < 1e-9) {
            return 0.0;
        }
        return Math.max(0.0, (bx * lx + by * ly + bz * lz) / loss);
    }

    /**
     * requires: every argument finite
     * effects: returns what a collision costs a body that carried (vx, vy, vz), was stepped to move
     * (wx, wy, wz) and moved only (gx, gy, gz): the speed it carried along the direction the move
     * lost, met going mostly down (within 45 degrees of straight down: a floor) a {@link #touchdown},
     * otherwise (a wall, a roof) a {@link #crash}; nothing when nothing was lost. The velocity
     * carried and the move stepped differ for a load on a rope, whose step is what the rope
     * demands: a load held under a roof is asked to move further every tick its aircraft climbs,
     * but carries nothing into the roof once it is stopped there.
     */
    public int impact(double vx, double vy, double vz, double wx, double wy, double wz, double gx, double gy, double gz) {
        double lx = wx - gx, ly = wy - gy, lz = wz - gz;
        double loss = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (loss < 1e-9) {
            return 0;
        }
        double speed = Math.max(0.0, (vx * lx + vy * ly + vz * lz) / loss);
        return ly / loss < -Math.sqrt(0.5) ? touchdown(speed) : crash(speed);
    }

    /**
     * requires: every argument finite
     * effects: returns what a collision that took the velocity (bx, by, bz) to (ax, ay, az) costs:
     * met going mostly down -- the loss pointing within 45 degrees of straight down, a floor -- a
     * {@link #touchdown} at its {@link #closing} speed; anything else, a wall or a roof, a
     * {@link #crash}
     */
    public int impact(double bx, double by, double bz, double ax, double ay, double az) {
        double lx = bx - ax, ly = by - ay, lz = bz - az;
        double loss = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (loss < 1e-9) {
            return 0;
        }
        double speed = closing(bx, by, bz, ax, ay, az);
        return ly / loss < -Math.sqrt(0.5) ? touchdown(speed) : crash(speed);
    }
}
