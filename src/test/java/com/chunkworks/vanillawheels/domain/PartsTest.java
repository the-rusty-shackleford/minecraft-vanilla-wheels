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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Each shipped vehicle (the trailer, the pickup, the
 * Trailblazer), its Blockbench project cut by its own profile's
 * selectors: every face in exactly one piece. The trailer's rear
 * reflectors, lenses nested in its doors: in the doors' lamps and not the
 * body's. The pickup's tailgate panels, paint nested in its door: in the
 * door's paint and not the body's. A small mesh: within a door, a face in
 * both lenses and paint is a lens; an absent selector cuts nothing.
 */
final class PartsTest {

    /** effects: returns the fixture project {@code name} cut by the selectors of its fixture profile */
    private static Cut cut(String name) {
        Mesh mesh = BbModel.parse(Fixtures.text(name + ".bbmodel")).mesh();
        Object profile = Json.parse(Fixtures.text(name + "_profile.json"));
        List<Selector> needles = new ArrayList<>();
        for (Object g : Json.list(Json.get(profile, "gauges"))) {
            needles.add(selector(Json.get(g, "part")).orElseThrow());
        }
        List<Selector> doors = new ArrayList<>();
        for (Object d : Json.list(Json.get(profile, "doors"))) {
            doors.add(selector(Json.get(d, "part")).orElseThrow());
        }
        Parts parts = Parts.cut(mesh, needles, doors,
                selector(Json.get(Json.get(profile, "headlights"), "part")),
                selector(Json.get(profile, "glass")),
                selector(Json.get(profile, "cockpit")),
                selector(Json.get(Json.get(profile, "paint"), "part")));
        return new Cut(mesh, parts);
    }

    private record Cut(Mesh mesh, Parts parts) {}

    /** effects: returns the profile's part selector, which in every shipped profile names one group; throws if it names more */
    private static Optional<Selector> selector(Object part) {
        if (part == null) {
            return Optional.empty();
        }
        Map<String, Object> keys = Json.map(part);
        if (!keys.keySet().equals(java.util.Set.of("group"))) {
            throw new IllegalStateException("a fixture selector that is not one group: " + keys);
        }
        return Optional.of(Selector.group(Json.string(keys.get("group"), "")));
    }

    /** effects: returns every piece of {@code p}, a door's three pieces each on their own */
    private static List<Mesh> pieces(Parts p) {
        List<Mesh> all = new ArrayList<>(p.needles());
        for (Parts.Door d : p.doors()) {
            all.add(d.lamps());
            all.add(d.painted());
            all.add(d.rest());
        }
        all.addAll(List.of(p.lamps(), p.glass(), p.cockpit(), p.body(), p.rest()));
        return all;
    }

    /** effects: returns whether a name on {@code face}'s group path is {@code name} */
    private static boolean under(Face face, String name) {
        return List.of(face.group().split("/")).contains(name);
    }

    /** effects: returns the group path of each face of {@code mesh}, in order (a mesh's faces carry normals it computed) */
    private static List<String> groups(Mesh mesh) {
        return mesh.faces().stream().map(Face::group).toList();
    }

    private static long count(Mesh mesh, String name) {
        return mesh.faces().stream().filter(f -> under(f, name)).count();
    }

    @Test
    void everyFaceOfEveryShippedVehicleIsInExactlyOnePiece() {
        for (String name : List.of("trailer", "pickup", "trailblazer")) {
            Cut c = cut(name);
            Map<Face, Integer> seen = new IdentityHashMap<>();
            for (Mesh piece : pieces(c.parts())) {
                for (Face f : piece.faces()) {
                    seen.merge(f, 1, Integer::sum);
                }
            }
            for (Face f : c.mesh().faces()) {
                assertEquals(1, seen.getOrDefault(f, 0), name + ": " + f.group() + " drawn this many times");
            }
            assertEquals(c.mesh().faceCount(), seen.size(), name + ": no piece holds a face the mesh does not");
        }
    }

    @Test
    void theTrailersRearReflectorsSwingWithTheirDoorsAndNowhereElse() {
        Parts p = cut("trailer").parts();
        String[] sides = {"rear_reflector_left", "rear_reflector_right"};
        for (int i = 0; i < 2; i++) {
            assertTrue(count(p.doors().get(i).lamps(), sides[i]) > 0, sides[i] + " rides its door's lamps");
            assertEquals(0, count(p.lamps(), sides[i]), sides[i] + " is not among the body's lamps");
            assertEquals(0, count(p.doors().get(i).rest(), sides[i]), sides[i] + " is not drawn unlit on the door");
        }
        assertTrue(count(p.lamps(), "front_lamp_left") > 0, "the body keeps its own lenses");
    }

    @Test
    void thePickupsTailgatePanelsArePaintedOnTheTailgateOnly() {
        Parts p = cut("pickup").parts();
        Parts.Door tailgate = p.doors().get(0);
        long panels = tailgate.painted().faces().stream().filter(f -> under(f, "tailgate_hinge") && under(f, "paint")).count();
        assertTrue(panels > 0, "the tailgate's panels take the paint with the tailgate");
        assertEquals(0, count(p.body(), "tailgate_hinge"), "no painted tailgate stays behind on the body");
    }

    @Test
    void withinADoorALensInThePaintIsALensAndAnAbsentSelectorCutsNothing() {
        List<Vec> positions = List.of(new Vec(0, 0, 0), new Vec(1, 0, 0), new Vec(0, 1, 0));
        List<Corner> corners = List.of(new Corner(0, 0), new Corner(1, 0), new Corner(2, 0));
        Face lens = new Face("m", "body/door/paint/lenses/lens", corners, Vec.Y);
        Face panel = new Face("m", "body/door/paint/panel", corners, Vec.Y);
        Face hinge = new Face("m", "body/door/hinge", corners, Vec.Y);
        Face side = new Face("m", "body/paint/side", corners, Vec.Y);
        Mesh mesh = Mesh.of(positions, List.of(new Uv(0, 0)), List.of(lens, panel, hinge, side));
        Parts p = Parts.cut(mesh, List.of(), List.of(Selector.group("door")),
                Optional.of(Selector.group("lenses")), Optional.empty(), Optional.empty(), Optional.of(Selector.group("paint")));
        Parts.Door door = p.doors().get(0);
        assertEquals(List.of(lens.group()), groups(door.lamps()));
        assertEquals(List.of(panel.group()), groups(door.painted()));
        assertEquals(List.of(hinge.group()), groups(door.rest()));
        assertEquals(List.of(), groups(p.lamps()));
        assertEquals(List.of(), groups(p.glass()));
        assertEquals(List.of(), groups(p.cockpit()));
        assertEquals(List.of(side.group()), groups(p.body()));
        assertEquals(List.of(), groups(p.rest()));
    }
}
