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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Partitions. The rate: 1 (a tick a tick, as every engine burnt before), 0 (nothing, nothing
 * owed), a fraction (whole ticks only as the shares add up), more than one (more than a tick some
 * ticks), the most allowed; refused: negative, past the most, not a number. What is owed: none,
 * some; refused out of [0, 1).
 */
final class FuelDebtTest {

    /** effects: returns the ticks taken over {@code n} ticks of burning at {@code rate} from nothing owed */
    private static int over(int n, double rate) {
        FuelDebt debt = FuelDebt.NONE;
        int taken = 0;
        for (int i = 0; i < n; i++) {
            FuelDebt.Burn b = debt.burn(rate);
            taken += b.ticks();
            debt = b.next();
        }
        return taken;
    }

    @Test
    void atOneATickEveryTickBurnsOneAndOwesNothing() {
        FuelDebt debt = FuelDebt.NONE;
        for (int i = 0; i < 1000; i++) {
            FuelDebt.Burn b = debt.burn(1.0);
            assertEquals(1, b.ticks(), "tick " + i);
            assertEquals(FuelDebt.NONE, b.next(), "tick " + i);
            debt = b.next();
        }
    }

    @Test
    void atNothingNothingIsTakenOrOwed() {
        FuelDebt.Burn b = FuelDebt.NONE.burn(0.0);
        assertEquals(0, b.ticks());
        assertEquals(FuelDebt.NONE, b.next());
        assertEquals(0, new FuelDebt(0.5).burn(0.0).ticks(), "a half owed is still owed");
        assertEquals(0.5, new FuelDebt(0.5).burn(0.0).next().owed(), 0.0);
    }

    @Test
    void aFractionBurnsWholeTicksAsItsSharesAddUp() {
        FuelDebt debt = FuelDebt.NONE;
        int[] taken = new int[8];
        for (int i = 0; i < 8; i++) {
            FuelDebt.Burn b = debt.burn(0.25);
            taken[i] = b.ticks();
            debt = b.next();
        }
        assertEquals("[0, 0, 0, 1, 0, 0, 0, 1]", java.util.Arrays.toString(taken), "a quarter: a tick every fourth");
        assertEquals(2500, over(10_000, 0.25));
        assertEquals(8000, over(10_000, 0.8), 1, "Industrial Gears' 0.8, within a tick of floating error");
        assertEquals(625, over(10_000, 0.0625), 1, "two Eco Engines' sixteenth");
    }

    @Test
    void moreThanOneBurnsAnExtraTickWhenItsSharesAddUp() {
        assertEquals(13_000, over(10_000, 1.3), 1, "a Nether Engine's 1.3");
        assertEquals(18_000, over(10_000, 1.8), 1);
        FuelDebt.Burn b = new FuelDebt(0.75).burn(1.5);
        assertEquals(2, b.ticks(), "0.75 owed and 1.5 more: two whole ticks");
        assertEquals(0.25, b.next().owed(), 1e-12);
        assertEquals(64, FuelDebt.NONE.burn(FuelDebt.MAX_RATE).ticks());
    }

    @Test
    void aRateOutOfBoundsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> FuelDebt.NONE.burn(-0.01));
        assertThrows(IllegalArgumentException.class, () -> FuelDebt.NONE.burn(FuelDebt.MAX_RATE + 0.01));
        assertThrows(IllegalArgumentException.class, () -> FuelDebt.NONE.burn(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> FuelDebt.NONE.burn(Double.POSITIVE_INFINITY));
    }

    @Test
    void whatIsOwedIsAShareOfATick() {
        assertThrows(IllegalArgumentException.class, () -> new FuelDebt(-0.01));
        assertThrows(IllegalArgumentException.class, () -> new FuelDebt(1.0));
        assertThrows(IllegalArgumentException.class, () -> new FuelDebt(Double.NaN));
    }
}
