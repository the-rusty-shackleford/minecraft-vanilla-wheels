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

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Partitions: zero/low/high speed; equal/unequal mass; head-on/glancing;
 * closing/separating/repeated contact; fragile threshold; reverse/sideways drive. */
final class ContactTest {
    private static Impact.Velocity v(double x, double z) { return new Impact.Velocity(x,z); }
    @Test void restAndSeparationDoNothingIncludingRepeatedContact() {
        var rest = Impact.contact(v(0,0),1,v(0,0),1,1,0);
        assertEquals(v(0,0), rest.first());
        var hit = Impact.contact(v(1,0),1,v(0,0),1,1,0);
        assertEquals(hit, Impact.contact(hit.first(),1,hit.second(),1,1,0));
    }
    @Test void equalMassSharesMomentumAndLosesEnergy() {
        var hit = Impact.contact(v(1,0),1,v(0,0),1,1,0);
        assertEquals(1, hit.first().x() + hit.second().x(), 1e-9);
        assertTrue(hit.first().x() > 0 && hit.second().x() > hit.first().x());
        assertTrue(hit.first().x()*hit.first().x() + hit.second().x()*hit.second().x() < 1);
        var gentle = Impact.contact(v(.1,0),1,v(0,0),1,1,0);
        assertEquals(.05, gentle.first().x(), 1e-9);
    }
    @Test void heavyBodiesDeflectLessAndGlancingContactsSlide() {
        var hit = Impact.contact(v(1,.4),4,v(0,-.2),1,1,0);
        assertTrue(hit.first().x() > .7);
        assertEquals(4, 4*hit.first().x() + hit.second().x(), 1e-9);
        assertEquals(.4, hit.first().z()); assertEquals(-.2, hit.second().z());
    }
    @Test void wallsReboundHardContactsButStopGentleOnes() {
        assertEquals(v(0,0), Impact.wall(v(.1,0),1,0));
        var hard = Impact.wall(v(1,.5),1,0);
        assertEquals(-.12,hard.x(),1e-9); assertEquals(.49,hard.z(),1e-9);
        assertEquals(hard, Impact.wall(hard,1,0));
    }
    @Test void fragileThresholdDependsOnSpeedAndMass() {
        assertFalse(Impact.breaksFragile(.1,50));
        assertFalse(Impact.breaksFragile(.2,1));
        assertTrue(Impact.breaksFragile(.4,1));
        assertTrue(Impact.breaksFragile(-.2,3));
        assertThrows(IllegalArgumentException.class, () -> Impact.breaksFragile(Double.NaN,1));
    }
    @Test void driveKeepsTheActualImpulseDirectionAndReverseSign() {
        Drive drive = Drive.atRest(0);
        for (var velocity : new Impact.Velocity[] {v(.4,.2),v(0,-.4),v(-.5,0)}) {
            Drive next = drive.impacted(velocity);
            assertEquals(velocity.x(), next.velocityX(),1e-9);
            assertEquals(velocity.z(), next.velocityZ(),1e-9);
            assertFalse(next.boosting());
        }
        assertTrue(drive.impacted(v(0,-.4)).speed() < 0);
    }
}
