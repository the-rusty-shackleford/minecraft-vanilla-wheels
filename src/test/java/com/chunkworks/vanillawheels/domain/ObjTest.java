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
import org.junit.jupiter.api.Test;

/**
 * Partitions. Line kinds: v, vt, vn (ignored), f, usemtl, g, comment,
 * blank, mtllib/o/s ignored, unknown (warning). Face arity: 3, 4, 12, 16,
 * fewer than 3 (throws). Corner forms: v/vt, v/vt/vn, v//vn (throws: no
 * uv), v (throws), negative index, zero (throws), out of range (throws).
 * Numbers: integers, decimals, exponents, negatives, not a number (throws).
 * Whitespace: tabs, runs, trailing, CRLF. No usemtl before the first face
 * gives "default"; a material re-selected later is the same name. Empty
 * text is an empty mesh. A degenerate face is dropped with a warning. Line
 * numbers in exceptions. The bundle's truck and wheel: their counts.
 */
final class ObjTest {

    private static final String BOX = """
            # a unit box, six quads
            mtllib whatever.mtl
            o box
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
            vn 0 0 -1
            s off
            usemtl paint
            g body
            f 1/1/1 4/4/1 3/3/1 2/2/1
            f 5/1 6/2 7/3 8/4
            f 1/1 2/2 6/3 5/4
            f 3/1 4/2 8/3 7/4
            usemtl glass
            f 2/1 3/2 7/3 6/4
            f 1/1 5/2 8/3 4/4
            """;

    @Test
    void aBoxParsesIntoSixQuadsWithMaterialsAndGroups() {
        Obj.Parsed parsed = Obj.parse(BOX);
        Mesh m = parsed.mesh();
        assertEquals(6, m.faceCount());
        assertEquals(8, m.positions().size());
        assertEquals(4, m.uvs().size());
        assertEquals(List.of("paint", "glass"), m.materials());
        assertEquals(List.of("body"), m.groups());
        assertEquals(4, m.part(Selector.material("paint")).faceCount());
        assertEquals(List.of(), parsed.warnings());
        for (Face f : m.faces()) {
            assertEquals(1.0, f.normal().length(), 1e-9);
        }
    }

    @Test
    void cornerFormsIndicesAndNumbers() {
        Mesh tri = Obj.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nvn 0 0 1\nf 1/1/1 2/2/1 3/3/1\n").mesh();
        assertEquals(3, tri.faces().get(0).corners().size());
        Mesh negative = Obj.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nf -3/-3 -2/-2 -1/-1\n").mesh();
        assertEquals(List.of(new Corner(0, 0), new Corner(1, 1), new Corner(2, 2)), negative.faces().get(0).corners());
        Mesh sci = Obj.parse("v 1e1 -2.5E0 +3\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nf 1/1 2/2 3/3\n").mesh();
        assertEquals(new Vec(10, -2.5, 3), sci.positions().get(0));
        assertThrows(ObjFormatException.class, () -> Obj.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1//1 2//1 3//1\n"), "no uv");
        assertThrows(ObjFormatException.class, () -> Obj.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nf 1 2 3\n"), "bare v");
        ObjFormatException zero = assertThrows(ObjFormatException.class, () -> Obj.parse("v 0 0 0\nvt 0 0\nf 0/1 1/1 1/1\n"));
        assertEquals(3, zero.line());
        assertTrue(zero.getMessage().contains("start at 1"));
        assertThrows(ObjFormatException.class, () -> Obj.parse("v 0 0 0\nvt 0 0\nf 1/1 2/1 1/1\n"), "out of range");
        assertThrows(ObjFormatException.class, () -> Obj.parse("v 0 0 0\nvt 0 0\nf 1/1 1/9 1/1\n"), "uv out of range");
        assertThrows(ObjFormatException.class, () -> Obj.parse("v 0 0\n"), "too few numbers");
        assertThrows(ObjFormatException.class, () -> Obj.parse("v a b c\n"), "not a number");
        assertThrows(ObjFormatException.class, () -> Obj.parse("v 0 0 0\nv 1 0 0\nvt 0 0\nf 1/1 2/1\n"), "two corners");
    }

    @Test
    void whitespaceDefaultsWarningsAndDegenerateFaces() {
        Obj.Parsed p = Obj.parse("v\t0  0 0 \r\nv 1 0 0\r\nv 0 1 0\r\nvt 0 0\r\n\r\nf 1/1 2/1 3/1\r\nfoo bar\r\nf 1/1 1/1 1/1\r\n");
        assertEquals(1, p.mesh().faceCount(), "the flat face was dropped");
        assertEquals("default", p.mesh().faces().get(0).material());
        assertEquals("", p.mesh().faces().get(0).group());
        assertEquals(2, p.warnings().size());
        assertTrue(p.warnings().get(0).contains("unknown keyword 'foo'"));
        assertTrue(p.warnings().get(1).contains("no area"));
        assertEquals(0, Obj.parse("").mesh().faceCount());
        assertEquals(0, Obj.parse("# nothing\n\n").mesh().faceCount());
    }

    @Test
    void theBundlesTruckAndWheelParseToTheirKnownShape() {
        Obj.Parsed truck = Obj.parse(Fixtures.text("trailblazer_frame.obj"));
        Mesh m = truck.mesh();
        assertEquals(764, m.faceCount());
        assertEquals(1056, m.positions().size());
        assertEquals(492, m.uvs().size());
        assertEquals(List.of(), truck.warnings());
        long quads = m.faces().stream().filter(f -> f.corners().size() == 4).count();
        long twelve = m.faces().stream().filter(f -> f.corners().size() == 12).count();
        long sixteen = m.faces().stream().filter(f -> f.corners().size() == 16).count();
        assertEquals(754, quads);
        assertEquals(2, twelve);
        assertEquals(8, sixteen);
        assertEquals(List.of("body_blue_shadow", "body_blue_dark", "body_blue", "black", "rubber", "metal", "metal_hi",
                "gauge", "glass", "gauge_dark", "needle", "wood", "wood_hi", "gold"), m.materials());
        assertEquals(12, m.part(Selector.material("needle")).faceCount());
        assertEquals(120, m.part(Selector.material("gauge")).faceCount());
        Region b = m.bounds();
        assertEquals(new Vec(-22, 2, -41), b.min());
        assertEquals(new Vec(22, 32, 46), b.max());
        assertEquals(820, m.quads().size(), "754 quads + 2 x 5 + 8 x 7");

        Mesh w = Obj.parse(Fixtures.text("trailblazer_wheel.obj")).mesh();
        assertEquals(146, w.faceCount());
        assertEquals(216, w.positions().size());
        assertEquals(List.of("rubber", "rubber_hi", "metal", "metal_hi", "black"), w.materials());
        assertEquals(new Vec(-4, -12, -12), w.bounds().min());
        assertEquals(178, w.quads().size());
    }

    @Test
    void theTrailerAsShippedHasNoTextureCoordinatesAndIsRefused() {
        // The art pipeline gives it uvs; the protocol never draws an untextured face.
        ObjFormatException e = assertThrows(ObjFormatException.class, () -> Obj.parse(Fixtures.text("trailblazer_animal_trailer.obj")));
        assertTrue(e.getMessage().contains("texture coordinate"), e.getMessage());
    }
}
