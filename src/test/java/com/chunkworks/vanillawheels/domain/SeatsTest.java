/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Who sits where aboard a vehicle (D-0038).
 *
 * <p>Partitions. empty: no seats, some; the driver's seat none (-1), first, last; bounds broken (a
 * negative count, a driver's seat below -1 or past the last). board: into empty seats, the driver's
 * seat first, last or none; the driver's seat taken (the first free seat, the lowest); every seat
 * taken; the rider aboard already. board with a preferred seat: free (taken even over a free driver's
 * seat), taken, out of range below and above. leave: the driver, a passenger (nobody moves up either
 * way), someone not aboard. takeControls: from a passenger's seat with the driver's free; with it
 * taken; from the driver's seat; not aboard; no driver's seat. keepOnly: a rider gone, everyone there.
 * encode and decode: empty, some and every seat taken; empty text; fewer entries than seats, more;
 * an entry that is not a UUID; a rider named twice.
 */
class SeatsTest {
    private static final UUID PILOT = UUID.fromString("c3717b88-de7e-4f17-9745-2481ea0a1f2e");
    private static final UUID CREW = UUID.fromString("39ffa0bc-180b-4877-9f9f-911c18223576");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID FOURTH = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void emptySeatsHoldNobodyAndBreakingABoundThrows() {
        Seats none = Seats.empty(0, -1);
        assertEquals(0, none.count());
        assertFalse(none.hasRoom(), "no seats, no room");
        assertFalse(none.controlsFree(), "no driver's seat, no controls to take");
        Seats huey = Seats.empty(8, 0);
        assertEquals(8, huey.count());
        assertEquals(0, huey.taken());
        assertTrue(huey.hasRoom());
        assertTrue(huey.controlsFree());
        assertEquals(Optional.empty(), huey.at(7));
        assertEquals(-1, huey.seatOf(PILOT));
        assertEquals(3, Seats.empty(4, 3).driver(), "the driver's seat last");
        assertThrows(IllegalArgumentException.class, () -> Seats.empty(-1, -1));
        assertThrows(IllegalArgumentException.class, () -> Seats.empty(2, -2));
        assertThrows(IllegalArgumentException.class, () -> Seats.empty(2, 2));
        assertThrows(IllegalArgumentException.class, () -> Seats.empty(0, 0));
    }

    @Test
    void boardingTakesTheDriversSeatWhileItIsFreeThenTheFirstFreeSeat() {
        Seats s = Seats.empty(3, 0).board(PILOT).board(CREW).board(THIRD);
        assertEquals(List.of(0, 1, 2), List.of(s.seatOf(PILOT), s.seatOf(CREW), s.seatOf(THIRD)), "in boarding order, the pilot's seat first");
        assertFalse(s.hasRoom());
        assertSame(s, s.board(FOURTH), "every seat taken: nobody boards");
        assertSame(s, s.board(CREW), "aboard already: they stay where they are");
        Seats last = Seats.empty(3, 2).board(PILOT).board(CREW);
        assertEquals(2, last.seatOf(PILOT), "the driver's seat last in the profile: the first to board still drives");
        assertEquals(0, last.seatOf(CREW), "then the first free seat");
        Seats unpowered = Seats.empty(2, -1).board(PILOT);
        assertEquals(0, unpowered.seatOf(PILOT), "no driver's seat: the first free seat");
        Seats gap = Seats.empty(4, 0).board(PILOT).board(CREW).board(THIRD).leave(CREW).board(FOURTH);
        assertEquals(1, gap.seatOf(FOURTH), "the lowest free seat, the one left behind");
    }

    @Test
    void aPreferredSeatIsTakenWhileFreeAndPassedOverOtherwise() {
        Seats s = Seats.empty(4, 0).board(CREW, 2);
        assertEquals(2, s.seatOf(CREW), "free: taken, even with the driver's seat free");
        assertTrue(s.controlsFree());
        assertEquals(0, s.board(PILOT, 2).seatOf(PILOT), "taken: the driver's seat, by boarding's rule");
        assertEquals(0, s.board(PILOT, -1).seatOf(PILOT), "below range: boarding's rule");
        assertEquals(0, s.board(PILOT, 4).seatOf(PILOT), "past the last seat: boarding's rule");
        Seats full = Seats.empty(2, 0).board(PILOT).board(CREW);
        assertSame(full, full.board(THIRD, 1), "every seat taken: nobody boards");
    }

