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

/**
 * A block's collision cut across at one height, as a vehicle's footprint reads it for a wall
 * (D-0028): the rectangle, in x and z, bounding every box of the block whose height range holds the
 * cut. Only what stands across that height counts, so a garage door's housing over the cut and its
 * side track through it are the track alone; everything that does stand across it counts as one
 * rectangle, so a fence corner's L still fills its bounds, as it did when a block was read as the
 * bounds of all its boxes. Where every box of a block spans the cut -- a full block, a slab, a
 * fence, a wall, a pane -- the rectangle is those bounds exactly, and where some do not, it lies
 * within them.
 *
 * <p>Boxes are packed six to a box: min x, y, z, then max x, y, z, relative to the block's corner.
 */
public final class CrossSection {
    private CrossSection() {}

    /**
     * requires: {@code boxes.length} is a multiple of 6, each box packed min x, y, z then max x, y, z
     * with each min at most its max
     * effects: returns whether the world point ({@code x}, {@code z}) lies in the rectangle bounding,
     * in x and z, every box of the block with its corner at ({@code ox}, {@code oy}, {@code oz}) that
     * spans height {@code y} (its bottom at or under {@code y}, its top above), edges included; false
     * when no box spans it
     */
    public static boolean holds(double[] boxes, double ox, double oy, double oz, double x, double y, double z) {
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i + 5 < boxes.length; i += 6) {
            if (boxes[i + 1] + oy <= y && y < boxes[i + 4] + oy) {
                minX = Math.min(minX, boxes[i]);
                minZ = Math.min(minZ, boxes[i + 2]);
                maxX = Math.max(maxX, boxes[i + 3]);
                maxZ = Math.max(maxZ, boxes[i + 5]);
            }
        }
        return minX + ox <= x && x <= maxX + ox && minZ + oz <= z && z <= maxZ + oz;
    }
}
