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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Parts: by material (present, absent), by group, by region
 * (a centre on the boundary is inside), material and region together (the
 * two needles), the complement. Normals: a planar quad's equals its cross
 * product; a reversed winding negates; a 16-gon in a plane; a bent quad
 * still has a unit normal. Orientation: a box wound inside-out comes out
 * outward; a right one is untouched; idempotent; two boxes with separate
 * vertices are separate pieces; the truck after orientation has every
 * face facing away from its piece. Transforms: mirror recomputes normals
 * and reflects; uniform scale keeps directions; composition order; a
 * rotation under a mirror reverses its angle and a dial its sweep. Quads:
 * 3, 4, 5, 12, 16 corners. Placeholder. Wheel spin wraps; pose lerp takes
 * the short way.
 */
final class MeshTest {

    private static Mesh box() {
        return Obj.parse("""
                v 0 0 0
                v 1 0 0
                v 1 1 0
                v 0 1 0
                v 0 0 1
                v 1 0 1
                v 1 1 1
                v 0 1 1
                vt 0 0
                vt 1 0
                vt 1 1
                vt 0 1
                usemtl a
                g left
                f 1/1 4/2 3/3 2/4
                f 5/1 6/2 7/3 8/4
                f 1/1 2/2 6/3 5/4
                usemtl b
                g right
                f 3/1 4/2 8/3 7/4
                f 2/1 3/2 7/3 6/4
                f 1/1 5/2 8/3 4/4
                """).mesh();
    }

    @Test
    void partsByMaterialGroupRegionAndComplement() {
        Mesh m = box();
        assertEquals(3, m.part(Selector.material("a")).faceCount());
        assertEquals(0, m.part(Selector.material("nothing")).faceCount());
        assertEquals(3, m.part(Selector.group("right")).faceCount());
        // The -z face's centre is at z = 0: on the boundary of z <= 0 counts as inside.
        Mesh back = m.part(new Selector(Set.of(), Set.of(), new Region(new Vec(-9, -9, -9), new Vec(9, 9, 0))));
        assertEquals(1, back.faceCount());
        Mesh aRight = m.part(new Selector(Set.of("a"), Set.of("right"), Region.ALL));
        assertEquals(0, aRight.faceCount(), "material a is all in group left");
        Mesh rest = m.without(m.part(Selector.material("a")));
        assertEquals(3, rest.faceCount());
        assertEquals(List.of("b"), rest.materials());
        Mesh truck = Fixtures.truck();
        Mesh speed = truck.part(Selector.material("needle").within(Region.xAtMost(-5)));
        Mesh fuel = truck.part(Selector.material("needle").within(Region.xAtLeast(-5)));
        assertEquals(6, speed.faceCount());
        assertEquals(6, fuel.faceCount());
        assertTrue(speed.bounds().max().x() < -5 && fuel.bounds().min().x() > -5);
    }

    @Test
    void normalsByNewellAgreeWithTheCrossProductAndHandleNGons() {
        List<Vec> p = List.of(new Vec(0, 0, 0), new Vec(1, 0, 0), new Vec(1, 1, 0), new Vec(0, 1, 0));
        List<Corner> ccw = List.of(new Corner(0, 0), new Corner(1, 0), new Corner(2, 0), new Corner(3, 0));
        assertEquals(new Vec(0, 0, 1), Mesh.newell(p, ccw));
        assertEquals(new Vec(0, 0, -1), Mesh.newell(p, ccw.reversed()));
        List<Vec> ring = new java.util.ArrayList<>();
        List<Corner> corners = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) {
            ring.add(new Vec(Math.cos(i * Math.PI / 8), Math.sin(i * Math.PI / 8), 3.0));
            corners.add(new Corner(i, 0));
        }
        assertTrue(Mesh.newell(ring, corners).near(new Vec(0, 0, 1), 1e-9));
        List<Vec> bent = List.of(new Vec(0, 0, 0), new Vec(1, 0, 0), new Vec(1, 1, 0.3), new Vec(0, 1, 0));
        assertEquals(1.0, Mesh.newell(bent, ccw).length(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> Mesh.newell(p, List.of(new Corner(0, 0), new Corner(0, 0), new Corner(0, 0))));
    }

    @Test
    void orientationTurnsEveryFaceOutwardAndIsIdempotent() {
        Mesh m = box();
        // As written, the -z face is wound one way and the rest the other; orientation fixes them all.
        Mesh o = m.orientedOutward();
        for (Face f : o.faces()) {
            Vec out = o.centre(f).minus(new Vec(0.5, 0.5, 0.5));
            assertTrue(f.normal().dot(out) > 0, "faces away from the centre: " + f);
        }
        assertEquals(o.faces(), o.orientedOutward().faces(), "idempotent");
        Mesh truck = Fixtures.truck().orientedOutward();
        assertEquals(764, truck.faceCount());
        // Two boxes sharing no vertex are two pieces with their own centres.
        Mesh two = Obj.parse("""
                v 0 0 0
                v 1 0 0
                v 1 1 0
                v 0 1 0
                v 10 0 0
                v 11 0 0
                v 11 1 0
                v 10 1 0
                v 10 0 1
                v 11 0 1
                v 11 1 1
                v 10 1 1
                vt 0 0
                f 1/1 2/1 3/1 4/1
                f 5/1 6/1 7/1 8/1
                f 9/1 10/1 11/1 12/1
                f 5/1 6/1 10/1 9/1
                f 7/1 8/1 12/1 11/1
                f 6/1 7/1 11/1 10/1
                f 5/1 9/1 12/1 8/1
                """).mesh().orientedOutward();
        Face far = two.faces().get(1);
        assertTrue(far.normal().dot(two.centre(far).minus(new Vec(10.5, 0.5, 0.5))) > 0, "the far box has its own centre");
    }

