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

/**
 * Partitions: the origin inside the box / outside with the ray entering / outside with the
 * ray missing / outside with the box behind; a ray parallel to an axis inside and outside
 * the slab; entry on a face, an edge, a corner.
 */
public class RayBoxTest {
    private static final Vec LO = new Vec(-1, 0, -2), HI = new Vec(1, 1, 2);

    @Test
    public void anOriginInsideIsAtZero() {
        assertEquals(0.0, RayBox.enter(new Vec(0.5, 0.5, 0), new Vec(0, -1, 0), LO, HI), 1e-9);
    }

    @Test
    public void aRayFromAboveEntersAtTheTopFace() {
        assertEquals(2.0, RayBox.enter(new Vec(0, 3, 0), new Vec(0, -1, 0), LO, HI), 1e-9);
    }

    @Test
    public void aSlantedRayEntersWhereItCrossesTheFirstFace() {
        // From (3, 3, 0) toward (-1, -1, 0) per unit: x hits 1 at t = 2, y hits 1 at t = 2 -- an edge.
        assertEquals(2.0, RayBox.enter(new Vec(3, 3, 0), new Vec(-1, -1, 0), LO, HI), 1e-9);
    }

    @Test
    public void aRayThatMissesIsMinusOne() {
        assertEquals(-1.0, RayBox.enter(new Vec(0, 3, 5), new Vec(0, -1, 0), LO, HI), 1e-9);
        assertEquals(-1.0, RayBox.enter(new Vec(5, 0.5, 0), new Vec(0, 0, 1), LO, HI), 1e-9);   // parallel, outside the slab
    }

    @Test
    public void aBoxBehindTheOriginIsMinusOne() {
        assertEquals(-1.0, RayBox.enter(new Vec(0, 3, 0), new Vec(0, 1, 0), LO, HI), 1e-9);
    }

    @Test
    public void aRayParallelToAnAxisInsideTheSlabEntersAtTheOtherFaces() {
        assertEquals(4.0, RayBox.enter(new Vec(0, 0.5, -6), new Vec(0, 0, 1), LO, HI), 1e-9);
    }

    @Test
    public void theDistanceIsInUnitsOfTheDirection() {
        assertEquals(1.0, RayBox.enter(new Vec(0, 3, 0), new Vec(0, -2, 0), LO, HI), 1e-9);
    }
}
