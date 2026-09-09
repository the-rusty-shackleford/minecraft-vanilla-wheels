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
 * What a vehicle is built from on the lift: its chassis, one wheel per
 * wheel position, and an engine if it has one. Derived from the profile,
 * so no vehicle needs a recipe and none can have two.
 *
 * <p>RI: wheels >= 1.
 * AF: the parts list "a chassis, {@code wheels} wheels, an engine iff
 *     {@code engine}".
 */
public record Assembly(int wheels, boolean engine) {
    public Assembly {
        if (wheels < 1) {
            throw new IllegalArgumentException("a vehicle has at least one wheel: " + wheels);
        }
    }

    /**
     * effects: returns whether the parts on the lift build this: a chassis,
     * exactly {@code wheelsOffered} wheels, and an engine exactly when one
     * is called for -- extras are refused, as a shapeless recipe refuses them
     */
    public boolean accepts(boolean chassis, int wheelsOffered, boolean engineOffered) {
        return chassis && wheelsOffered == wheels && engineOffered == engine;
    }
}
