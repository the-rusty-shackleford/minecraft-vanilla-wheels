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

/**
 * How a body is posed on the ground it stands on: the terrain sensed as a
 * vehicle's wheels would sense it, one plane fitted through it, and the
 * body eased onto that plane. nfx's design, ported from his patch
 * ({@code GroundPose} and {@code TrailerPose}) into the pure layer: the
 * world is reached only through {@link Columns}, so the whole thing runs
 * under a test with a synthetic terrain.
 *
 * <p>A wheel is a disc, not a column: it starts riding up over a step one
 * radius before its centre gets there, along a quarter circle. So a wheel's
 * ground is sampled along four rays across its diameter, each column beyond
 * a block boundary lowered by the rim's height there, and the wheel rests
 * on the highest result; since the boundary distances vary continuously as
 * the body moves, so does the result, where fixed offsets gave a staircase
 * that shook the body.
 *
 * <p>The body's targets come from one plane, {@code h = a z + b x + c},
 * fitted by weighted least squares through samples along both wheel tracks
 * from {@link #LOOK} behind the tail to {@link #LOOK} past the nose, weights
 * fading over the last {@link #FADE} of the window. A staircase is then one
 * steady angle, not a nod per riser. Walking outward along a track, a rise
 * between neighbours of more than the climb is a wall face: that sample and
 * everything beyond it are left out, so the fit flattens toward a wall.
 * Pitch and roll are critically damped springs toward the fit (a first-order
 * filter changes the rate abruptly at every small target change, which a
 * first-person view shows as a 20 Hz stutter). Height follows the fitted
 * plane directly -- a spring cannot track a ramp -- bounded so no wheel sits
 * more than {@link #MAX_SINK} under its ground or the body floats more than
 * {@link #MAX_FLOAT} over the plane. Then the descending end is checked: the
 * pitch is cut back until the body's underside clears the ground there. Only
 * the descending end: the rising end sits under the next step legitimately.
 *
 * <p>Frames: {@code x} is the body's own axis toward the mesh's +X (its
 * left), {@code z} forward, heights relative to the body's true y. The pose
 * is drawn as {@code rotX(pitch) . rotZ(roll)} then {@code + lift}: a
 * positive pitch lowers the nose, a positive roll raises +x.
 */
public final class Terrain {
    private Terrain() {}

    /** The ground: the top of the highest collision in the column at {@code (x, z)} between {@code lo} and {@code hi} (heights relative to the body's y), or {@code Double.NEGATIVE_INFINITY} for none. */
    @FunctionalInterface
    public interface Columns {
        double top(double x, double z, double lo, double hi);
    }

    /** Where the body is and which way it faces: the origin and the yaw in radians (0 is +z, growing clockwise from above, the game's). */
    public record Frame(double x, double z, double yaw) {
        /** effects: returns the world point of body-frame ({@code bx}, {@code bz}) */
        public double worldX(double bx, double bz) {
            return x + bx * Math.cos(yaw) - bz * Math.sin(yaw);
        }

        public double worldZ(double bx, double bz) {
            return z + bz * Math.cos(yaw) + bx * Math.sin(yaw);
        }
    }

    /** What the sensing needs to know of the vehicle: wheels as (x, z) pairs in the body's frame, blocks. */
    public record Shape(double wheelRadius, double climb, double bodyLength, double track, double[] wheelX, double[] wheelZ) {
        /**
         * effects: returns how far under the body's y a terrain sample may look: as far as the fit's
         * window is long, so on a steep descent the samples behind a long body read the real ground
         * rather than bottoming out at BELOW -- a floor that shifted with every step-up and rippled
         * the fitted height (nfx's open item)
         */
        public double sampleBelow() {
            return Math.max(BELOW, bodyLength / 2.0 + LOOK + 1.0);
        }

