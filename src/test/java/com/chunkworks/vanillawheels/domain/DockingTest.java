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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Fit, on each shape: narrow and short; as wide as the deck, a
 * hair wider; as long as the longest, a hair longer; the fleet (the
 * Trailblazer, the Pickup, the Trailer, the subs dock on both; the Huey
 * and the Chinook on neither); what the long deck takes that the short
 * one does not. Depth (D-0043): a body that leaves GAP at both ends sits
 * centred between the front edge and the wall's face, the same margin each
 * end, on each shape; one that does not backs up to the wall, its tail GAP
 * off the face; the two rules agree at the boundary; the longest body's
 * nose lands OVERHANG past the front; the Pickup centres on the long deck
 * and backs up on the short, its nose over the edge; no length refused. Progress: nowhere
 * through the rise, rising through the hold and monotone, there through
 * the fall, out of range refused. Turn: none, a quarter each way, a half
 * either way goes positive, more than a turn around, wrapping past 360.
 * Yaw and position at a progress: the ends and the middle. Rest: moving,
 * counting up, ready at REST and on that tick alone, quiet past it, a move
 * starts it over, out of range refused, each state one instance.
 */
final class DockingTest {

    @Test
    void whatFitsEachDeck() {
        assertEquals(5.0 + 5.0 / 16.0 - 0.25 + 1.5, Docking.longest(Footprint.SHORT), 1e-12);
        assertEquals(7.0 + 5.0 / 16.0 - 0.25 + 1.5, Docking.longest(Footprint.LONG), 1e-12);
        for (Footprint f : java.util.List.of(Footprint.SHORT, Footprint.LONG)) {
            double longest = Docking.longest(f);
            assertTrue(Docking.fits(f, 1.5, 3.0), "a small car");
            assertTrue(Docking.fits(f, Footprint.WIDTH, 3.0), "as wide as the deck");
            assertFalse(Docking.fits(f, Footprint.WIDTH + 0.01, 3.0), "a hair wider");
            assertTrue(Docking.fits(f, 2.0, longest), "as long as the longest");
            assertFalse(Docking.fits(f, 2.0, longest + 0.01), "a hair longer");
            // The fleet, by the bodies in their profiles.
            assertTrue(Docking.fits(f, 1.84, 4.64), "Trailblazer");
            assertTrue(Docking.fits(f, 2.54, 6.44), "Farmer's Pickup");
            assertTrue(Docking.fits(f, 2.35, 5.18), "Trailer");
            assertTrue(Docking.fits(f, 2.7, 5.3), "Explorer");
            assertTrue(Docking.fits(f, 1.4, 3.3), "Scout");
            assertFalse(Docking.fits(f, 2.6, 12.6), "Huey");
            assertFalse(Docking.fits(f, 3.8, 15.9), "Chinook");
        }
        assertFalse(Docking.fits(Footprint.SHORT, 1.5, 8.0), "an eight-block body is past the short deck");
        assertTrue(Docking.fits(Footprint.LONG, 1.5, 8.0), "and docks on the long one, its nose over the edge");
    }

    @Test
    void aDockedBodySitsCentredWithTheSameMarginAtBothEndsOrBacksUpToTheWall() {
        for (Footprint f : java.util.List.of(Footprint.SHORT, Footprint.LONG)) {
            double face = f.wallFace();
            for (double length : new double[] {3.0, 4.64}) {
                assertTrue(Docking.centres(f, length), length + " centres on " + f.length());
                double depth = Docking.depth(f, length);
                double front = depth - length / 2.0, back = face - (depth + length / 2.0);
                assertEquals(front, back, 1e-12, "the same margin at the nose and the tail");
                assertTrue(back >= Docking.GAP, "and at least the gap");
                assertEquals(face / 2.0, depth, 1e-12, "the middle of the stretch from the front edge to the wall's face");
            }
            double edge = face - 2.0 * Docking.GAP;
            assertTrue(Docking.centres(f, edge), "a body that leaves exactly the gap at both ends centres");
            assertFalse(Docking.centres(f, edge + 1e-9), "a hair longer backs up");
            assertEquals(Docking.depth(f, edge), face - Docking.GAP - edge / 2.0, 1e-12, "and the two rules agree there");
            double longest = Docking.longest(f);
            assertEquals(face - Docking.GAP, Docking.depth(f, longest) + longest / 2.0, 1e-12, "backed up: the tail GAP off the wall's face");
            assertEquals(-Docking.OVERHANG, Docking.depth(f, longest) - longest / 2.0, 1e-12, "the longest body's nose hangs OVERHANG past the front edge");
            assertThrows(IllegalArgumentException.class, () -> Docking.depth(f, 0.0));
            assertThrows(IllegalArgumentException.class, () -> Docking.depth(f, -1.0));
        }
        assertEquals(2.65625, Docking.depth(Footprint.SHORT, 4.64), 1e-12, "a Trailblazer: the short deck's middle, a third of a block off the wall");
        assertEquals(3.65625, Docking.depth(Footprint.LONG, 4.64), 1e-12, "and a block deeper on the long one, 1.34 off each end");
        assertFalse(Docking.centres(Footprint.SHORT, 6.44), "the Pickup backs up on the short deck");
        assertTrue(Docking.depth(Footprint.SHORT, 6.44) - 3.22 < 0, "its nose past the front edge");
        assertTrue(Docking.centres(Footprint.LONG, 6.44), "and centres on the long one");
        assertEquals(0.43625, Docking.depth(Footprint.LONG, 6.44) - 3.22, 1e-12, "0.44 inside the front edge, and the same off the wall (D-0042, D-0043)");
    }

