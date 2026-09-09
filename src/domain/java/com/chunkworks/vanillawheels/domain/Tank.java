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
 * A fuel tank counted in burn ticks, as a furnace counts fuel: a coal is
 * 1600 of them. A fill is all or nothing -- an item that does not fit is
 * refused and stays in the hand -- and the tank burns one tick of fuel per
 * tick of throttle.
 *
 * <p>RI: 0 <= ticks <= capacity; capacity >= 0.
 * AF: AF(ticks, capacity) = "{@code ticks} ticks of burning left in a tank
 *     that holds {@code capacity}".
 */
public record Tank(int ticks, int capacity) {
    public Tank {
        if (capacity < 0 || ticks < 0 || ticks > capacity) {
            throw new IllegalArgumentException("ticks must be 0..capacity: " + ticks + " of " + capacity);
        }
    }

    /** effects: returns an empty tank of {@code capacity} */
    public static Tank empty(int capacity) {
        return new Tank(0, capacity);
    }

    /** effects: returns whether the tank has any fuel */
    public boolean hasFuel() {
        return ticks > 0;
    }

    /** effects: returns how full the tank is, 0..1; 0 for a tank that holds nothing */
    public double fraction() {
        return capacity == 0 ? 0.0 : (double) ticks / capacity;
    }

    /** effects: returns whether a fuel worth {@code burnTicks} would fit whole */
    public boolean accepts(int burnTicks) {
        return burnTicks > 0 && ticks + burnTicks <= capacity;
    }

    /**
     * effects: returns the tank with a fuel worth {@code burnTicks} added,
     * or this tank unchanged if it does not fit whole (the caller keeps
     * the item then)<br>
     * throws: {@link IllegalArgumentException} if burnTicks <= 0
     */
    public Tank fill(int burnTicks) {
        if (burnTicks <= 0) {
            throw new IllegalArgumentException("a fuel burns for a positive time: " + burnTicks);
        }
        return accepts(burnTicks) ? new Tank(ticks + burnTicks, capacity) : this;
    }

    /** effects: returns the tank after burning {@code n} ticks, or all that is left if fewer remain<br>throws: IllegalArgumentException if n < 0 */
    public Tank burn(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("cannot burn a negative time");
        }
        return new Tank(Math.max(0, ticks - n), capacity);
    }
}
