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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Speed: below the pace, at it, half of the top, the top,
 * beyond the top (capped), reverse (sign dropped). Mass: one, heavier hurts
 * more. Knockback: the same speed bands. Speed after a hit: a large and a
 * small victim. Bad numbers refused.
 */
final class ImpactTest {

    @Test
    void nothingUnderAWalkingPaceThenDamageBySpeedSquared() {
        assertEquals(0.0f, Impact.damage(0.1, 0.9, 1.0));
        assertEquals(0.0, Impact.knockback(0.1, 0.9));
        float atPace = Impact.damage(Impact.PACE, 0.9, 1.0);
        assertTrue(atPace > 0);
        float half = Impact.damage(0.45, 0.9, 1.0);
        float top = Impact.damage(0.9, 0.9, 1.0);
        assertEquals(8.0f, top, 1e-6);
        assertEquals(2.0f, half, 1e-6, "half the speed, a quarter of the damage");
        assertEquals(top, Impact.damage(1.5, 0.9, 1.0), 1e-6, "beyond the top counts as the top");
        assertEquals(half, Impact.damage(-0.45, 0.9, 1.0), 1e-6, "reverse hits too");
        assertEquals(8.0f * 1.45f, Impact.damage(0.9, 0.9, 1.45), 1e-5, "a heavier vehicle hurts more");
    }

    @Test
    void theShoveGrowsWithSpeed() {
        double crawl = Impact.knockback(Impact.PACE, 0.9);
        double top = Impact.knockback(0.9, 0.9);
        assertTrue(crawl > 0.4 && crawl < top);
        assertEquals(Impact.KNOCKBACK_BASE + Impact.KNOCKBACK_GAIN, top, 1e-9);
        assertEquals(top, Impact.knockback(2.0, 0.9), 1e-9, "capped");
    }

    @Test
    void aCowSlowsTheVehicleAndAChickenBarely() {
        assertEquals(0.9 * Impact.KEEP_AFTER_LARGE, Impact.speedAfter(0.9, 0.9), 1e-9);
        assertEquals(0.9 * Impact.KEEP_AFTER_SMALL, Impact.speedAfter(0.9, 0.4), 1e-9);
        assertTrue(Impact.speedAfter(0.9, 0.9) < Impact.speedAfter(0.9, 0.4));
    }

    @Test
    void badNumbersAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Impact.damage(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> Impact.damage(1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> Impact.knockback(1, 0));
    }
}