        public Shape {
            if (wheelX.length != wheelZ.length || wheelX.length == 0) {
                throw new IllegalArgumentException("wheels come in (x, z) pairs, at least one");
            }
            if (!(wheelRadius > 0) || !(climb >= 0) || !(bodyLength > 0) || !(track > 0)) {
                throw new IllegalArgumentException("a shape has a positive radius, length and track and a climb of at least 0");
            }
            wheelX = wheelX.clone();
            wheelZ = wheelZ.clone();
        }
    }

    /**
     * The body's eased pose and its springs' velocities: what carries from
     * one tick to the next. {@code height} is absolute (the true y plus the
     * lift), which is continuous through a physics step-up.
     */
    public record Pose(double pitch, double roll, double height, double pitchV, double rollV) {
        public Pose {
            if (!Double.isFinite(pitch) || !Double.isFinite(roll) || !Double.isFinite(height) || !Double.isFinite(pitchV) || !Double.isFinite(rollV)) {
                throw new IllegalArgumentException("a pose must be finite");
            }
        }

        /** effects: returns a pose at rest, level, with the body at {@code height} */
        public static Pose level(double height) {
            return new Pose(0.0, 0.0, height, 0.0, 0.0);
        }

        /** effects: returns the suspension this pose draws with the body's true y at {@code y} */
        public Suspension suspension(double y) {
            return new Suspension(height - y, pitch, roll);
        }
    }

    /** How far under the body's y the sensing looks, and how far over the climb a terrain sample may look. */
    public static final double BELOW = 2.5;
    public static final double ABOVE = 2.5;
    /** The body's underside sits this far over its wheels' bottoms at the nose and tail. */
    public static final double CLEARANCE = 0.3;
    /** The steepest pose drawn: a 1:1 staircase reads as 45 degrees and must not be flattened. */
    public static final double TILT_LIMIT = Math.toRadians(50.0);
    /** The springs' natural frequency, per tick: a tilt settles in about 4 / TILT_W ticks. */
    public static final double TILT_W = 0.22;
    /** The body may sit this far under where its wheels would rest, and float this far over the fitted plane. */
    public static final double MAX_SINK = 1.0;
    public static final double MAX_FLOAT = 0.8;
    /** The fit's window past the nose and tail, its sample spacing, and the length of its fade, blocks. */
    public static final double LOOK = 1.0;
    public static final double SPACING = 0.25;
    public static final double FADE = 1.5;
    /** Roll settles to this share of the geometric roll: a short track makes a wheel up a step a flick. */
    public static final double ROLL_SCALE = 0.7;

    // --- sensing -----------------------------------------------------------

    /**
     * effects: returns the height a wheel centred at world ({@code x}, {@code z}) rests at, relative
     * to the body's y, as a disc of {@code radius} would: the highest of its own column and the
     * rim-lowered columns along four rays of {@code frame}'s axes. With {@code wallRule}, a column
     * solid higher than {@code base + climb} -- higher than the wheel could step onto from where it
     * is drawn -- is a wall the wheel leans on and reads as its own floor; without, a terrain sample
     * reads the true surface up to {@code climb + ABOVE}, walls being the fit's business.
     */
    public static double wheelGround(Columns ground, Frame frame, Shape s, double x, double z, boolean wallRule, double base) {
        double fx = -Math.sin(frame.yaw()), fz = Math.cos(frame.yaw());
        double sx = Math.cos(frame.yaw()), sz = Math.sin(frame.yaw());
        double below = wallRule ? BELOW : s.sampleBelow();
        double best = column(ground, x, z, s.climb(), wallRule, base, below);
        best = Math.max(best, alongRay(ground, x, z, fx, fz, s, wallRule, base, below));
        best = Math.max(best, alongRay(ground, x, z, -fx, -fz, s, wallRule, base, below));
        best = Math.max(best, alongRay(ground, x, z, sx, sz, s, wallRule, base, below));
        best = Math.max(best, alongRay(ground, x, z, -sx, -sz, s, wallRule, base, below));
        return best;
    }

