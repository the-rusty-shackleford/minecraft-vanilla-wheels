/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

/**
 * What a blow costs a vehicle's condition (D-0034): a mob's, an arrow's, a bullet's, a blast's, a
 * fire's -- every blow but a player's own punch, which is a knock (D-0025). A point of damage costs
 * {@link #PER_POINT} condition at durability 1, the vanilla boat's five points from whole to a wreck;
 * a profile's durability divides it.
 */
public final class Blows {
    private Blows() {}

    /** Condition a point of damage costs at durability 1: five points wreck a whole vehicle, as a boat. */
    public static final double PER_POINT = 2000.0;

    /**
     * requires: {@code amount} finite and positive; {@code durability} finite and positive.
     * effects: the condition a blow of {@code amount} costs a vehicle of {@code durability}:
     *     amount / durability * PER_POINT, rounded up so that no blow is free, never more than
     *     {@link Condition#MAX}.
     * throws: IllegalArgumentException otherwise.
     */
    public static int wear(double amount, double durability) {
        if (!(amount > 0) || Double.isInfinite(amount)) throw new IllegalArgumentException("A blow's amount is positive: " + amount);
        if (!(durability > 0) || Double.isInfinite(durability)) throw new IllegalArgumentException("A durability is positive: " + durability);
        return (int) Math.min(Condition.MAX, Math.ceil(amount / durability * PER_POINT));
    }

    /**
     * The blasts a vehicle has taken this tick: an explosion reaches the body and each of its hit
     * boxes, each a blow of its own amount by its own distance, in one tick. The vehicle takes a
     * blast once, at the most any of its pieces took, as a living thing of many parts (the dragon)
     * does under its hurt cooldown. A second blast on the same tick charges only its excess, as it
     * would a living thing.
     * AF: by game tick {@code tick} the blasts have charged {@code taken} condition; none before.
     * RI: taken >= 0. Immutable.
     */
    public record Area(long tick, int taken) {
        /** No blast yet. */
        public static final Area NONE = new Area(Long.MIN_VALUE, 0);

        /** requires: none; effects: constructs the record; throws: IllegalArgumentException for a negative charge. */
        public Area {
            if (taken < 0) throw new IllegalArgumentException("Negative wear " + taken);
        }

        /**
         * requires: {@code wear} >= 0.
         * effects: the condition a blast's piece costing {@code wear} charges at game tick {@code tick}:
         *     all of it on a tick of its own, else only what it exceeds this tick's largest.
         * throws: IllegalArgumentException for negative wear.
         */
        public int due(long tick, int wear) {
            if (wear < 0) throw new IllegalArgumentException("Negative wear " + wear);
            return tick == this.tick ? Math.max(0, wear - taken) : wear;
        }

        /**
         * requires: {@code wear} >= 0.
         * effects: the blasts after a piece costing {@code wear} at game tick {@code tick}: this
         *     tick's largest.
         * throws: IllegalArgumentException for negative wear.
         */
        public Area after(long tick, int wear) {
            if (wear < 0) throw new IllegalArgumentException("Negative wear " + wear);
            return new Area(tick, tick == this.tick ? Math.max(taken, wear) : wear);
        }
    }
}
