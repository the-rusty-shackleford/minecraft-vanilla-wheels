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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Filling: into an empty tank, up to exactly full, one over
 * (refused, unchanged), a zero or negative fuel (refused). Burning: some,
 * more than is left, none, negative. Fraction: empty, half, full, a tank
 * of no capacity. Bounds: ticks over capacity refused.
 */
final class TankTest {

    @Test
    void aFuelFitsWholeOrNotAtAll() {
        Tank t = Tank.empty(3200);
        assertFalse(t.hasFuel());
        Tank one = t.fill(1600);
        assertEquals(1600, one.ticks());
        assertTrue(one.hasFuel());
        Tank full = one.fill(1600);
        assertEquals(3200, full.ticks());
        assertEquals(1.0, full.fraction());
        assertFalse(full.accepts(1));
        assertEquals(full, full.fill(1600), "a coal that does not fit is refused, and stays in the hand");
        Tank half = Tank.empty(3200).fill(1600);
        assertEquals(0.5, half.fraction());
        assertFalse(half.accepts(1601));
        assertTrue(half.accepts(1600));
        assertThrows(IllegalArgumentException.class, () -> t.fill(0));
        assertThrows(IllegalArgumentException.class, () -> t.fill(-5));
    }

    @Test
    void burningTakesWhatIsThere() {
        Tank t = Tank.empty(1000).fill(400);
        assertEquals(390, t.burn(10).ticks());
        assertEquals(0, t.burn(1000).ticks(), "no further than empty");
        assertEquals(t, t.burn(0));
        assertThrows(IllegalArgumentException.class, () -> t.burn(-1));
    }

    @Test
    void theRepIsHeld() {
        assertThrows(IllegalArgumentException.class, () -> new Tank(5, 4));
        assertThrows(IllegalArgumentException.class, () -> new Tank(-1, 4));
        assertEquals(0.0, Tank.empty(0).fraction(), "a tank of nothing is never full");
        assertFalse(Tank.empty(0).accepts(1));
    }
}
