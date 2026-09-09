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
 * The numbers a vehicle's driving boils down to, in blocks and ticks and
 * radians. A profile decodes into one of these; nothing in the physics
 * reads anything else.
 *
 * <p>RI: maxSpeed > 0; 0 <= reverseSpeed <= maxSpeed; acceleration, brake > 0;
 *     0 <= drag < 1; 0 < grip <= 1; 0 < driftGrip <= grip; 0 < steer < pi/2;
 *     driftBoost >= 0; driftChargeTicks >= 1; wheelBase > 0; climb >= 0;
 *     mass > 0. A vehicle without an engine (a trailer) has maxSpeed and
 *     acceleration but is never given throttle; see {@code Drive}.
 * AF: the meaning of each number is its javadoc.
 *
 * @param maxSpeed         the fastest forward speed, blocks per tick
 * @param reverseSpeed     the fastest speed in reverse
 * @param acceleration     speed gained per tick at full throttle
 * @param brake            speed lost per tick when the throttle opposes the motion
 * @param drag             the fraction of speed lost per tick with no throttle
 * @param grip             how fast the motion follows the heading each tick, 0..1: 1 is on rails
 * @param driftGrip        the same while drifting; lower, so the tail hangs out
 * @param steer            the front wheels' angle at full lock, radians
 * @param driftBoost       speed added on releasing a full drift charge
 * @param driftChargeTicks ticks of drifting to a full charge
 * @param wheelBase        distance between the axles, blocks
 * @param climb            the highest step the vehicle rolls up, blocks
 * @param mass             relative to a small car; scales what a collision does
 */
public record Tuning(double maxSpeed, double reverseSpeed, double acceleration, double brake, double drag,
                     double grip, double driftGrip, double steer, double driftBoost, int driftChargeTicks,
                     double wheelBase, double climb, double mass) {
    public Tuning {
        require(maxSpeed > 0, "maxSpeed must be positive");
        require(reverseSpeed >= 0 && reverseSpeed <= maxSpeed, "reverseSpeed must be 0..maxSpeed");
        require(acceleration > 0 && brake > 0, "acceleration and brake must be positive");
        require(drag >= 0 && drag < 1, "drag must be 0..1");
        require(grip > 0 && grip <= 1, "grip must be in (0, 1]");
        require(driftGrip > 0 && driftGrip <= grip, "driftGrip must be in (0, grip]");
        require(steer > 0 && steer < Math.PI / 2, "steer must be in (0, pi/2)");
        require(driftBoost >= 0, "driftBoost must not be negative");
        require(driftChargeTicks >= 1, "driftChargeTicks must be at least 1");
        require(wheelBase > 0, "wheelBase must be positive");
        require(climb >= 0, "climb must not be negative");
        require(mass > 0, "mass must be positive");
    }

    private static void require(boolean ok, String what) {
        if (!ok) {
            throw new IllegalArgumentException(what);
        }
    }

    /** A small pickup: the Trailblazer's numbers, and the tests' */
    public static Tuning pickup() {
        return new Tuning(0.9, 0.3, 0.02, 0.05, 0.01, 0.85, 0.4, Math.toRadians(32), 0.3, 40, 2.9, 2.0, 1.45);
    }
}
