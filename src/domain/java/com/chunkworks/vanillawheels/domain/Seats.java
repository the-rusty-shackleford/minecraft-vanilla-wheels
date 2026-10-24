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

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Who sits where aboard a vehicle (D-0038). A rider keeps the seat they took for as long as they
 * are aboard: one who leaves frees their own seat and nobody moves up into it, so the driver's seat
 * is never handed to whoever boarded next. Boarding takes the driver's seat while it is free, else
 * the first free seat; a rider already aboard takes the free driver's seat only by asking
 * ({@link #takeControls}).
 *
 * <p>RI: riders != null; -1 <= driver < riders.length; no rider in two seats.
 * AF: AF(riders, driver) = "seat i holds riders[i], or is empty where riders[i] is null; the
 *     driver's seat is seat {@code driver}, or there is none when it is -1".
 */
public final class Seats {
    private final UUID[] riders;
    private final int driver;

    private Seats(UUID[] riders, int driver) {
        this.riders = riders;
        this.driver = driver;
        checkRep();
    }

    private void checkRep() {
        if (driver < -1 || driver >= riders.length) {
            throw new IllegalArgumentException("the driver's seat is -1.." + (riders.length - 1) + ": " + driver);
        }
        for (int i = 0; i < riders.length; i++) {
            for (int j = i + 1; j < riders.length; j++) {
                if (riders[i] != null && riders[i].equals(riders[j])) {
                    throw new IllegalArgumentException("a rider in two seats: " + riders[i]);
                }
            }
        }
    }

    /**
     * requires: count >= 0, -1 <= driver < count
     * effects: returns {@code count} empty seats, the driver's seat {@code driver} (-1 for none)
     * throws: IllegalArgumentException if a bound is broken
     */
    public static Seats empty(int count, int driver) {
        if (count < 0) {
            throw new IllegalArgumentException("a seat count is never negative: " + count);
        }
        return new Seats(new UUID[count], driver);
    }

    /** effects: returns how many seats there are */
    public int count() {
        return riders.length;
    }

    /** effects: returns the driver's seat, or -1 if there is none */
    public int driver() {
        return driver;
    }

    /** requires: 0 <= seat < count(); effects: returns who sits in {@code seat}, if anyone; throws: IndexOutOfBoundsException */
    public Optional<UUID> at(int seat) {
        return Optional.ofNullable(riders[seat]);
    }

    /** effects: returns the seat {@code rider} sits in, or -1 if they are not aboard */
    public int seatOf(UUID rider) {
        for (int i = 0; i < riders.length; i++) {
            if (rider.equals(riders[i])) {
                return i;
            }
        }
        return -1;
    }

    /** effects: returns how many seats are taken */
    public int taken() {
        int n = 0;
        for (UUID r : riders) {
            if (r != null) {
                n++;
            }
        }
        return n;
    }

    /** effects: returns whether a seat is free */
    public boolean hasRoom() {
        return taken() < riders.length;
    }

    /** effects: returns whether there is a driver's seat and nobody sits in it */
    public boolean controlsFree() {
        return driver >= 0 && riders[driver] == null;
    }

    /**
     * effects: returns the seats with {@code rider} in the seat boarding gives: the driver's while
     * it is free, else the first free seat; unchanged if they are aboard already or no seat is free
     */
    public Seats board(UUID rider) {
        return board(rider, -1);
    }

    /**
     * effects: as {@link #board(UUID)}, but into {@code preferred} while it is free (a returning
     * rider's own seat); a preferred seat that is out of range or taken is passed over
     */
    public Seats board(UUID rider, int preferred) {
        if (seatOf(rider) >= 0) {
            return this;
        }
        int seat = preferred >= 0 && preferred < riders.length && riders[preferred] == null ? preferred
                : controlsFree() ? driver : firstFree();
        return seat < 0 ? this : with(seat, rider);
    }

    /** effects: returns the seats with {@code rider}'s seat free and everyone else where they sat; unchanged if they are not aboard */
    public Seats leave(UUID rider) {
        int seat = seatOf(rider);
        return seat < 0 ? this : with(seat, null);
    }

    /**
     * effects: returns the seats with {@code rider} moved into the driver's seat if they are aboard
     * and it is free; unchanged otherwise (not aboard, at the controls already, someone else there,
     * or no driver's seat)
     */
    public Seats takeControls(UUID rider) {
        int seat = seatOf(rider);
        if (seat < 0 || !controlsFree()) {
            return this;
        }
        UUID[] next = riders.clone();
        next[seat] = null;
        next[driver] = rider;
        return new Seats(next, driver);
    }

    /** effects: returns the seats with everyone not in {@code aboard} out of their seat: riders a load did not bring back */
    public Seats keepOnly(Collection<UUID> aboard) {
        UUID[] next = riders.clone();
        boolean changed = false;
        for (int i = 0; i < next.length; i++) {
            if (next[i] != null && !aboard.contains(next[i])) {
                next[i] = null;
                changed = true;
            }
        }
        return changed ? new Seats(next, driver) : this;
    }

    /** effects: returns the seats' riders in seat order as text, comma separated, an empty seat empty: the form {@link #decode} reads */
    public String encode() {
        StringJoiner out = new StringJoiner(",");
        for (UUID r : riders) {
            out.add(r == null ? "" : r.toString());
        }
        return out.toString();
    }

    /**
     * requires: count >= 0, -1 <= driver < count
     * effects: returns the seats {@code text} names ({@link #encode}'s form), fitted to {@code count}
     * seats with the driver's seat {@code driver}: seats past the last are dropped, missing ones are
     * empty, and an entry that is not a UUID, or a rider named again after their first seat, leaves
     * that seat empty. Never throws for bad text: a profile's seats may change under a saved vehicle.
     * throws: IllegalArgumentException if a bound is broken
     */
    public static Seats decode(String text, int count, int driver) {
        Seats out = empty(count, driver);
        if (text.isEmpty()) {
            return out;
        }
        String[] entries = text.split(",", -1);
        for (int i = 0; i < Math.min(entries.length, count); i++) {
            UUID r = parse(entries[i]);
            if (r != null && out.seatOf(r) < 0) {
                out = out.with(i, r);
            }
        }
        return out;
    }

    private static UUID parse(String s) {
        if (s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int firstFree() {
        for (int i = 0; i < riders.length; i++) {
            if (riders[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private Seats with(int seat, UUID rider) {
        UUID[] next = riders.clone();
        next[seat] = rider;
        return new Seats(next, driver);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Seats s && s.driver == driver && Arrays.equals(s.riders, riders);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(riders) + driver;
    }

    @Override
    public String toString() {
        return "Seats[" + encode() + "; driver " + driver + "]";
    }
}
