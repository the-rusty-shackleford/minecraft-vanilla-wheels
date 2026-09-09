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
 * The lift's rise while a job runs: up over UP ticks, held for HOLD, down
 * over DOWN, by LIFT blocks. Pure arithmetic on the ticks left in the job,
 * so the server and every client draw the same rise from one number.
 */
public final class LiftMotion {
    private LiftMotion() {}

    public static final int UP = 10;
    public static final int HOLD = 20;
    public static final int DOWN = 10;
    /** A whole job, ticks. */
    public static final int JOB = UP + HOLD + DOWN;
    /** How far the deck rises, blocks. */
    public static final double LIFT = 0.5;

    /**
     * requires: 0 <= ticksLeft <= JOB
     * effects: returns how far the deck is up, blocks, {@code ticksLeft}
     * ticks before the job ends, {@code partial} of a tick further on
     */
    public static double raise(int ticksLeft, double partial) {
        if (ticksLeft < 0 || ticksLeft > JOB) {
            throw new IllegalArgumentException("ticks left is 0.." + JOB + ": " + ticksLeft);
        }
        double t = JOB - ticksLeft + partial;
        if (t <= 0) {
            return 0.0;
        }
        if (t < UP) {
            return LIFT * ease(t / UP);
        }
        if (t < UP + HOLD) {
            return LIFT;
        }
        if (t < JOB) {
            return LIFT * ease((JOB - t) / DOWN);
        }
        return 0.0;
    }

    /** Smoothstep: a start and a stop with no jolt. */
    private static double ease(double x) {
        return x * x * (3 - 2 * x);
    }
}