    @Test
    void theGlideRunsThroughTheHold() {
        assertEquals(0.0, Docking.progress(LiftMotion.JOB), "nowhere at the start");
        assertEquals(0.0, Docking.progress(LiftMotion.JOB - LiftMotion.UP), "nowhere as the deck reaches the top");
        double last = 0.0;
        for (int t = 1; t <= LiftMotion.HOLD; t++) {
            double g = Docking.progress(LiftMotion.JOB - LiftMotion.UP - t);
            assertTrue(g >= last, "gliding on at hold tick " + t);
            last = g;
        }
        assertEquals(0.5, Docking.progress(LiftMotion.JOB - LiftMotion.UP - LiftMotion.HOLD / 2), 1e-12, "half way through the hold");
        assertEquals(1.0, Docking.progress(LiftMotion.DOWN), "there as the deck starts down");
        assertEquals(1.0, Docking.progress(0), "there at the end");
        assertThrows(IllegalArgumentException.class, () -> Docking.progress(-1));
        assertThrows(IllegalArgumentException.class, () -> Docking.progress(LiftMotion.JOB + 1));
    }

    @Test
    void theTurnGoesTheShortWayAndAHalfTurnGoesPositive() {
        assertEquals(0.0, Docking.turn(180.0, 180.0), 1e-12);
        assertEquals(90.0, Docking.turn(0.0, 90.0), 1e-12);
        assertEquals(-90.0, Docking.turn(90.0, 0.0), 1e-12);
        assertEquals(180.0, Docking.turn(0.0, 180.0), 1e-12, "a half turn");
        assertEquals(180.0, Docking.turn(180.0, 0.0), 1e-12, "the other half turn, the same way");
        assertEquals(20.0, Docking.turn(350.0, 10.0), 1e-12, "across 360");
        assertEquals(-20.0, Docking.turn(10.0, 350.0), 1e-12);
        assertEquals(-45.0, Docking.turn(135.0, 90.0 + 360.0 * 3), 1e-12, "turns around count for nothing");
        assertEquals(45.0, Docking.turn(-90.0 - 720.0, -45.0), 1e-12);
    }

    @Test
    void positionAndYawAtAProgress() {
        assertEquals(2.0, Docking.at(2.0, 5.0, 0.0), 1e-12);
        assertEquals(3.5, Docking.at(2.0, 5.0, 0.5), 1e-12);
        assertEquals(5.0, Docking.at(2.0, 5.0, 1.0), 1e-12);
        assertEquals(135.0, Docking.yawAt(135.0, 180.0, 0.0), 1e-12);
        assertEquals(157.5, Docking.yawAt(135.0, 180.0, 0.5), 1e-12);
        assertEquals(180.0, Docking.yawAt(135.0, 180.0, 1.0), 1e-12);
        // Backed in (facing the back, yaw 0, on a lift whose front is yaw 180): spun half a turn, through 90.
        assertEquals(90.0, Docking.yawAt(0.0, 180.0, 0.5), 1e-12);
        assertEquals(180.0, Docking.yawAt(0.0, 180.0, 1.0), 1e-12);
    }

    @Test
    void theRestWatchIsReadyOnTheRestTickAloneAndAMoveStartsItOver() {
        Docking.Rest r = Docking.Rest.MOVING;
        assertFalse(r.ready());
        for (int t = 1; t < Docking.REST; t++) {
            r = r.observe(true);
            assertEquals(t, r.ticks());
            assertFalse(r.ready(), "not yet at " + t);
        }
        r = r.observe(true);
        assertTrue(r.ready(), "ready at REST");
        Docking.Rest asked = r.observe(true);
        assertFalse(asked.ready(), "asked: quiet from then on");
        assertEquals(Docking.Rest.ASKED, asked.ticks());
        assertEquals(asked, asked.observe(true), "and stays quiet");
        assertFalse(asked.observe(true).observe(true).ready());
        assertEquals(Docking.Rest.MOVING, r.observe(false), "a move starts it over");
        assertEquals(Docking.Rest.MOVING, asked.observe(false));
        assertFalse(r.observe(false).observe(true).ready());
        assertTrue(r.observe(false) == Docking.Rest.MOVING && asked.observe(true) == asked.observe(true), "one instance a state");
        assertThrows(IllegalArgumentException.class, () -> new Docking.Rest(-1));
        assertThrows(IllegalArgumentException.class, () -> new Docking.Rest(Docking.Rest.ASKED + 1));
    }
}
