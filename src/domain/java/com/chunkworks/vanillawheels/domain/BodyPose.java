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
 * Where the body points: yaw about the vertical, then pitch (nose down
 * positive, the game's sense), then roll (leaning right positive), all in
 * radians. The renderer interpolates between two of these across a frame.
 */
public record BodyPose(double yaw, double pitch, double roll) {
    public BodyPose {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || !Double.isFinite(roll)) {
            throw new IllegalArgumentException("a pose must be finite");
        }
    }

    public static final BodyPose ZERO = new BodyPose(0, 0, 0);

    /** effects: returns the pose {@code t} of the way from {@code a} to {@code b}, the yaw by the short way round */
    public static BodyPose lerp(BodyPose a, BodyPose b, double t) {
        return new BodyPose(a.yaw + Drive.wrap(b.yaw - a.yaw) * t, a.pitch + (b.pitch - a.pitch) * t, a.roll + (b.roll - a.roll) * t);
    }
}
