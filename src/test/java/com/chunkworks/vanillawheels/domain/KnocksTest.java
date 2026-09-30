/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Punches toward packing a vehicle (D-0025).
 * Partitions: the first punch; punches in a row (at once, exactly GAP apart); a pause of GAP + 1
 * (a new row); the sixth (packs) and beyond (stays packing); a punch before the last (refused);
 * construction outside 0..TO_PACK.
 */
class KnocksTest {
    @Test void theFirstPunchStartsARow() {
        assertFalse(Knocks.NONE.packs());
        assertEquals(new Knocks(1, 500), Knocks.NONE.knocked(500));
        assertEquals(new Knocks(1, 0), Knocks.NONE.knocked(0), "a world's first tick");
    }

    @Test void sixPunchesInARowPack() {
        Knocks k = Knocks.NONE;
        for (int i = 0; i < Knocks.TO_PACK - 1; i++) {
            k = k.knocked(100 + i * Knocks.GAP);
            assertFalse(k.packs(), "punch " + (i + 1) + " of a row, GAP apart");
        }
        k = k.knocked(100 + (Knocks.TO_PACK - 1) * Knocks.GAP);
        assertTrue(k.packs(), "the sixth");
        assertEquals(Knocks.TO_PACK, k.knocked(k.last()).count(), "a seventh at the same tick stays at six");
    }

    @Test void punchesAtOneTickCount() {
        Knocks k = Knocks.NONE;
        for (int i = 0; i < Knocks.TO_PACK; i++) k = k.knocked(7);
        assertTrue(k.packs());
    }

    @Test void aPauseLongerThanTheGapStartsAgain() {
        Knocks k = Knocks.NONE.knocked(0).knocked(10).knocked(20);
        assertEquals(3, k.count());
        assertEquals(new Knocks(1, 20 + Knocks.GAP + 1), k.knocked(20 + Knocks.GAP + 1));
        assertEquals(4, k.knocked(20 + Knocks.GAP).count(), "exactly GAP is still in the row");
    }

    @Test void timeRunsForward() {
        assertThrows(IllegalArgumentException.class, () -> Knocks.NONE.knocked(50).knocked(49));
    }

    @Test void theCountStaysInRange() {
        assertThrows(IllegalArgumentException.class, () -> new Knocks(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Knocks(Knocks.TO_PACK + 1, 0));
    }
}
