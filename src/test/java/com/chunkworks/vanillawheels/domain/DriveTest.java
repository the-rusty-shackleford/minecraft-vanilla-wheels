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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Throttle {-1, 0, 1} x fuel {empty, some} x ground {on, off}:
 * forward gains to the top and no further, reverse to its own top, the
 * brake stops before reversing, no fuel stalls, in the air nothing changes
 * but the boost decays. Drag and rolling: a coasting car slows and stops
 * without ever reversing, within the ticks rolling resistance alone allows;
 * rolling bites only off the throttle and only on the ground. Steering: none at rest, a turn at low speed, a smaller turn
 * per block at top speed; left and right symmetric; the wheels ease to the
 * lock and back. Drift: refused below the floor, refused without steer,
 * held: grip drops and the charge fills to one and stops, a skid is
 * reported while the tail is out; released: a boost proportional to the
 * charge, capped, decaying back to the top speed; released with no charge:
 * nothing; a drift needs fuel to boost. Tuning: each bound refused. Angles
 * wrap.
 */
final class DriveTest {
    private static final Tuning T = Tuning.pickup();

    private static Drive run(Drive d, Input in, int ticks) {
        for (int i = 0; i < ticks; i++) {
            d = d.step(in, T).next();
        }
        return d;
    }

    private static final Input GAS = new Input(1, 0, false, true, true);
    private static final Input REVERSE = new Input(-1, 0, false, true, true);

    @Test
    void theThrottleGainsToTheTopSpeedAndNoFurther() {
        Drive d = Drive.atRest(0.0);
        Drive one = d.step(GAS, T).next();
        assertEquals(T.acceleration() * (1 - T.drag()), one.speed(), 1e-9, "one tick of gas, less drag");
        Drive fast = run(d, GAS, 400);
        assertTrue(fast.speed() <= T.maxSpeed() && fast.speed() > T.maxSpeed() * 0.97, "at the top: " + fast.speed());
        assertEquals(0.0, fast.velocityX(), 1e-9, "heading 0 is +Z");
        assertEquals(fast.speed(), fast.velocityZ(), 1e-9);
    }

    @Test
    void reverseHasItsOwnTopAndTheBrakeStopsBeforeReversing() {
        Drive fast = run(Drive.atRest(0.0), GAS, 400);
        Drive braking = fast.step(REVERSE, T).next();
        assertTrue(braking.speed() < fast.speed() && braking.speed() > 0, "braking, still forward");
        Drive stopped = run(fast, REVERSE, 40);
        assertTrue(stopped.speed() <= 0, "brake through zero into reverse: " + stopped.speed());
        Drive back = run(Drive.atRest(0.0), REVERSE, 400);
        assertTrue(back.speed() < 0 && back.speed() >= -T.reverseSpeed(), "reverse top: " + back.speed());
    }

    @Test
    void noFuelStallsAndTheAirChangesNothing() {
        Drive.Step stalled = Drive.atRest(0.0).step(new Input(1, 0, false, true, false), T);
        assertEquals(0.0, stalled.next().speed());
        assertTrue(stalled.effects().contains(Drive.Effect.STALLED));
        Drive moving = run(Drive.atRest(0.0), GAS, 50);
        Drive airborne = moving.step(new Input(1, 1, false, false, true), T).next();
        assertEquals(moving.speed(), airborne.speed(), 1e-12, "no gas, no drag in the air");
        assertEquals(moving.heading(), airborne.heading(), 1e-12, "no turning in the air");
        assertEquals(moving.motion(), airborne.motion(), 1e-12);
    }

    @Test
    void aCoastingCarSlowsAndStopsWithoutReversingWithinAFewSeconds() {
        Drive d = run(Drive.atRest(0.0), GAS, 100);
        assertTrue(d.speed() > T.maxSpeed() * 0.97, "at the top after 100 ticks of gas: " + d.speed());
        Drive coasting = d;
        double last = coasting.speed();
        int ticks = 0;
        for (; ticks < 2000 && coasting.speed() > 0; ticks++) {
            coasting = coasting.step(Input.NONE, T).next();
            assertTrue(coasting.speed() <= last && coasting.speed() >= 0, "monotone to rest");
            last = coasting.speed();
        }
        assertEquals(0.0, coasting.speed(), "stopped");
        assertTrue(ticks <= T.maxSpeed() / Drive.ROLLING + 1, "rolling resistance alone bounds the stop: " + ticks);
        assertTrue(ticks >= 40, "but a car at the top does roll on for a couple of seconds: " + ticks);
        // Reverse coasts to rest the same way, never past zero.
        Drive backing = run(Drive.atRest(0.0), REVERSE, 100);
        assertTrue(backing.speed() < 0, "backing up");
        Drive stopped = run(backing, Input.NONE, 200);
        assertEquals(0.0, stopped.speed(), "reverse rolls to rest too");
    }

