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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Boxes: none; one; several all spanning the cut (a fence corner, a wall post and arms);
 * several of which some do not (a garage door's top row, a stair). The cut: under a box's bottom,
 * at it, inside, at its top. The point: inside, on each edge, outside in x and in z. The block's
 * corner: the origin and elsewhere. And the property the footprint keeps: where every box spans the
 * cut, the answer is the bounds of all the boxes, as 1.10.0 read every block.
 */
final class CrossSectionTest {
    private static final double P = 1.0 / 16.0;

    /** A garage door's open top-row cell at its west edge: the housing over the doorway and the side track. */
    private static final double[] DOOR_EDGE_TOP = {
        0, .5, .2, 1, 1, .8,
        0, 0, .35, .1, 1, .65};
    /** A fence corner with arms north and west, as the game builds it: post and arms 4/16 wide, 1.5 tall. */
    private static final double[] FENCE_NW = {
        6 * P, 0, 6 * P, 10 * P, 1.5, 10 * P,
        6 * P, 0, 0, 10 * P, 1.5, 10 * P,
        0, 0, 6 * P, 10 * P, 1.5, 10 * P};
    /** A wall post with one arm east: post 8/16 wide, arm 6/16, both 1.5 tall. */
    private static final double[] WALL_POST_EAST = {
        4 * P, 0, 4 * P, 12 * P, 1.5, 12 * P,
        5 * P, 0, 5 * P, 1, 1.5, 11 * P};
    /** A bottom stair rising east: the slab, and the upper quarter on the east half. */
    private static final double[] STAIR_EAST = {
        0, 0, 0, 1, .5, 1,
        .5, .5, 0, 1, 1, 1};

    @Test void noBoxesHoldNothing() {
        assertFalse(CrossSection.holds(new double[0], 0, 0, 0, .5, .5, .5));
    }

    @Test void oneBoxHoldsItsOwnRectangleAtItsHeights() {
        double[] slab = {0, 0, 0, 1, .5, 1};
        assertTrue(CrossSection.holds(slab, 0, 0, 0, .5, .25, .5), "inside");
        assertTrue(CrossSection.holds(slab, 0, 0, 0, .5, 0, .5), "the cut at the bottom");
        assertFalse(CrossSection.holds(slab, 0, 0, 0, .5, .5, .5), "the cut at the top is over it");
        assertFalse(CrossSection.holds(slab, 0, 0, 0, .5, -.01, .5), "the cut under it");
        assertTrue(CrossSection.holds(slab, 0, 0, 0, 0, .25, 1), "edges included");
        assertFalse(CrossSection.holds(slab, 0, 0, 0, 1.01, .25, .5), "outside in x");
        assertFalse(CrossSection.holds(slab, 0, 0, 0, .5, .25, -.01), "outside in z");
    }

    @Test void theBlocksCornerPlacesIt() {
        double[] post = {6 * P, 0, 6 * P, 10 * P, 1.5, 10 * P};
        assertTrue(CrossSection.holds(post, 24, 4, -7, 24.5, 5.05, -6.5));
        assertFalse(CrossSection.holds(post, 24, 4, -7, 24.3, 5.05, -6.5), "short of the post");
        assertFalse(CrossSection.holds(post, 24, 4, -7, 24.5, 5.6, -6.5), "over it");
        assertFalse(CrossSection.holds(post, 24, 4, -7, 24.5, 3.9, -6.5), "under it");
    }

    @Test void aDoorsTopRowUnderItsHousingIsItsTrackAlone() {
        // The cut 0.05 into the top row: the box car's climb line under a 3-high door.
        assertTrue(CrossSection.holds(DOOR_EDGE_TOP, 0, 0, 0, .05, .05, .5), "on the track");
        assertFalse(CrossSection.holds(DOOR_EDGE_TOP, 0, 0, 0, .5, .05, .5), "in the doorway beside it (1.10.0's bounds held here)");
        assertFalse(CrossSection.holds(DOOR_EDGE_TOP, 0, 0, 0, .05, .05, .3), "beside the track in z");
        // The cut through the housing: the housing is really there.
        assertTrue(CrossSection.holds(DOOR_EDGE_TOP, 0, 0, 0, .5, .75, .5), "the housing");
        assertTrue(CrossSection.holds(DOOR_EDGE_TOP, 0, 0, 0, .5, .75, .25), "the housing's own depth");
    }

    @Test void aFenceCornersLFillsItsBounds() {
        assertTrue(CrossSection.holds(FENCE_NW, 0, 0, 0, .2, .5, .2), "the empty quadrant between the arms");
        assertTrue(CrossSection.holds(FENCE_NW, 0, 0, 0, .5, 1.2, .05), "the north arm");
        assertFalse(CrossSection.holds(FENCE_NW, 0, 0, 0, .7, .5, .5), "east of the post");
        assertFalse(CrossSection.holds(FENCE_NW, 0, 0, 0, .5, .5, .7), "south of the post");
        assertFalse(CrossSection.holds(FENCE_NW, 0, 0, 0, .5, 1.5, .5), "over its top");
    }

    @Test void aStairsUpperHalfIsTheBackQuarterAlone() {
        assertTrue(CrossSection.holds(STAIR_EAST, 0, 0, 0, .2, .25, .5), "the lower half is the whole cell");
        assertTrue(CrossSection.holds(STAIR_EAST, 0, 0, 0, .8, .75, .5), "the upper half at the back");
        assertFalse(CrossSection.holds(STAIR_EAST, 0, 0, 0, .2, .75, .5), "the upper half at the front is open");
    }

    /** Where every box spans the cut, the answer is the bounds test 1.10.0 made of every block. */
    @Test void whereEveryBoxSpansTheCutItIsTheBounds() {
        for (double[] boxes : new double[][] {FENCE_NW, WALL_POST_EAST, {0, 0, 0, 1, 1, 1}}) {
            double minX = 1, minZ = 1, maxX = 0, maxZ = 0;
            for (int i = 0; i < boxes.length; i += 6) {
                minX = Math.min(minX, boxes[i]); minZ = Math.min(minZ, boxes[i + 2]);
                maxX = Math.max(maxX, boxes[i + 3]); maxZ = Math.max(maxZ, boxes[i + 5]);
            }
            for (int i = -1; i <= 17; i++) {
                for (int k = -1; k <= 17; k++) {
                    double x = 10 + i * P, z = -3 + k * P;
                    boolean bounds = minX + 10 <= x && x <= maxX + 10 && minZ - 3 <= z && z <= maxZ - 3;
                    assertEquals(bounds, CrossSection.holds(boxes, 10, 64, -3, x, 64.4, z), "at " + i + "/16, " + k + "/16");
                }
            }
        }
    }
}
