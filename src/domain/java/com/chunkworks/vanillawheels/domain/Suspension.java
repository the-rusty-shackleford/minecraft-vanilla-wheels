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
 * How the body rides the ground: the drawn body, and the riders on it,
 * follow the true position with a lag, so a step the collision box takes
 * in one tick is a glide over several. Three numbers chase three targets
 * each tick -- a height above the true position, a pitch and a roll from
 * the ground under the axles -- at a rate that settles in about eight
 * ticks without overshooting.
 *
 * <p>RI: all finite; |pitch| and |roll| <= pi/2.
 * AF: AF(lift, pitch, roll) = "draw the body {@code lift} blocks above
 *     where it is, pitched {@code pitch} (positive nose down, the game's
 *     sense) and rolled {@code roll} (positive leaning right)".
 *
 * @param lift  the drawn body's height above the true position, blocks; negative when it lags behind a step down
 * @param pitch radians, positive nose down
 * @param roll  radians, positive leaning right
 */
public record Suspension(double lift, double pitch, double roll) {
    /** The fraction of the remaining distance closed each tick. */
    public static final double RATE = 0.3;
    /** The most the body may lag the true position, blocks. */
    public static final double MAX_LIFT = 2.5;
    /** The steepest the body is drawn, radians. */
    public static final double MAX_TILT = Math.toRadians(35);

    public static final Suspension LEVEL = new Suspension(0.0, 0.0, 0.0);

    public Suspension {
        if (!Double.isFinite(lift) || !Double.isFinite(pitch) || !Double.isFinite(roll)) {
            throw new IllegalArgumentException("a suspension state must be finite");
        }
        if (Math.abs(pitch) > Math.PI / 2 || Math.abs(roll) > Math.PI / 2) {
            throw new IllegalArgumentException("pitch and roll are within a right angle");
        }
    }

    /**
     * effects: returns this state one tick on, chasing a body height of
     * 0 (the true position) and the pitch and roll the ground gives: the
     * front axle's ground {@code front} and the rear's {@code rear} blocks
     * above the true position, {@code wheelBase} apart; the left side's
     * {@code left} and the right's {@code right}, {@code track} apart. The
     * lift chases the ground's mean height so the body rides the wheels,
     * clamped to {@link #MAX_LIFT}; tilts are clamped to {@link #MAX_TILT}.
     */
    public Suspension step(double front, double rear, double left, double right, double wheelBase, double track) {
        double targetLift = clamp((front + rear) * 0.5, -MAX_LIFT, MAX_LIFT);
        double targetPitch = clamp(Math.atan2(rear - front, wheelBase), -MAX_TILT, MAX_TILT);
        double targetRoll = clamp(Math.atan2(left - right, track), -MAX_TILT, MAX_TILT);
        return new Suspension(chase(lift, targetLift), chase(pitch, targetPitch), chase(roll, targetRoll));
    }

    /**
     * effects: returns this state having just stepped up or down by
     * {@code dy} blocks: the drawn body stays where it was, so the lift
     * takes the jump the true position made, and the glide follows
     */
    public Suspension jumped(double dy) {
        return new Suspension(clamp(lift - dy, -MAX_LIFT, MAX_LIFT), pitch, roll);
    }

    /** effects: returns whether the body is within a hair of level and of the true position */
    public boolean isSettled() {
        return Math.abs(lift) < 0.01 && Math.abs(pitch) < 0.005 && Math.abs(roll) < 0.005;
    }

    private static double chase(double from, double to) {
        double next = from + (to - from) * RATE;
        return Math.abs(next - to) < 1e-4 ? to : next;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
