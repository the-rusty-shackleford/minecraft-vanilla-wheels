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

    private static void require(double maxSpeed, double mass) {
        if (maxSpeed <= 0 || mass <= 0) {
            throw new IllegalArgumentException("maxSpeed and mass must be positive");
        }
    }
}
