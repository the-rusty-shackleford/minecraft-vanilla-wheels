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
 * The part of a tick of fuel an engine has burnt but not yet taken from its {@link Tank}, carried
 * from one tick to the next: an engine that burns at a rate other than one tick of fuel a tick (a
 * protocol's upgrade, D-0031) burns exactly that over time, while the tank keeps whole ticks.
 *
 * <p>RI: 0 <= owed < 1.
 * AF: AF(owed) = "{@code owed} of a tick of fuel burnt and not yet taken from the tank".
 */
public record FuelDebt(double owed) {
    /** Nothing owed. */
    public static final FuelDebt NONE = new FuelDebt(0.0);
    /** The fastest an engine may burn, ticks of fuel a tick. */
    public static final double MAX_RATE = 64.0;

    public FuelDebt {
        if (!(owed >= 0.0 && owed < 1.0)) {
            throw new IllegalArgumentException("a share of a tick is owed: " + owed);
        }
    }

    /** A tick's burning: the whole ticks of fuel to take from the tank now, and what is owed after. */
    public record Burn(int ticks, FuelDebt next) {}

    /**
     * requires: 0 <= rate <= MAX_RATE
     * effects: returns a tick's burning at {@code rate} ticks of fuel a tick: what is owed with
     * this tick's burn added, its whole ticks taken now and the rest carried; at a rate of 1 with
     * nothing owed, one tick taken and nothing carried
     * throws: IllegalArgumentException for a rate out of bounds or not a number
     */
    public Burn burn(double rate) {
        if (!(rate >= 0.0 && rate <= MAX_RATE)) {
            throw new IllegalArgumentException("an engine burns 0.." + MAX_RATE + " ticks of fuel a tick: " + rate);
        }
        double total = owed + rate;
        int whole = (int) Math.floor(total);
        return new Burn(whole, new FuelDebt(total - whole));
    }
}
