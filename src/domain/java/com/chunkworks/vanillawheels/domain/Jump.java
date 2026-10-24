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
package com.chunkworks.vanillawheels.domain;

/**
 * A ridden jump, made as the game makes a horse's (D-0047): the rider holds the jump key, the bar
 * fills, and letting go jumps with what it held. The numbers are the game's own, read from its
 * {@code AbstractHorse} and {@code LivingEntity} for 1.21.1: the bar's percent maps to a share of
 * full strength, the launch is the strength times that share, and the body then falls under the
 * game's gravity and air drag every tick.
 *
 * <p>AF: a jump of strength {@code strength}, blocks a tick of upward speed at a full bar.
 * <p>RI: 0 < strength <= 2.
 */
public record Jump(double strength) {
    /** The game's gravity on a living body, blocks a tick per tick. */
    public static final double GRAVITY = 0.08;
    /** The share of its upward speed a living body keeps each tick in the air. */
    public static final double DRAG = 0.98;
    /** An average horse's jump strength: the middle of the game's 0.4 to 1.0. */
    public static final double AVERAGE_HORSE = 0.7;
    /** The bar's percent from which a jump is full. */
    public static final int FULL = 90;
    /** The share of full strength the least jump has: a tap. */
    public static final double TAP = 0.4;

    public static final Jump HORSE = new Jump(AVERAGE_HORSE);

    public Jump {
        if (!(strength > 0.0 && strength <= 2.0)) {
            throw new IllegalArgumentException("a jump's strength is in (0, 2]: " + strength);
        }
    }

    /**
     * effects: returns the share of full strength a bar of {@code power} percent jumps with, the
     * horse's: below nothing it is nothing; from {@link #FULL} all of it; between, {@link #TAP} and
     * as much again in proportion. So a tap still jumps, at two fifths.
     */
    public static double share(int power) {
        int p = Math.max(0, power);
        return p >= FULL ? 1.0 : TAP + TAP * p / FULL;
    }

    /** effects: returns the upward speed a bar of {@code power} percent launches with, blocks a tick */
    public double launch(int power) {
        return strength * share(power);
    }

    /** effects: returns the upward speed a tick after moving at {@code vy} in the air: gravity, then drag */
    public static double airborne(double vy) {
        return (vy - GRAVITY) * DRAG;
    }

    /**
     * requires: launch finite
     * effects: returns how high a body launched at {@code launch} rises, blocks: the sum of its
     * upward moves, one a tick, each the last made {@link #airborne}, until one is not upward
     */
    public static double peak(double launch) {
        if (!Double.isFinite(launch)) {
            throw new IllegalArgumentException("a launch is finite: " + launch);
        }
        double h = 0.0;
        for (double v = launch; v > 0.0; v = airborne(v)) {
            h += v;
        }
        return h;
    }
}