    /** effects: returns the terrain surface under world ({@code x}, {@code z}) as a disc reads it, no wall rule */
    public static double sample(Columns ground, Frame frame, Shape s, double x, double z) {
        return wheelGround(ground, frame, s, x, z, false, 0.0);
    }

    /**
     * Disc contact along one ray: the ground is constant per block column, so the rim can only
     * touch a new surface where the ray crosses a block boundary; each boundary within a radius is
     * found exactly (distance {@code t}) and the column beyond it lowered by the rim height
     * {@code r - sqrt(r^2 - t^2)}.
     */
    private static double alongRay(Columns ground, double x, double z, double dx, double dz, Shape s, boolean wallRule, double base, double below) {
        double r = s.wheelRadius();
        double best = -below, t = 0.0;
        for (int guard = 0; guard < 6; guard++) {
            double px = x + dx * t, pz = z + dz * t;
            double tx = dx > 1e-9 ? (Math.floor(px) + 1.0 - px) / dx : dx < -1e-9 ? (px - Math.floor(px)) / -dx : Double.POSITIVE_INFINITY;
            double tz = dz > 1e-9 ? (Math.floor(pz) + 1.0 - pz) / dz : dz < -1e-9 ? (pz - Math.floor(pz)) / -dz : Double.POSITIVE_INFINITY;
            t += Math.max(1e-6, Math.min(tx, tz));
            if (t >= r) {
                break;
            }
            double d = t + 1.0E-4;
            double h = column(ground, x + dx * d, z + dz * d, s.climb(), wallRule, base, below) - (r - Math.sqrt(r * r - t * t));
            best = Math.max(best, h);
        }
        return best;
    }

    /** effects: returns one column's surface with the wall rule and the reach applied; -below for nothing within reach */
    static double column(Columns ground, double x, double z, double climb, boolean wallRule, double base, double below) {
        double reach = wallRule ? base + climb : climb + ABOVE;
        double top = ground.top(x, z, -below, reach);
        if (top == Double.NEGATIVE_INFINITY) {
            return -below;
        }
        if (wallRule && top > reach + 1.0E-6) {
            return Math.min(base, 0.0);
        }
        return Math.min(top, reach);
    }

    // --- the plane ---------------------------------------------------------

    /** A plane {@code h = a z + b x + c} through the terrain, in the body's frame. */
    public record Plane(double a, double b, double c) {
        public double at(double x, double z) {
            return a * z + b * x + c;
        }
    }

    /** Sums for a weighted least-squares plane. */
    private static final class Fit {
        double sw, sz, sx, sh, szz, sxx, szh, sxh;

        void add(double w, double x, double z, double h) {
            sw += w; sz += w * z; sx += w * x; sh += w * h;
            szz += w * z * z; sxx += w * x * x; szh += w * z * h; sxh += w * x * h;
        }

        Plane plane() {
            if (sw <= 0) {
                return new Plane(0.0, 0.0, 0.0);
            }
            double mz = sz / sw, mx = sx / sw, mh = sh / sw;
            double varZ = szz / sw - mz * mz, varX = sxx / sw - mx * mx;
            double a = varZ > 1.0E-9 ? (szh / sw - mz * mh) / varZ : 0.0;
            double b = varX > 1.0E-9 ? (sxh / sw - mx * mh) / varX : 0.0;
            return new Plane(a, b, mh - a * mz - b * mx);
        }
    }

    /**
     * effects: fits the plane through samples along both wheel tracks, walking from {@code fromZ}
     * (the body-frame z the walks start at) {@code back} blocks rearward and {@code front} blocks
     * forward, spaced {@link #SPACING}, weights fading over {@link #FADE} at each end; a walk ends at
     * a wall face (a rise of more than the climb between neighbours)
     */
    public static Plane fit(Columns ground, Frame frame, Shape s, double fromZ, double back, double front) {
        Fit f = new Fit();
        double trackHalf = s.track() / 2.0;
        for (double x : new double[] {-trackHalf, trackHalf}) {
            for (int dir = -1; dir <= 1; dir += 2) {
                double prev = Double.NaN, reach = dir < 0 ? back : front;
                for (double d = 0.0; d <= reach + 1.0E-9; d += SPACING) {
                    if (dir < 0 && d == 0.0) {
                        continue;   // the centre sample belongs to the forward walk
                    }
                    double z = fromZ + dir * d;
                    double h = sample(ground, frame, s, frame.worldX(x, z), frame.worldZ(x, z));
                    if (!Double.isNaN(prev) && h - prev > s.climb() + 0.2) {
                        break;
                    }
                    prev = h;
                    f.add(Math.min(1.0, (reach - d) / FADE + 0.05), x, z, h);
                }
            }
        }
        return f.plane();
    }