    @Test
    void whoeverLeavesFreesOnlyTheirOwnSeatAndNobodyMovesUp() {
        Seats s = Seats.empty(3, 0).board(PILOT).board(CREW).board(THIRD);
        Seats pilotGone = s.leave(PILOT);
        assertEquals(-1, pilotGone.seatOf(PILOT));
        assertEquals(1, pilotGone.seatOf(CREW), "the next to have boarded is not handed the controls");
        assertEquals(2, pilotGone.seatOf(THIRD));
        assertTrue(pilotGone.controlsFree(), "nobody at the controls");
        Seats crewGone = s.leave(CREW);
        assertEquals(List.of(0, -1, 2), List.of(crewGone.seatOf(PILOT), crewGone.seatOf(CREW), crewGone.seatOf(THIRD)));
        assertFalse(crewGone.controlsFree());
        assertSame(s, s.leave(FOURTH), "not aboard: unchanged");
    }

    @Test
    void takingTheControlsMovesARiderIntoAFreeDriversSeatOnly() {
        Seats pilotGone = Seats.empty(3, 0).board(PILOT).board(CREW).board(THIRD).leave(PILOT);
        Seats taken = pilotGone.takeControls(THIRD);
        assertEquals(0, taken.seatOf(THIRD), "into the driver's seat");
        assertEquals(Optional.empty(), taken.at(2), "out of their own");
        assertEquals(1, taken.seatOf(CREW), "everyone else where they sat");
        assertFalse(taken.controlsFree());
        assertSame(taken, taken.takeControls(CREW), "someone at the controls: unchanged");
        assertSame(taken, taken.takeControls(THIRD), "at the controls already: unchanged");
        assertSame(pilotGone, pilotGone.takeControls(PILOT), "not aboard: unchanged");
        Seats unpowered = Seats.empty(2, -1).board(PILOT);
        assertSame(unpowered, unpowered.takeControls(PILOT), "no driver's seat: unchanged");
    }

    @Test
    void keepOnlyEmptiesTheSeatsOfRidersNotAboard() {
        Seats s = Seats.empty(3, 0).board(PILOT).board(CREW).board(THIRD);
        Seats kept = s.keepOnly(List.of(CREW));
        assertEquals(List.of(-1, 1, -1), List.of(kept.seatOf(PILOT), kept.seatOf(CREW), kept.seatOf(THIRD)), "nobody moves up into the emptied seats");
        assertSame(s, s.keepOnly(List.of(PILOT, CREW, THIRD, FOURTH)), "everyone there: unchanged");
    }

    @Test
    void encodeAndDecodeRoundTripAndBadTextLeavesSeatsEmpty() {
        Seats none = Seats.empty(3, 0);
        Seats some = none.board(PILOT).board(CREW).leave(PILOT);
        Seats full = none.board(PILOT).board(CREW).board(THIRD);
        for (Seats s : List.of(none, some, full)) {
            assertEquals(s, Seats.decode(s.encode(), 3, 0), "round trip of " + s);
        }
        assertEquals(",,", none.encode());
        assertEquals("," + CREW + ",", some.encode());
        assertEquals(none, Seats.decode("", 3, 0), "empty text: empty seats (a vehicle saved before 1.16.0)");
        Seats shorter = Seats.decode(PILOT.toString(), 3, 0);
        assertEquals(0, shorter.seatOf(PILOT));
        assertEquals(1, shorter.taken(), "fewer entries than seats: the rest empty");
        Seats longer = Seats.decode(full.encode(), 2, 0);
        assertEquals(List.of(0, 1, -1), List.of(longer.seatOf(PILOT), longer.seatOf(CREW), longer.seatOf(THIRD)), "a seat past the last is dropped");
        Seats garbage = Seats.decode("nobody," + CREW, 2, 0);
        assertEquals(Optional.empty(), garbage.at(0), "not a UUID: an empty seat");
        assertEquals(1, garbage.seatOf(CREW));
        Seats twice = Seats.decode(CREW + "," + CREW, 2, 0);
        assertEquals(0, twice.seatOf(CREW), "named twice: the first seat");
        assertEquals(1, twice.taken(), "and the second empty");
        assertEquals(Seats.decode(full.encode(), 3, 0).hashCode(), full.hashCode());
    }
}
