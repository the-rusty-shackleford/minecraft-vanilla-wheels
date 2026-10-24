/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Chunkworks
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chunkworks.vanillawheels.domain.Footprint.Cell;
import com.chunkworks.vanillawheels.domain.Footprint.Heading;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Shapes: the short one and the long one, by flag; each one's
 * count, distinctness, the controller first, the deck rows, the posts on
 * the corners, the wall between the back posts and last in the order; the
 * short shape's order exactly as 1.16.0 and 1.17.0 laid it (the indices
 * saved lifts carry), the long shape's parts the most any lift has. Index:
 * round trip on 1..parts(), 0 for the controller, -1 off the lift, out of
 * range refused, in each shape. Headings: each of the four turns a known
 * cell to the expected world offset; a part's offset back to the controller
 * undoes its offset; bounds per heading and shape; a point on the deck at a
 * depth, the deck's centre among them; the deck box holds it; the wall's
 * face per shape. Validity: all pass; one cell not free (a wall cell too);
 * one deck cell unsupported (a post cell need not be); free is tested
 * before support. Bars: none on the deck, a column on a front post, a
 * column run on to the wall on a back post (each side), the wall across its
 * cell, in each shape; a point in a cell lands in its block per heading.
 */
final class FootprintTest {

    @Test
    void twoShapesByFlagSixAndEightLong() {
        assertSame(Footprint.SHORT, Footprint.of(false));
        assertSame(Footprint.LONG, Footprint.of(true));
        assertEquals(6, Footprint.SHORT.length());
        assertEquals(8, Footprint.LONG.length());
        assertFalse(Footprint.SHORT.isLong());
        assertTrue(Footprint.LONG.isLong());
        assertEquals(36, Footprint.SHORT.parts(), "29 deck parts, 4 posts, 3 wall cells");
        assertEquals(46, Footprint.LONG.parts(), "39 deck parts, 4 posts, 3 wall cells");
        assertEquals(Footprint.LONG.parts(), Footprint.MAX_PARTS, "the long shape has the most parts");
    }

    @Test
    void eachShapeIsADeckFourPostsAndThreeWallCellsDistinctWithTheControllerFirst() {
        for (Footprint f : List.of(Footprint.SHORT, Footprint.LONG)) {
            int back = f.length() - 1;
            assertEquals(Footprint.WIDTH * f.length() + 4 + 3, f.cells().size(), "cells of " + f.length());
            assertEquals(new Cell(0, 0, 0), f.cells().get(0), "the controller first");
            Set<Cell> distinct = new HashSet<>(f.cells());
            assertEquals(f.cells().size(), distinct.size(), "no cell twice");
            assertEquals(Footprint.WIDTH * f.length(), f.cells().stream().filter(Cell::isDeck).count());
            assertEquals(4, f.cells().stream().filter(Cell::isPost).count());
            assertEquals(3, f.cells().stream().filter(Cell::isWall).count());
            for (Cell c : f.cells()) {
                assertTrue(c.right() >= -2 && c.right() <= 2 && c.back() >= 0 && c.back() <= back && c.up() >= 0 && c.up() <= 1, "within the shape: " + c);
                assertEquals(1, (c.isDeck() ? 1 : 0) + (c.isPost() ? 1 : 0) + (c.isWall() ? 1 : 0), "deck, post or wall, one of them: " + c);
                if (c.isPost()) {
                    assertTrue(Math.abs(c.right()) == 2 && (c.back() == 0 || c.back() == back), "posts stand on the corners: " + c);
                }
                if (c.isWall()) {
                    assertTrue(Math.abs(c.right()) <= 1 && c.back() == back && c.up() == 1, "the wall stands on the back row between the posts: " + c);
                }
            }
        }
    }

    @Test
    void theShortShapeIsOrderedAsEveryLiftBeforeItWasLaidSoASavedLiftKeepsEveryIndex() {
        // 1.16.0's lift: 29 deck parts then 4 posts, indices 1..33; 1.17.0's wall is 34..36; 1.18.0 moves nothing.
        Footprint f = Footprint.SHORT;
        int i = 1;
        for (int back = 0; back < 6; back++) {
            for (int right = -2; right <= 2; right++) {
                if (back != 0 || right != 0) {
                    assertEquals(new Cell(right, 0, back), f.cell(i), "deck part " + i);
                    i++;
                }
            }
        }
        assertEquals(30, i);
        assertEquals(new Cell(-2, 1, 0), f.cell(30));
        assertEquals(new Cell(-2, 1, 5), f.cell(31));
        assertEquals(new Cell(2, 1, 0), f.cell(32));
        assertEquals(new Cell(2, 1, 5), f.cell(33));
        assertEquals(new Cell(-1, 1, 5), f.cell(34));
        assertEquals(new Cell(0, 1, 5), f.cell(35));
        assertEquals(new Cell(1, 1, 5), f.cell(36));
    }

