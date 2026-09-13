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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drawn pose's value: its invariant, and what "settled" means. The posing itself is {@link TerrainTest}'s. */
class SuspensionTest {

    @Test
    void levelIsSettledAndATiltOrALiftIsNot() {
        assertTrue(Suspension.LEVEL.isSettled());
        assertTrue(new Suspension(0.005, 0.001, -0.001).isSettled());
        assertFalse(new Suspension(0.2, 0.0, 0.0).isSettled());
        assertFalse(new Suspension(0.0, 0.1, 0.0).isSettled());
        assertFalse(new Suspension(0.0, 0.0, -0.1).isSettled());
    }

    @Test
    void refusesWhatCannotBeDrawn() {
        assertThrows(IllegalArgumentException.class, () -> new Suspension(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Suspension(0, 2.0, 0), "a pitch past a right angle");
        assertThrows(IllegalArgumentException.class, () -> new Suspension(0, 0, -2.0), "a roll past a right angle");
    }
}
