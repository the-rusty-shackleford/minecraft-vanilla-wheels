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
 * How a trailer follows the vehicle towing it: its tongue sits on the
 * tower's hitch, its axle stays where it is unless the tongue pulls it, and
 * its heading is the line from the axle to the tongue. That is the
 * kinematics of a real trailer -- the axle cannot slide sideways, so it is
 * dragged along the line to the hitch -- and it needs no damping, since the
 * axle only ever moves toward where the hitch has gone. Everything is in
 * blocks on the ground plane and radians in the game's yaw sense (0 is +Z,
 * forward is (-sin h, cos h)); height is the ground's business.
 */
public final class Tow {
    private Tow() {}

    /** The most a trailer may fold against its tower, radians: a right angle. */
    public static final double FOLD = Math.PI / 2;

    /** Where the axle and the heading ended, and how far the axle travelled. */
    public record Follow(double axleX, double axleZ, double heading, double travelled) {}

    /**
     * requires: length > 0
     * effects: returns the trailer one tick on: its heading turned to point
     * from its axle at the hitch (held within FOLD of {@code towerHeading},
     * so a hard reverse cannot fold it past a right angle), and its axle
     * moved along that heading to {@code length} behind the hitch. A hitch
     * that has not moved from the tongue leaves the trailer where it is.
     */
    public static Follow follow(double hitchX, double hitchZ, double axleX, double axleZ, double heading, double length, double towerHeading) {
        if (!(length > 0)) {
            throw new IllegalArgumentException("the tongue is ahead of the axle: " + length);
        }
        double vx = hitchX - axleX;
        double vz = hitchZ - axleZ;
        double h = heading;
        if (vx * vx + vz * vz > 1e-12) {
            h = Math.atan2(-vx, vz);
        }
        double fold = wrap(h - towerHeading);
        if (Math.abs(fold) > FOLD) {
            h = wrap(towerHeading + Math.copySign(FOLD, fold));
        }
        double ax = hitchX + Math.sin(h) * length;
        double az = hitchZ - Math.cos(h) * length;
        double dx = ax - axleX;
        double dz = az - axleZ;
        return new Follow(ax, az, h, Math.sqrt(dx * dx + dz * dz));
    }

    /** effects: returns where the tongue is for an axle at (x, z) and a heading, {@code length} ahead */
    public static double[] tongue(double axleX, double axleZ, double heading, double length) {
        return new double[] {axleX - Math.sin(heading) * length, axleZ + Math.cos(heading) * length};
    }

    /** effects: returns {@code a} wrapped into -pi..pi */
    public static double wrap(double a) {
        a %= 2 * Math.PI;
        if (a > Math.PI) {
            a -= 2 * Math.PI;
        } else if (a < -Math.PI) {
            a += 2 * Math.PI;
        }
        return a;
    }
}