    @Test
    void rollingResistanceOnlyBitesOffTheThrottle() {
        Drive d = run(Drive.atRest(0.0), GAS, 100);
        Drive held = d.step(GAS, T).next();
        assertEquals(d.speed(), held.speed(), 1e-9, "the throttle holds the top speed against drag and rolling");
        Drive airborne = d.step(Input.coasting(false, true), T).next();
        assertEquals(d.speed(), airborne.speed(), 1e-12, "nothing rolls in the air");
    }

    @Test
    void steeringTurnsWithSpeedAndNotAtRest() {
        Drive still = Drive.atRest(0.0).step(new Input(0, 1, false, true, true), T).next();
        assertEquals(0.0, still.heading(), "no turning at rest");
        Input rightSlow = new Input(1, 1, false, true, true);
        Drive slow = run(Drive.atRest(0.0), rightSlow, 20);
        assertTrue(slow.heading() > 0, "a right turn raises the heading (the game's yaw)");
        Drive left = run(Drive.atRest(0.0), new Input(1, -1, false, true, true), 20);
        assertEquals(-slow.heading(), left.heading(), 1e-9, "left mirrors right");
        // Per block travelled, a car at top speed turns less than one at a fifth of it.
        Drive fast = run(Drive.atRest(0.0), GAS, 400);
        double turnFast = fast.step(rightSlow, T).next().heading() / fast.speed();
        Drive fifth = run(Drive.atRest(0.0), GAS, 12);
        double fifthSpeed = fifth.speed();
        assertTrue(fifthSpeed < T.maxSpeed() * 0.3, "a fifth or so: " + fifthSpeed);
        Drive turned = fifth;
        for (int i = 0; i < 4; i++) {
            turned = turned.step(new Input(0, 1, false, true, true), T).next();
        }
        double turnSlow = turned.heading() / (4 * fifthSpeed);
        assertTrue(turnFast < turnSlow, "less lock at speed: " + turnFast + " vs " + turnSlow);
        assertTrue(Math.abs(fast.steer()) < 1e-9, "wheels straight before the turn");
        Drive eased = fast.step(rightSlow, T).next();
        assertTrue(eased.steer() > 0 && eased.steer() < T.steer(), "the wheels ease toward the lock");
    }

    @Test
    void aDriftNeedsSpeedAndSteerThenSlidesChargesAndBoosts() {
        Input driftRight = new Input(1, 1, true, true, true);
        Drive slow = run(Drive.atRest(0.0), GAS, 5);
        assertFalse(slow.step(driftRight, T).next().drifting(), "too slow to drift");
        Drive fast = run(Drive.atRest(0.0), GAS, 400);
        assertFalse(fast.step(new Input(1, 0, true, true, true), T).next().drifting(), "no steer, no drift");
        Drive.Step first = fast.step(driftRight, T);
        assertTrue(first.next().drifting());
        Drive d = first.next();
        boolean skidded = false;
        for (int i = 0; i < 60; i++) {
            Drive.Step s = d.step(driftRight, T);
            skidded |= s.effects().contains(Drive.Effect.SKID);
            d = s.next();
        }
        assertEquals(1.0, d.driftCharge(), 1e-9, "a full charge after the charge ticks");
        assertTrue(skidded, "the tail was out");
        assertTrue(Math.abs(d.slip()) > 0.05, "pointing one way, going another: " + d.slip());
        Drive.Step release = d.step(new Input(1, 1, false, true, true), T);
        assertTrue(release.effects().contains(Drive.Effect.BOOST));
        assertTrue(release.next().speed() > T.maxSpeed(), "boosted past the top: " + release.next().speed());
        assertTrue(release.next().speed() <= T.maxSpeed() * Drive.BOOST_CAP, "capped");
        assertEquals(0.0, release.next().driftCharge());
        assertFalse(release.next().drifting());
        Drive settled = run(release.next(), GAS, 100);
        assertTrue(settled.speed() <= T.maxSpeed() + 1e-9, "the boost decays back to the top");
        assertTrue(Math.abs(settled.slip()) < 0.02, "grip pulls the motion back to the heading");
    }