    @Test
    void theLongShapeIsOrderedTheSameWayOverEightRows() {
        Footprint f = Footprint.LONG;
        for (int i = 1; i <= 39; i++) {
            assertTrue(f.cell(i).isDeck(), "deck at " + i);
        }
        assertEquals(new Cell(2, 0, 7), f.cell(39), "the last deck cell is the back-right corner");
        for (int i = 40; i <= 43; i++) {
            assertTrue(f.cell(i).isPost(), "post at " + i);
        }
        assertEquals(new Cell(-2, 1, 7), f.cell(41));
        for (int i = 44; i <= 46; i++) {
            assertTrue(f.cell(i).isWall(), "wall at " + i);
        }
        assertEquals(new Cell(-1, 1, 7), f.cell(44));
        assertEquals(new Cell(1, 1, 7), f.cell(46));
    }

    @Test
    void indicesRoundTripInEachShape() {
        for (Footprint f : List.of(Footprint.SHORT, Footprint.LONG)) {
            for (int i = 1; i <= f.parts(); i++) {
                assertEquals(i, f.indexOf(f.cell(i)));
            }
            assertEquals(0, f.indexOf(new Cell(0, 0, 0)));
            assertEquals(-1, f.indexOf(new Cell(3, 0, 0)));
            assertEquals(-1, f.indexOf(new Cell(0, 0, f.length())), "a row past the deck is no cell");
            assertThrows(IllegalArgumentException.class, () -> f.cell(0));
            assertThrows(IllegalArgumentException.class, () -> f.cell(f.parts() + 1));
        }
        assertEquals(-1, Footprint.SHORT.indexOf(new Cell(0, 0, 7)), "the long shape's back row is off the short one");
        assertEquals(37, Footprint.LONG.indexOf(new Cell(0, 0, 7)), "and a deck cell of the long one");
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
        for (Footprint f : List.of(Footprint.SHORT, Footprint.LONG)) {
            for (Heading h : Heading.values()) {
                for (int i = 1; i <= f.parts(); i++) {
                    int[] o = h.offset(f.cell(i));
                    int[] back = f.toController(i, h);
                    assertArrayEquals(new int[] {0, 0, 0}, new int[] {o[0] + back[0], o[1] + back[1], o[2] + back[2]}, "back to the controller from " + i + " facing " + h + " on " + f.length());
                }
            }
        }
    }

    @Test
    void boundsPointsOnTheDeckAndTheDeckBoxPerHeadingAndShape() {
        int[][] north = Footprint.SHORT.bounds(Heading.NORTH);
        assertArrayEquals(new int[] {-2, 0, 0}, north[0]);
        assertArrayEquals(new int[] {2, 1, 5}, north[1], "the wall adds nothing to the bounds");
        int[][] east = Footprint.SHORT.bounds(Heading.EAST);
        assertArrayEquals(new int[] {-5, 0, -2}, east[0]);
        assertArrayEquals(new int[] {0, 1, 2}, east[1]);
        int[][] longNorth = Footprint.LONG.bounds(Heading.NORTH);
        assertArrayEquals(new int[] {-2, 0, 0}, longNorth[0]);
        assertArrayEquals(new int[] {2, 1, 7}, longNorth[1], "two rows deeper");
        assertArrayEquals(new int[] {-7, 0, -2}, Footprint.LONG.bounds(Heading.EAST)[0]);
        for (Footprint f : List.of(Footprint.SHORT, Footprint.LONG)) {
            double centre = f.length() / 2.0;
            assertArrayEquals(new double[] {0.5, 1.0, centre}, f.onDeck(Heading.NORTH, centre), 1e-12, "the deck's centre, on top of it");
            assertArrayEquals(new double[] {1.0 - centre, 1.0, 0.5}, f.onDeck(Heading.EAST, centre), 1e-12);
            // Depth runs back from the front edge: the front edge itself, then deeper along the look's opposite.
            assertArrayEquals(new double[] {0.5, 1.0, 0.0}, f.onDeck(Heading.NORTH, 0.0), 1e-12);
            assertArrayEquals(new double[] {0.5, 1.0, 1.0}, f.onDeck(Heading.SOUTH, 0.0), 1e-12);
            assertArrayEquals(new double[] {-4.5, 1.0, 0.5}, f.onDeck(Heading.EAST, 5.5), 1e-12);
            assertArrayEquals(new double[] {5.5, 1.0, 0.5}, f.onDeck(Heading.WEST, 5.5), 1e-12);
            for (Heading h : Heading.values()) {
                double[][] box = f.deckBox(h);
                double[] s = f.onDeck(h, centre);
                for (int i = 0; i < 3; i++) {
                    assertTrue(s[i] >= box[0][i] && s[i] <= box[1][i], "the centre is inside the deck box facing " + h + " on axis " + i);
                }
                double[] end = f.onDeck(h, f.length() - 0.01);
                for (int i = 0; i < 3; i++) {
                    assertTrue(end[i] >= box[0][i] && end[i] <= box[1][i], "the back of the deck is inside the deck box facing " + h + " on axis " + i);
                }
                assertEquals(1.0, box[0][1], 1e-12, "from the deck's top");
                assertEquals(2.0, box[1][1], 1e-12, "to the posts' top");
            }
        }
        double[][] longBox = Footprint.LONG.deckBox(Heading.NORTH);
        assertEquals(8.0, longBox[1][2], 1e-12, "the long deck box runs eight blocks back");
        assertEquals(0.0f, Heading.SOUTH.yaw());
        assertEquals(-90.0f, Heading.EAST.yaw());
        assertEquals(5.0 + 5.0 / 16.0, Footprint.SHORT.wallFace(), 1e-12, "the wall's face: five cells back, at the bar's near edge");
        assertEquals(7.0 + 5.0 / 16.0, Footprint.LONG.wallFace(), 1e-12, "seven cells back on the long deck");
    }

