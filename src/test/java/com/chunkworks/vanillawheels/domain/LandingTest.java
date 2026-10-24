/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.chunkworks.vanillawheels.domain.Landing.Cell;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

/**
 * Where a rider who logs back in to a vehicle gone is stood (D-0038).
 *
 * <p>Partitions. Feet in open air over: a full block far below (OtatopMalloy's fifty), a slab, a
 * fence, water, a harm, nothing down to the bottom. Feet in the floor's block, standing on it; feet
 * inside a solid block. No room over the floor. Feet in water: an open surface above, a ceiling over
 * the water, water to the top of the world, a surface with no room for the head. Bounds: feet below
 * the bottom, at the top, a height of zero. A cell: a solid with no top, or too tall; another kind
 * with a top.
 */
class LandingTest {
    private static final int MIN = -64;
    private static final int MAX = 320;
    private static final double PLAYER = 1.8;

    /** effects: returns a column open everywhere but at the heights named */
    private static Landing.Column column(Object... heightsAndCells) {
        Map<Integer, Cell> cells = new HashMap<>();
        for (int i = 0; i < heightsAndCells.length; i += 2) {
            cells.put((Integer) heightsAndCells[i], (Cell) heightsAndCells[i + 1]);
        }
        return y -> cells.getOrDefault(y, Cell.OPEN);
    }

    /** effects: returns a column of {@code cell} from {@code from} to {@code to} inclusive, open elsewhere but for the extras */
    private static Landing.Column filled(int from, int to, Cell cell, Object... extras) {
        Landing.Column rest = column(extras);
        return y -> y >= from && y <= to ? cell : rest.at(y);
    }

    private static OptionalDouble stand(Landing.Column column, double feet) {
        return Landing.standAt(column, feet, MIN, MAX, PLAYER);
    }

    @Test
    void inOpenAirTheyGoDownToTheFirstFloorUnderThem() {
        // OtatopMalloy logged back in at y 122.71 over ground whose top block was at 71.
        assertEquals(OptionalDouble.of(72.0), stand(column(71, Cell.solid(1.0)), 122.70676952505119), "a full block's top");
        assertEquals(OptionalDouble.of(71.5), stand(column(71, Cell.solid(0.5)), 100.0), "a slab's");
        assertEquals(OptionalDouble.of(72.5), stand(column(71, Cell.solid(1.5)), 100.0), "a fence's");
        assertEquals(OptionalDouble.of(63.0), stand(filled(40, 62, Cell.WATER), 100.0), "water's surface: a fall into it hurts nobody");
        assertEquals(OptionalDouble.empty(), stand(column(71, Cell.HARM, 60, Cell.solid(1.0)), 100.0), "lava first: never, though there is a floor under it");
        assertEquals(OptionalDouble.empty(), stand(column(), 100.0), "nothing down to the bottom of the world");
    }

    @Test
    void standingOnTheFloorOrInsideABlockTheyStayOrGoToItsTop() {
        assertEquals(OptionalDouble.of(72.0), stand(column(71, Cell.solid(1.0)), 72.0), "standing on it: where they are");
        assertEquals(OptionalDouble.of(71.5), stand(column(71, Cell.solid(0.5)), 71.5), "on a slab: where they are");
        assertEquals(OptionalDouble.of(72.0), stand(column(71, Cell.solid(1.0)), 71.4), "inside the block: on its top");
        assertEquals(OptionalDouble.empty(), stand(column(71, Cell.solid(1.0), 73, Cell.solid(1.0)), 72.0), "a block a block over the floor: no room for the head");
        assertEquals(OptionalDouble.of(74.0), stand(column(71, Cell.solid(1.0), 73, Cell.solid(1.0)), 100.0), "from above it, that block is the floor");
        assertEquals(OptionalDouble.empty(), stand(column(71, Cell.solid(1.0), 72, Cell.HARM), 71.4), "a harm over the floor: no room either");
    }

    @Test
    void inWaterTheyGoUpToItsSurface() {
        // A submarine's crew logged back in with the submarine gone, at 40 in a sea whose surface is 63.
        assertEquals(OptionalDouble.of(63.0), stand(filled(30, 62, Cell.WATER), 40.0), "the surface over them");
        assertEquals(OptionalDouble.empty(), stand(filled(30, 62, Cell.WATER, 63, Cell.solid(1.0)), 40.0), "ice over the water: nowhere");
        assertEquals(OptionalDouble.empty(), stand(filled(30, MAX, Cell.WATER), 40.0), "water to the top of the world: nowhere");
        assertEquals(OptionalDouble.empty(), stand(filled(30, 62, Cell.WATER, 64, Cell.solid(1.0)), 40.0), "a block a block over the surface: no room for the head");
        assertEquals(OptionalDouble.of(63.0), stand(filled(30, 62, Cell.WATER), 62.9), "at the surface already: there");
    }

    @Test
    void breakingABoundThrows() {
        Landing.Column ground = column(71, Cell.solid(1.0));
        assertThrows(IllegalArgumentException.class, () -> Landing.standAt(ground, MIN - 0.5, MIN, MAX, PLAYER));
        assertThrows(IllegalArgumentException.class, () -> Landing.standAt(ground, MAX, MIN, MAX, PLAYER));
        assertThrows(IllegalArgumentException.class, () -> Landing.standAt(ground, 100.0, MIN, MAX, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Cell.solid(0.0));
        assertThrows(IllegalArgumentException.class, () -> Cell.solid(1.6));
        assertThrows(IllegalArgumentException.class, () -> new Cell(Landing.Kind.WATER, 0.5));
    }
}
