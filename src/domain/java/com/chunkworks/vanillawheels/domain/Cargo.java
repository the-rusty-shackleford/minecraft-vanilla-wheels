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
 * The animals a trailer holds: so many adults or so many young, or a mix
 * in proportion -- an adult takes 1/{@code adultRoom} of the space, a
 * young 1/{@code youngRoom}. Four adults or eight young means two adults
 * and four young also fit, and a fifth adult never does.
 *
 * <p>RI: adultRoom >= 1, youngRoom >= 1, adults >= 0, young >= 0,
 *     adults / adultRoom + young / youngRoom <= 1 (within rounding).
 * AF: a trailer with room for adultRoom adults or youngRoom young,
 *     holding {@code adults} adults and {@code young} young.
 */
public record Cargo(int adultRoom, int youngRoom, int adults, int young) {
    private static final double SLACK = 1e-9;

    public Cargo {
        if (adultRoom < 1 || youngRoom < 1) {
            throw new IllegalArgumentException("room for at least one of each: " + adultRoom + ", " + youngRoom);
        }
        if (adults < 0 || young < 0) {
            throw new IllegalArgumentException("counts are not negative: " + adults + ", " + young);
        }
        if (used(adultRoom, youngRoom, adults, young) > 1 + SLACK) {
            throw new IllegalArgumentException("more than fits: " + adults + " adults and " + young + " young in room for " + adultRoom + " or " + youngRoom);
        }
    }

    /** effects: returns an empty trailer with the given room */
    public static Cargo empty(int adultRoom, int youngRoom) {
        return new Cargo(adultRoom, youngRoom, 0, 0);
    }

    private static double used(int adultRoom, int youngRoom, int adults, int young) {
        return (double) adults / adultRoom + (double) young / youngRoom;
    }

    /** effects: returns how much of the room is taken, 0..1 */
    public double fraction() {
        return used(adultRoom, youngRoom, adults, young);
    }

    /** effects: returns whether one more animal fits, {@code baby} or grown */
    public boolean accepts(boolean baby) {
        return used(adultRoom, youngRoom, adults + (baby ? 0 : 1), young + (baby ? 1 : 0)) <= 1 + SLACK;
    }

    /**
     * requires: accepts(baby)
     * effects: returns this with one more animal aboard
     */
    public Cargo with(boolean baby) {
        if (!accepts(baby)) {
            throw new IllegalArgumentException("no room for " + (baby ? "a young" : "an adult"));
        }
        return new Cargo(adultRoom, youngRoom, adults + (baby ? 0 : 1), young + (baby ? 1 : 0));
    }

    /** effects: returns how many animals are aboard */
    public int count() {
        return adults + young;
    }
}
