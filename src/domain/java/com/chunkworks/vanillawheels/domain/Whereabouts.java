/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Chunkworks
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
 * Where a keyring's vehicle is, as the ring's row says it (D-0041): deployed somewhere, lying on
 * the ground as its packed item, or already collected into someone's hands; how far from the
 * player, as the recovery index last knew it, and whether in the player's own dimension. Pure,
 * so the row's words and its quoted share of a tank are decided in one place and tested there.
 */
public record Whereabouts(Kind kind, boolean sameDimension, double distance) {
    /** What the vehicle is now: an entity in the world; a packed item on the ground; a packed item picked up. */
    public enum Kind { DEPLOYED, ON_THE_GROUND, COLLECTED }

    public Whereabouts {
        if (kind == null || !Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("a whereabouts has a kind and a distance");
        }
    }

    /** effects: returns whether a recall can be asked for: not for one already collected */
    public boolean recallable() {
        return kind != Kind.COLLECTED;
    }

    /** effects: returns the share of a full tank a recall from here costs ({@link RecallCost#share}) */
    public double share() {
        return RecallCost.share(distance, sameDimension);
    }

    /**
     * effects: returns the distance as the row quotes it, blocks, to two significant figures (the
     * index knows a vehicle to its chunk, so a finer number would be a lie): 7 is 7, 87 is 87, 1234
     * is 1200, 4473 is 4500
     */
    public long about() {
        if (distance < 100) {
            return Math.round(distance);
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(distance)) - 1);
        return Math.round(Math.round(distance / magnitude) * magnitude);
    }
}
