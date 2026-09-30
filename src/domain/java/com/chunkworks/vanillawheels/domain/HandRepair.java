/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

/**
 * A repair by hand at the vehicle (D-0025, after Immersive Aircraft): each click restores STEP of
 * MAX and tires the player. The tiring scales with the vehicle's repair job, whose size its profile
 * already states as the lift's full cost in material: a full rebuild, CLICKS clicks, costs that many
 * points of exhaustion, a food point for every four. A twenty-ingot Trailblazer pays Immersive
 * Aircraft's five food points; a twelve-ingot trailer, three. Static rules only.
 */
public final class HandRepair {
    /** What one click restores: 2.5%, Immersive Aircraft's step. */
    public static final int STEP = Condition.MAX / 40;
    /** Clicks from a wreck to MAX. */
    public static final int CLICKS = Condition.MAX / STEP;

    private HandRepair() {}

    /** requires: positive fullCost; effects: the exhaustion one click costs, fullCost / CLICKS; throws: IllegalArgumentException for invalid cost. */
    public static float exhaustion(int fullCost) {
        if (fullCost < 1) throw new IllegalArgumentException("Invalid repair cost");
        return (float) fullCost / CLICKS;
    }

    /** requires: a condition below MAX; effects: the condition after one click, STEP more, at most MAX; throws: IllegalArgumentException at MAX (nothing to repair). */
    public static Condition clicked(Condition condition) {
        if (condition.remaining() >= Condition.MAX) throw new IllegalArgumentException("Nothing to repair");
        return new Condition(Math.min(Condition.MAX, condition.remaining() + STEP));
    }
}