    // --- the body's pose ---------------------------------------------------

    /**
     * effects: returns the pose one tick on from {@code last} for a body at true height {@code y}
     * standing in {@code frame}: the wheels probed from where the last pose drew them, the plane
     * fitted over the footprint, the springs stepped toward it, the height put on the plane within
     * the sink and float bounds, and the descending end kept clear of its ground
     */
    public static Pose step(Columns ground, Frame frame, Shape s, double y, Pose last) {
        int n = s.wheelX().length;
        double[] wx = s.wheelX(), wz = s.wheelZ(), wh = new double[n];
        double lastLift = last.height() - y, sr0 = Math.sin(last.roll()), cp0 = Math.cos(last.pitch()), sp0 = Math.sin(last.pitch());
        for (int i = 0; i < n; i++) {
            double base = lastLift + wx[i] * sr0 * cp0 - wz[i] * sp0;
            wh[i] = wheelGround(ground, frame, s, frame.worldX(wx[i], wz[i]), frame.worldZ(wx[i], wz[i]), true, base);
        }
        double half = s.bodyLength() / 2.0 + LOOK;
        Plane plane = fit(ground, frame, s, 0.0, half, half);
        double pitchTarget = -Math.atan(plane.a()), rollTarget = Math.atan(plane.b());
        double pitchV = last.pitchV() + TILT_W * TILT_W * (clamp(pitchTarget) - last.pitch()) - 2.0 * TILT_W * last.pitchV();
        double rollV = last.rollV() + TILT_W * TILT_W * (clamp(rollTarget) * ROLL_SCALE - last.roll()) - 2.0 * TILT_W * last.rollV();
        double pitch = clamp(last.pitch() + pitchV), roll = clamp(last.roll() + rollV);
        double planeH = y + plane.c();
        double wheelsH = y + restLift(pitch, roll, wx, wz, wh);
        double h = Math.max(wheelsH - MAX_SINK, Math.min(planeH + MAX_FLOAT, planeH));
        double lift = h - y;
        if (pitch != 0.0) {
            double bodyHalf = s.bodyLength() / 2.0;
            double endZ = pitch > 0.0 ? bodyHalf : -bodyHalf;
            double endGround = wheelGround(ground, frame, s, frame.worldX(0.0, endZ), frame.worldZ(0.0, endZ), true, 0.0);
            if (!clears(pitch, lift, endZ, endGround)) {
                if (!clears(0.0, lift, endZ, endGround)) {
                    pitch = 0.0;
                } else {
                    double lo = 0.0, hi = pitch;
                    for (int i = 0; i < 12; i++) {
                        double mid = (lo + hi) / 2.0;
                        if (clears(mid, lift, endZ, endGround)) {
                            lo = mid;
                        } else {
                            hi = mid;
                        }
                    }
                    pitch = lo;
                }
            }
        }
        return new Pose(pitch, roll, h, pitchV, rollV);
    }

    /** effects: returns the lift that rests the body on its wheels: none below the ground under it */
    static double restLift(double pitch, double roll, double[] wx, double[] wz, double[] wh) {
        double sr = Math.sin(roll), cp = Math.cos(pitch), sp = Math.sin(pitch), lift = -BELOW;
        for (int i = 0; i < wx.length; i++) {
            lift = Math.max(lift, wh[i] - wx[i] * sr * cp + wz[i] * sp);
        }
        return lift;
    }