    @Test
    void releasingWithNoChargeOrNoFuelBoostsNothing() {
        Drive fast = run(Drive.atRest(0.0), GAS, 400);
        Drive.Step plain = fast.step(new Input(1, 0, false, true, true), T);
        assertFalse(plain.effects().contains(Drive.Effect.BOOST));
        Drive drifting = run(fast, new Input(1, 1, true, true, true), 10);
        assertTrue(drifting.driftCharge() > 0);
        Drive.Step dry = drifting.step(new Input(1, 1, false, true, false), T);
        assertFalse(dry.effects().contains(Drive.Effect.BOOST), "no fuel, no boost");
        assertEquals(0.0, dry.next().driftCharge(), "the charge is spent anyway");
    }

    @Test
    void tuningRefusesEachBadNumber() {
        Tuning t = T;
        assertThrows(IllegalArgumentException.class, () -> new Tuning(0, t.reverseSpeed(), t.acceleration(), t.brake(), t.drag(), t.grip(), t.driftGrip(), t.steer(), t.driftBoost(), t.driftChargeTicks(), t.wheelBase(), t.climb(), t.mass()));
        assertThrows(IllegalArgumentException.class, () -> new Tuning(t.maxSpeed(), t.maxSpeed() + 1, t.acceleration(), t.brake(), t.drag(), t.grip(), t.driftGrip(), t.steer(), t.driftBoost(), t.driftChargeTicks(), t.wheelBase(), t.climb(), t.mass()));
        assertThrows(IllegalArgumentException.class, () -> new Tuning(t.maxSpeed(), t.reverseSpeed(), t.acceleration(), t.brake(), 1.0, t.grip(), t.driftGrip(), t.steer(), t.driftBoost(), t.driftChargeTicks(), t.wheelBase(), t.climb(), t.mass()));
        assertThrows(IllegalArgumentException.class, () -> new Tuning(t.maxSpeed(), t.reverseSpeed(), t.acceleration(), t.brake(), t.drag(), 0.5, 0.6, t.steer(), t.driftBoost(), t.driftChargeTicks(), t.wheelBase(), t.climb(), t.mass()));
        assertThrows(IllegalArgumentException.class, () -> new Tuning(t.maxSpeed(), t.reverseSpeed(), t.acceleration(), t.brake(), t.drag(), t.grip(), t.driftGrip(), Math.PI / 2, t.driftBoost(), t.driftChargeTicks(), t.wheelBase(), t.climb(), t.mass()));
        assertThrows(IllegalArgumentException.class, () -> new Tuning(t.maxSpeed(), t.reverseSpeed(), t.acceleration(), t.brake(), t.drag(), t.grip(), t.driftGrip(), t.steer(), t.driftBoost(), 0, t.wheelBase(), t.climb(), t.mass()));
        assertThrows(IllegalArgumentException.class, () -> new Input(2, 0, false, true, true));
        assertThrows(IllegalArgumentException.class, () -> new Drive(0, 0, 0, 0, 1.5, false));
    }

    @Test
    void anglesWrap() {
        assertEquals(-Math.PI / 2, Drive.wrap(3 * Math.PI / 2), 1e-12);
        assertEquals(Math.PI, Drive.wrap(Math.PI), 1e-12);
        assertEquals(Math.PI, Drive.wrap(-Math.PI), 1e-12);
        assertEquals(0.5, Drive.wrap(0.5 + 4 * Math.PI), 1e-12);
        Drive d = run(Drive.atRest(3.0), new Input(1, 1, false, true, true), 200);
        assertTrue(d.heading() > -Math.PI && d.heading() <= Math.PI, "heading stays wrapped: " + d.heading());
    }
}
