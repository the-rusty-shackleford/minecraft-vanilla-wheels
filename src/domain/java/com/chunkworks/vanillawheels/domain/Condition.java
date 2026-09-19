/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

/**
 * Persistent vehicle condition, in hundredths of one percent.
 * AF: remaining/MAX is the usable fraction; zero is a repairable wreck.
 * RI: 0 <= remaining <= MAX. Immutable; damage never wraps or destroys property.
 */
public record Condition(int remaining) {
    public static final int MAX = 10_000;

    /** requires: none; effects: constructs a condition; throws: IllegalArgumentException outside 0..MAX. */
    public Condition {
        if (remaining < 0 || remaining > MAX) throw new IllegalArgumentException("Invalid vehicle condition");
    }

    /** requires: nonnegative units; effects: returns condition after damage, bounded at zero; throws: IllegalArgumentException for negative damage. */
    public Condition damaged(int units) {
        if (units < 0) throw new IllegalArgumentException("Negative damage");
        return new Condition(Math.max(0, remaining - units));
    }

    /** requires: positive fullRepairCost; effects: rounds proportional material cost upward; throws: IllegalArgumentException for invalid cost. */
    public int repairCost(int fullRepairCost) {
        if (fullRepairCost < 1) throw new IllegalArgumentException("Invalid repair cost");
        return (int) (((long) (MAX - remaining) * fullRepairCost + MAX - 1) / MAX);
    }

    /** requires: none; effects: reports whether the vehicle can drive; throws: none. */
    public boolean broken() { return remaining == 0; }
}
