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
 * Partitions. Room for four adults or eight young: four adults then a fifth
 * refused; eight young then a ninth refused; two adults and four young, then
 * either refused; three adults, two young, a third young refused; with()
 * refuses what accepts() refuses; counts; the fraction at empty, half, full;
 * bad room and counts refused; an over-full construction refused.
 */
final class CargoTest {

    @Test
    void fourAdultsOrEightYoungOrAMix() {
        Cargo c = Cargo.empty(4, 8);
        assertEquals(0.0, c.fraction());
        for (int i = 0; i < 4; i++) {
            assertTrue(c.accepts(false), "adult " + (i + 1));
            c = c.with(false);
        }
        assertFalse(c.accepts(false), "a fifth adult");
        assertFalse(c.accepts(true), "nor a calf");
        assertEquals(1.0, c.fraction(), 1e-12);
        assertEquals(4, c.count());

        Cargo young = Cargo.empty(4, 8);
        for (int i = 0; i < 8; i++) {
            young = young.with(true);
        }
        assertFalse(young.accepts(true), "a ninth young");
        assertEquals(8, young.count());

        Cargo mix = Cargo.empty(4, 8).with(false).with(false).with(true).with(true).with(true).with(true);
        assertEquals(1.0, mix.fraction(), 1e-12, "two adults and four young fill it");
        assertFalse(mix.accepts(false));
        assertFalse(mix.accepts(true));

        Cargo three = Cargo.empty(4, 8).with(false).with(false).with(false).with(true).with(true);
        assertFalse(three.accepts(true), "three adults and two young leave no half");
        assertEquals(0.5, Cargo.empty(4, 8).with(false).with(false).fraction(), 1e-12);
        Cargo full = mix;
        assertThrows(IllegalArgumentException.class, () -> full.with(true));
    }

    @Test
    void boundsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Cargo(0, 8, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cargo(4, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cargo(4, 8, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cargo(4, 8, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cargo(4, 8, 2, 5));
    }
}
