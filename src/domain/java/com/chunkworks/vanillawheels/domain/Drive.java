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

import java.util.EnumSet;
import java.util.Set;

/**
 * How a vehicle moves: its state one tick to the next under a driver's
 * {@link Input} and a {@link Tuning}. An arcade model with a bicycle's
 * geometry -- the front wheels' angle turns the heading by an amount that
 * grows with speed and shrinks with the lock allowed at speed -- and a
 * motion direction that follows the heading at the rate the grip allows,
 * so with the grip down (a drift) the body points one way and slides
 * another. Everything is in blocks, ticks and radians; the caller turns
 * {@link #velocityX()} and {@link #velocityZ()} into a move.
 *
 * <p>RI: |speed| <= maxSpeed * BOOST_CAP (any tuning it was stepped with);
 *     heading and motion are angles; 0 <= driftCharge <= 1; steer is within
 *     the lock; drifting implies the state was stepped with drift held.
 * AF: AF(speed, heading, motion, steer, driftCharge, drifting) = "moving
 *     at {@code speed} blocks a tick (negative in reverse) along
 *     {@code motion}, pointing along {@code heading}, front wheels at
 *     {@code steer}, a drift {@code driftCharge} of the way to full,
 *     drifting iff {@code drifting}". Angles: 0 is +Z, growing clockwise
 *     seen from above (the game's yaw), so +X is at -pi/2.
 *
 * @param speed       blocks per tick along {@code motion}; negative in reverse
 * @param heading     where the body points, radians
 * @param motion      where the body goes, radians; equals heading on rails
 * @param steer       the front wheels' angle, radians, positive right
 * @param driftCharge 0..1, the boost banked by drifting
 * @param drifting    whether the tail is out this tick
 */
public record Drive(double speed, double heading, double motion, double steer, double driftCharge, boolean drifting) {

    /** What a step wants the world to do beside moving. */
    public enum Effect { SKID, BOOST, STALLED }

    /** A step's result: the next state and its effects. */
    public record Step(Drive next, Set<Effect> effects) {}

    /** Below this fraction of top speed nothing turns and no drift starts. */
    public static final double CREEP = 0.02;
    /** The fraction of top speed under which a drift cannot begin. */
    public static final double DRIFT_FLOOR = 0.35;
    /** How much of the lock the wheels keep at top speed. */
    public static final double LOCK_AT_SPEED = 0.4;
    /** How fast the wheels reach the lock, per tick. */
    public static final double STEER_RATE = 0.25;
    /** A drift widens the lock by this factor. */
    public static final double DRIFT_LOCK = 1.5;
    /** Speed may exceed the maximum by this factor for a moment after a boost. */
    public static final double BOOST_CAP = 1.3;
    /** Below this speed with no throttle the vehicle is stopped. */
    public static final double STOPPED = 0.005;

    public Drive {
        if (driftCharge < 0 || driftCharge > 1) {
            throw new IllegalArgumentException("driftCharge is 0..1: " + driftCharge);
        }
        if (!Double.isFinite(speed) || !Double.isFinite(heading) || !Double.isFinite(motion) || !Double.isFinite(steer)) {
            throw new IllegalArgumentException("a drive must be finite");
        }
    }

    /** effects: returns a vehicle at rest pointing along {@code heading} */
    public static Drive atRest(double heading) {
        return new Drive(0.0, heading, heading, 0.0, 0.0, false);
    }

    /** effects: returns the speed as a fraction of the top speed, 0..BOOST_CAP, sign dropped */
    public double fraction(Tuning t) {
        return Math.abs(speed) / t.maxSpeed();
    }

    /** effects: returns the velocity's x component this tick */
    public double velocityX() {
        return -Math.sin(motion) * speed;
    }

    /** effects: returns the velocity's z component this tick */
    public double velocityZ() {
        return Math.cos(motion) * speed;
    }

    /** effects: returns the angle between where the body points and where it goes, radians, signed */
    public double slip() {
        return wrap(heading - motion);
    }

