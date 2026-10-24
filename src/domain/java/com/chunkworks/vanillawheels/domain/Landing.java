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

import java.util.OptionalDouble;

/**
 * Where to stand a rider who logs back in to find the vehicle they left in gone (D-0038): they were
 * saved where their seat was, which for an aircraft is in the air and for a submarine under water.
 * A column of blocks is read one at a time from where their feet were. In open air they go down to
 * the first floor under them: a block's top, or water's surface, which a fall into hurts nobody. In
 * water they go up to its surface. Either way only where they fit, and never onto a harm.
 *
 * <p>AF: pure functions of a column and where the feet were. RI: none (no state).
 */
public final class Landing {
    private Landing() {}

    /** What one block of a column is to someone standing in it. */
    public enum Kind {
        /** Nothing to stand on or swim in: air, grass, a flower. */
        OPEN,
        /** Water, with nothing solid in the block. */
        WATER,
        /** Something with a top to stand on, at {@link Cell#top} over the block's bottom. */
        SOLID,
        /** Lava, fire, a cactus, a magma block: never stood on, never passed through. */
        HARM
    }

    /**
     * One block of a column.
     *
     * <p>RI: top is 0 unless kind is SOLID, where 0 < top <= 1.5 (a fence's or a wall's collision).
     */
    public record Cell(Kind kind, double top) {
        public static final Cell OPEN = new Cell(Kind.OPEN, 0.0);
        public static final Cell WATER = new Cell(Kind.WATER, 0.0);
        public static final Cell HARM = new Cell(Kind.HARM, 0.0);

        public Cell {
            if (kind == Kind.SOLID ? !(top > 0.0 && top <= 1.5) : top != 0.0) {
                throw new IllegalArgumentException(kind + " with a top of " + top);
            }
        }

        /** requires: 0 < top <= 1.5; effects: returns a block stood on at {@code top} over its bottom; throws: IllegalArgumentException */
        public static Cell solid(double top) {
            return new Cell(Kind.SOLID, top);
        }
    }

    /** A column of blocks at one x and z, asked one height at a time. */
    @FunctionalInterface
    public interface Column {
        /** effects: returns the block at height {@code y} */
        Cell at(int y);
    }

    /**
     * requires: minY <= feet < maxY, height > 0
     * effects: returns the height to stand someone {@code height} tall at in {@code column}, their
     * feet having been at {@code feet}:
     * <ul>
     *   <li>feet in water: at the surface above, the bottom of the first open block over the water;</li>
     *   <li>otherwise: on the first floor at or under the feet, a solid block's top or the top of the
     *       first water met;</li>
     * </ul>
     * either way only if every block from the floor up through their head is open. Empty when the
     * column holds them nowhere safe: a harm met first, a ceiling over the water, no room at the
     * floor, or nothing down to {@code minY} or up to {@code maxY}.
     * throws: IllegalArgumentException if a bound is broken
     */
    public static OptionalDouble standAt(Column column, double feet, int minY, int maxY, double height) {
        if (!(feet >= minY && feet < maxY) || !(height > 0.0)) {
            throw new IllegalArgumentException("feet " + feet + " in " + minY + ".." + maxY + ", height " + height);
        }
        int y = (int) Math.floor(feet);
        if (column.at(y).kind() == Kind.WATER) {
            // Up through the water to the first block over it: the surface, if it opens into air.
            for (int u = y + 1; u < maxY; u++) {
                Kind k = column.at(u).kind();
                if (k == Kind.WATER) {
                    continue;
                }
                return k == Kind.OPEN ? fits(column, u - 1, u, maxY, height) : OptionalDouble.empty();
            }
            return OptionalDouble.empty();
        }
        for (int f = y; f >= minY; f--) {
            Cell c = column.at(f);
            switch (c.kind()) {
                case OPEN -> {
                    continue;
                }
                case SOLID -> {
                    return fits(column, f, f + c.top(), maxY, height);
                }
                case WATER -> {
                    return fits(column, f, f + 1.0, maxY, height);
                }
                case HARM -> {
                    return OptionalDouble.empty();
                }
            }
        }
        return OptionalDouble.empty();
    }

    /** effects: returns {@code stand} if every block over the floor's block {@code floor} up through a head {@code height} over {@code stand} is open, else empty */
    private static OptionalDouble fits(Column column, int floor, double stand, int maxY, double height) {
        int head = (int) Math.floor(stand + height - 1e-9);
        if (head >= maxY) {
            return OptionalDouble.empty();
        }
        for (int y = floor + 1; y <= head; y++) {
            if (column.at(y).kind() != Kind.OPEN) {
                return OptionalDouble.empty();
            }
        }
        return OptionalDouble.of(stand);
    }
}
