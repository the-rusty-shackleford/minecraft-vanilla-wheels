/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Vehicle Sensor's rules (D-0035).
 *
 * <p>Partitions. step: up and down; from a rung, from between rungs, from below the bottom rung, from
 * the top and bottom rungs (going round), from MIN_RANGE and MAX_RANGE; out of bounds below and above.
 * decay: the countdown at HOLD, between, at 1, at 0; out of range below and above. on: 0, positive.
 */
class SensingTest {
    @Test
    void stepClimbsTheLadderAndGoesRoundAtTheTop() {
        int[] ladder = {2, 4, 8, 12, 16, 20, 24, 32};
        for (int i = 0; i + 1 < ladder.length; i++) {
            assertEquals(ladder[i + 1], Sensing.step(ladder[i], false), "up from " + ladder[i]);
            assertEquals(ladder[i], Sensing.step(ladder[i + 1], true), "down from " + ladder[i + 1]);
        }
        assertEquals(2, Sensing.step(32, false), "up from the top: round to the bottom");
        assertEquals(32, Sensing.step(2, true), "down from the bottom: round to the top");
        assertTrue(Sensing.DEFAULT_RANGE == 8 && Sensing.step(Sensing.step(Sensing.DEFAULT_RANGE, false), true) == Sensing.DEFAULT_RANGE,
                "a new sensor sits on a rung");
    }

    @Test
    void aRangeOffTheLadderStepsToTheRungBesideIt() {
        assertEquals(12, Sensing.step(10, false));
        assertEquals(8, Sensing.step(10, true));
        assertEquals(2, Sensing.step(Sensing.MIN_RANGE, false), "below the bottom rung, up");
        assertEquals(32, Sensing.step(Sensing.MIN_RANGE, true), "below the bottom rung, down: round to the top");
        assertEquals(24, Sensing.step(Sensing.MAX_RANGE, true));
        assertThrows(IllegalArgumentException.class, () -> Sensing.step(Sensing.MIN_RANGE - 1, false));
        assertThrows(IllegalArgumentException.class, () -> Sensing.step(Sensing.MAX_RANGE + 1, true));
    }

    @Test
    void theCountdownRunsDownOneScanAtATimeAndStops() {
        assertEquals(Sensing.HOLD - 1, Sensing.decay(Sensing.HOLD));
        assertEquals(1, Sensing.decay(2));
        assertEquals(0, Sensing.decay(1));
        assertEquals(0, Sensing.decay(0));
        assertTrue(Sensing.on(Sensing.HOLD));
        assertTrue(Sensing.on(1));
        assertFalse(Sensing.on(0));
        int hold = Sensing.HOLD, scans = 0;
        while (Sensing.on(hold)) {
            hold = Sensing.decay(hold);
            scans++;
        }
        assertEquals(Sensing.HOLD, scans, "on for HOLD scans after the last sighting");
        assertEquals(10, Sensing.HOLD * Sensing.SCAN_PERIOD, "half a second");
        assertThrows(IllegalArgumentException.class, () -> Sensing.decay(-1));
        assertThrows(IllegalArgumentException.class, () -> Sensing.decay(Sensing.HOLD + 1));
    }
}
