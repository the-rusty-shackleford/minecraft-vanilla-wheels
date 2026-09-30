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
package com.chunkworks.vanillawheels;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A collision shape's boxes, packed as {@link com.chunkworks.vanillawheels.domain.CrossSection}
 * reads them, with their bounds, remembered by the shape's identity. Every way a {@code VoxelShape}
 * gives up its boxes or even its bounds allocates, and the footprint reads each block under a
 * vehicle's nose and tail some twenty times a move; a block's shape is one object per state (the
 * garage door's, one per panel travel), so a shape is unpacked once and read from here after.
 *
 * <p>The table is direct-mapped by identity hash, and a slot's entry is replaced whole: an entry is
 * immutable, so the client and the integrated server may share the table without a lock, each at
 * worst unpacking a shape the other just evicted. An entry holds its shape, so a slot never answers
 * for a different one.
 */
final class ShapeBoxes {
    private ShapeBoxes() {}

    /** A shape's boxes packed six to a box (min x, y, z, max x, y, z) and the bounds of them all. */
    record Entry(VoxelShape shape, double[] boxes, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    private static final int SLOTS = 1024;
    private static final Entry[] TABLE = new Entry[SLOTS];

    /** effects: returns {@code shape}'s boxes and bounds, or null for an empty shape */
    static @Nullable Entry of(VoxelShape shape) {
        if (shape.isEmpty()) {
            return null;
        }
        int slot = System.identityHashCode(shape) & (SLOTS - 1);
        Entry e = TABLE[slot];
        if (e != null && e.shape() == shape) {
            return e;
        }
        e = unpack(shape);
        TABLE[slot] = e;
        return e;
    }

    private static Entry unpack(VoxelShape shape) {
        List<AABB> list = shape.toAabbs();
        double[] boxes = new double[list.size() * 6];
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < list.size(); i++) {
            AABB b = list.get(i);
            boxes[i * 6] = b.minX;
            boxes[i * 6 + 1] = b.minY;
            boxes[i * 6 + 2] = b.minZ;
            boxes[i * 6 + 3] = b.maxX;
            boxes[i * 6 + 4] = b.maxY;
            boxes[i * 6 + 5] = b.maxZ;
            minX = Math.min(minX, b.minX); minY = Math.min(minY, b.minY); minZ = Math.min(minZ, b.minZ);
            maxX = Math.max(maxX, b.maxX); maxY = Math.max(maxY, b.maxY); maxZ = Math.max(maxZ, b.maxZ);
        }
        return new Entry(shape, boxes, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