    @Test
    void barsStandOnThePostsAndTheWallAndMeetAlongTheBackRowInEachShape() {
        double lo = Footprint.BAR_MIN, hi = Footprint.BAR_MAX;
        for (Footprint f : List.of(Footprint.SHORT, Footprint.LONG)) {
            int back = f.length() - 1;
            assertTrue(f.bars(new Cell(1, 0, 3)).isEmpty(), "nothing stands on a deck cell");
            assertBars(f.bars(new Cell(-2, 1, 0)), new double[] {lo, hi, lo, hi});
            assertBars(f.bars(new Cell(2, 1, 0)), new double[] {lo, hi, lo, hi});
            assertBars(f.bars(new Cell(-2, 1, back)), new double[] {lo, 1.0, lo, hi});
            assertBars(f.bars(new Cell(2, 1, back)), new double[] {0.0, hi, lo, hi});
            for (int right = -1; right <= 1; right++) {
                assertBars(f.bars(new Cell(right, 1, back)), new double[] {0.0, 1.0, lo, hi});
            }
        }
        assertBars(Footprint.LONG.bars(new Cell(-2, 1, 5)), new double[] {lo, hi, lo, hi}, "on the long deck row five is no back row: a plain column there");
    }

    private static void assertBars(java.util.List<double[]> bars, double[]... expected) {
        assertEquals(expected.length, bars.size());
        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals(expected[i], bars.get(i), 1e-12, "bar " + i);
        }
    }

    private static void assertBars(java.util.List<double[]> bars, double[] expected, String why) {
        assertEquals(1, bars.size(), why);
        assertArrayEquals(expected, bars.get(0), 1e-12, why);
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
        for (Footprint f : List.of(Footprint.SHORT, Footprint.LONG)) {
            int back = f.length() - 1;
            assertNull(f.firstBlocked(c -> true, c -> true));
            Cell blocked = new Cell(1, 0, 3);
            assertEquals(blocked, f.firstBlocked(c -> !c.equals(blocked), c -> true));
            Cell soft = new Cell(-1, 0, back);
            assertEquals(soft, f.firstBlocked(c -> true, c -> !c.equals(soft)));
            Cell post = new Cell(2, 1, 0);
            assertNull(f.firstBlocked(c -> true, c -> !c.equals(post)), "a post needs no ground under it");
            Cell wall = new Cell(0, 1, back);
            assertEquals(wall, f.firstBlocked(c -> !c.equals(wall), c -> true), "a wall cell must be free");
            assertNull(f.firstBlocked(c -> true, c -> !c.equals(wall)), "and needs no ground under it");
            Cell first = f.firstBlocked(c -> !c.equals(soft), c -> !c.equals(blocked));
            assertNotNull(first);
            assertEquals(soft, first, "not free is reported before unsupported");
        }
        Cell seventh = new Cell(0, 0, 7);
        assertEquals(seventh, Footprint.LONG.firstBlocked(c -> !c.equals(seventh), c -> true), "the long deck needs its eighth row");
        assertNull(Footprint.SHORT.firstBlocked(c -> !c.equals(seventh), c -> true), "the short one never asks for it");
    }
}
