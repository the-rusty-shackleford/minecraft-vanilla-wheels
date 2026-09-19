/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Partitions: pristine/partial/broken condition; near/quadratic/capped distance;
 * sufficient/partial/zero fuel; exact/fractional shortfall; creative; invalid inputs.
 */
class RecoveryTest {
    @Test void distanceCurveAndFullTankCap() {
        var intact = new Condition(Condition.MAX);
        assertEquals(240, RecallCost.quote(250, 24000, 24000, intact, false).fuelCost());
        assertEquals(1200, RecallCost.quote(1000, 24000, 24000, intact, false).fuelCost());
        assertEquals(4800, RecallCost.quote(2000, 24000, 24000, intact, false).fuelCost());
        assertEquals(19200, RecallCost.quote(4000, 24000, 24000, intact, false).fuelCost());
        assertEquals(24000, RecallCost.quote(30_000_000, 24000, 24000, intact, false).fuelCost());
    }

    @Test void missingFuelCostsHalfAsMuchDurabilityAndMayBreakTheVehicle() {
        var quote = RecallCost.quote(2000, 24000, 1200, new Condition(10000), false);
        assertEquals(0, quote.fuelAfter());
        assertEquals(9250, quote.conditionAfter().remaining());
        assertEquals(0, RecallCost.quote(4000, 24000, 0, new Condition(1000), false).conditionAfter().remaining());
        assertEquals(0, RecallCost.quote(0, 24000, 0, new Condition(0), false).conditionAfter().remaining());
        assertEquals(9999, RecallCost.quote(0, 24000, 239, new Condition(10000), false).conditionAfter().remaining());
    }

    @Test void sufficientFuelAndCreativePreserveCondition() {
        var intact = new Condition(10000);
        var quote = RecallCost.quote(1000, 24000, 24000, intact, false);
        assertEquals(22800, quote.fuelAfter());
        assertEquals(intact, quote.conditionAfter());
        assertEquals(new RecallCost(0, 2, intact), RecallCost.quote(5000, 24000, 2, intact, true));
    }

    @Test void repairsAreProportionalAndNeverRoundDamageAway() {
        assertEquals(0, new Condition(10000).repairCost(20));
        assertEquals(1, new Condition(9999).repairCost(20));
        assertEquals(10, new Condition(5000).repairCost(20));
        assertEquals(20, new Condition(0).repairCost(20));
        assertEquals(18, new Condition(0).repairCost(18));
        assertEquals(12, new Condition(0).repairCost(12));
    }

    @Test void invalidValuesCannotEnterTheDomain() {
        assertThrows(IllegalArgumentException.class, () -> new Condition(-1));
        assertThrows(IllegalArgumentException.class, () -> new Condition(10001));
        assertThrows(IllegalArgumentException.class, () -> new Condition(10000).damaged(-1));
        assertThrows(IllegalArgumentException.class, () -> new Condition(10000).repairCost(0));
        assertThrows(IllegalArgumentException.class, () -> RecallCost.quote(Double.NaN, 24000, 0, new Condition(10000), false));
        assertThrows(IllegalArgumentException.class, () -> RecallCost.quote(-1, 24000, 0, new Condition(10000), false));
        assertThrows(IllegalArgumentException.class, () -> RecallCost.quote(0, 0, 0, new Condition(10000), false));
        assertThrows(IllegalArgumentException.class, () -> RecallCost.quote(0, 24000, 24001, new Condition(10000), false));
    }
}
