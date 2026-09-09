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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The Mechanic Lift's shape: a deck five cells wide and seven long, one
 * cell high, with a two-cell post on each corner of the deck. The
 * controller is the deck's front-centre cell; every other cell is a part
 * with an index, and the index alone finds the controller again.
 *
 * <p>Cells are in the lift's own frame, offsets from the controller:
 * {@code right} across the deck (negative to the left), {@code back}
 * along it away from the front, {@code up} from the deck. A {@link Heading}
 * turns them into world offsets; the front is where the controller looks,
 * the deck lies behind it.
 *
 * <p>RI: cells() has WIDTH * LENGTH + 4 * POST entries, distinct, the
 *     controller first; indexOf and cell are inverse on 1..PARTS.
 * AF: the list of cells, in index order, is the lift.
 */
public final class Footprint {
    private Footprint() {}

    public static final int WIDTH = 5;
    public static final int LENGTH = 7;
    /** Blocks in each corner post, above the deck. */
    public static final int POST = 2;
    /** Cells besides the controller. */
    public static final int PARTS = WIDTH * LENGTH - 1 + 4 * POST;

    /** One cell of the lift, offsets from the controller in the lift's frame. */
    public record Cell(int right, int up, int back) {
        /** effects: returns whether this is a deck cell (on the ground row) rather than a post */
        public boolean isDeck() {
            return up == 0;
        }
    }

    /** The four ways a lift can face, with the turn each applies to a cell. */
    public enum Heading {
        /** The controller looks toward -Z: the deck runs to +Z. */
        NORTH(0, -1),
        /** The controller looks toward +X. */
        EAST(1, 0),
        /** The controller looks toward +Z. */
        SOUTH(0, 1),
        /** The controller looks toward -X. */
        WEST(-1, 0);

        /** The world direction the front looks. */
        public final int dx;
        public final int dz;

        Heading(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        /**
         * effects: returns a cell's world offset from the controller, as
         * (x, y, z): {@code back} runs opposite to the look, {@code right}
         * is the look turned clockwise seen from above
         */
        public int[] offset(Cell c) {
            // Clockwise from above: (dx, dz) -> (-dz, dx).
            int rx = -dz, rz = dx;
            return new int[] {
                    c.right() * rx - c.back() * dx,
                    c.up(),
                    c.right() * rz - c.back() * dz,
            };
        }

        /** effects: returns the yaw, degrees in the game's sense, of something that looks the way the front does */
        public float yaw() {
            return switch (this) {
                case SOUTH -> 0.0f;
                case WEST -> 90.0f;
                case NORTH -> 180.0f;
                case EAST -> -90.0f;
            };
        }
    }

    private static final List<Cell> CELLS = build();

    private static List<Cell> build() {
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell(0, 0, 0));
        int half = WIDTH / 2;
        for (int back = 0; back < LENGTH; back++) {
            for (int right = -half; right <= half; right++) {
                if (back != 0 || right != 0) {
                    cells.add(new Cell(right, 0, back));
                }
            }
        }
        for (int right : new int[] {-half, half}) {
            for (int back : new int[] {0, LENGTH - 1}) {
                for (int up = 1; up <= POST; up++) {
                    cells.add(new Cell(right, up, back));
                }
            }
        }
        return List.copyOf(cells);
    }

    /** effects: returns every cell, the controller at index 0, the parts at 1..PARTS */
    public static List<Cell> cells() {
        return CELLS;
    }

    /**
     * requires: 1 <= index <= PARTS
     * effects: returns the part with {@code index}
     */
    public static Cell cell(int index) {
        if (index < 1 || index > PARTS) {
            throw new IllegalArgumentException("a part index is 1.." + PARTS + ": " + index);
        }
        return CELLS.get(index);
    }

    /**
     * effects: returns the index of {@code cell}: 0 for the controller,
     * 1..PARTS for a part; -1 if it is no cell of the lift
     */
    public static int indexOf(Cell cell) {
        return CELLS.indexOf(cell);
    }

    /**
     * effects: returns the world offset (x, y, z) from a part at
     * {@code index} back to its controller, for a lift facing {@code h}
     */
    public static int[] toController(int index, Heading h) {
        int[] o = h.offset(cell(index));
        return new int[] {-o[0], -o[1], -o[2]};
    }

    /**
     * effects: returns the first cell that keeps a lift from standing --
     * one that fails {@code free}, else a deck cell that fails
     * {@code supported} (the caller tests the block under it) -- or null
     * when every cell passes
     */
    public static Cell firstBlocked(Predicate<Cell> free, Predicate<Cell> supported) {
        for (Cell c : CELLS) {
            if (!free.test(c)) {
                return c;
            }
        }
        for (Cell c : CELLS) {
            if (c.isDeck() && !supported.test(c)) {
                return c;
            }
        }
        return null;
    }

    /**
     * effects: returns the lift's bounds as world offsets from the
     * controller, inclusive minimum and maximum (x, y, z), for a lift
     * facing {@code h}
     */
    public static int[][] bounds(Heading h) {
        int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] max = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (Cell c : CELLS) {
            int[] o = h.offset(c);
            for (int i = 0; i < 3; i++) {
                min[i] = Math.min(min[i], o[i]);
                max[i] = Math.max(max[i], o[i]);
            }
        }
        return new int[][] {min, max};
    }

    /**
     * effects: returns where a built vehicle stands, as an offset from the
     * controller's corner (x, y, z), for a lift facing {@code h}: the
     * centre of the deck, one block up, on top of it
     */
    public static double[] spawn(Heading h) {
        double back = (LENGTH - 1) / 2.0;
        return new double[] {
                0.5 - back * h.dx,
                1.0,
                0.5 - back * h.dz,
        };
    }

    /**
     * effects: returns the box above the deck, as offsets from the
     * controller's corner: min (x, y, z) and max, the deck's top face up
     * to the posts' top, for a lift facing {@code h}
     */
    public static double[][] deckBox(Heading h) {
        int[][] b = bounds(h);
        return new double[][] {
                {b[0][0], 1.0, b[0][2]},
                {b[1][0] + 1.0, 1.0 + POST, b[1][2] + 1.0},
        };
    }
}
