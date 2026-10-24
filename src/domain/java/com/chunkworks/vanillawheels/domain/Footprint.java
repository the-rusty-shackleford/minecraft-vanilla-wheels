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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The Mechanic Lift's shape: a deck five cells wide and {@code length} long, one cell high, with
 * a one-cell post on each corner of the deck and a one-cell wall between the two back posts
 * (D-0040), so the lift has an obvious back. The controller is the deck's front-centre cell;
 * every other cell is a part with an index, and the index alone finds the controller again.
 *
 * <p>Two lengths exist (D-0042): {@link #SHORT}, six long, every lift laid before 1.18.0; and
 * {@link #LONG}, eight long, every lift laid since, which takes the Farmer's Pickup whole. A
 * lift keeps the shape it was laid with: its blocks say which. The cells of the six-long shape
 * are in the order they always were, so a saved lift keeps every index it has.
 *
 * <p>Cells are in the lift's own frame, offsets from the controller: {@code right} across the
 * deck (negative to the left), {@code back} along it away from the front, {@code up} from the
 * deck. A {@link Heading} turns them into world offsets; the front is where the controller
 * looks, the deck lies behind it.
 *
 * <p>RI: cells() has WIDTH * length + 4 * POST + (WIDTH - 2) entries, distinct, the controller
 *     first, then the deck row by row, the posts, the wall last; indexOf and cell are inverse on
 *     1..parts(). AF: the list of cells, in index order, is the lift.
 */
public final class Footprint {
    public static final int WIDTH = 5;
    /** Blocks in each corner post, above the deck. */
    public static final int POST = 1;
    /**
     * Where a post or the wall stands within its cell, across the slim way, as a share of the
     * cell: the posts are columns six sixteenths square in the middle, the wall a bar as thick
     * along the deck, flush with the posts' faces.
     */
    public static final double BAR_MIN = 5.0 / 16.0;
    public static final double BAR_MAX = 11.0 / 16.0;

    /** The lift as laid before 1.18.0: a deck six long. */
    public static final Footprint SHORT = new Footprint(6);
    /** The lift as laid since 1.18.0: a deck eight long, which takes the Farmer's Pickup whole (D-0042). */
    public static final Footprint LONG = new Footprint(8);
    /** The most parts a lift has, for a part's index property. */
    public static final int MAX_PARTS = LONG.parts();

    private final int length;
    private final List<Cell> cells;

    private Footprint(int length) {
        this.length = length;
        this.cells = build(length);
    }

    /** effects: returns the shape a lift laid {@code isLong} has */
    public static Footprint of(boolean isLong) {
        return isLong ? LONG : SHORT;
    }

    /** effects: returns the deck's length, cells */
    public int length() {
        return length;
    }

    /** effects: returns whether this is the long shape */
    public boolean isLong() {
        return this == LONG;
    }

    /** effects: returns how many cells besides the controller: the deck, the posts, the back wall */
    public int parts() {
        return cells.size() - 1;
    }

    /** effects: returns the depth of the wall's front face from the deck's front edge, blocks: what a docked body backs up to */
    public double wallFace() {
        return length - 1 + BAR_MIN;
    }

    /** One cell of the lift, offsets from the controller in the lift's frame. */
    public record Cell(int right, int up, int back) {
        /** effects: returns whether this is a deck cell (on the ground row) rather than a post or the wall */
        public boolean isDeck() {
            return up == 0;
        }

        /** effects: returns whether this is a post cell: above the deck on a corner */
        public boolean isPost() {
            return up > 0 && Math.abs(right) == WIDTH / 2;
        }

        /** effects: returns whether this is a cell of the back wall: above the deck's back row, between the posts */
        public boolean isWall() {
            return up > 0 && Math.abs(right) < WIDTH / 2;
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

        /**
         * effects: returns where a point within a cell lands within its block, as fractions (x, z)
         * of the block, given the point's fractions {@code right} across the cell and {@code back}
         * along it in the lift's frame: a fraction along an axis the turn sends negative is
         * measured from the block's far side
         */
        public double[] local(double right, double back) {
            int rx = -dz, rz = dx;
            double x = rx != 0 ? (rx > 0 ? right : 1.0 - right) : (dx > 0 ? 1.0 - back : back);
            double z = rz != 0 ? (rz > 0 ? right : 1.0 - right) : (dz > 0 ? 1.0 - back : back);
            return new double[] {x, z};
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

    private static List<Cell> build(int length) {
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell(0, 0, 0));
        int half = WIDTH / 2;
        for (int back = 0; back < length; back++) {
            for (int right = -half; right <= half; right++) {
                if (back != 0 || right != 0) {
                    cells.add(new Cell(right, 0, back));
                }
            }
        }
        for (int right : new int[] {-half, half}) {
            for (int back : new int[] {0, length - 1}) {
                for (int up = 1; up <= POST; up++) {
                    cells.add(new Cell(right, up, back));
                }
            }
        }
        // The wall last: in the six-long shape the indices before it are what lifts saved before 1.17.0 carry.
        for (int right = -half + 1; right <= half - 1; right++) {
            cells.add(new Cell(right, 1, length - 1));
        }
        return List.copyOf(cells);
    }

    /** effects: returns every cell, the controller at index 0, the parts at 1..parts() */
    public List<Cell> cells() {
        return cells;
    }

    /**
     * effects: returns the bars standing in {@code c}, each as fractions of the cell in the
     * lift's frame (rightMin, rightMax, backMin, backMax), full height: none for a deck cell;
     * a front post's column; a back post's column run on to the cell's edge toward the wall;
     * the wall right across its cell -- so the back row's bars meet end to end
     */
    public List<double[]> bars(Cell c) {
        if (c.isDeck()) {
            return List.of();
        }
        if (c.isWall()) {
            return List.of(new double[] {0.0, 1.0, BAR_MIN, BAR_MAX});
        }
        if (c.back() == length - 1) {
            return c.right() < 0
                    ? List.of(new double[] {BAR_MIN, 1.0, BAR_MIN, BAR_MAX})
                    : List.of(new double[] {0.0, BAR_MAX, BAR_MIN, BAR_MAX});
        }
        return List.of(new double[] {BAR_MIN, BAR_MAX, BAR_MIN, BAR_MAX});
    }

    /**
     * requires: 1 <= index <= parts()
     * effects: returns the part with {@code index}
     */
    public Cell cell(int index) {
        if (index < 1 || index > parts()) {
            throw new IllegalArgumentException("a part index is 1.." + parts() + ": " + index);
        }
        return cells.get(index);
    }

    /**
     * effects: returns the index of {@code cell}: 0 for the controller,
     * 1..parts() for a part; -1 if it is no cell of the lift
     */
    public int indexOf(Cell cell) {
        return cells.indexOf(cell);
    }

    /**
     * effects: returns the world offset (x, y, z) from a part at
     * {@code index} back to its controller, for a lift facing {@code h}
     */
    public int[] toController(int index, Heading h) {
        int[] o = h.offset(cell(index));
        return new int[] {-o[0], -o[1], -o[2]};
    }

    /**
     * effects: returns the first cell that keeps a lift from standing --
     * one that fails {@code free}, else a deck cell that fails
     * {@code supported} (the caller tests the block under it) -- or null
     * when every cell passes
     */
    public Cell firstBlocked(Predicate<Cell> free, Predicate<Cell> supported) {
        for (Cell c : cells) {
            if (!free.test(c)) {
                return c;
            }
        }
        for (Cell c : cells) {
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
    public int[][] bounds(Heading h) {
        int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] max = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (Cell c : cells) {
            int[] o = h.offset(c);
            for (int i = 0; i < 3; i++) {
                min[i] = Math.min(min[i], o[i]);
                max[i] = Math.max(max[i], o[i]);
            }
        }
        return new int[][] {min, max};
    }

    /**
     * effects: returns the point on the deck's centre line {@code depth}
     * blocks back from its front edge, on top of the deck, as an offset
     * from the controller's corner (x, y, z), for a lift facing {@code h};
     * the deck's own centre is at depth length() / 2
     */
    public double[] onDeck(Heading h, double depth) {
        double back = depth - 0.5;
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
    public double[][] deckBox(Heading h) {
        int[][] b = bounds(h);
        return new double[][] {
                {b[0][0], 1.0, b[0][2]},
                {b[1][0] + 1.0, 1.0 + POST, b[1][2] + 1.0},
        };
    }
}
