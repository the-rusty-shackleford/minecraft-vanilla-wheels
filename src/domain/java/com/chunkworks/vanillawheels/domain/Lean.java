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
 * How a two-wheeler leans (D-0047). Its wheels are in a line, so the ground under them says
 * nothing about its roll: a plane fitted through two points in a line can tip either way. It
 * stands upright, leans into a turn as far as balancing the turn needs, and leans onto its stand
 * when parked.
 *
 * <p>Balanced, the lean's tangent is the turn's sideways pull over gravity: speed times turn rate
 * over the game's gravity, signed like the roll (positive right; a right turn pulls right).
 *
 * <p>AF: a lean of at most {@code most} radians either way, eased toward its target at {@code rate}
 * of the gap a tick, resting at {@code parked} radians with nobody at the controls.
 * <p>RI: 0 < most < pi/2; 0 < rate <= 1; |parked| <= most.
 */
public record Lean(double most, double parked, double rate) {
    /** The game's gravity, blocks a tick per tick: what a turn's pull is weighed against. */
    public static final double GRAVITY = 0.08;
    /** The share of the gap to its target a lean closes a tick. */
    public static final double RATE = 0.25;

    public Lean {
        if (!(most > 0.0 && most < Math.PI / 2) || !(rate > 0.0 && rate <= 1.0) || !(Math.abs(parked) <= most)) {
            throw new IllegalArgumentException("a lean of at most " + most + ", parked at " + parked + ", eased at " + rate);
        }
    }

    /**
     * requires: speed and turn finite
     * effects: returns the roll a rider balances a turn at: atan(speed * turn / gravity), held to
     * {@code most}; speed in blocks a tick, turn in radians a tick, positive right
     */
    public double balanced(double speed, double turn) {
        if (!Double.isFinite(speed) || !Double.isFinite(turn)) {
            throw new IllegalArgumentException("speed and turn are finite: " + speed + ", " + turn);
        }
        return Math.clamp(Math.atan(speed * turn / GRAVITY), -most, most);
    }

    /** effects: returns the roll a tick on from {@code roll} toward {@code target} */
    public double toward(double roll, double target) {
        return roll + (target - roll) * rate;
    }
}
