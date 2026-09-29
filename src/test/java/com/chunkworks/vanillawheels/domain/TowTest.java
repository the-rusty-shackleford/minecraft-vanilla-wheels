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
 * Partitions. The hitch on the tongue: nothing moves. The hitch pulled
 * straight ahead: the axle follows by the same distance, the heading holds,
 * the tongue meets the hitch. The hitch pulled to the side: the heading
 * turns toward it and the tongue meets it; a long pull converges on the
 * tower's line. A hitch pushed back past a right angle: the fold is held
 * at a right angle. Length refused at zero. Angles wrap. The tongue helper
 * inverts follow. The catch: a loose tongue within reach, outside, at the
 * edge; a release holds on the ball, ends at the first parting, and the
 * trailer is caught again after. The let-go speed: it coasts the distance,
 * a hair less does not, a longer distance takes more, a distance the top
 * speed cannot coast gives the top speed, and no distance is refused.
 */
final class TowTest {
    private static final double L = 3.0;

    @Test
    void aStillHitchLeavesTheTrailerAlone() {
        double[] tongue = Tow.tongue(0.0, 0.0, 0.0, L);
        Tow.Follow f = Tow.follow(tongue[0], tongue[1], 0.0, 0.0, 0.0, L, 0.0);
        assertEquals(0.0, f.axleX(), 1e-12);
        assertEquals(0.0, f.axleZ(), 1e-12);
        assertEquals(0.0, f.heading(), 1e-12);
        assertEquals(0.0, f.travelled(), 1e-12);
    }

    @Test
    void aStraightPullDragsTheAxleTheSameDistanceAndTheTongueMeetsTheHitch() {
        // Heading 0 is +Z; the hitch moves 0.4 along +Z.
        Tow.Follow f = Tow.follow(0.0, L + 0.4, 0.0, 0.0, 0.0, L, 0.0);
        assertEquals(0.0, f.axleX(), 1e-12);
        assertEquals(0.4, f.axleZ(), 1e-12);
        assertEquals(0.0, f.heading(), 1e-12);
        assertEquals(0.4, f.travelled(), 1e-12);
        double[] tongue = Tow.tongue(f.axleX(), f.axleZ(), f.heading(), L);
        assertEquals(0.0, tongue[0], 1e-12);
        assertEquals(L + 0.4, tongue[1], 1e-12);
    }

    @Test
    void aSidePullTurnsTheHeadingTowardTheHitchAndConverges() {
        // The tower has turned to face +X (yaw -pi/2) and its hitch sits off to the trailer's side.
        double tower = -Math.PI / 2;
        double hx = 1.0, hz = L;
        Tow.Follow f = Tow.follow(hx, hz, 0.0, 0.0, 0.0, L, tower);
        assertTrue(f.heading() < 0 && f.heading() > tower, "turned toward +X, not all the way: " + f.heading());
        double[] tongue = Tow.tongue(f.axleX(), f.axleZ(), f.heading(), L);
        assertEquals(hx, tongue[0], 1e-9, "the tongue is on the hitch");
        assertEquals(hz, tongue[1], 1e-9);
        // Pull the hitch on along +X for a while: the trailer lines up behind the tower.
        for (int i = 0; i < 200; i++) {
            hx += 0.3;
            f = Tow.follow(hx, hz, f.axleX(), f.axleZ(), f.heading(), L, tower);
        }
        assertEquals(tower, f.heading(), 1e-3, "in line behind the tower");
        assertEquals(hz, f.axleZ(), 1e-2, "on the tower's line");
    }

    @Test
    void aHardReverseHoldsTheFoldAtARightAngle() {
        // The tower faces +Z; the hitch is pushed back to the trailer's side and behind its axle.
        Tow.Follow f = Tow.follow(2.0, -1.0, 0.0, 0.0, 0.0, L, 0.0);
        assertEquals(Tow.FOLD, Math.abs(Tow.wrap(f.heading() - 0.0)), 1e-9, "folded no further than a right angle");
    }

    @Test
    void boundsAndWrapping() {
        assertThrows(IllegalArgumentException.class, () -> Tow.follow(0, 0, 0, 0, 0, 0.0, 0));
        assertEquals(-Math.PI / 2, Tow.wrap(3 * Math.PI / 2), 1e-12);
        assertEquals(Math.PI / 2, Tow.wrap(-3 * Math.PI / 2), 1e-12);
        // A tower at yaw just past pi and a trailer just under -pi are nearly aligned, not folded.
        Tow.Follow f = Tow.follow(0.0, -L - 0.1, 0.0, 0.0, -Math.PI + 0.01, L, Math.PI - 0.01);
        assertTrue(Math.abs(Tow.wrap(f.heading() - (Math.PI - 0.01))) < 0.05, "aligned across the wrap: " + f.heading());
    }

    @Test
    void aReleaseHoldsUntilTheTwoPartThenTheTrailerIsCaughtAsAnyLooseOne() {
        double reach = 0.5;
        assertTrue(Tow.catches(0.1, false, reach), "a loose tongue on the ball is caught");
        assertTrue(!Tow.catches(0.5, false, reach), "at reach, not");
        assertTrue(!Tow.catches(0.9, false, reach), "beyond, not");
        // Let go by hand: the tongue lies on the ball and the tower drives off.
        boolean released = true;
        double[] path = {0.0, 0.1, 0.3, 0.49, 0.6, 1.2, 0.8, 0.3};
        StringBuilder caught = new StringBuilder();
        for (double d : path) {
            caught.append(Tow.catches(d, released, reach) ? '#' : '.');
            released = Tow.stillReleased(d, released, reach);
        }
        assertEquals(".......#", caught.toString(), "not caught until the two have parted and come together again");
        assertTrue(!released, "the release is spent");
    }

    @Test
    void theLetGoSpeedCoastsTheDistanceAndAHairLessDoesNot() {
        Tuning t = Tuning.pickup();
        double v = Tow.letGoSpeed(0.75, t);
        assertTrue(Tow.coasted(v, t) >= 0.75, "it coasts the distance: " + Tow.coasted(v, t));
        assertTrue(Tow.coasted(v - 0.001, t) < 0.75, "a hair less does not: " + Tow.coasted(v - 0.001, t));
        assertTrue(v < t.maxSpeed() / 2, "a gentle roll, not a shove: " + v);
        assertTrue(Tow.letGoSpeed(1.5, t) > v, "further takes more");
        assertEquals(t.maxSpeed(), Tow.letGoSpeed(10_000.0, t), 0.0, "past what the top speed coasts: the top speed, never a throw in a click");
        assertThrows(IllegalArgumentException.class, () -> Tow.letGoSpeed(0.0, t));
    }
}
