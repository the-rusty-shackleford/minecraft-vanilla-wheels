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

    @Test void theShareOfATankGrowsWithTheSquareOfTheDistanceAndAnotherDimensionPaysItAll() {
        assertEquals(0.01, RecallCost.share(0, true), 1e-12, "the floor");
        assertEquals(0.01, RecallCost.share(250, true), 1e-12, "a twentieth of a twentieth, floored");
        assertEquals(0.05, RecallCost.share(1000, true), 1e-12);
        assertEquals(0.2, RecallCost.share(2000, true), 1e-12);
        assertEquals(0.8, RecallCost.share(4000, true), 1e-12);
        assertEquals(1.0, RecallCost.share(4473, true), 1e-3, "the cap");
        assertEquals(1.0, RecallCost.share(30_000_000, true), 1e-12);
        assertEquals(1.0, RecallCost.share(10, false), 1e-12, "another dimension, however near");
        assertThrows(IllegalArgumentException.class, () -> RecallCost.share(-1, true));
        assertThrows(IllegalArgumentException.class, () -> RecallCost.share(Double.POSITIVE_INFINITY, true));
    }

    @Test void whereaboutsSayHowFarToTwoFiguresAndWhetherARecallMayBeAsked() {
        var near = new Whereabouts(Whereabouts.Kind.DEPLOYED, true, 7.4);
        assertEquals(7, near.about());
        assertEquals(87, new Whereabouts(Whereabouts.Kind.DEPLOYED, true, 87.2).about());
        assertEquals(100, new Whereabouts(Whereabouts.Kind.DEPLOYED, true, 99.6).about(), "rounds up across a hundred");
        assertEquals(1200, new Whereabouts(Whereabouts.Kind.DEPLOYED, true, 1234).about());
        assertEquals(4500, new Whereabouts(Whereabouts.Kind.ON_THE_GROUND, true, 4473).about());
        assertEquals(12_000, new Whereabouts(Whereabouts.Kind.DEPLOYED, true, 12_345).about());
        assertEquals(0, new Whereabouts(Whereabouts.Kind.DEPLOYED, true, 0).about());
        assertTrue(near.recallable());
        assertTrue(new Whereabouts(Whereabouts.Kind.ON_THE_GROUND, true, 10).recallable(), "a packed wreck on the ground comes back");
        assertFalse(new Whereabouts(Whereabouts.Kind.COLLECTED, true, 0).recallable(), "one in a hand does not");
        assertEquals(0.2, new Whereabouts(Whereabouts.Kind.DEPLOYED, true, 2000).share(), 1e-12);
        assertEquals(1.0, new Whereabouts(Whereabouts.Kind.DEPLOYED, false, 2000).share(), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> new Whereabouts(null, true, 1));
        assertThrows(IllegalArgumentException.class, () -> new Whereabouts(Whereabouts.Kind.DEPLOYED, true, -1));
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
