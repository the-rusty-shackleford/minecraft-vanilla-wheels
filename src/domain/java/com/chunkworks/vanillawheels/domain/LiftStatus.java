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
 * What the lift's buttons can do right now, and what it says of the
 * vehicle on its deck, decided from the facts in a fixed order so the
 * message names the first thing in the way.
 */
public final class LiftStatus {
    private LiftStatus() {}

    /** Whether Build may run: a job is running; the parts match no vehicle; the deck is occupied; ready. */
    public enum Build { BUSY, NO_RECIPE, OCCUPIED, READY }

    /** Whether Paint may run: a job is running; no vehicle on the deck; no dye in the slot; ready. */
    public enum Paint { BUSY, NO_VEHICLE, NO_DYE, READY }

    /**
     * What the lift says of the vehicle on its deck (D-0040): none there; docked; being
     * taken, or about to be; it tows or is towed; too big for the deck; the deck is another
     * vehicle's; another vehicle stands where it would go; someone is at its wheel.
     */
    public enum Dock { NONE, DOCKED, TAKING, TOWING, TOO_BIG, TAKEN, BLOCKED, DRIVER }

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

    /**
     * effects: returns the dock status: NONE unless {@code vehicle}; for one {@code docked}
     * here, TAKING while {@code busy} (the glide) else DOCKED; else the first thing in the way
     * in a fixed order -- TOWING, TOO_BIG unless {@code fits}, TAKEN, BLOCKED unless
     * {@code clear}, DRIVER -- the hard refusals before the one a step out of the seat mends;
     * else TAKING, since the lift will take it once it stands still
     */
    public static Dock dock(boolean vehicle, boolean docked, boolean busy, boolean towing, boolean fits, boolean taken, boolean clear, boolean driver) {
        if (!vehicle) {
            return Dock.NONE;
        }
        if (docked) {
            return busy ? Dock.TAKING : Dock.DOCKED;
        }
        if (towing) {
            return Dock.TOWING;
        }
        if (!fits) {
            return Dock.TOO_BIG;
        }
        if (taken) {
            return Dock.TAKEN;
        }
        if (!clear) {
            return Dock.BLOCKED;
        }
        if (driver) {
            return Dock.DRIVER;
        }
        return Dock.TAKING;
    }
}
