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
 * What running into something costs, from the vehicle's speed as a
 * fraction of its top speed and its mass: nothing under a walking pace,
 * then damage that grows with the square of the speed and a shove that
 * grows with it, and a loss of speed for the vehicle by what it hit.
 */
public final class Impact {
    private Impact() {}

    /** Below this speed, blocks per tick, a vehicle bumps and hurts nothing. */
    public static final double PACE = 0.15;
    /** Damage at top speed for a vehicle of mass 1, in health points. */
    public static final double DAMAGE_AT_TOP = 8.0;
    /** The shove at a crawl and how much it grows to top speed. */
    public static final double KNOCKBACK_BASE = 0.4;
    public static final double KNOCKBACK_GAIN = 2.2;
    /** How much of its speed a vehicle keeps after hitting something large, and something small. */
    public static final double KEEP_AFTER_LARGE = 0.85;
    public static final double KEEP_AFTER_SMALL = 0.97;
    /** A victim at least this wide is large. */
    public static final double LARGE_WIDTH = 0.9;

    /**
     * effects: returns the damage a vehicle of {@code mass} moving at
     * {@code speed} blocks a tick with a top speed of {@code maxSpeed} does
     * to what it hits: 0 under the pace, else {@code mass * 8 * (speed/max)^2}<br>
     * throws: {@link IllegalArgumentException} if maxSpeed <= 0 or mass <= 0
     */
    public static float damage(double speed, double maxSpeed, double mass) {
        require(maxSpeed, mass);
        double v = Math.abs(speed);
        if (v < PACE) {
            return 0.0f;
        }
        double f = Math.min(1.0, v / maxSpeed);
        return (float) (mass * DAMAGE_AT_TOP * f * f);
    }

    /**
     * effects: returns the shove, in the game's knockback strength, for a
     * hit at {@code speed}: 0 under the pace, else 0.4 rising to 2.6 at the top speed
     */
    public static double knockback(double speed, double maxSpeed) {
        require(maxSpeed, 1.0);
        double v = Math.abs(speed);
        if (v < PACE) {
            return 0.0;
        }
        return KNOCKBACK_BASE + KNOCKBACK_GAIN * Math.min(1.0, v / maxSpeed);
    }

    /** effects: returns the vehicle's speed after hitting a victim {@code width} blocks wide: less for a cow than a chicken */
    public static double speedAfter(double speed, double width) {
        return speed * (width >= LARGE_WIDTH ? KEEP_AFTER_LARGE : KEEP_AFTER_SMALL);
    }

    /** A planar velocity. RI: finite components. AF: blocks travelled along x/z per tick. */
    public record Velocity(double x, double z) {
        public Velocity {
            if (!Double.isFinite(x) || !Double.isFinite(z)) throw new IllegalArgumentException("finite velocity required");
        }
    }

    /** The two velocities following contact; immutable and finite. */
    public record Contact(Velocity first, Velocity second) {}

    /**
     * requires: normal points from first body to second; masses positive and finite
     * effects: exchanges normal momentum with restrained, speed-dependent restitution;
     * tangential velocity is preserved. Separating/stationary bodies receive no impulse.
     * throws: IllegalArgumentException for invalid mass or zero/nonfinite normal
     */
    public static Contact contact(Velocity a, double massA, Velocity b, double massB, double nx, double nz) {
        require(massA, massB);
        double length = Math.hypot(nx, nz);
        if (!Double.isFinite(length) || length < 1e-9) throw new IllegalArgumentException("nonzero normal required");
        nx /= length; nz /= length;
        double closing = (a.x - b.x) * nx + (a.z - b.z) * nz;
        if (closing <= 0) return new Contact(a, b);
        double restitution = closing < PACE ? 0 : Math.min(0.18, closing * 0.15);
        double impulse = (1 + restitution) * closing / (1 / massA + 1 / massB);
        return new Contact(new Velocity(a.x - impulse * nx / massA, a.z - impulse * nz / massA),
                new Velocity(b.x + impulse * nx / massB, b.z + impulse * nz / massB));
    }

    /**
     * requires: normal points into the wall and is nonzero and finite
     * effects: retains tangent motion with slight friction; a hard impact rebounds at most 12%.
     * Gentle contact stops along the normal; separating motion is unchanged.
     * throws: IllegalArgumentException for an invalid normal
     */
    public static Velocity wall(Velocity v, double nx, double nz) {
        double length = Math.hypot(nx, nz);
        if (!Double.isFinite(length) || length < 1e-9) throw new IllegalArgumentException("nonzero normal required");
        nx /= length; nz /= length;
        double closing = v.x * nx + v.z * nz;
        if (closing <= 0) return v;
        double rebound = closing < PACE ? 0 : Math.min(0.12, closing * 0.12);
        return new Velocity((v.x - closing * nx) * 0.98 - closing * rebound * nx,
                (v.z - closing * nz) * 0.98 - closing * rebound * nz);
    }

    /** effects: returns whether kinetic energy exceeds the fragile-block threshold; throws: IllegalArgumentException for invalid numbers */
    public static boolean breaksFragile(double speed, double mass) {
        require(1, mass);
        if (!Double.isFinite(speed)) throw new IllegalArgumentException("finite speed required");
        return Math.abs(speed) >= 0.2 && mass * speed * speed >= 0.09;
    }

    private static void require(double maxSpeed, double mass) {
        if (!Double.isFinite(maxSpeed) || !Double.isFinite(mass) || maxSpeed <= 0 || mass <= 0) {
            throw new IllegalArgumentException("maxSpeed and mass must be positive");
        }
    }
}
