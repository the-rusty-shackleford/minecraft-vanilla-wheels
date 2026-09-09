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

/** How far a wheel has turned for the distance it rolled. */
public final class WheelSpin {
    private WheelSpin() {}

    /**
     * effects: returns the wheel's angle, radians in (-pi, pi], after
     * rolling {@code travelled} blocks on a radius of {@code radius}
     * blocks; forward travel is a positive turn about the axle<br>
     * throws: {@link IllegalArgumentException} if radius <= 0
     */
    public static double radians(double travelled, double radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("a wheel has a positive radius");
        }
        return Drive.wrap(travelled / radius);
    }
}
