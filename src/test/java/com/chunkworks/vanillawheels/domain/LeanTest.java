/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Chunkworks
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
 * Partitions. The turn: none, right, left; at speed, standing, reversing. The balance: under the
 * most, held at the most either way. Easing: from upright, at the target, a full rate. Refused: a
 * most of nothing or a right angle, a parked lean past the most, a rate of nothing or over one,
 * speed or turn not finite.
 */
final class LeanTest {
    private static final Lean BIKE = new Lean(Math.toRadians(35), Math.toRadians(-8), Lean.RATE);

    @Test
    void goingStraightOrStandingItIsUpright() {
        assertEquals(0.0, BIKE.balanced(0.6, 0.0), 0.0);
        assertEquals(0.0, BIKE.balanced(0.0, 0.05), 0.0);
    }

    @Test
    void itLeansIntoTheTurnAsFarAsBalanceNeeds() {
        double right = BIKE.balanced(0.3, 0.05);
        assertEquals(Math.atan(0.3 * 0.05 / 0.08), right, 1e-12);
        assertTrue(right > 0.0, "a right turn leans right");
        assertEquals(-right, BIKE.balanced(0.3, -0.05), 1e-12, "a left turn as far left");
        assertTrue(BIKE.balanced(-0.2, 0.05) < 0.0, "reversing, the pull is the other way");
    }

    @Test
    void aHardTurnIsHeldAtTheMost() {
        assertEquals(Math.toRadians(35), BIKE.balanced(0.9, 0.2), 1e-12);
        assertEquals(-Math.toRadians(35), BIKE.balanced(0.9, -0.2), 1e-12);
    }

    @Test
    void itEasesTowardItsTarget() {
        assertEquals(0.25, BIKE.toward(0.0, 1.0), 1e-12);
        assertEquals(0.4, BIKE.toward(0.4, 0.4), 0.0, "at the target it stays");
        assertEquals(-0.3, new Lean(1.0, 0.0, 1.0).toward(0.2, -0.3), 1e-12, "a full rate arrives at once");
        double r = 0.0;
        for (int i = 0; i < 40; i++) {
            r = BIKE.toward(r, BIKE.parked());
        }
        assertEquals(BIKE.parked(), r, 1e-4, "settled onto the stand");
    }

    @Test
    void aLeanIsSoundOrRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Lean(0.0, 0.0, 0.25));
        assertThrows(IllegalArgumentException.class, () -> new Lean(Math.PI / 2, 0.0, 0.25));
        assertThrows(IllegalArgumentException.class, () -> new Lean(0.3, 0.4, 0.25));
        assertThrows(IllegalArgumentException.class, () -> new Lean(0.3, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Lean(0.3, 0.0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> BIKE.balanced(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> BIKE.balanced(0.1, Double.POSITIVE_INFINITY));
    }
}
