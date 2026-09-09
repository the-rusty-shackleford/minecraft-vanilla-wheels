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
 * What the driver is doing this tick, and what the ground allows.
 *
 * <p>RI: throttle and steer are -1, 0 or 1.
 * AF: throttle 1 is the accelerator, -1 the brake-and-reverse; steer 1 is
 *     right, -1 left; drift is the drift key held; onGround says the
 *     wheels touch; fuel says the tank is not empty.
 */
public record Input(int throttle, int steer, boolean drift, boolean onGround, boolean fuel) {
    public Input {
        if (throttle < -1 || throttle > 1 || steer < -1 || steer > 1) {
            throw new IllegalArgumentException("throttle and steer are -1, 0 or 1: " + throttle + ", " + steer);
        }
    }

    /** Nobody at the wheel. */
    public static final Input NONE = new Input(0, 0, false, true, true);

    /** effects: returns hands-off input with the given ground contact and fuel */
    public static Input coasting(boolean onGround, boolean fuel) {
        return new Input(0, 0, false, onGround, fuel);
    }
}
