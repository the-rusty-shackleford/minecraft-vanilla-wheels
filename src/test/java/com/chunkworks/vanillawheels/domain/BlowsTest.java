/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What a blow costs (D-0034).
 * Partitions, wear: durability 1 (the boat's five points), above 1 (the profiles' 1.5 to 12), under
 *     1; an amount that divides exactly, one that rounds up, one tiny (still a point), one past a
 *     wreck (capped); a non-positive or non-finite amount or durability (refused).
 * Partitions, Area: the first blast; a second on the same tick smaller (nothing), equal
 *     (nothing), larger (the excess); one on a later tick (all of it); negative wear (refused).
 */
class BlowsTest {
    @Test void atDurabilityOneFivePointsWreck() {
        assertEquals(2000, Blows.wear(1.0, 1.0));
        assertEquals(Condition.MAX, Blows.wear(5.0, 1.0), "the boat's five points");
        assertEquals(Condition.MAX, Blows.wear(6.0, 1.0), "a pistol round wrecks a durability-1 vehicle");
    }

    @Test void durabilityDividesTheBlow() {
        assertEquals(1500, Blows.wear(6.0, 8.0), "a pistol round on a car: seven to a wreck");
        assertEquals(1200, Blows.wear(6.0, 10.0), "on a Huey");
        assertEquals(1000, Blows.wear(6.0, 12.0), "on a Chinook");
        assertEquals(8000, Blows.wear(6.0, 1.5), "on a Scout");
        assertEquals(4000, Blows.wear(1.0, 0.5), "under one, a blow costs more");
    }

    @Test void aBlowRoundsUpAndIsNeverFree() {
        assertEquals(667, Blows.wear(4.0, 12.0), "666.67 rounds up");
        assertEquals(1, Blows.wear(1e-6, 1.0), "the least blow still costs a point");
    }

    @Test void aBlowPastAWreckIsCapped() {
        assertEquals(Condition.MAX, Blows.wear(1000.0, 8.0));
        assertEquals(Condition.MAX, Blows.wear(Float.MAX_VALUE, 1.0));
    }

    @Test void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Blows.wear(0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Blows.wear(-1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Blows.wear(Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Blows.wear(Double.POSITIVE_INFINITY, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Blows.wear(1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Blows.wear(1.0, Double.NaN));
    }

    @Test void theFirstBlastChargesAll() {
        assertEquals(3000, Blows.Area.NONE.due(0, 3000), "even on the world's first tick");
        assertEquals(new Blows.Area(0, 3000), Blows.Area.NONE.after(0, 3000));
    }

    @Test void oneBlastChargesItsLargestPieceOnce() {
        // A rocket beside a car: the body, two hit boxes and two idle parts, in the game's order.
        int[] pieces = {2400, 3000, 2400, 1700, 2400};
        Blows.Area area = Blows.Area.NONE;
        int charged = 0;
        for (int wear : pieces) {
            charged += area.due(100, wear);
            area = area.after(100, wear);
        }
        assertEquals(3000, charged, "the largest piece, not the 11900 of all five");
        assertEquals(0, area.due(100, 3000), "an equal piece charges nothing");
        assertEquals(500, area.due(100, 3500), "a larger one only its excess");
    }

    @Test void aLaterTickIsABlowOfItsOwn() {
        Blows.Area area = Blows.Area.NONE.after(100, 3000);
        assertEquals(1000, area.due(101, 1000), "a second rocket a tick later counts whole");
        assertEquals(new Blows.Area(101, 1000), area.after(101, 1000), "and starts the tick's count again");
    }

    @Test void negativeWearIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Blows.Area.NONE.due(0, -1));
        assertThrows(IllegalArgumentException.class, () -> Blows.Area.NONE.after(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Blows.Area(0, -1));
    }
}
