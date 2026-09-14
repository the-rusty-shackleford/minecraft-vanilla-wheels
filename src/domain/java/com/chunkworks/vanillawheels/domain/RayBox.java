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

/** A ray against an axis-aligned box: where a click's line meets a chest. */
public final class RayBox {
    private RayBox() {}

    /**
     * requires: {@code d} is not the zero vector
     * effects: returns the distance along the ray from {@code o} in direction {@code d} (in
     * units of {@code d}'s length) at which it enters the box {@code [lo, hi]}, 0 if {@code o}
     * is inside it, or -1 if the ray never meets it ahead of {@code o}
     */
    public static double enter(Vec o, Vec d, Vec lo, Vec hi) {
        double tIn = Double.NEGATIVE_INFINITY, tOut = Double.POSITIVE_INFINITY;
        double[] os = {o.x(), o.y(), o.z()}, ds = {d.x(), d.y(), d.z()}, los = {lo.x(), lo.y(), lo.z()}, his = {hi.x(), hi.y(), hi.z()};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(ds[a]) < 1.0e-9) {
                if (os[a] < los[a] || os[a] > his[a]) {
                    return -1.0;
                }
                continue;
            }
            double t1 = (los[a] - os[a]) / ds[a], t2 = (his[a] - os[a]) / ds[a];
            tIn = Math.max(tIn, Math.min(t1, t2));
            tOut = Math.min(tOut, Math.max(t1, t2));
        }
        if (tOut < tIn || tOut < 0.0) {
            return -1.0;
        }
        return Math.max(tIn, 0.0);
    }
}
