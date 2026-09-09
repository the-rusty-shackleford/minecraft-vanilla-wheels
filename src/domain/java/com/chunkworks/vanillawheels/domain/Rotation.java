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
 * A turn of {@code radians} about {@code axis} through {@code pivot}: how a
 * needle, a wheel or a door moves. The renderer applies it as matrices;
 * {@link #apply} is the same turn on a point, for tests and for anything
 * that needs a moved point.
 *
 * <p>RI: |axis| = 1; radians finite.
 */
public record Rotation(Vec pivot, Vec axis, double radians) {
    public Rotation {
        if (Math.abs(axis.length() - 1.0) > 1e-6) {
            throw new IllegalArgumentException("the axis is unit: " + axis);
        }
        if (!Double.isFinite(radians)) {
            throw new IllegalArgumentException("radians must be finite");
        }
    }

    /** effects: returns {@code p} turned (Rodrigues' formula) */
    public Vec apply(Vec p) {
        Vec v = p.minus(pivot);
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        Vec turned = v.times(c).plus(axis.cross(v).times(s)).plus(axis.times(axis.dot(v) * (1 - c)));
        return turned.plus(pivot);
    }

    /** effects: returns this rotation under a reflection: pivot and axis mapped, the angle reversed */
    public Rotation mirrored(Transform t) {
        return new Rotation(t.apply(pivot), t.applyDirection(axis).normalized(), t.isReflection() ? -radians : radians);
    }
}
