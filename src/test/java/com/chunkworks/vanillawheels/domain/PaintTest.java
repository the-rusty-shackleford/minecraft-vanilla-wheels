/*
 * Vanilla Wheels - vehicles for Minecraft.
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

/**
 * Partitions. Channels: 0, 255, in between; each channel on its own;
 * the top byte ignored. Colours: white is a fixed point; black lifts to
 * one grey; the light-blue dye, the case that motivated the lift.
 */
final class PaintTest {

    @Test
    void whiteStaysWhiteAndBlackBecomesAGrey() {
        assertEquals(0xFFFFFF, Paint.lift(0xFFFFFF));
        assertEquals(0x404040, Paint.lift(0x000000), "a quarter of the way to white, rounded");
    }

    @Test
    void eachChannelLiftsOnItsOwn() {
        assertEquals(0x40FF40, Paint.lift(0x00FF00), "only the green channel was full");
        assertEquals(0xFF4040, Paint.lift(0xFF0000));
        assertEquals(0x4040FF, Paint.lift(0x0000FF));
    }

    @Test
    void theTopByteIsIgnored() {
        assertEquals(Paint.lift(0x3AB3DA), Paint.lift(0xFF3AB3DA));
    }

    @Test
    void theLightBlueDyeReadsSkyBlue() {
        // (58, 179, 218) -> (107, 198, 227): more red in it than the dye can give by multiplying alone.
        assertEquals((107 << 16) | (198 << 8) | 227, Paint.lift(0x3AB3DA));
    }
}
