/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

/**
 * A player's punches at a vehicle, counted toward packing it up (D-0025): knocks, as a boat takes
 * them, not wear. They add up only while they come in a row, so a stray swing does nothing.
 * AF: {@code count} punches have landed in a row, the last at game tick {@code last}; zero is none.
 * RI: 0 <= count <= TO_PACK. Immutable.
 */
public record Knocks(int count, long last) {
    /** Punches in a row that pack a vehicle: Immersive Aircraft's six. */
    public static final int TO_PACK = 6;
    /** The longest pause, in ticks, between two punches of one row. */
    public static final int GAP = 20;
    /** No punches. */
    public static final Knocks NONE = new Knocks(0, 0L);

    /** requires: none; effects: constructs a count; throws: IllegalArgumentException outside 0..TO_PACK. */
    public Knocks {
        if (count < 0 || count > TO_PACK) throw new IllegalArgumentException("Invalid knock count " + count);
    }

    /**
     * requires: {@code tick} is not before the last punch (game time runs forward).
     * effects: the count after a punch at {@code tick}: one more when it comes within GAP ticks of
     *     the last, else a new row of one; never past TO_PACK.
     * throws: IllegalArgumentException for a tick before the last punch.
     */
    public Knocks knocked(long tick) {
        if (count > 0 && tick < last) throw new IllegalArgumentException("A punch before the last one");
        boolean inRow = count > 0 && tick - last <= GAP;
        return new Knocks(inRow ? Math.min(TO_PACK, count + 1) : 1, tick);
    }

    /** requires: none; effects: whether this row of punches packs the vehicle; throws: none. */
    public boolean packs() { return count >= TO_PACK; }
}
