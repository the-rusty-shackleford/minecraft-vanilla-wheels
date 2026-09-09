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
 * A gauge's needle: it rests at {@code rest} radians about {@code axis}
 * through {@code pivot} and sweeps {@code sweep} more at full scale.
 *
 * <p>RI: |axis| = 1; rest and sweep finite.
 */
public record Dial(Vec pivot, Vec axis, double rest, double sweep) {
    public Dial {
        if (Math.abs(axis.length() - 1.0) > 1e-6) {
            throw new IllegalArgumentException("the axis is unit");
        }
        if (!Double.isFinite(rest) || !Double.isFinite(sweep)) {
            throw new IllegalArgumentException("rest and sweep must be finite");
        }
    }

    /** effects: returns the needle's turn at {@code fraction} of full scale, clamped to 0..1 */
    public Rotation rotation(double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        return new Rotation(pivot, axis, rest + sweep * f);
    }

    /** effects: returns this dial under {@code t}: pivot and axis mapped, the angles reversed if it reflects */
    public Dial transformed(Transform t) {
        double sign = t.isReflection() ? -1 : 1;
        return new Dial(t.apply(pivot), t.applyDirection(axis).normalized(), rest * sign, sweep * sign);
    }
}
