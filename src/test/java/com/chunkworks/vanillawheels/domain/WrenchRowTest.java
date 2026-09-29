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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chunkworks.vanillawheels.domain.WrenchRow.Fill;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Halves: nothing, a sliver, exactly a half, just over, a
 * scratch off full, full. Fill: every icon at full, none, an odd count.
 * Jiggle: at a fifth, just over, nothing; the offset is 0 or 1 and moves.
 * The watch: steady; a hurt (blinks twenty ticks, the old condition pale,
 * lit as the game's hearts are); a second hurt inside the blink (the first
 * old condition stays pale); a repair (ten ticks, nothing pale); the first
 * tick told (no blink).
 */
final class WrenchRowTest {

    @Test
    void halvesRoundUpSoAnythingAboveNothingShows() {
        assertEquals(0, WrenchRow.halves(0));
        assertEquals(1, WrenchRow.halves(1), "a sliver shows as half a wrench");
        assertEquals(1, WrenchRow.halves(500));
        assertEquals(2, WrenchRow.halves(501));
        assertEquals(20, WrenchRow.halves(9_999));
        assertEquals(20, WrenchRow.halves(Condition.MAX));
    }

    @Test
    void wrenchesFillFromTheFirst() {
        for (int i = 0; i < WrenchRow.ICONS; i++) {
            assertEquals(Fill.FULL, WrenchRow.fill(20, i));
            assertEquals(Fill.EMPTY, WrenchRow.fill(0, i));
        }
        assertEquals(Fill.FULL, WrenchRow.fill(3, 0));
        assertEquals(Fill.HALF, WrenchRow.fill(3, 1));
        assertEquals(Fill.EMPTY, WrenchRow.fill(3, 2));
        assertEquals(Fill.HALF, WrenchRow.fill(19, 9));
    }

    @Test
    void theRowJigglesAtAFifthOrLessAndTheJiggleMoves() {
        assertTrue(WrenchRow.jiggles(0));
        assertTrue(WrenchRow.jiggles(2_000));
        assertFalse(WrenchRow.jiggles(2_001));
        boolean[] seen = new boolean[2];
        for (int tick = 0; tick < 100; tick++) {
            for (int i = 0; i < WrenchRow.ICONS; i++) {
                int j = WrenchRow.jiggle(tick, i);
                assertTrue(j == 0 || j == 1, "a pixel at most");
                seen[j] = true;
            }
        }
        assertTrue(seen[0] && seen[1], "both offsets occur");
        int changes = 0;
        for (int tick = 0; tick < 100; tick++) {
            if (WrenchRow.jiggle(tick, 3) != WrenchRow.jiggle(tick + 1, 3)) {
                changes++;
            }
        }
        assertTrue(changes > 20, "a wrench moves from tick to tick: " + changes);
    }

    @Test
    void aSteadyRowDoesNotBlinkNorDoesTheFirstTickTold() {
        WrenchRow.Watch w = new WrenchRow.Watch();
        w.observe(4_000);
        assertEquals(0, w.blink());
        for (int i = 0; i < 30; i++) {
            w.observe(4_000);
            assertFalse(w.lit());
        }
        assertEquals(4_000, w.shown());
    }

    @Test
    void aHurtBlinksASecondWithWhatWasLostShownPaleAsHeartsDo() {
        WrenchRow.Watch w = new WrenchRow.Watch();
        w.observe(Condition.MAX);
        w.observe(8_000);
        assertEquals(WrenchRow.HURT_BLINK, w.blink());
        assertEquals(Condition.MAX, w.shown(), "the lost fifth shows pale");
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < WrenchRow.HURT_BLINK; i++) {
            pattern.append(w.lit() ? '#' : '.');
            w.observe(8_000);
        }
        // The game's: lit while (ticks left / 3) is odd, from 20 ticks left down to 1.
        assertEquals("...###...###...###..", pattern.toString());
        assertEquals(0, w.blink());
        assertEquals(8_000, w.shown(), "the pale part goes when the blink ends");
    }

    @Test
    void aSecondHurtInsideTheBlinkKeepsTheFirstConditionPale() {
        WrenchRow.Watch w = new WrenchRow.Watch();
        w.observe(9_000);
        w.observe(7_000);
        for (int i = 0; i < 5; i++) {
            w.observe(7_000);
        }
        w.observe(5_000);
        assertEquals(WrenchRow.HURT_BLINK, w.blink(), "the blink starts again");
        assertEquals(9_000, w.shown(), "everything lost since the blink began shows pale");
    }

    @Test
    void aRepairBlinksHalfASecondWithNothingPale() {
        WrenchRow.Watch w = new WrenchRow.Watch();
        w.observe(3_000);
        w.observe(Condition.MAX);
        assertEquals(WrenchRow.REPAIR_BLINK, w.blink());
        assertEquals(Condition.MAX, w.shown());
    }
}