    /** effects: returns whether the body's underside at body-frame z clears {@code ground} under the pose */
    static boolean clears(double pitch, double lift, double z, double ground) {
        return CLEARANCE * Math.cos(pitch) - z * Math.sin(pitch) + lift >= ground - 1.0E-3;
    }

    static double clamp(double a) {
        return Math.max(-TILT_LIMIT, Math.min(TILT_LIMIT, a));
    }

    // --- a towed body's pose -----------------------------------------------

    /** A towed body's roll spring: what carries between ticks. */
    public record TowedPose(double roll, double rollV) {
        public static final TowedPose LEVEL = new TowedPose(0.0, 0.0);
    }

    /** The ball the coupler hangs from, in the world: its height relative to the towed body's y. */
    public record Ball(double heightOverY, double restHeight) {}

    /** How far ahead of the axle a towed body's fit looks. */
    public static final double AHEAD = 1.0;

    /**
     * effects: returns the towed body's suspension and roll spring one tick on: a lever on its
     * axle with its coupler held at {@code ball} -- the axle on a line fitted along its own wheel
     * tracks from {@link #LOOK} behind the tail to {@link #AHEAD} past the axle, bounded by where
     * the wheels rest; pitch solved exactly from both constraints (the gentler of the two
     * solutions), the drawbar counted level at rest ({@code ball.restHeight()} is the ball's own
     * rest height: honest coupler geometry would rake a trailer nose-up on flat ground); roll
     * through the same spring as a driven body. No spring on pitch or height, which the coupler
     * fixes: easing them would show as the drawbar leaving the ball.
     *
     * @param axleZ    the axle's body-frame z (negative: behind the origin)
     * @param couplerZ the coupler's body-frame z
     */
    public static Towed towed(Columns ground, Frame frame, Shape s, double y, double axleZ, double couplerZ, Ball ball, TowedPose last) {
        double back = s.bodyLength() / 2.0 + axleZ + LOOK;
        Plane plane = fit(ground, frame, s, axleZ, back, AHEAD);
        double axleH = plane.at(0.0, axleZ);
        double rest = -BELOW;
        for (int i = 0; i < s.wheelX().length; i++) {
            rest = Math.max(rest, wheelGround(ground, frame, s, frame.worldX(s.wheelX()[i], s.wheelZ()[i]), frame.worldZ(s.wheelX()[i], s.wheelZ()[i]), true, 0.0));
        }
        axleH = Math.max(rest - MAX_SINK, Math.min(rest + MAX_FLOAT, axleH));
        double rollV = last.rollV() + TILT_W * TILT_W * (clamp(Math.atan(plane.b())) * ROLL_SCALE - last.roll()) - 2.0 * TILT_W * last.rollV();
        double roll = clamp(last.roll() + rollV);
        // wheel bottoms at y' = -axleZ sin p + lift = axleH; coupler at cy cos p - cz sin p + lift = ball
        //   => cy cos p + (axleZ - cz) sin p = ball - axleH, i.e. R sin(p + phi) = d
        double a = ball.restHeight() * Math.cos(roll), b = axleZ - couplerZ, d = ball.heightOverY() - axleH;
        double r = Math.hypot(a, b), phi = Math.atan2(a, b);
        double theta = Math.asin(Math.max(-1.0, Math.min(1.0, r == 0.0 ? 0.0 : d / r)));
        double p1 = Drive.wrap(theta - phi), p2 = Drive.wrap(Math.PI - theta - phi);
        double pitch = clamp(Math.abs(p1) <= Math.abs(p2) ? p1 : p2);
        double lift = axleH + axleZ * Math.sin(pitch);
        return new Towed(new Suspension(lift, pitch, roll), new TowedPose(roll, rollV));
    }

    /** A towed body's result: what to draw, and what to carry to the next tick. */
    public record Towed(Suspension suspension, TowedPose next) {}
}