    @Test
    void transformsMirrorScaleAndCompose() {
        Mesh m = box().orientedOutward();
        Mesh mirrored = m.transformed(Transform.MIRROR_X);
        assertEquals(new Vec(-1, 0, 0), mirrored.positions().get(1));
        assertTrue(Transform.MIRROR_X.isReflection());
        assertTrue(!Transform.scale(2).isReflection());
        Mesh scaled = m.transformed(Transform.scale(0.5));
        assertEquals(new Vec(0.5, 0.5, 0.5), scaled.positions().get(6));
        assertEquals(m.faces().get(0).normal(), scaled.faces().get(0).normal(), "a uniform scale keeps normals");
        Transform both = Transform.scale(2).andThen(Transform.translate(new Vec(1, 0, 0)));
        assertEquals(new Vec(3, 2, 2), both.apply(new Vec(1, 1, 1)), "scale first, then translate");
        Transform other = Transform.translate(new Vec(1, 0, 0)).andThen(Transform.scale(2));
        assertEquals(new Vec(4, 2, 2), other.apply(new Vec(1, 1, 1)), "translate first, then scale");
        Rotation r = new Rotation(new Vec(1, 2, 3), Vec.Z, 0.5);
        Rotation rm = r.mirrored(Transform.MIRROR_X);
        assertEquals(new Vec(-1, 2, 3), rm.pivot());
        assertEquals(-0.5, rm.radians(), 1e-12, "a reflection reverses the turn");
        assertEquals(0.5, r.mirrored(Transform.scale(2)).radians(), 1e-12);
        Dial d = new Dial(new Vec(-8, 17, 21.9), Vec.Z, 0.3, 4.7);
        Dial dm = d.transformed(Transform.MIRROR_X);
        assertEquals(new Vec(8, 17, 21.9), dm.pivot());
        assertEquals(-0.3, dm.rest(), 1e-12);
        assertEquals(-4.7, dm.sweep(), 1e-12);
        assertEquals(-0.3 - 4.7 * 0.5, dm.rotation(0.5).radians(), 1e-12);
        assertEquals(0.3, d.rotation(-1).radians(), 1e-12, "clamped below");
        assertEquals(5.0, d.rotation(2).radians(), 1e-12, "clamped above");
    }

    @Test
    void aRotationTurnsAPointAboutItsPivot() {
        Rotation quarter = new Rotation(new Vec(1, 1, 0), Vec.Z, Math.PI / 2);
        assertTrue(quarter.apply(new Vec(2, 1, 0)).near(new Vec(1, 2, 0), 1e-9), "x toward y about z");
        assertThrows(IllegalArgumentException.class, () -> new Rotation(Vec.ZERO, new Vec(0, 2, 0), 1));
    }

    @Test
    void quadsFanNGonsAndRepeatATrianglesLastCorner() {
        assertEquals(1, ngon(3).quads().size());
        assertEquals(1, ngon(4).quads().size());
        assertEquals(2, ngon(5).quads().size());
        assertEquals(5, ngon(12).quads().size());
        assertEquals(7, ngon(16).quads().size());
        Mesh.Quad tri = ngon(3).quads().get(0);
        assertEquals(tri.c(), tri.d(), "a triangle repeats its last corner");
        Mesh.Quad five = ngon(5).quads().get(1);
        assertEquals(five.c(), five.d(), "an odd fan's last quad repeats too");
        Mesh ph = Mesh.placeholder(1.0);
        assertEquals(6, ph.faceCount());
        assertEquals(List.of("placeholder"), ph.materials());
    }

    private static Mesh ngon(int n) {
        List<Vec> pts = new java.util.ArrayList<>();
        List<Corner> corners = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            pts.add(new Vec(Math.cos(i * 2 * Math.PI / n), Math.sin(i * 2 * Math.PI / n), 0));
            corners.add(new Corner(i, 0));
        }
        return Mesh.of(pts, List.of(new Uv(0, 0)), List.of(new Face("m", "", corners, Vec.Z)));
    }

    @Test
    void bakingScalesFlipsVAndKeepsOneNormalPerQuad() {
        Mesh m = box().orientedOutward();
        BakedMesh b = BakedMesh.of(m, 1.0 / 16);
        assertEquals(6, b.quadCount());
        // Quad 0 corner 1 is position 4 (0,1,0) -> scaled
        assertEquals(1.0f / 16, b.position(0, 1, 1), 1e-7f);
        assertEquals(1.0f, b.uv(0, 0, 1), 1e-7f, "vt 0 0 becomes v = 1");
        assertEquals(0.0f, b.uv(0, 2, 1), 1e-7f, "vt 1 1 becomes v = 0");
        assertEquals(-1.0f, b.normal(0, 2), 1e-6f, "the first face is the -z side");
        assertEquals(820, BakedMesh.of(Fixtures.truck(), 1.0 / 16).quadCount());
    }

    @Test
    void wheelSpinWrapsAndPosesLerpTheShortWayRound() {
        assertEquals(1.0, WheelSpin.radians(0.75, 0.75), 1e-12);
        assertEquals(Drive.wrap(100.0), WheelSpin.radians(75.0, 0.75), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> WheelSpin.radians(1, 0));
        BodyPose a = new BodyPose(3.0, 0, 0);
        BodyPose b = new BodyPose(-3.0, 0.2, 0);
        BodyPose mid = BodyPose.lerp(a, b, 0.5);
        assertTrue(Math.abs(Math.abs(mid.yaw()) - Math.PI) < 0.15, "through pi, not through zero: " + mid.yaw());
        assertEquals(0.1, mid.pitch(), 1e-12);
    }
}
