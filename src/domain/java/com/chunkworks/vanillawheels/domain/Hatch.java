/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

/**
 * The Floor Hatch's rules (D-0037): a housing in a floor slides a plate over the opening in front of
 * it, and draws it back under the floor, so the opening is a real hole (air) while open and a solid
 * floor while shut. The ask: "a floor of some kind that closes when the elevator passes underneath, and
 * opens right before the elevator comes back up", and "a guard so nobody in the house accidentally
 * falls down a chasm that suddenly appeared".
 *
 * <ul>
 *   <li><b>When it opens:</b> while it is powered, while something it watches is rising toward it from
 *   below (a lift, a car, anything not alive), and while something fills the opening (the car at
 *   home), the hatch is wanted open. Otherwise it is wanted shut.</li>
 *   <li><b>Never under a foot:</b> it does not start opening while someone stands on the plate, unless
 *   what is rising is within {@link #CLOSE} blocks under it: a car that reaches a shut plate takes the
 *   plate with it, so the plate is always gone before one arrives, and the one standing drops a step
 *   onto the car.</li>
 *   <li><b>Never onto anything:</b> it shuts only over an empty opening with nothing in the plate's
 *   layer.</li>
 *   <li><b>The guard:</b> a rail rises on the housing's open edge before the plate draws back, stays up
 *   while the hole is open, and folds away when the plate is shut or the opening is filled.</li>
 * </ul>
 *
 * <p>AF: pure functions of depths, travels (eighth-blocks the plate has drawn back), guard progress
 * (ticks of rising, 0 down) and what the hatch sees. RI: none (no state).
 */
public final class Hatch {
    private Hatch() {}

    public static final int MIN_DEPTH = 1;
    public static final int MAX_DEPTH = 16;
    /** A new hatch's opening, in blocks from its housing. */
    public static final int DEFAULT_DEPTH = 3;
    /** Eighth-blocks the plate moves a tick: a three-deep plate slides in 0.3 s. */
    public static final int STEP = 4;
    /** Ticks for the guard to rise or fold. */
    public static final int GUARD_TICKS = 4;
    /** Blocks below the plate within which something rising opens it even under a foot. */
    public static final double CLOSE = 1.5;
    /** Blocks below the plate the hatch watches for something rising. */
    public static final int RANGE = 16;
    /** Ticks between the hatch's looks below it. */
    public static final int SCAN_PERIOD = 2;
    /** Looks without seeing what it was waiting for before it stops waiting (ten seconds). */
    public static final int FORGET = 100;

    /** requires: MIN_DEPTH <= depth <= MAX_DEPTH; effects: returns the plate's travel from shut to
     * open, in eighth-blocks; throws: depth out of range */
    public static int travel(int depth) {
        if (depth < MIN_DEPTH || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("a hatch is " + MIN_DEPTH + ".." + MAX_DEPTH + " deep: " + depth);
        }
        return depth * Shutter.UNITS;
    }

    /** requires: depth in range, 0 <= travel <= travel(depth); effects: returns the travel a tick on,
     * a step toward open or shut, never past either; throws: out of range */
    public static int advance(int depth, int travel, boolean opening) {
        int limit = travel(depth);
        if (travel < 0 || travel > limit) {
            throw new IllegalArgumentException("travel outside the hatch: " + travel);
        }
        return opening ? Math.min(limit, travel + STEP) : Math.max(0, travel - STEP);
    }

    /** requires: depth in range; effects: returns the depth a click makes it, one more (one less when
     * {@code down}), going round from MAX_DEPTH to MIN_DEPTH and back; throws: depth out of range */
    public static int step(int depth, boolean down) {
        travel(depth);
        if (down) {
            return depth == MIN_DEPTH ? MAX_DEPTH : depth - 1;
        }
        return depth == MAX_DEPTH ? MIN_DEPTH : depth + 1;
    }

    /** effects: returns whether the hatch is wanted open */
    public static boolean wanted(boolean powered, boolean expecting, boolean occupied) {
        return powered || expecting || occupied;
    }

    /** requires: gap >= 0 or infinite (nothing rising); effects: returns whether a shut plate may
     * start to open: nobody on it, or what rises within CLOSE of it */
    public static boolean mayOpen(boolean someoneOn, double gap) {
        return !someoneOn || gap <= CLOSE;
    }

    /** effects: returns whether the plate may move toward shut: the opening empty and nothing in the plate's layer */
    public static boolean mayShut(boolean openingEmpty, boolean layerClear) {
        return openingEmpty && layerClear;
    }

    /** effects: returns whether the guard is wanted up: the hole open or opening, and not filled */
    public static boolean guardWanted(int travel, boolean wanted, boolean occupied) {
        return (travel > 0 || wanted) && !occupied;
    }

    /** requires: 0 <= progress <= GUARD_TICKS; effects: returns the guard's progress a tick on;
     * throws: progress out of range */
    public static int guard(int progress, boolean up) {
        if (progress < 0 || progress > GUARD_TICKS) {
            throw new IllegalArgumentException("guard progress 0.." + GUARD_TICKS + ": " + progress);
        }
        return up ? Math.min(GUARD_TICKS, progress + 1) : Math.max(0, progress - 1);
    }

    /** What the hatch makes of the highest top it sees below it, against the one it saw a look before. */
    public enum Motion { NONE, RISING, STILL, FALLING }

    /** requires: tops finite or NaN (nothing seen); effects: returns what a top seen now (NaN: nothing)
     * means against the top seen a look before (NaN: nothing): something first seen, or no higher or
     * lower by a thousandth, stands still */
    public static Motion motion(double before, double now) {
        if (Double.isNaN(now)) {
            return Motion.NONE;
        }
        if (Double.isNaN(before) || Math.abs(now - before) < 1e-3) {
            return Motion.STILL;
        }
        return now > before ? Motion.RISING : Motion.FALLING;
    }

    /** requires: 0 <= unseen <= FORGET; effects: returns the countdown of looks the hatch keeps
     * waiting after this look: FORGET on seeing something rise, 0 on seeing it fall or on the opening
     * filling, one less while nothing is seen, unchanged while what it saw stands still (held) */
    public static int expect(int waiting, Motion motion, boolean occupied) {
        if (waiting < 0 || waiting > FORGET) {
            throw new IllegalArgumentException("waiting 0.." + FORGET + ": " + waiting);
        }
        if (occupied) {
            return 0;
        }
        return switch (motion) {
            case RISING -> FORGET;
            case FALLING -> 0;
            case NONE -> Math.max(0, waiting - 1);
            case STILL -> waiting;
        };
    }
}
