/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

/**
 * A recall quote paid from one motor vehicle's tank, including its hitched trailers.
 * AF: fuelCost is the requested fuel; fuelAfter and conditionAfter are the result.
 * RI: nonnegative fuel values and non-null valid condition. Immutable.
 */
public record RecallCost(int fuelCost, int fuelAfter, Condition conditionAfter) {
    /** requires: none; effects: constructs a quote; throws: IllegalArgumentException for invalid fields. */
    public RecallCost {
        if (fuelCost < 0 || fuelAfter < 0 || conditionAfter == null) throw new IllegalArgumentException("Invalid recall quote");
    }

    /**
     * requires: finite nonnegative distance, positive capacity, fuel in 0..capacity, valid condition.
     * effects: charges 5% of capacity at 1000 blocks, proportional to distance squared,
     * bounded to 1..100%; missing fuel costs half that fraction of MAX condition.
     * Creative pays neither fuel nor condition. All fractional costs round upward once.
     * throws: IllegalArgumentException for invalid inputs.
     */
    /**
     * requires: distance finite and not negative
     * effects: returns the share of a full tank a recall over {@code distance} blocks costs,
     * 0.01..1: a twentieth at a thousand blocks, growing with the square; a vehicle in another
     * dimension ({@code sameDimension} false) pays the whole tank. What the keyring quotes a row
     * with before the vehicle is found (D-0041), and what {@link #quote} charges.
     * throws: IllegalArgumentException for an invalid distance
     */
    public static double share(double distance, boolean sameDimension) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid recall distance");
        if (!sameDimension) return 1.0;
        return Math.clamp(0.05 * Math.pow(distance / 1000.0, 2), 0.01, 1.0);
    }

    public static RecallCost quote(double distance, int capacity, int fuel, Condition condition, boolean creative) {
        if (!Double.isFinite(distance) || distance < 0 || capacity < 1 || fuel < 0 || fuel > capacity || condition == null)
            throw new IllegalArgumentException("Invalid recall inputs");
        if (creative) return new RecallCost(0, fuel, condition);
        double fraction = share(distance, true);
        int cost = (int) Math.ceil(capacity * fraction);
        int missing = Math.max(0, cost - fuel);
        int damage = (int) (((long) missing * Condition.MAX + 2L * capacity - 1) / (2L * capacity));
        return new RecallCost(cost, Math.max(0, fuel - cost), condition.damaged(damage));
    }
}
