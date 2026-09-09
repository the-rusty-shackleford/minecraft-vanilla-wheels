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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. The exact parts; no chassis; too few wheels; too many;
 * a missing engine; an engine where none is wanted (a trailer); zero
 * wheels refused.
 */
final class AssemblyTest {

    @Test
    void exactPartsOnly() {
        Assembly truck = new Assembly(4, true);
        assertTrue(truck.accepts(true, 4, true));
        assertFalse(truck.accepts(false, 4, true), "no chassis");
        assertFalse(truck.accepts(true, 3, true), "a wheel short");
        assertFalse(truck.accepts(true, 5, true), "a wheel over");
        assertFalse(truck.accepts(true, 4, false), "no engine");
        Assembly trailer = new Assembly(2, false);
        assertTrue(trailer.accepts(true, 2, false));
        assertFalse(trailer.accepts(true, 2, true), "an engine a trailer has no place for");
        assertThrows(IllegalArgumentException.class, () -> new Assembly(0, true));
    }
}
