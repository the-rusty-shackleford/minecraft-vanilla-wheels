/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A repair by hand (D-0025).
 * Partitions: clicked from a wreck, mid-way, within a step of MAX (capped), at MAX (refused);
 * exhaustion by full cost 20, 18, 12, 1, and an invalid cost; a full rebuild's total.
 */
class HandRepairTest {
    @Test void aClickRestoresAStep() {
        assertEquals(new Condition(250), HandRepair.clicked(new Condition(0)));
        assertEquals(new Condition(5250), HandRepair.clicked(new Condition(5000)));
        assertEquals(new Condition(Condition.MAX), HandRepair.clicked(new Condition(9900)), "never past MAX");
        assertThrows(IllegalArgumentException.class, () -> HandRepair.clicked(new Condition(Condition.MAX)));
    }

    @Test void fortyClicksRebuildAWreck() {
        Condition c = new Condition(0);
        int clicks = 0;
        while (c.remaining() < Condition.MAX) { c = HandRepair.clicked(c); clicks++; }
        assertEquals(HandRepair.CLICKS, clicks);
        assertEquals(40, clicks);
    }

    @Test void theTiringScalesWithTheJob() {
        assertEquals(0.5f, HandRepair.exhaustion(20), "the Trailblazer: Immersive Aircraft's half point a click");
        assertEquals(0.45f, HandRepair.exhaustion(18), 1e-6f, "the Pickup");
        assertEquals(0.3f, HandRepair.exhaustion(12), 1e-6f, "the Trailer");
        assertEquals(0.025f, HandRepair.exhaustion(1), 1e-6f);
        assertEquals(20.0f, HandRepair.exhaustion(20) * HandRepair.CLICKS, 1e-4f, "a full rebuild: five food points");
        assertThrows(IllegalArgumentException.class, () -> HandRepair.exhaustion(0));
    }
}
