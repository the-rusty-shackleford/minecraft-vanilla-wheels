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

/**
 * Car paint from a dye. A dye's colour is a wool's: matte and deep. A
 * body swatch is a grey the colour multiplies into, so the paint can only
 * ever be as light as the dye, and light blue comes out teal. Lifting the
 * dye a quarter of the way toward white before it multiplies gives every
 * colour a coat of gloss: light blue reads sky blue, red stays red, black
 * is a dark grey.
 */
public final class Paint {
    private Paint() {}

    /** How far toward white a dye is lifted, 0..1. */
    public static final double LIFT = 0.25;

    /**
     * requires: rgb is a colour in 0xRRGGBB, its top byte ignored
     * effects: returns the colour with each channel moved LIFT of the way
     *     from its value to 255, rounded, as 0xRRGGBB
     */
    public static int lift(int rgb) {
        int r = channel(rgb >> 16);
        int g = channel(rgb >> 8);
        int b = channel(rgb);
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(int v) {
        int c = v & 0xFF;
        return (int) Math.round(c + (255 - c) * LIFT);
    }
}
