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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A vehicle's mesh cut into the pieces the renderer draws apart: the
 * pieces another protocol moves (a rotor, a spray boom: D-0030), each
 * needle, each door (its lenses, its painted panels and the rest of it),
 * the body's lenses, glass, the glass the cockpit names too, cockpit and
 * paint, and what is left. Every cut
 * takes from what the cuts before it left, so each face is in exactly one
 * piece and is drawn once. A selector's group matches any name on a face's
 * path, so a lens nested in a door answers the body's lens selector too:
 * cut from the whole mesh, the trailer's rear reflectors were drawn twice,
 * once swinging with the door and once where the shut door stood.
 */
public record Parts(List<Mesh> extras, List<Mesh> needles, List<Door> doors, Mesh lamps, Mesh glass, Mesh cockpitGlass, Mesh cockpit,
                    Mesh body, Mesh rest) {

    /** A door's faces: its lenses (lit with the lamps), its painted panels, and the rest of it. */
    public record Door(Mesh lamps, Mesh painted, Mesh rest) {}

    public Parts {
        extras = List.copyOf(extras);
        needles = List.copyOf(needles);
        doors = List.copyOf(doors);
    }

    /** effects: returns {@code mesh} cut with no extra pieces: {@link #cut(Mesh, List, List, List, Optional, Optional, Optional, Optional)} with none */
    public static Parts cut(Mesh mesh, List<Selector> needles, List<Selector> doors,
            Optional<Selector> lamps, Optional<Selector> glass, Optional<Selector> cockpit, Optional<Selector> paint) {
        return cut(mesh, List.of(), needles, doors, lamps, glass, cockpit, paint);
    }

    /**
     * effects: returns {@code mesh} cut in this order -- the extras (what
     * another protocol moves, first, so a rotor's painted blade turns with
     * the rotor and is not painted on the body as well), the needles, the
     * doors, then the lenses, glass, cockpit and paint -- each from what the
     * cuts before it left; within a door, its lenses before its paint; the
     * glass the cockpit selector names as well is a piece of its own, the
     * cockpit's glass (D-0031); an absent selector cuts nothing
     */
    public static Parts cut(Mesh mesh, List<Selector> extras, List<Selector> needles, List<Selector> doors,
            Optional<Selector> lamps, Optional<Selector> glass, Optional<Selector> cockpit, Optional<Selector> paint) {
        Mesh remaining = mesh;
        List<Mesh> xs = new ArrayList<>();
        for (Selector s : extras) {
            Mesh extra = remaining.part(s);
            remaining = remaining.without(extra);
            xs.add(extra);
        }
        List<Mesh> ns = new ArrayList<>();
        for (Selector s : needles) {
            Mesh needle = remaining.part(s);
            remaining = remaining.without(needle);
            ns.add(needle);
        }
        List<Door> ds = new ArrayList<>();
        for (Selector s : doors) {
            Mesh door = remaining.part(s);
            remaining = remaining.without(door);
            Mesh doorLamps = pick(door, lamps);
            Mesh left = door.without(doorLamps);
            Mesh painted = pick(left, paint);
            ds.add(new Door(doorLamps, painted, left.without(painted)));
        }
        Mesh lampMesh = pick(remaining, lamps);
        remaining = remaining.without(lampMesh);
        Mesh glassMesh = pick(remaining, glass);
        remaining = remaining.without(glassMesh);
        Mesh cockpitGlass = pick(glassMesh, cockpit);
        glassMesh = glassMesh.without(cockpitGlass);
        Mesh cockpitMesh = pick(remaining, cockpit);
        remaining = remaining.without(cockpitMesh);
        Mesh bodyMesh = pick(remaining, paint);
        remaining = remaining.without(bodyMesh);
        return new Parts(xs, ns, ds, lampMesh, glassMesh, cockpitGlass, cockpitMesh, bodyMesh, remaining);
    }

    /** effects: returns the faces of {@code from} that {@code selector} picks, none when it is absent */
    private static Mesh pick(Mesh from, Optional<Selector> selector) {
        return selector.map(from::part).orElseGet(() -> from.part(f -> false));
    }
}
