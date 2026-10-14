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
 * deck row, the posts, the wall between the back posts and last in the
 * order (the indices before it are what saved lifts carry). Index: round
 * trip on 1..PARTS, 0 for the controller, -1 off the lift, out of range
 * refused. Headings: each of the four turns a known cell to the expected
 * world offset; a part's offset back to the controller undoes its offset;
 * bounds per heading; a point on the deck at a depth, the deck's centre
 * among them; the deck box holds it; the wall's face. Validity: all pass;
 * one cell not free (a wall cell too); one deck cell unsupported (a post
 * cell need not be); free is tested before support. Bars: none on the
 * deck, a column on a front post, a column run on to the wall on a back
 * post (each side), the wall across its cell; a point in a cell lands in
 * its block per heading, the cell's near corner and far corner.
 */
final class FootprintTest {

    @Test
    void theLiftIsThirtyDeckCellsFourPostCellsAndThreeWallCells() {
        assertEquals(30 + 4 + 3, Footprint.cells().size());
        assertEquals(36, Footprint.PARTS);
        assertEquals(new Cell(0, 0, 0), Footprint.cells().get(0), "the controller first");
        Set<Cell> distinct = new HashSet<>(Footprint.cells());
        assertEquals(Footprint.cells().size(), distinct.size(), "no cell twice");
        assertEquals(30, Footprint.cells().stream().filter(Cell::isDeck).count());
        assertEquals(4, Footprint.cells().stream().filter(Cell::isPost).count());
        assertEquals(3, Footprint.cells().stream().filter(Cell::isWall).count());
        for (Cell c : Footprint.cells()) {
            assertTrue(c.right() >= -2 && c.right() <= 2 && c.back() >= 0 && c.back() <= 5 && c.up() >= 0 && c.up() <= 1, "within the shape: " + c);
            assertEquals(1, (c.isDeck() ? 1 : 0) + (c.isPost() ? 1 : 0) + (c.isWall() ? 1 : 0), "deck, post or wall, one of them: " + c);
            if (c.isPost()) {
                assertTrue(Math.abs(c.right()) == 2 && (c.back() == 0 || c.back() == 5), "posts stand on the corners: " + c);
            }
            if (c.isWall()) {
                assertTrue(Math.abs(c.right()) <= 1 && c.back() == 5 && c.up() == 1, "the wall stands on the back row between the posts: " + c);
            }
        }
    }

    @Test
    void theWallComesLastSoALiftSavedBeforeItKeepsEveryIndex() {
        // 1.16.0's lift: 29 deck parts then 4 posts, indices 1..33; the wall is 34..36.
        for (int i = 1; i <= 29; i++) {
            assertTrue(Footprint.cell(i).isDeck(), "deck at " + i);
        }
        for (int i = 30; i <= 33; i++) {
            assertTrue(Footprint.cell(i).isPost(), "post at " + i);
        }
        for (int i = 34; i <= 36; i++) {
            assertTrue(Footprint.cell(i).isWall(), "wall at " + i);
        }
        assertEquals(new Cell(-1, 1, 5), Footprint.cell(34));
        assertEquals(new Cell(1, 1, 5), Footprint.cell(36));
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
        Cell backRight = new Cell(2, 0, 5);
        // Looking north (-Z): the deck runs south (+Z), right is east (+X).
        assertArrayEquals(new int[] {2, 0, 5}, Heading.NORTH.offset(backRight));
        // Looking south (+Z): the deck runs north, right is west.
        assertArrayEquals(new int[] {-2, 0, -5}, Heading.SOUTH.offset(backRight));
        // Looking east (+X): the deck runs west, right is south (+Z).
        assertArrayEquals(new int[] {-5, 0, 2}, Heading.EAST.offset(backRight));
        // Looking west (-X): the deck runs east, right is north.
        assertArrayEquals(new int[] {5, 0, -2}, Heading.WEST.offset(backRight));
        Cell post = new Cell(-2, 1, 0);
        assertArrayEquals(new int[] {-2, 1, 0}, Heading.NORTH.offset(post));
        for (Heading h : Heading.values()) {
            for (int i = 1; i <= Footprint.PARTS; i++) {
                int[] o = h.offset(Footprint.cell(i));
                int[] back = Footprint.toController(i, h);
                assertArrayEquals(new int[] {0, 0, 0}, new int[] {o[0] + back[0], o[1] + back[1], o[2] + back[2]}, "back to the controller from " + i + " facing " + h);
            }
        }
    }

