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
 * Where a vehicle docks on a Mechanic Lift and how it gets there (D-0040).
 * A body left standing on the deck is taken: backed up to the back wall
 * with GAP between its tail and the wall's face, centred across, facing
 * the front -- so a garage of lifts is a row of cars parked alike -- and
 * held there until its driver's throttle lets it go. The move from where
 * it stood is a glide through the hold of the lift's ordinary job
 * ({@link LiftMotion}): the deck comes up, the body slides and turns while
 * it is up, the deck sets it down. Pure arithmetic, so the server and a
 * test agree on every number.
 */
public final class Docking {
    private Docking() {}

    /** Between a docked body's tail and the wall's face, blocks. */
    public static final double GAP = 0.25;
    /** How far a body may hang past the deck's front edge and still dock, blocks: a Pickup's bonnet on a six-long deck, not a Huey. */
    public static final double OVERHANG = 1.5;

    /** effects: returns the longest body that docks on a lift shaped {@code f}, blocks: from GAP off the wall to OVERHANG past the front */
    public static double longest(Footprint f) {
        return f.wallFace() - GAP + OVERHANG;
    }
    /** Ticks a body must stand still on the deck before the lift takes it. */
    public static final int REST = 10;
    /** How far a docked body may be found from its spot and still be docked, blocks: further, something moved it away. */
    public static final double HOLD = 1.0;

    /** effects: returns whether a body {@code width} across and {@code length} long docks on a lift shaped {@code f}: within the deck's width, no longer than {@link #longest} */
    public static boolean fits(Footprint f, double width, double length) {
        return width <= Footprint.WIDTH && length <= longest(f);
    }

    /**
     * requires: length > 0
     * effects: returns the depth from the deck's front edge of a docked body's centre on a lift
     * shaped {@code f}, for a body {@code length} long: its tail GAP off the wall's face
     */
    public static double depth(Footprint f, double length) {
        if (!(length > 0)) {
            throw new IllegalArgumentException("a body has length: " + length);
        }
        return f.wallFace() - GAP - length / 2.0;
    }

    /**
     * requires: 0 <= ticksLeft <= LiftMotion.JOB
     * effects: returns how far the glide has gone, 0..1, {@code ticksLeft} ticks before the
     * job ends: nowhere through the rise, eased through the hold, there through the fall
     */
    public static double progress(int ticksLeft) {
        if (ticksLeft < 0 || ticksLeft > LiftMotion.JOB) {
            throw new IllegalArgumentException("ticks left is 0.." + LiftMotion.JOB + ": " + ticksLeft);
        }
        double into = LiftMotion.JOB - ticksLeft;
        double x = Math.max(0.0, Math.min(1.0, (into - LiftMotion.UP) / LiftMotion.HOLD));
        return x * x * (3 - 2 * x);
    }

    /** effects: returns the point {@code progress} of the way from {@code from} to {@code to} */
    public static double at(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    /**
     * effects: returns the turn from heading {@code from} to heading {@code to}, degrees, the
     * shorter way: in (-180, 180], a half turn going the positive way
     */
    public static double turn(double from, double to) {
        double d = (to - from) % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }

    /** effects: returns the heading {@code progress} of the way through the turn from {@code from} to {@code to} */
    public static double yawAt(double from, double to, double progress) {
        return from + turn(from, to) * progress;
    }

    /**
     * The watch on a body's stillness: how many ticks running it has stood
     * with no speed on the ground. At REST it is ready, the one tick on which
     * the lift under it is asked; still after that, it has asked and keeps
     * quiet (ASKED) until a tick of motion starts it over. Every state is one
     * shared instance, so a tick allocates nothing.
     */
    public record Rest(int ticks) {
        /** Still past REST: the lift has been asked, or there was none under it. */
        public static final int ASKED = REST + 1;
        private static final Rest[] AT = java.util.stream.IntStream.rangeClosed(0, ASKED).mapToObj(Rest::new).toArray(Rest[]::new);
        public static final Rest MOVING = AT[0];

        public Rest {
            if (ticks < 0 || ticks > ASKED) {
                throw new IllegalArgumentException("ticks is 0.." + ASKED + ": " + ticks);
            }
        }

        /** effects: returns the watch after a tick in which the body was {@code still}, or not */
        public Rest observe(boolean still) {
            return still ? AT[Math.min(ASKED, ticks + 1)] : MOVING;
        }

        /** effects: returns whether this is the tick to ask the lift: still for exactly REST ticks */
        public boolean ready() {
            return ticks == REST;
        }
    }
}
