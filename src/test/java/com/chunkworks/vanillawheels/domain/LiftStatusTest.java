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

import org.junit.jupiter.api.Test;

/** Partitions: every combination of the three facts for each button; busy wins, then the parts, then the deck. */
final class LiftStatusTest {

    @Test
    void buildStatusInPrecedence() {
        for (boolean busy : new boolean[] {false, true}) {
            for (boolean matches : new boolean[] {false, true}) {
                for (boolean occupied : new boolean[] {false, true}) {
                    LiftStatus.Build expected = busy ? LiftStatus.Build.BUSY : !matches ? LiftStatus.Build.NO_RECIPE : occupied ? LiftStatus.Build.OCCUPIED : LiftStatus.Build.READY;
                    assertEquals(expected, LiftStatus.build(busy, matches, occupied), busy + " " + matches + " " + occupied);
                }
            }
        }
    }

    @Test
    void paintStatusInPrecedence() {
        for (boolean busy : new boolean[] {false, true}) {
            for (boolean vehicle : new boolean[] {false, true}) {
                for (boolean dye : new boolean[] {false, true}) {
                    LiftStatus.Paint expected = busy ? LiftStatus.Paint.BUSY : !vehicle ? LiftStatus.Paint.NO_VEHICLE : !dye ? LiftStatus.Paint.NO_DYE : LiftStatus.Paint.READY;
                    assertEquals(expected, LiftStatus.paint(busy, vehicle, dye), busy + " " + vehicle + " " + dye);
                }
            }
        }
    }
}
