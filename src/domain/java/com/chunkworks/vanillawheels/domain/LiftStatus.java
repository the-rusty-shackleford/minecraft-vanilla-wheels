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
 * What the lift's two buttons can do right now, decided from the facts
 * in a fixed order so the message names the first thing in the way.
 */
public final class LiftStatus {
    private LiftStatus() {}

    /** Whether Build may run: a job is running; the parts match no vehicle; the deck is occupied; ready. */
    public enum Build { BUSY, NO_RECIPE, OCCUPIED, READY }

    /** Whether Paint may run: a job is running; no vehicle on the deck; no dye in the slot; ready. */
    public enum Paint { BUSY, NO_VEHICLE, NO_DYE, READY }

    /**
     * effects: returns the build status: BUSY while {@code busy}, else
     * NO_RECIPE unless {@code matches}, else OCCUPIED if {@code occupied},
     * else READY
     */
    public static Build build(boolean busy, boolean matches, boolean occupied) {
        if (busy) {
            return Build.BUSY;
        }
        if (!matches) {
            return Build.NO_RECIPE;
        }
        if (occupied) {
            return Build.OCCUPIED;
        }
        return Build.READY;
    }

    /**
     * effects: returns the paint status: BUSY while {@code busy}, else
     * NO_VEHICLE unless {@code vehicle}, else NO_DYE unless {@code dye},
     * else READY
     */
    public static Paint paint(boolean busy, boolean vehicle, boolean dye) {
        if (busy) {
            return Paint.BUSY;
        }
        if (!vehicle) {
            return Paint.NO_VEHICLE;
        }
        if (!dye) {
            return Paint.NO_DYE;
        }
        return Paint.READY;
    }
}
