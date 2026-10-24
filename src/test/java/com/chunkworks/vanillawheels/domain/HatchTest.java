/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Floor Hatch's rules (D-0037).
 *
 * <p>Partitions. travel: MIN_DEPTH, MAX_DEPTH, out of range. advance: opening and shutting from shut,
 * between and open; a step that would pass open or shut; travel out of range. step: up, down, round
 * at both ends. wanted: each reason alone and none. mayOpen: nobody on; someone on with nothing
 * rising, something far, something at CLOSE. mayShut: each condition failing. guardWanted: shut and
 * not wanted, opening, open, filled. guard: up and down from each end and between; out of range.
 * motion: nothing now; first seen; level; up; down. expect: each motion, filled, the countdown's ends.
 */
class HatchTest {
    @Test
    void thePlateTravelsItsDepthAndStopsAtEitherEnd() {
        assertEquals(8, Hatch.travel(Hatch.MIN_DEPTH));
        assertEquals(128, Hatch.travel(Hatch.MAX_DEPTH));
        assertThrows(IllegalArgumentException.class, () -> Hatch.travel(0));
        assertThrows(IllegalArgumentException.class, () -> Hatch.travel(17));
        assertEquals(4, Hatch.advance(3, 0, true), "half a block a tick");
        assertEquals(24, Hatch.advance(3, 22, true), "never past open");
        assertEquals(24, Hatch.advance(3, 24, true));
        assertEquals(20, Hatch.advance(3, 24, false));
        assertEquals(0, Hatch.advance(3, 2, false), "never past shut");
        assertEquals(0, Hatch.advance(3, 0, false));
        int travel = 0, ticks = 0;
        while (travel < Hatch.travel(3)) {
            travel = Hatch.advance(3, travel, true);
            ticks++;
        }
        assertEquals(6, ticks, "a three-deep plate slides open in 0.3 s");
        assertThrows(IllegalArgumentException.class, () -> Hatch.advance(3, -1, true));
        assertThrows(IllegalArgumentException.class, () -> Hatch.advance(3, 25, false));
    }

    @Test
    void aClickStepsTheDepthRoundTheRange() {
        assertEquals(4, Hatch.step(3, false));
        assertEquals(2, Hatch.step(3, true));
        assertEquals(Hatch.MIN_DEPTH, Hatch.step(Hatch.MAX_DEPTH, false));
        assertEquals(Hatch.MAX_DEPTH, Hatch.step(Hatch.MIN_DEPTH, true));
        assertThrows(IllegalArgumentException.class, () -> Hatch.step(0, false));
    }

    @Test
    void itIsWantedOpenWhilePoweredWaitingOrFilled() {
        assertTrue(Hatch.wanted(true, false, false));
        assertTrue(Hatch.wanted(false, true, false));
        assertTrue(Hatch.wanted(false, false, true));
        assertFalse(Hatch.wanted(false, false, false));
    }

    @Test
    void itNeverOpensUnderAFootUnlessTheCarIsAlmostThere() {
        assertTrue(Hatch.mayOpen(false, Double.POSITIVE_INFINITY), "nobody on it");
        assertFalse(Hatch.mayOpen(true, Double.POSITIVE_INFINITY), "someone on it, nothing rising (powered)");
        assertFalse(Hatch.mayOpen(true, 6.0), "someone on it, the car far below");
        assertTrue(Hatch.mayOpen(true, Hatch.CLOSE), "the car this close takes the plate if it stays: it goes");
        assertTrue(Hatch.mayOpen(true, 0.2));
    }

    @Test
    void itShutsOnlyOverAnEmptyOpening() {
        assertTrue(Hatch.mayShut(true, true));
        assertFalse(Hatch.mayShut(false, true), "something fills the opening");
        assertFalse(Hatch.mayShut(true, false), "something is in the plate's layer");
    }

    @Test
    void theGuardStandsWhileTheHoleIsOpenAndEmpty() {
        assertFalse(Hatch.guardWanted(0, false, false), "shut and staying shut");
        assertTrue(Hatch.guardWanted(0, true, false), "about to open: the guard first");
        assertTrue(Hatch.guardWanted(12, false, false), "part open, shutting");
        assertTrue(Hatch.guardWanted(24, true, false), "open");
        assertFalse(Hatch.guardWanted(24, true, true), "the car home fills the hole");
        assertEquals(1, Hatch.guard(0, true));
        assertEquals(Hatch.GUARD_TICKS, Hatch.guard(Hatch.GUARD_TICKS, true));
        assertEquals(Hatch.GUARD_TICKS - 1, Hatch.guard(Hatch.GUARD_TICKS, false));
        assertEquals(0, Hatch.guard(0, false));
        assertEquals(2, Hatch.guard(3, false));
        assertThrows(IllegalArgumentException.class, () -> Hatch.guard(-1, true));
        assertThrows(IllegalArgumentException.class, () -> Hatch.guard(Hatch.GUARD_TICKS + 1, false));
    }

    @Test
    void whatItSeesBelowRisesFallsOrStandsStill() {
        assertEquals(Hatch.Motion.NONE, Hatch.motion(60.0, Double.NaN));
        assertEquals(Hatch.Motion.NONE, Hatch.motion(Double.NaN, Double.NaN));
        assertEquals(Hatch.Motion.STILL, Hatch.motion(Double.NaN, 62.0), "first seen");
        assertEquals(Hatch.Motion.STILL, Hatch.motion(62.0, 62.0005));
        assertEquals(Hatch.Motion.RISING, Hatch.motion(62.0, 62.2));
        assertEquals(Hatch.Motion.FALLING, Hatch.motion(62.2, 62.0));
    }

    @Test
    void itWaitsForWhatRoseUntilItArrivesFallsOrIsLongGone() {
        assertEquals(Hatch.FORGET, Hatch.expect(0, Hatch.Motion.RISING, false));
        assertEquals(0, Hatch.expect(Hatch.FORGET, Hatch.Motion.FALLING, false), "sent back down");
        assertEquals(0, Hatch.expect(Hatch.FORGET, Hatch.Motion.RISING, true), "arrived: the opening is filled");
        assertEquals(Hatch.FORGET - 1, Hatch.expect(Hatch.FORGET, Hatch.Motion.NONE, false));
        assertEquals(0, Hatch.expect(0, Hatch.Motion.NONE, false));
        assertEquals(37, Hatch.expect(37, Hatch.Motion.STILL, false), "held: still waiting");
        assertThrows(IllegalArgumentException.class, () -> Hatch.expect(-1, Hatch.Motion.NONE, false));
        assertThrows(IllegalArgumentException.class, () -> Hatch.expect(Hatch.FORGET + 1, Hatch.Motion.NONE, false));
    }
}
