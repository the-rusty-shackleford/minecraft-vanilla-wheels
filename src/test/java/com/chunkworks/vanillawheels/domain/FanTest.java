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

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A polygon with more than four corners is fanned into quads that tile it:
 * a 16-gon gives seven quads whose corners walk the perimeter in order,
 * each quad convex and planar, their areas summing to the polygon's, and
 * the baked copy keeps every corner where the mesh had it.
 */
final class FanTest {

    @Test
    void aSixteenGonFansIntoSevenTilingQuads() {
        StringBuilder obj = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            double a = 2 * Math.PI * i / 16;
            obj.append(String.format("v %.5f %.5f 0\n", Math.cos(a), Math.sin(a)));
        }
        obj.append("vt 0 0\nusemtl m\nf");
        for (int i = 1; i <= 16; i++) {
            obj.append(" ").append(i).append("/1");
        }
        obj.append("\n");
        Mesh mesh = Obj.parse(obj.toString()).mesh();
        List<Mesh.Quad> quads = mesh.quads();
        assertEquals(7, quads.size());
        double area = 0;
        for (Mesh.Quad q : quads) {
            Vec a = mesh.positions().get(q.a().position());
            Vec b = mesh.positions().get(q.b().position());
            Vec c = mesh.positions().get(q.c().position());
            Vec d = mesh.positions().get(q.d().position());
            double s1 = b.minus(a).cross(c.minus(a)).z();
            double s2 = c.minus(a).cross(d.minus(a)).z();
            assertTrue(s1 > 0 && s2 >= 0, "both halves turn the same way: " + s1 + " " + s2);
            area += (s1 + s2) / 2;
        }
        assertEquals(16 * Math.sin(Math.PI / 16) * Math.cos(Math.PI / 16), area, 1e-4, "the quads tile the polygon");
        BakedMesh baked = BakedMesh.of(mesh, 1.0);
        assertEquals(7, baked.quadCount());
        for (int i = 0; i < 7; i++) {
            for (int k = 0; k < 4; k++) {
                double r = Math.hypot(baked.position(i, k, 0), baked.position(i, k, 1));
                assertEquals(1.0, r, 1e-4, "every baked corner is on the rim");
            }
        }
    }
}