    @Test
    void boundsPointsOnTheDeckAndTheDeckBoxPerHeading() {
        int[][] north = Footprint.bounds(Heading.NORTH);
        assertArrayEquals(new int[] {-2, 0, 0}, north[0]);
        assertArrayEquals(new int[] {2, 1, 5}, north[1], "the wall adds nothing to the bounds");
        int[][] east = Footprint.bounds(Heading.EAST);
        assertArrayEquals(new int[] {-5, 0, -2}, east[0]);
        assertArrayEquals(new int[] {0, 1, 2}, east[1]);
        double centre = Footprint.LENGTH / 2.0;
        assertArrayEquals(new double[] {0.5, 1.0, 3.0}, Footprint.onDeck(Heading.NORTH, centre), 1e-12, "the deck's centre, on top of it");
        assertArrayEquals(new double[] {-2.0, 1.0, 0.5}, Footprint.onDeck(Heading.EAST, centre), 1e-12);
        // Depth runs back from the front edge: the front edge itself, then deeper along the look's opposite.
        assertArrayEquals(new double[] {0.5, 1.0, 0.0}, Footprint.onDeck(Heading.NORTH, 0.0), 1e-12);
        assertArrayEquals(new double[] {0.5, 1.0, 1.0}, Footprint.onDeck(Heading.SOUTH, 0.0), 1e-12);
        assertArrayEquals(new double[] {-4.5, 1.0, 0.5}, Footprint.onDeck(Heading.EAST, 5.5), 1e-12);
        assertArrayEquals(new double[] {5.5, 1.0, 0.5}, Footprint.onDeck(Heading.WEST, 5.5), 1e-12);
        for (Heading h : Heading.values()) {
            double[][] box = Footprint.deckBox(h);
            double[] s = Footprint.onDeck(h, centre);
            for (int i = 0; i < 3; i++) {
                assertTrue(s[i] >= box[0][i] && s[i] <= box[1][i], "the centre is inside the deck box facing " + h + " on axis " + i);
            }
            assertEquals(1.0, box[0][1], 1e-12, "from the deck's top");
            assertEquals(2.0, box[1][1], 1e-12, "to the posts' top");
        }
        assertEquals(0.0f, Heading.SOUTH.yaw());
        assertEquals(-90.0f, Heading.EAST.yaw());
        assertEquals(5.0 + 5.0 / 16.0, Footprint.WALL_FACE, 1e-12, "the wall's face: five cells back, at the bar's near edge");
    }

    @Test
    void barsStandOnThePostsAndTheWallAndMeetAlongTheBackRow() {
        assertTrue(Footprint.bars(new Cell(1, 0, 3)).isEmpty(), "nothing stands on a deck cell");
        double lo = Footprint.BAR_MIN, hi = Footprint.BAR_MAX;
        assertBars(Footprint.bars(new Cell(-2, 1, 0)), new double[] {lo, hi, lo, hi});
        assertBars(Footprint.bars(new Cell(2, 1, 0)), new double[] {lo, hi, lo, hi});
        assertBars(Footprint.bars(new Cell(-2, 1, 5)), new double[] {lo, 1.0, lo, hi});
        assertBars(Footprint.bars(new Cell(2, 1, 5)), new double[] {0.0, hi, lo, hi});
        for (int right = -1; right <= 1; right++) {
            assertBars(Footprint.bars(new Cell(right, 1, 5)), new double[] {0.0, 1.0, lo, hi});
        }
    }

    private static void assertBars(java.util.List<double[]> bars, double[]... expected) {
        assertEquals(expected.length, bars.size());
        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals(expected[i], bars.get(i), 1e-12, "bar " + i);
        }
    }

    @Test
    void aPointInACellLandsInItsBlockPerHeading() {
        // The cell's corner where right and back are least, and the opposite one.
        assertArrayEquals(new double[] {0.0, 0.0}, Heading.NORTH.local(0.0, 0.0), 1e-12);
        assertArrayEquals(new double[] {1.0, 1.0}, Heading.SOUTH.local(0.0, 0.0), 1e-12);
        assertArrayEquals(new double[] {1.0, 0.0}, Heading.EAST.local(0.0, 0.0), 1e-12);
        assertArrayEquals(new double[] {0.0, 1.0}, Heading.WEST.local(0.0, 0.0), 1e-12);
        // A quarter across, three quarters back: looking north that is x 0.25, z 0.75.
        assertArrayEquals(new double[] {0.25, 0.75}, Heading.NORTH.local(0.25, 0.75), 1e-12);
        assertArrayEquals(new double[] {0.75, 0.25}, Heading.SOUTH.local(0.25, 0.75), 1e-12);
        assertArrayEquals(new double[] {0.25, 0.25}, Heading.EAST.local(0.25, 0.75), 1e-12, "east: back runs -X, right runs +Z");
        assertArrayEquals(new double[] {0.75, 0.75}, Heading.WEST.local(0.25, 0.75), 1e-12);
        // Consistent with offset(): a cell's far corner lies a block along each axis the cell's offset grows.
        for (Heading h : Heading.values()) {
            int[] near = h.offset(new Cell(0, 0, 0));
            int[] far = h.offset(new Cell(1, 0, 1));
            double[] l = h.local(1.0, 1.0);
            assertEquals(far[0] - near[0] > 0 ? 1.0 : 0.0, l[0], 1e-12, "x facing " + h);
            assertEquals(far[2] - near[2] > 0 ? 1.0 : 0.0, l[2 - 1], 1e-12, "z facing " + h);
        }
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
        Cell wall = new Cell(0, 1, 5);
        assertEquals(wall, Footprint.firstBlocked(c -> !c.equals(wall), c -> true), "a wall cell must be free");
        assertNull(Footprint.firstBlocked(c -> true, c -> !c.equals(wall)), "and needs no ground under it");
        Cell first = Footprint.firstBlocked(c -> !c.equals(soft), c -> !c.equals(blocked));
        assertNotNull(first);
        assertEquals(soft, first, "not free is reported before unsupported");
    }
}
