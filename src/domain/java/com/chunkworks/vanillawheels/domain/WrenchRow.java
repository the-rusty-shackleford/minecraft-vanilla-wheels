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
 * A vehicle's condition drawn the way the game draws health and hunger
 * (Rusty: "a bar of wrench icons that behave similarly to the health and
 * hunger bars"). Ten wrenches, each a tenth of the condition, in halves,
 * any condition above nothing showing at least a half. After a hurt the row
 * blinks for a second with what was lost still shown pale, and for half a
 * second after a repair, as hearts do. At a fifth or less it jiggles, as
 * hearts do at four health.
 */
public final class WrenchRow {
    private WrenchRow() {}

    public static final int ICONS = 10;
    public static final int HALVES = 2 * ICONS;
    /** Ticks the row blinks after a hurt, and after a repair: the game's for health. */
    public static final int HURT_BLINK = 20, REPAIR_BLINK = 10;

    public enum Fill { EMPTY, HALF, FULL }

    /** requires: 0 <= condition <= Condition.MAX; effects: returns the halves shown, rounded up */
    public static int halves(int condition) {
        return (int) (((long) condition * HALVES + Condition.MAX - 1) / Condition.MAX);
    }

    /** requires: 0 <= icon < ICONS; effects: returns how full wrench {@code icon} is (0 first to fill) with {@code halves} showing */
    public static Fill fill(int halves, int icon) {
        int left = halves - 2 * icon;
        return left >= 2 ? Fill.FULL : left == 1 ? Fill.HALF : Fill.EMPTY;
    }

    /** effects: returns whether the row jiggles: at a fifth of full condition or less */
    public static boolean jiggles(int condition) {
        return (long) condition * 5 <= Condition.MAX;
    }

    /** effects: returns the jiggle of wrench {@code icon} at {@code tick}, 0 or 1 pixels down, varying tick to tick as the game's does */
    public static int jiggle(int tick, int icon) {
        int h = tick * 312871 + icon * 0x9E3779B9;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h & 1;
    }

    /**
     * One viewer's watch on one vehicle's row, told the condition once a tick.
     * AF: the row blinks for {@code blink} more ticks, showing {@code shown}
     * pale beneath the condition; {@code last} is the condition told last,
     * -1 before the first. RI: 0 <= blink <= HURT_BLINK; shown and last in
     * 0..Condition.MAX once told.
     */
    public static final class Watch {
        private int last = -1;
        private int shown;
        private int blink;

        /** requires: 0 <= condition <= Condition.MAX, called once a tick; effects: a drop starts the hurt blink, holding the condition before the first drop pale until it ends; a rise starts the repair blink */
        public void observe(int condition) {
            if (last < 0) {
                shown = condition;
            } else if (condition < last) {
                if (blink == 0) {
                    shown = last;
                }
                blink = HURT_BLINK;
            } else if (condition > last) {
                shown = condition;
                blink = REPAIR_BLINK;
            } else if (blink > 0 && --blink == 0) {
                shown = condition;
            }
            last = condition;
        }

        /** effects: returns the condition to show pale beneath the present one while blinking */
        public int shown() {
            return shown;
        }

        /** effects: returns whether the row is drawn lit this tick: every other three ticks of a blink, as the game's hearts are */
        public boolean lit() {
            return blink > 0 && (blink / 3) % 2 == 1;
        }

        /** effects: returns the ticks of blink left */
        public int blink() {
            return blink;
        }
    }
}
