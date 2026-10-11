/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

import java.util.*;

/** Pure door-curtain rules, for the rolling garage door and the hidden door (D-0036).
 * AF: a rectangle is one coplanar connected door; its travel counts eighth-blocks the curtain has
 * moved off its closed place: a rolling curtain's lift above its sill, a sinking curtain's drop
 * into it. RI: dimensions 1..16, unique cells, travel 0..travel(rectangle, kind). Values are
 * immutable and do not retain caller collections. */
public final class Shutter {
    public static final int MAX_SIZE = 16;
    public static final int MAX_PANELS = MAX_SIZE * MAX_SIZE;
    public static final int UNITS = 8;
    public static final double HEADER = .5;
    private Shutter() {}

    /** How a curtain opens, and how fast. ROLLING rolls up into a half-block header, which stays
     * (the garage door), an eighth-block a tick; SINKING slides down whole into its sill and leaves
     * the full opening (the hidden door), half a block a tick, so a four-high door is open in 0.4 s
     * (Rusty: "if I am driving towards the door at full speed, I dont have to slow down"). */
    public enum Kind {
        ROLLING(1), SINKING(4);
        /** Eighth-blocks moved a tick. */
        public final int step;
        Kind(int step) { this.step = step; }
    }

    /** AF: one position in a door plane; RI: integer coordinates. */
    public record Cell(int across, int up) {}

    /** AF: a filled rectangle in a door plane; RI: positive bounded dimensions. */
    public record Rectangle(int left, int bottom, int width, int height) {
        /** requires: bounded dimensions; effects: creates rectangle; throws: invalid dimensions. */
        public Rectangle {
            if (width < 1 || width > MAX_SIZE || height < 1 || height > MAX_SIZE)
                throw new IllegalArgumentException("door must fit 16 by 16");
        }
        /** requires: none; effects: returns fully raised position; throws: none. */
        public int fullyRaised() { return height * UNITS - 4; }
    }

    /** requires: nonnull collection/cells; effects: returns a bounded filled rectangle,
     * or empty for a gap, duplicate, disconnected or oversized shape; throws: null input. */
    public static Optional<Rectangle> rectangle(Collection<Cell> cells) {
        Objects.requireNonNull(cells);
        if (cells.isEmpty() || cells.size() > MAX_PANELS) return Optional.empty();
        Set<Cell> unique = Set.copyOf(cells);
        if (unique.size() != cells.size()) return Optional.empty();
        int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE;
        int bottom = Integer.MAX_VALUE, top = Integer.MIN_VALUE;
        for (Cell c : unique) {
            left = Math.min(left, c.across); right = Math.max(right, c.across);
            bottom = Math.min(bottom, c.up); top = Math.max(top, c.up);
        }
        long width = (long) right - left + 1, height = (long) top - bottom + 1;
        if (width > MAX_SIZE || height > MAX_SIZE || width * height != cells.size()) return Optional.empty();
        return Optional.of(new Rectangle(left, bottom, (int) width, (int) height));
    }

    /** requires: current within rectangle's travel; effects: advances one eighth-block
     * toward the power level, pausing closure when obstructed; throws: invalid travel. */
    public static int advance(Rectangle rectangle, int current, boolean powered, boolean obstructed) {
        return advance(rectangle, Kind.ROLLING, current, powered, obstructed);
    }

    /** requires: none; effects: returns the eighth-blocks a curtain of this kind travels from shut
     * to open: a rolling one stops half a block short of its full height, under its header; a
     * sinking one goes all the way down; throws: null kind. */
    public static int travel(Rectangle rectangle, Kind kind) {
        return switch (Objects.requireNonNull(kind)) {
            case ROLLING -> rectangle.fullyRaised();
            case SINKING -> rectangle.height() * UNITS;
        };
    }

    /** requires: current within the kind's travel; effects: advances the kind's step toward the
     * power level (powered opens), never past shut or open, pausing closure when obstructed;
     * throws: invalid travel. */
    public static int advance(Rectangle rectangle, Kind kind, int current, boolean powered, boolean obstructed) {
        int limit = travel(rectangle, kind);
        if (current < 0 || current > limit) throw new IllegalArgumentException("lift outside door");
        if (powered) return Math.min(limit, current + kind.step);
        return obstructed ? current : Math.max(0, current - kind.step);
    }

    /** requires: 0 <= row < height and drop within the sinking travel; effects: returns how much
     * of the cell in {@code row} (0 at the sill) a sinking curtain still fills, in blocks, from the
     * cell's bottom: the sheet's top edge stands {@code height - drop/8} blocks above the sill, so
     * a row is full below it, empty above it and cut by it; throws: row or drop out of range. */
    public static double sunkTop(Rectangle rectangle, int row, int drop) {
        if (row < 0 || row >= rectangle.height()) throw new IllegalArgumentException("row outside door");
        if (drop < 0 || drop > travel(rectangle, Kind.SINKING)) throw new IllegalArgumentException("drop outside door");
        double edge = rectangle.height() - drop / (double) UNITS - row;
        return Math.max(0, Math.min(1, edge));
    }
}
