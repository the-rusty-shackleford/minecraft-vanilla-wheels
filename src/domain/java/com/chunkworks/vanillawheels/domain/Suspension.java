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
 * How the body is drawn against its true position: lifted, pitched and
 * rolled. The numbers come from {@link Terrain}, which poses the body on
 * the ground under it; this is only the value the renderer and the riders
 * read.
 *
 * <p>RI: all finite; |pitch| and |roll| <= pi/2.
 * AF: AF(lift, pitch, roll) = "draw the body {@code lift} blocks above
 *     where it is, pitched {@code pitch} (positive nose down, the game's
 *     sense) and rolled {@code roll} (positive raising the mesh's +x, its
 *     left, so leaning right)".
 *
 * @param lift  the drawn body's height above the true position, blocks
 * @param pitch radians, positive nose down
 * @param roll  radians, positive leaning right
 */
public record Suspension(double lift, double pitch, double roll) {
    public static final Suspension LEVEL = new Suspension(0.0, 0.0, 0.0);

    public Suspension {
        if (!Double.isFinite(lift) || !Double.isFinite(pitch) || !Double.isFinite(roll)) {
            throw new IllegalArgumentException("a suspension state must be finite");
        }
        if (Math.abs(pitch) > Math.PI / 2 || Math.abs(roll) > Math.PI / 2) {
            throw new IllegalArgumentException("pitch and roll are within a right angle");
        }
    }

    /** effects: returns whether the body is within a hair of level and of the true position */
    public boolean isSettled() {
        return Math.abs(lift) < 0.01 && Math.abs(pitch) < 0.005 && Math.abs(roll) < 0.005;
    }
}
