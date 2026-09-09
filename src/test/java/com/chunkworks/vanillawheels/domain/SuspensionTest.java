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
 * Partitions. Flat ground: level stays level. A step up of one and of two:
 * the drawn body starts where it was (the lift takes the jump) and glides
 * up, never overshooting, settled within a dozen ticks. A step down. A
 * slope: the pitch's sign (nose down when the front is lower) and the
 * roll's (leaning right when the right side is lower). Clamps: a jump
 * beyond the maximum lift, a cliff beyond the maximum tilt. Bounds refused.
 */
final class SuspensionTest {
    private static final double WHEEL_BASE = 2.9;
    private static final double TRACK = 2.0;

    private static Suspension settle(Suspension s, double front, double rear, double left, double right, int ticks) {
        for (int i = 0; i < ticks; i++) {
            s = s.step(front, rear, left, right, WHEEL_BASE, TRACK);
        }
        return s;
    }

    @Test
    void flatGroundStaysLevel() {
        Suspension s = settle(Suspension.LEVEL, 0, 0, 0, 0, 20);
        assertEquals(Suspension.LEVEL, s);
        assertTrue(s.isSettled());
    }

    @Test
    void aStepUpIsAGlideNotAJump() {
        for (double step : new double[] {1.0, 2.0}) {
            Suspension s = Suspension.LEVEL.jumped(step);
            assertEquals(-step, s.lift(), 1e-9, "the drawn body stays put as the box jumps up " + step);
            double last = s.lift();
            for (int i = 0; i < 30; i++) {
                s = s.step(0, 0, 0, 0, WHEEL_BASE, TRACK);
                assertTrue(s.lift() >= last - 1e-9 && s.lift() <= 1e-9, "glides up monotonically, never past level: " + s.lift());
                last = s.lift();
            }
            assertTrue(s.isSettled(), "settled after thirty ticks: " + s);
            Suspension twelve = settle(Suspension.LEVEL.jumped(step), 0, 0, 0, 0, 12);
            assertTrue(Math.abs(twelve.lift()) < step * 0.03, "within three percent after twelve ticks: " + twelve.lift());
        }
    }

    @Test
    void aStepDownGlidesDown() {
        Suspension s = Suspension.LEVEL.jumped(-1.0);
        assertEquals(1.0, s.lift(), 1e-9, "drawn a block above the box that dropped");
        s = settle(s, 0, 0, 0, 0, 30);
        assertTrue(s.isSettled());
    }

    @Test
    void slopesPitchAndRollTheRightWay() {
        Suspension nose = settle(Suspension.LEVEL, -0.5, 0.0, 0, 0, 40);
        assertTrue(nose.pitch() > 0, "front lower: nose down is positive: " + nose.pitch());
        assertEquals(Math.atan2(0.5, WHEEL_BASE), nose.pitch(), 1e-6);
        assertEquals(-0.25, nose.lift(), 1e-6, "the body rides the mean of the axles");
        Suspension climb = settle(Suspension.LEVEL, 1.0, 0.0, 0, 0, 40);
        assertTrue(climb.pitch() < 0, "front higher: nose up");
        Suspension lean = settle(Suspension.LEVEL, 0, 0, 0.0, -0.4, 40);
        assertTrue(lean.roll() > 0, "right side lower: leaning right is positive");
    }

    @Test
    void jumpsAndTiltsAreClamped() {
        assertEquals(-Suspension.MAX_LIFT, Suspension.LEVEL.jumped(10.0).lift(), 1e-9);
        Suspension cliff = settle(Suspension.LEVEL, -10.0, 0.0, 0, 0, 60);
        assertEquals(Suspension.MAX_TILT, cliff.pitch(), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> new Suspension(0, Math.PI, 0));
        assertThrows(IllegalArgumentException.class, () -> new Suspension(Double.NaN, 0, 0));
    }
}
