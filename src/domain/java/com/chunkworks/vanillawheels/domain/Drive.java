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
 *     the lock; drifting implies the state was stepped with drift held and
 *     driftSide is the side (-1 left, 1 right) it began on, else 0;
 *     boostTicks >= 0 and boostPower >= 0, and boostPower is 0 when
 *     boostTicks is.
 * AF: AF(speed, heading, motion, steer, driftCharge, drifting, boostTicks,
 *     boostPower) = "moving at {@code speed} blocks a tick (negative in
 *     reverse) along {@code motion}, pointing along {@code heading}, front
 *     wheels at {@code steer}, a drift {@code driftCharge} of the way to
 *     full, drifting iff {@code drifting}, and for {@code boostTicks} more
 *     ticks allowed {@code boostPower} over the top speed". Angles: 0 is
 *     +Z, growing clockwise seen from above (the game's yaw), so +X is at
 *     -pi/2.
 *
 * @param speed       blocks per tick along {@code motion}; negative in reverse
 * @param heading     where the body points, radians
 * @param motion      where the body goes, radians; equals heading on rails
 * @param steer       the front wheels' angle, radians, positive right
 * @param driftCharge 0..1, the boost banked by drifting
 * @param drifting    whether the tail is out this tick
 * @param driftSide   which way the drift began, -1 left or 1 right; 0 when not drifting
 * @param boostTicks  ticks of boost left, 0 when not boosting
 * @param boostPower  blocks per tick allowed over the top speed while boosting; 0 when not
 */
public record Drive(double speed, double heading, double motion, double steer, double driftCharge, boolean drifting, int driftSide,
                    int boostTicks, double boostPower) {

    /** What a step wants the world to do beside moving. */
    public enum Effect { SKID, BOOST, STALLED }

    /** A step's result: the next state and its effects. */
    public record Step(Drive next, Set<Effect> effects) {}

    /** Below this fraction of top speed nothing turns and no drift starts. */
    public static final double CREEP = 0.02;
    /** The fraction of top speed under which a drift cannot begin. */
    public static final double DRIFT_FLOOR = 0.35;
    /** How much of the lock the wheels keep at top speed. */
    public static final double LOCK_AT_SPEED = 0.3;
    /** How fast the wheels reach the lock, per tick. */
    public static final double STEER_RATE = 0.25;
    /** The slip angle a drift settles at with the stick centred, radians. */
    public static final double BASE_SLIP = Math.toRadians(26);
    /** How much the stick into or against the turn tightens or widens the slip, radians. */
    public static final double SLIP_RANGE = Math.toRadians(14);
    /** The fraction of the way to the slip angle the nose swings each tick. */
    public static final double DRIFT_TURN = 0.15;
    /** The fraction of speed a drifting tick costs. */
    public static final double DRIFT_BLEED = 0.003;
    /** A drift shorter than this fraction of a full charge pays no boost. */
    public static final double MIN_CHARGE = 0.35;
    /** The slip past which the tyres are heard. */
    public static final double SKID_SLIP = Math.toRadians(10);
    /** Speed may exceed the maximum by this factor while a boost lasts. */
    public static final double BOOST_CAP = 1.3;
    /** How long a full charge's boost lasts, ticks; a part charge lasts its fraction. */
    public static final int BOOST_TICKS = 40;
    /** The fraction of the boost's power the speed gains each boosting tick until it is at the cap. */
    public static final double BOOST_RAMP = 0.25;
    /** Below this speed with no throttle the vehicle is stopped. */
    public static final double STOPPED = 0.005;
    /**
     * Rolling resistance: what a tick with no throttle takes off the speed
     * on the ground, blocks per tick, on top of drag. Drag alone leaves a
     * car rolling for a quarter of a minute; this is the engine braking
     * that brings it to rest in a few seconds.
     */
    public static final double ROLLING = 0.008;

    public Drive {
        if (driftCharge < 0 || driftCharge > 1) {
            throw new IllegalArgumentException("driftCharge is 0..1: " + driftCharge);
        }
        if (driftSide < -1 || driftSide > 1 || (drifting && driftSide == 0) || (!drifting && driftSide != 0)) {
            throw new IllegalArgumentException("a drift has a side, -1 or 1, and nothing else does: " + drifting + " " + driftSide);
        }
        if (!Double.isFinite(speed) || !Double.isFinite(heading) || !Double.isFinite(motion) || !Double.isFinite(steer) || !Double.isFinite(boostPower)) {
            throw new IllegalArgumentException("a drive must be finite");
        }
        if (boostTicks < 0 || boostPower < 0 || (boostTicks == 0) != (boostPower == 0)) {
            throw new IllegalArgumentException("a boost has ticks and power together or neither: " + boostTicks + " " + boostPower);
        }
    }

    /** effects: returns a vehicle at rest pointing along {@code heading} */
    public static Drive atRest(double heading) {
        return new Drive(0.0, heading, heading, 0.0, 0.0, false, 0, 0, 0.0);
    }

    /** effects: returns a vehicle on rails along {@code heading} at {@code speed} with its wheels at {@code steer}, drifting to {@code side} if not 0 */
    public static Drive onRails(double speed, double heading, double steer, int side) {
        return new Drive(speed, heading, heading, steer, 0.0, side != 0, side, 0, 0.0);
    }

    /** effects: returns whether a boost is running */
    public boolean boosting() {
        return boostTicks > 0;
    }

    /**
     * effects: returns how hard the boost burns, 0..1: its power as a fraction
     * of the most a boost can add under {@code t}, tailing off over its last
     * few ticks; 0 when not boosting
     */
    public double burn(Tuning t) {
        if (boostTicks == 0) {
            return 0.0;
        }
        double most = t.maxSpeed() * (BOOST_CAP - 1.0);
        return Math.min(1.0, boostPower / most) * Math.min(1.0, boostTicks / 8.0);
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
     * fuel or off the ground; drag always takes its fraction and rolling
     * resistance a fixed amount whenever the throttle is off; the wheels
     * ease toward the lock the steer asks for, scaled down with speed; the
     * heading turns by the bicycle rule; the motion follows the heading at
     * the grip's rate. A drift begins when the key is held above the floor
     * speed with the wheels turned, and from then on the nose swings to a
     * slip angle on that side (tighter with the stick into the turn, wider
     * against it) while the body slides round an arc whose curvature is the
     * slip times the drift grip; it charges while held, bleeds a little
     * speed, and pays its boost when the key is released after enough of a
     * charge. Effects: SKID while drifting with the tail out, BOOST on every
     * tick a boost runs, STALLED when the throttle is pressed with no fuel.
     */
    public Step step(Input in, Tuning t) {
        EnumSet<Effect> effects = EnumSet.noneOf(Effect.class);
        double v = speed;
        boolean driving = in.onGround();

        // The boost: while it lasts the top speed is raised by its power, and the speed climbs to it.
        int boostLeft = Math.max(0, boostTicks - 1);
        double power = boostLeft > 0 ? boostPower : 0.0;
        double top = t.maxSpeed() + power;
        if (boostTicks > 0 && driving && in.fuel() && v >= 0) {
            v = Math.min(top, v + Math.max(t.acceleration(), boostPower * BOOST_RAMP));
        }

        // Throttle, brake, reverse.
        if (in.throttle() != 0 && driving) {
            if (!in.fuel()) {
                effects.add(Effect.STALLED);
            } else if (in.throttle() > 0) {
                v = v < 0 ? Math.min(0.0, v + t.brake()) : Math.min(Math.max(top, v), v + t.acceleration());
            } else {
                v = v > 0 ? Math.max(0.0, v - t.brake()) : Math.max(-t.reverseSpeed(), v - t.acceleration() * 0.6);
            }
        }
        // Drag, rolling resistance off the throttle, and a stop when there is nothing left.
        if (driving) {
            v *= 1.0 - t.drag();
            if (in.throttle() == 0) {
                v = Math.signum(v) * Math.max(0.0, Math.abs(v) - ROLLING);
            }
        }
        if (in.throttle() == 0 && Math.abs(v) < STOPPED) {
            v = 0.0;
        }
        // Over the top with no boost to hold it there, the speed decays back to the top.
        if (Math.abs(v) > top) {
            v = Math.signum(v) * Math.max(top, Math.abs(v) - t.acceleration());
        }

        // Steering: the wheels ease to the lock, the lock shrinks with speed.
        double fraction = Math.min(1.0, Math.abs(v) / t.maxSpeed());
        boolean canStart = in.drift() && driving && fraction >= DRIFT_FLOOR && in.steer() != 0;
        boolean holding = drifting && in.drift() && driving && fraction >= DRIFT_FLOOR * 0.6;
        boolean nowDrifting = holding || canStart;
        int side = holding ? driftSide : canStart ? in.steer() : 0;
        double lockScale = 1.0 - (1.0 - LOCK_AT_SPEED) * fraction;
        double lock = t.steer() * lockScale;
        double wantSteer = nowDrifting ? side * t.steer() : in.steer() * lock;
        // The wheels ease to the lock, except on a drift's release, when they take the stick's angle at
        // once: the body goes where it points, rather than carrying on round for the ticks the wheels
        // would take to come back from the drift's full lock.
        boolean released = drifting && !nowDrifting;
        double s = released ? wantSteer : steer + (wantSteer - steer) * STEER_RATE;

        double h = heading;
        double m = motion;
        if (nowDrifting) {
            // A drift, the kart way: the nose swings out to a slip angle on the
            // side the drift began, tighter with the stick into the turn and
            // wider against it, and the body slides round an arc whose
            // curvature is the slip times the drift grip. Speed bleeds a little.
            double targetSlip = side * (BASE_SLIP + side * in.steer() * SLIP_RANGE);
            double slipNow = wrap(h - m);
            double slipNext = slipNow + (targetSlip - slipNow) * DRIFT_TURN;
            m = wrap(m + slipNext * t.driftGrip());
            h = wrap(m + slipNext);
            v *= 1.0 - DRIFT_BLEED;
        } else {
            // The heading turns by the bicycle rule; nothing turns at a creep.
            if (driving && Math.abs(v) > t.maxSpeed() * CREEP) {
                h = wrap(h + Math.tan(s) * v / t.wheelBase());
            }
            // The motion follows the heading at the grip's rate.
            if (driving) {
                m = wrap(motion + wrap(h - motion) * t.grip());
            }
        }

        // The drift charge, and its boost on release: a surge lasting the charge's share of BOOST_TICKS,
        // allowing the charge's share of the tuning's boost over the top, and the throttle cannot cancel it.
        double charge = driftCharge;
        if (nowDrifting) {
            charge = Math.min(1.0, charge + 1.0 / t.driftChargeTicks());
            if (Math.abs(wrap(h - m)) > SKID_SLIP) {
                effects.add(Effect.SKID);
            }
        } else if (drifting && !in.drift() && charge >= MIN_CHARGE) {
            if (in.fuel() && driving && v > 0) {
                boostLeft = (int) Math.round(BOOST_TICKS * charge);
                power = Math.min(t.driftBoost() * charge, t.maxSpeed() * (BOOST_CAP - 1.0));
                if (power <= 0.0) {
                    boostLeft = 0;
                }
            }
            charge = 0.0;
        } else if (!nowDrifting) {
            charge = 0.0;
        }
        if (boostLeft > 0) {
            effects.add(Effect.BOOST);
        } else {
            power = 0.0;
        }

        return new Step(new Drive(v, h, m, s, charge, nowDrifting, nowDrifting ? side : 0, boostLeft, power), Set.copyOf(effects));
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
