/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Chunkworks
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. The bar's percent: below nothing, nothing (a tap), between, just under full (89),
 * full (90), past full (100, the most the game sends). The strength: the average horse's, the
 * least and the most a horse is born with, refused at 0, negative, over 2, not a number. The
 * flight: a launch upward, at nothing, downward; refused not finite. The game's own numbers are
 * the oracle: {@code AbstractHorse.onPlayerJump} and {@code LivingEntity.travel} for 1.21.1.
 */
final class JumpTest {

    @Test
    void theBarMapsAsTheHorsesDoes() {
        assertEquals(0.4, Jump.share(-5), 0.0, "below nothing, as nothing");
        assertEquals(0.4, Jump.share(0), 0.0, "a tap jumps at two fifths");
        assertEquals(0.4 + 0.4 * 45 / 90.0, Jump.share(45), 1e-12);
        assertEquals(0.4 + 0.4 * 89 / 90.0, Jump.share(89), 1e-12, "just under full is still a share");
        assertEquals(1.0, Jump.share(90), 0.0, "full from ninety");
        assertEquals(1.0, Jump.share(100), 0.0);
    }

    @Test
    void theLaunchIsTheStrengthTimesTheShare() {
        assertEquals(0.7, Jump.HORSE.launch(100), 1e-12);
        assertEquals(0.28, Jump.HORSE.launch(0), 1e-12);
        assertEquals(0.4, new Jump(0.4).launch(90), 1e-12, "the weakest horse born");
        assertEquals(1.0, new Jump(1.0).launch(90), 1e-12, "the strongest horse born");
    }

    @Test
    void inTheAirGravityThenDrag() {
        assertEquals((0.7 - 0.08) * 0.98, Jump.airborne(0.7), 1e-12);
        assertEquals(-0.08 * 0.98, Jump.airborne(0.0), 1e-12);
        assertEquals((-0.5 - 0.08) * 0.98, Jump.airborne(-0.5), 1e-12);
    }

    @Test
    void anAverageHorseAtAFullBarRisesOverThreeBlocksAndATapUnderOne() {
        double full = Jump.peak(Jump.HORSE.launch(100));
        double tap = Jump.peak(Jump.HORSE.launch(0));
        assertEquals(3.124, full, 1e-3, "the sum of the moves the game makes");
        assertTrue(full > 2.0 + 0.5, "clears a two-block wall with room to spare: " + full);
        assertEquals(0.623, tap, 1e-3);
        assertTrue(tap < 1.0, "a tap clears no more than a step: " + tap);
    }

    @Test
    void aLaunchAtOrBelowNothingRisesNothing() {
        assertEquals(0.0, Jump.peak(0.0), 0.0);
        assertEquals(0.0, Jump.peak(-0.3), 0.0);
        assertThrows(IllegalArgumentException.class, () -> Jump.peak(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Jump.peak(Double.POSITIVE_INFINITY));
    }

    @Test
    void aStrengthIsPositiveAndAtMostTwo() {
        assertThrows(IllegalArgumentException.class, () -> new Jump(0.0));
        assertThrows(IllegalArgumentException.class, () -> new Jump(-0.7));
        assertThrows(IllegalArgumentException.class, () -> new Jump(2.01));
        assertThrows(IllegalArgumentException.class, () -> new Jump(Double.NaN));
        assertEquals(2.0, new Jump(2.0).strength(), 0.0);
    }
}
