/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

/**
 * The Vehicle Sensor's rules (D-0035): its range, and how long it stays on.
 *
 * <p>A sensor looks around itself every {@link #SCAN_PERIOD} ticks for a vehicle within its range: any
 * part of a vehicle within that many blocks of the sensor, in every direction (Rusty: "range based and
 * not 'tripwire' based"). A car at full speed is seen within a tenth of a second of coming into range,
 * so a door can be open before it arrives (Rusty: "if I am driving towards the door at full speed, I
 * dont have to slow down"). Its range is a rung of a ladder, {@code 2, 4, 8, 12, 16, 20, 24, 32}
 * blocks, stepped by hand; a command may set any range from {@link #MIN_RANGE} to {@link #MAX_RANGE}.
 * Seeing a vehicle, the sensor is on for {@link #HOLD} more scans, counting down one each scan that
 * sees none: it holds through a vehicle bobbing at the edge of its range, and lets go half a second
 * after the vehicle has left it.
 *
 * <p>AF: pure functions of a range and a countdown. RI: none (no state).
 */
public final class Sensing {
    private Sensing() {}

    /** Ticks between a sensor's looks around it. */
    public static final int SCAN_PERIOD = 2;
    /** The countdown a sighting sets: scans a sensor stays on after the last one that saw a vehicle. */
    public static final int HOLD = 5;
    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 32;
    /** A new sensor's range, in blocks. */
    public static final int DEFAULT_RANGE = 8;
    private static final int[] LADDER = {2, 4, 8, 12, 16, 20, 24, 32};

    /** requires: MIN_RANGE <= range <= MAX_RANGE; effects: returns the ladder's next rung above
     * {@code range}, or below it when {@code down}, going round from the top rung to the bottom one and
     * back; a range between rungs steps to the rung beside it; throws: range out of bounds */
    public static int step(int range, boolean down) {
        if (range < MIN_RANGE || range > MAX_RANGE) {
            throw new IllegalArgumentException("a sensor's range is " + MIN_RANGE + ".." + MAX_RANGE + ": " + range);
        }
        if (down) {
            for (int i = LADDER.length - 1; i >= 0; i--) {
                if (LADDER[i] < range) {
                    return LADDER[i];
                }
            }
            return LADDER[LADDER.length - 1];
        }
        for (int rung : LADDER) {
            if (rung > range) {
                return rung;
            }
        }
        return LADDER[0];
    }

    /** requires: 0 <= hold <= HOLD; effects: returns the countdown after a scan that saw no vehicle;
     * throws: hold out of range */
    public static int decay(int hold) {
        if (hold < 0 || hold > HOLD) {
            throw new IllegalArgumentException("a sensor's countdown is 0.." + HOLD + ": " + hold);
        }
        return Math.max(0, hold - 1);
    }

    /** requires: 0 <= hold <= HOLD; effects: returns whether a sensor with this countdown is on */
    public static boolean on(int hold) {
        return hold > 0;
    }
}
