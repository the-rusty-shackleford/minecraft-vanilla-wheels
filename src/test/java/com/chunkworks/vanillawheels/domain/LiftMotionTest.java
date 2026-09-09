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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Ticks left: the whole job (down), during the rise (rising,
 * monotone), the hold (up), during the fall (falling, monotone), the end
 * (down). Partial ticks continue between whole ticks. Out of range refused.
 */
final class LiftMotionTest {

    @Test
    void theDeckRisesHoldsAndSettles() {
        assertEquals(0.0, LiftMotion.raise(LiftMotion.JOB, 0.0), "down at the start");
        double last = 0.0;
        for (int t = 1; t <= LiftMotion.UP; t++) {
            double r = LiftMotion.raise(LiftMotion.JOB - t, 0.0);
            assertTrue(r >= last, "rising at tick " + t);
            last = r;
        }
        assertEquals(LiftMotion.LIFT, last, 1e-12, "at the top");
        for (int t = LiftMotion.UP; t < LiftMotion.UP + LiftMotion.HOLD; t++) {
            assertEquals(LiftMotion.LIFT, LiftMotion.raise(LiftMotion.JOB - t, 0.5), 1e-12, "held at tick " + t);
        }
        for (int t = LiftMotion.UP + LiftMotion.HOLD + 1; t <= LiftMotion.JOB; t++) {
            double r = LiftMotion.raise(LiftMotion.JOB - t, 0.0);
            assertTrue(r <= last, "falling at tick " + t);
            last = r;
        }
        assertEquals(0.0, LiftMotion.raise(0, 0.0), "down at the end");
    }

    @Test
    void partialTicksInterpolate() {
        double whole = LiftMotion.raise(LiftMotion.JOB - 3, 0.0);
        double half = LiftMotion.raise(LiftMotion.JOB - 3, 0.5);
        double next = LiftMotion.raise(LiftMotion.JOB - 4, 0.0);
        assertTrue(whole < half && half < next, "between the ticks: " + whole + " " + half + " " + next);
        assertThrows(IllegalArgumentException.class, () -> LiftMotion.raise(-1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> LiftMotion.raise(LiftMotion.JOB + 1, 0.0));
    }
}
