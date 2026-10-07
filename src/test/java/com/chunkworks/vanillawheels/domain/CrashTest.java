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
 * Partitions. The speeds: an aircraft's (safe 0.25, touchdown 0.3, wreck 1.5) and a slower body's;
 * refused when a safe speed is negative, at or past the wreck, or not finite. A crash: at rest, up
 * to the safe speed (nothing), just past it (a little), at 1.0 (about 36% for the aircraft), at and
 * past the wreck (all), monotone between, not finite (refused). A touchdown: up to its safe sink
 * (nothing), past it. What two moves lost: no change, a change the body's own model could make
 * (none), more. The closing speed of a collision: a wall met square (the whole speed, met early or
 * late in the tick), at a slant (the part into it), a roof (the climb), nothing lost (none). Its
 * cost: a floor met within the landing's safe sink (none), past it (a touchdown's), a wall or a
 * roof (a crash's), nothing lost (none); a load on a rope, charged for what it carried.
 */
final class CrashTest {
    /** Rotorcraft's aircraft, whose numbers this was built with. */
    private static final Crash AIRCRAFT = new Crash(0.25, 0.3, 1.5);

    @Test
    void aCrashCostsNothingUpToTheSafeSpeedThenGrowsWithTheSquareToAWreck() {
        assertEquals(0, AIRCRAFT.crash(0.0));
        assertEquals(0, AIRCRAFT.crash(AIRCRAFT.safe()));
        assertTrue(AIRCRAFT.crash(AIRCRAFT.safe() + 0.01) > 0 && AIRCRAFT.crash(AIRCRAFT.safe() + 0.01) < 100, "a little: " + AIRCRAFT.crash(AIRCRAFT.safe() + 0.01));
        assertEquals(3600, AIRCRAFT.crash(1.0), 1, "three quarters of the way to a wreck, squared");
        assertEquals(Condition.MAX, AIRCRAFT.crash(AIRCRAFT.wreck()));
        assertEquals(Condition.MAX, AIRCRAFT.crash(9.0));
        int last = 0;
        for (double v = 0.0; v < 2.0; v += 0.01) {
            int w = AIRCRAFT.crash(v);
            assertTrue(w >= last, "never less for a faster crash: " + v);
            last = w;
        }
        assertThrows(IllegalArgumentException.class, () -> AIRCRAFT.crash(Double.NaN));
    }

    @Test
    void aSlowerBodyIsWreckedSoonerByItsOwnSpeeds() {
        Crash slow = new Crash(0.1, 0.1, 0.6);
        assertEquals(0, slow.crash(0.1));
        assertEquals(Condition.MAX / 4, slow.crash(0.35), 1, "half way from safe to a wreck: a quarter");
        assertEquals(Condition.MAX, slow.crash(0.6));
        assertTrue(slow.crash(0.5) > AIRCRAFT.crash(0.5), "the same blow costs a slower body more");
    }

    @Test
    void speedsThatCannotMakeACrashAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Crash(-0.1, 0.3, 1.5), "a negative safe speed");
        assertThrows(IllegalArgumentException.class, () -> new Crash(0.25, -0.1, 1.5), "a negative safe sink");
        assertThrows(IllegalArgumentException.class, () -> new Crash(1.5, 0.3, 1.5), "safe at the wreck");
        assertThrows(IllegalArgumentException.class, () -> new Crash(0.25, 2.0, 1.5), "a safe sink past the wreck");
        assertThrows(IllegalArgumentException.class, () -> new Crash(0.25, 0.3, Double.POSITIVE_INFINITY), "an endless wreck speed");
        assertThrows(IllegalArgumentException.class, () -> new Crash(Double.NaN, 0.3, 1.5), "not a number");
    }

    @Test
    void aLandingCostsNothingUpToItsSafeSinkAndAHardOneDoes() {
        assertEquals(0, AIRCRAFT.touchdown(AIRCRAFT.touchdownSafe()));
        assertTrue(AIRCRAFT.touchdown(0.6) > 0);
        assertTrue(AIRCRAFT.touchdown(0.6) < AIRCRAFT.crash(0.6), "the ground's safe sink is a little more than a wall's");
        assertThrows(IllegalArgumentException.class, () -> AIRCRAFT.touchdown(Double.POSITIVE_INFINITY));
    }

    @Test
    void whatTwoMovesLostIsTheChangeTheBodysOwnModelCouldNotHaveMade() {
        double shed = 0.04;
        assertEquals(0.0, Crash.lost(1, 0, 0, 1, 0, 0, shed), 1e-12, "no change");
        assertEquals(0.0, Crash.lost(1, 0, 0, 0.97, 0, 0, shed), 1e-12, "a change the model could make");
        assertEquals(1.0 - shed, Crash.lost(1, 0, 0, 0, 0, 0, shed), 1e-12, "stopped");
        assertEquals(0.5 - shed, Crash.lost(0, -0.5, 0, 0, 0, 0, shed), 1e-12, "the ground met sinking");
    }

    @Test
    void theClosingSpeedIsTheSpeedCarriedAlongWhatWasLostWhereverInTheTickTheWallWasMet() {
        assertEquals(1.0, Crash.closing(1, 0, 0, 0, 0, 0), 1e-12, "a wall met square at the end of the tick");
        assertEquals(1.0, Crash.closing(1, 0, 0, 0.7, 0, 0), 1e-12, "the same wall met a third of the way in: the same crash");
        double v = 1.0 / Math.sqrt(2.0);
        assertEquals(v, Crash.closing(v, 0, v, 0, 0, v), 1e-12, "a wall met at a slant: only the part into it");
        assertEquals(0.4, Crash.closing(0, 0.4, 0, 0, 0.1, 0), 1e-12, "a roof met climbing");
        assertEquals(0.0, Crash.closing(0.6, 0, 0.2, 0.6, 0, 0.2), 1e-12, "nothing lost");
    }

    @Test
    void aCollisionCostsALandingGoingDownAndACrashOtherwise() {
        assertEquals(0, AIRCRAFT.impact(0, -0.2, 0, 0, 0, 0), "a soft landing");
        assertEquals(AIRCRAFT.touchdown(0.6), AIRCRAFT.impact(0.1, -0.6, 0, 0.1, 0, 0), "a hard one, gliding a little");
        assertEquals(AIRCRAFT.crash(0.6), AIRCRAFT.impact(0, 0.6, 0, 0, 0, 0), "a roof met climbing is a crash");
        assertEquals(AIRCRAFT.crash(1.0), AIRCRAFT.impact(1.0, 0, 0, 0.2, 0, 0), "a wall");
        assertEquals(0, AIRCRAFT.impact(0.5, 0, 0, 0.5, 0, 0), "nothing lost");
    }

    @Test
    void aLoadHeldByARoofIsChargedForWhatItCarriedNotWhatItsRopeAsked() {
        assertEquals(AIRCRAFT.crash(0.4), AIRCRAFT.impact(0, 0.4, 0, 0, 0.4, 0, 0, 0.1, 0), "rising at 0.4 into the roof: a crash at 0.4");
        assertEquals(0, AIRCRAFT.impact(0, 0, 0, 0, 1.6, 0, 0, 0, 0), "held there, the rope asking more each tick, it carries nothing into it");
        assertEquals(AIRCRAFT.crash(1.0), AIRCRAFT.impact(1.0, 0, 0, 0.9, 0.3, 0, 0, 0.3, 0), "swung into a wall at 1.0, the rope pulling it up as well");
        assertEquals(0, AIRCRAFT.impact(1.0, 0, 0, 0.9, 0.3, 0, 0.9, 0.3, 0), "swinging free, the rope turning it: nothing lost to the world");
    }
}