    /**
     * effects: returns this state one tick on under {@code in} and
     * {@code t}: throttle changes speed toward the top (forward) or reverse
     * speed, or brakes when it opposes the motion, and does nothing without
     * fuel or off the ground; drag always takes its fraction; the wheels
     * ease toward the lock the steer asks for, scaled down with speed; the
     * heading turns by the bicycle rule; the motion follows the heading at
     * the grip's rate, or the drift grip's while drifting; a drift begins
     * when the key is held above the floor speed with the wheels turned,
     * charges while held, and pays its boost when the key is released.
     * Effects: SKID while drifting with the tail out, BOOST on the release,
     * STALLED when the throttle is pressed with no fuel.
     */
    public Step step(Input in, Tuning t) {
        EnumSet<Effect> effects = EnumSet.noneOf(Effect.class);
        double v = speed;
        boolean driving = in.onGround();

        // Throttle, brake, reverse.
        if (in.throttle() != 0 && driving) {
            if (!in.fuel()) {
                effects.add(Effect.STALLED);
            } else if (in.throttle() > 0) {
                v = v < 0 ? Math.min(0.0, v + t.brake()) : Math.min(t.maxSpeed(), v + t.acceleration());
            } else {
                v = v > 0 ? Math.max(0.0, v - t.brake()) : Math.max(-t.reverseSpeed(), v - t.acceleration() * 0.6);
            }
        }
        // Drag, and a stop when there is nothing left.
        if (driving) {
            v *= 1.0 - t.drag();
        }
        if (in.throttle() == 0 && Math.abs(v) < STOPPED) {
            v = 0.0;
        }
        // A boost above the top speed decays back to it.
        if (Math.abs(v) > t.maxSpeed()) {
            v = Math.signum(v) * Math.max(t.maxSpeed(), Math.abs(v) - t.acceleration());
        }

        // Steering: the wheels ease to the lock, the lock shrinks with speed.
        double fraction = Math.min(1.0, Math.abs(v) / t.maxSpeed());
        boolean canDrift = in.drift() && driving && fraction >= DRIFT_FLOOR && in.steer() != 0;
        boolean nowDrifting = canDrift || (drifting && in.drift() && driving && fraction >= DRIFT_FLOOR * 0.6);
        double lockScale = 1.0 - (1.0 - LOCK_AT_SPEED) * fraction;
        double lock = t.steer() * lockScale * (nowDrifting ? DRIFT_LOCK : 1.0);
        double wantSteer = in.steer() * lock;
        double s = steer + (wantSteer - steer) * STEER_RATE;
        if (Math.abs(s) > t.steer() * DRIFT_LOCK) {
            s = Math.signum(s) * t.steer() * DRIFT_LOCK;
        }

        // The heading turns by the bicycle rule; nothing turns at a creep.
        double h = heading;
        if (driving && Math.abs(v) > t.maxSpeed() * CREEP) {
            h = wrap(h + Math.tan(s) * v / t.wheelBase());
        }

        // The motion follows the heading at the grip's rate.
        double gripNow = nowDrifting ? t.driftGrip() : t.grip();
        double m = wrap(motion + wrap(h - motion) * gripNow);
        if (!driving) {
            m = motion;
        }

        // The drift charge, and its boost on release.
        double charge = driftCharge;
        if (nowDrifting) {
            charge = Math.min(1.0, charge + 1.0 / t.driftChargeTicks());
            if (Math.abs(wrap(h - m)) > 0.05) {
                effects.add(Effect.SKID);
            }
        } else if (drifting && !in.drift() && charge > 0.0) {
            if (in.fuel() && driving && v > 0) {
                v = Math.min(t.maxSpeed() * BOOST_CAP, v + t.driftBoost() * charge);
                effects.add(Effect.BOOST);
            }
            charge = 0.0;
        } else if (!nowDrifting) {
            charge = 0.0;
        }

        return new Step(new Drive(v, h, m, s, charge, nowDrifting), Set.copyOf(effects));
    }

    /** effects: returns {@code a} wrapped into (-pi, pi] */
    public static double wrap(double a) {
        double w = a % (2 * Math.PI);
        if (w <= -Math.PI) {
            w += 2 * Math.PI;
        } else if (w > Math.PI) {
            w -= 2 * Math.PI;
        }
        return w;
    }
}
