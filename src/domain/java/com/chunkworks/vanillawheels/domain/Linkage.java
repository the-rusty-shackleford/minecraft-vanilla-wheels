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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The parts of a body drawn in its own mesh that move with the ride (D-0047): a wheel that rolls,
 * a fork that steers with its wheel in it, a stand that folds up while someone rides. Each turns
 * about its own axis through its own pivot, and is carried by the part it hangs in, so a front
 * wheel rolls in the fork and turns with it.
 *
 * <p>All in the game's local frame, blocks: +X the vehicle's left, +Y up, +Z forward.
 *
 * <p>AF: joint i turns its part by {@link #rotation}, after which every joint it hangs in, from
 *     its own parent outward, turns it again.
 * <p>RI: every joint's parent is -1 (none) or an earlier joint's index.
 */
public record Linkage(List<Joint> joints) {
    /** What turns a joint. */
    public enum Kind {
        /** Rolls with the distance the wheels travel, once round for its circumference. */
        ROLL,
        /** Turns with the front wheels' steer. */
        STEER,
        /** Turns while someone is at the controls, and back when nobody is. */
        RIDDEN
    }

    /**
     * One moving part's turn.
     *
     * <p>AF: turns about {@code axis} through {@code pivot}. For ROLL, {@code amount} is the
     *     radius, blocks, and the axis points to the left so that travelling forward rolls the
     *     top forward; for STEER, the share of the wheels' steer it turns by, the axis pointing up
     *     so that a right steer turns it right; for RIDDEN, the radians it turns while ridden,
     *     about the axis as given.
     * <p>RI: |axis| = 1; amount finite, and positive for ROLL; ROLL's axis has x >= 0 and STEER's
     *     y >= 0 (the constructor flips one that does not).
     */
    public record Joint(Vec pivot, Vec axis, Kind kind, double amount, int parent) {
        public Joint {
            if (Math.abs(axis.length() - 1.0) > 1e-6 || !Double.isFinite(amount) || (kind == Kind.ROLL && !(amount > 0.0)) || parent < -1) {
                throw new IllegalArgumentException("a joint about " + axis + ", " + kind + " by " + amount + ", in " + parent);
            }
            if ((kind == Kind.ROLL && axis.x() < 0.0) || (kind == Kind.STEER && axis.y() < 0.0)) {
                axis = axis.times(-1.0);
            }
        }
    }

    /**
     * What the ride is doing, as the joints read it.
     *
     * <p>AF: the wheels have travelled {@code travel} blocks, are steered {@code steer} radians
     *     (positive right), and the controls are {@code ridden} of the way to taken (0 to 1).
     */
    public record State(double travel, double steer, double ridden) {
        public static final State REST = new State(0.0, 0.0, 0.0);

        public State {
            if (!Double.isFinite(travel) || !Double.isFinite(steer) || !(ridden >= 0.0 && ridden <= 1.0)) {
                throw new IllegalArgumentException("a ride state " + travel + ", " + steer + ", " + ridden);
            }
        }
    }

    public static final Linkage NONE = new Linkage(List.of());

    public Linkage {
        joints = List.copyOf(joints);
        for (int i = 0; i < joints.size(); i++) {
            if (joints.get(i).parent() >= i) {
                throw new IllegalArgumentException("joint " + i + " hangs in " + joints.get(i).parent() + ", not an earlier joint");
            }
        }
    }

    /** requires: 0 <= i < joints.size(); effects: returns joint i's own turn in state {@code s} */
    public Rotation rotation(int i, State s) {
        Joint j = joints.get(i);
        double radians = switch (j.kind()) {
            case ROLL -> s.travel() / j.amount();
            case STEER -> -s.steer() * j.amount();
            case RIDDEN -> j.amount() * s.ridden();
        };
        return new Rotation(j.pivot(), j.axis(), radians);
    }

    /**
     * requires: 0 <= i < joints.size()
     * effects: returns the turns that move joint i's part, outermost first: its root ancestor's,
     * down through its parent's, to its own; the order a pose stack applies them in
     */
    public List<Rotation> chain(int i, State s) {
        List<Rotation> out = new ArrayList<>();
        for (int k = i; k != -1; k = joints.get(k).parent()) {
            out.add(0, rotation(k, s));
        }
        return out;
    }

    /** requires: 0 <= i < joints.size(); effects: returns point {@code p} of joint i's part, moved by its chain */
    public Vec apply(int i, State s, Vec p) {
        List<Rotation> chain = chain(i, s);
        Vec moved = p;
        for (int k = chain.size() - 1; k >= 0; k--) {
            moved = chain.get(k).apply(moved);
        }
        return moved;
    }

    /** requires: 0 <= i < joints.size(); effects: returns how many joints joint i hangs in */
    public int depth(int i) {
        int d = 0;
        for (int k = joints.get(i).parent(); k != -1; k = joints.get(k).parent()) {
            d++;
        }
        return d;
    }

    /**
     * effects: returns the joints' indices in the order their parts are cut from the mesh: the
     * deepest first, then in index order, so a wheel is taken before the fork it hangs in and the
     * fork's own selector, which names the wheel's faces too, keeps only the fork
     */
    public List<Integer> cutOrder() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < joints.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt((Integer i) -> -depth(i)).thenComparingInt(i -> i));
        return order;
    }
}
