/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

import java.util.*;

/** Pure rolling-shutter rules. AF: a rectangle is one coplanar connected door;
 * lift counts eighth-blocks above its sill. RI: dimensions 1..16, unique cells,
 * lift 0..height*8-4. Values are immutable and do not retain caller collections. */
public final class Shutter {
    public static final int MAX_SIZE = 16;
    public static final int MAX_PANELS = MAX_SIZE * MAX_SIZE;
    public static final int UNITS = 8;
    public static final double HEADER = .5;
    private Shutter() {}

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
        int limit = rectangle.fullyRaised();
        if (current < 0 || current > limit) throw new IllegalArgumentException("lift outside door");
        if (powered) return Math.min(limit, current + 1);
        return obstructed ? current : Math.max(0, current - 1);
    }
}
