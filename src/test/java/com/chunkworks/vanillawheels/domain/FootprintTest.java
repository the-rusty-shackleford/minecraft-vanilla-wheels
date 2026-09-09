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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chunkworks.vanillawheels.domain.Footprint.Cell;
import com.chunkworks.vanillawheels.domain.Footprint.Heading;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Cells: the count, distinctness, the controller first, the
 * deck row and the posts. Index: round trip on 1..PARTS, 0 for the
 * controller, -1 off the lift, out of range refused. Headings: each of the
 * four turns a known cell to the expected world offset; a part's offset
 * back to the controller undoes its offset; bounds per heading; the spawn
 * is the deck centre one up; the deck box holds the spawn. Validity: all
 * pass; one cell not free; one deck cell unsupported (a post cell need
 * not be); free is tested before support.
 */
final class FootprintTest {

    @Test
    void theLiftIsThirtyFiveDeckCellsAndEightPostCells() {
        assertEquals(35 + 8, Footprint.cells().size());
        assertEquals(42, Footprint.PARTS);
        assertEquals(new Cell(0, 0, 0), Footprint.cells().get(0), "the controller first");
        Set<Cell> distinct = new HashSet<>(Footprint.cells());
        assertEquals(Footprint.cells().size(), distinct.size(), "no cell twice");
        long deck = Footprint.cells().stream().filter(Cell::isDeck).count();
        assertEquals(35, deck);
        for (Cell c : Footprint.cells()) {
            assertTrue(c.right() >= -2 && c.right() <= 2 && c.back() >= 0 && c.back() <= 6 && c.up() >= 0 && c.up() <= 2, "within the shape: " + c);
            if (!c.isDeck()) {
                assertTrue(Math.abs(c.right()) == 2 && (c.back() == 0 || c.back() == 6), "posts stand on the corners: " + c);
            }
        }
    }

    @Test
    void indicesRoundTrip() {
        for (int i = 1; i <= Footprint.PARTS; i++) {
            assertEquals(i, Footprint.indexOf(Footprint.cell(i)));
        }
        assertEquals(0, Footprint.indexOf(new Cell(0, 0, 0)));
        assertEquals(-1, Footprint.indexOf(new Cell(3, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> Footprint.cell(0));
        assertThrows(IllegalArgumentException.class, () -> Footprint.cell(Footprint.PARTS + 1));
    }

    @Test
    void eachHeadingTurnsTheDeckBehindTheFrontAndRightToTheRight() {
        Cell backRight = new Cell(2, 0, 6);
        // Looking north (-Z): the deck runs south (+Z), right is east (+X).
        assertArrayEquals(new int[] {2, 0, 6}, Heading.NORTH.offset(backRight));
        // Looking south (+Z): the deck runs north, right is west.
        assertArrayEquals(new int[] {-2, 0, -6}, Heading.SOUTH.offset(backRight));
        // Looking east (+X): the deck runs west, right is south (+Z).
        assertArrayEquals(new int[] {-6, 0, 2}, Heading.EAST.offset(backRight));
        // Looking west (-X): the deck runs east, right is north.
        assertArrayEquals(new int[] {6, 0, -2}, Heading.WEST.offset(backRight));
        Cell post = new Cell(-2, 2, 0);
        assertArrayEquals(new int[] {-2, 2, 0}, Heading.NORTH.offset(post));
        for (Heading h : Heading.values()) {
            for (int i = 1; i <= Footprint.PARTS; i++) {
                int[] o = h.offset(Footprint.cell(i));
                int[] back = Footprint.toController(i, h);
                assertArrayEquals(new int[] {0, 0, 0}, new int[] {o[0] + back[0], o[1] + back[1], o[2] + back[2]}, "back to the controller from " + i + " facing " + h);
            }
        }
    }

    @Test
    void boundsSpawnAndDeckBoxPerHeading() {
        int[][] north = Footprint.bounds(Heading.NORTH);
        assertArrayEquals(new int[] {-2, 0, 0}, north[0]);
        assertArrayEquals(new int[] {2, 2, 6}, north[1]);
        int[][] east = Footprint.bounds(Heading.EAST);
        assertArrayEquals(new int[] {-6, 0, -2}, east[0]);
        assertArrayEquals(new int[] {0, 2, 2}, east[1]);
        double[] spawn = Footprint.spawn(Heading.NORTH);
        assertArrayEquals(new double[] {0.5, 1.0, 3.5}, spawn, 1e-12, "the deck's centre, on top of it");
        assertArrayEquals(new double[] {-2.5, 1.0, 0.5}, Footprint.spawn(Heading.EAST), 1e-12);
        for (Heading h : Heading.values()) {
            double[][] box = Footprint.deckBox(h);
            double[] s = Footprint.spawn(h);
            for (int i = 0; i < 3; i++) {
                assertTrue(s[i] >= box[0][i] && s[i] <= box[1][i], "the spawn is inside the deck box facing " + h + " on axis " + i);
            }
            assertEquals(1.0, box[0][1], 1e-12, "from the deck's top");
            assertEquals(3.0, box[1][1], 1e-12, "to the posts' top");
        }
        assertEquals(0.0f, Heading.SOUTH.yaw());
        assertEquals(-90.0f, Heading.EAST.yaw());
    }

    @Test
    void validityNamesTheFirstCellInTheWay() {
        assertNull(Footprint.firstBlocked(c -> true, c -> true));
        Cell blocked = new Cell(1, 0, 3);
        assertEquals(blocked, Footprint.firstBlocked(c -> !c.equals(blocked), c -> true));
        Cell soft = new Cell(-1, 0, 5);
        assertEquals(soft, Footprint.firstBlocked(c -> true, c -> !c.equals(soft)));
        Cell post = new Cell(2, 1, 0);
        assertNull(Footprint.firstBlocked(c -> true, c -> !c.equals(post)), "a post needs no ground under it");
        Cell first = Footprint.firstBlocked(c -> !c.equals(soft), c -> !c.equals(blocked));
        assertNotNull(first);
        assertEquals(soft, first, "not free is reported before unsupported");
    }
}
