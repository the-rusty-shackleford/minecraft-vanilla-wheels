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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Partitions. The kind: roll, steer, ridden. The axis as given: the canonical way, reversed (roll
 * to the right, steer down: flipped), raked. Nesting: none, one deep (a wheel in a fork), two deep;
 * the cut order then. The state: at rest, travelled, steered right and left, ridden half and all.
 * Refused: a parent not earlier, a roll of no radius, an axis not unit, a ridden share outside
 * [0, 1].
 *
 * <p>In the local frame: +X the vehicle's left, +Y up, +Z forward.
 */
final class LinkageTest {
    private static final Vec AXLE = new Vec(0.0, 0.4, 0.8);
    /** A fork raked back 25 degrees: its axis leans its top rearward. */
    private static final Vec RAKE = new Vec(0.0, Math.cos(Math.toRadians(25)), -Math.sin(Math.toRadians(25)));
    private static final Linkage BIKE = new Linkage(List.of(
            new Linkage.Joint(new Vec(0.0, 1.2, 0.4), RAKE, Linkage.Kind.STEER, 1.0, -1),     // 0: the fork
            new Linkage.Joint(AXLE, Vec.X, Linkage.Kind.ROLL, 0.4, 0),                          // 1: the front wheel, in it
            new Linkage.Joint(new Vec(0.0, 0.4, -0.8), Vec.X.times(-1.0), Linkage.Kind.ROLL, 0.4, -1), // 2: the rear, axis given rightward
            new Linkage.Joint(new Vec(0.2, 0.3, 0.0), Vec.Z, Linkage.Kind.RIDDEN, Math.toRadians(-90), -1))); // 3: the stand

    @Test
    void atRestNothingMoves() {
        for (int i = 0; i < BIKE.joints().size(); i++) {
            Vec p = new Vec(0.1, 0.2, 0.3);
            assertTrue(BIKE.apply(i, Linkage.State.REST, p).near(p, 1e-12), "joint " + i);
        }
    }

    @Test
    void aWheelRollsItsTopForwardAsItTravelsWhicheverWayItsAxleWasGiven() {
        double quarter = Math.PI / 2 * 0.4;   // a quarter of the way round
        Linkage.State s = new Linkage.State(quarter, 0.0, 0.0);
        Vec rearTop = new Vec(0.0, 0.8, -0.8);
        assertTrue(BIKE.apply(2, s, rearTop).near(new Vec(0.0, 0.4, -0.4), 1e-9), "the top a quarter on is at the front: " + BIKE.apply(2, s, rearTop));
        assertEquals(1.0, BIKE.joints().get(2).axis().x(), 0.0, "the rightward axle is flipped to the left");
    }

    @Test
    void aForkSteersRightAboutItsRakeAndCarriesItsWheel() {
        Linkage.State right = new Linkage.State(0.0, 0.3, 0.0);
        Vec nose = new Vec(0.0, 0.4, 1.2);   // ahead of the front axle
        Vec turned = BIKE.apply(0, right, nose);
        assertTrue(turned.x() < -0.05, "a right steer swings the front to the right, -X: " + turned);
        assertTrue(BIKE.apply(0, new Linkage.State(0.0, -0.3, 0.0), nose).x() > 0.05, "and left to the left");
        assertTrue(BIKE.apply(1, right, AXLE).near(BIKE.apply(0, right, AXLE), 1e-12), "the wheel's axle goes where the fork takes it");
        Linkage.State both = new Linkage.State(Math.PI / 2 * 0.4, 0.3, 0.0);
        Vec top = new Vec(0.0, 0.8, 0.8);
        Vec rolledThenSteered = BIKE.rotation(0, both).apply(BIKE.rotation(1, both).apply(top));
        assertTrue(BIKE.apply(1, both, top).near(rolledThenSteered, 1e-12), "rolled in the fork, then the fork steered");
        assertEquals(List.of(BIKE.rotation(0, both), BIKE.rotation(1, both)), BIKE.chain(1, both), "outermost first");
    }

    @Test
    void aSteerAxisGivenDownwardIsFlippedUp() {
        Linkage.Joint j = new Linkage.Joint(Vec.ZERO, Vec.Y.times(-1.0), Linkage.Kind.STEER, 1.0, -1);
        assertEquals(1.0, j.axis().y(), 0.0);
    }

    @Test
    void aStandTurnsAsItsRidersTakeTheControls() {
        Vec foot = new Vec(0.2, 0.0, 0.0);
        assertTrue(BIKE.apply(3, new Linkage.State(0.0, 0.0, 1.0), foot).near(BIKE.rotation(3, new Linkage.State(0.0, 0.0, 1.0)).apply(foot), 1e-12));
        assertEquals(Math.toRadians(-45), BIKE.rotation(3, new Linkage.State(0.0, 0.0, 0.5)).radians(), 1e-12, "half way, half turned");
        assertEquals(Math.toRadians(-90), BIKE.rotation(3, new Linkage.State(0.0, 0.0, 1.0)).radians(), 1e-12);
    }

    @Test
    void partsAreCutDeepestFirst() {
        assertEquals(List.of(1, 0, 2, 3), BIKE.cutOrder());
        Linkage deep = new Linkage(List.of(
                new Linkage.Joint(Vec.ZERO, Vec.Y, Linkage.Kind.STEER, 1.0, -1),
                new Linkage.Joint(Vec.ZERO, Vec.X, Linkage.Kind.RIDDEN, 0.5, 0),
                new Linkage.Joint(Vec.ZERO, Vec.X, Linkage.Kind.ROLL, 0.3, 1)));
        assertEquals(List.of(2, 1, 0), deep.cutOrder());
        assertEquals(2, deep.depth(2));
        assertEquals(List.of(), Linkage.NONE.cutOrder());
    }

    @Test
    void anUnsoundLinkageIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Linkage(List.of(new Linkage.Joint(Vec.ZERO, Vec.X, Linkage.Kind.ROLL, 0.3, 0))));
        assertThrows(IllegalArgumentException.class, () -> new Linkage(List.of(
                new Linkage.Joint(Vec.ZERO, Vec.X, Linkage.Kind.ROLL, 0.3, 1),
                new Linkage.Joint(Vec.ZERO, Vec.Y, Linkage.Kind.STEER, 1.0, -1))));
        assertThrows(IllegalArgumentException.class, () -> new Linkage.Joint(Vec.ZERO, Vec.X, Linkage.Kind.ROLL, 0.0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Linkage.Joint(Vec.ZERO, new Vec(2, 0, 0), Linkage.Kind.STEER, 1.0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Linkage.State(0.0, 0.0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new Linkage.State(Double.NaN, 0.0, 0.0));
    }
}
