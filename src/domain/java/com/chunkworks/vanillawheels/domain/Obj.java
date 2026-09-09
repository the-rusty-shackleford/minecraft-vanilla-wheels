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

/**
 * Reads Wavefront OBJ text into a {@link Mesh}: {@code v}, {@code vt},
 * {@code f} with every corner form ({@code v}, {@code v/vt}, {@code v/vt/vn},
 * {@code v//vn}), 1-based and negative (relative) indices, {@code usemtl}
 * and {@code g} as the labels the faces carry. Normals in the file are
 * ignored -- the mesh computes its own -- and so are {@code mtllib},
 * {@code o}, {@code s}, {@code l} and comments; an unknown keyword is a
 * warning. Every face must have texture coordinates: this mod draws
 * nothing untextured.
 */
public final class Obj {
    private Obj() {}

    /** A parsed file: the mesh, and what was odd about the text. */
    public record Parsed(Mesh mesh, List<String> warnings) {}

    /**
     * effects: returns the mesh {@code text} describes and the warnings it
     * raised; faces are labelled with the {@code usemtl} and {@code g} in
     * force ({@code "default"} and {@code ""} before any); a face whose
     * corners span no area is dropped with a warning<br>
     * throws: {@link ObjFormatException} for a malformed {@code v},
     * {@code vt} or {@code f} line, an index that is zero or out of range,
     * or a face corner without a texture coordinate
     */
    public static Parsed parse(String text) {
        List<Vec> positions = new ArrayList<>();
        List<Uv> uvs = new ArrayList<>();
        List<Face> faces = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String material = "default";
        String group = "";
        int lineNo = 0;
        for (String raw : text.split("\\r?\\n")) {
            lineNo++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\\s+");
            switch (p[0]) {
                case "v" -> positions.add(new Vec(number(p, 1, lineNo), number(p, 2, lineNo), number(p, 3, lineNo)));
                case "vt" -> uvs.add(new Uv(number(p, 1, lineNo), number(p, 2, lineNo)));
                case "vn", "vp", "mtllib", "o", "s", "l" -> { }
                case "usemtl" -> material = p.length > 1 ? p[1] : "default";
                case "g" -> group = p.length > 1 ? p[1] : "";
                case "f" -> {
                    if (p.length < 4) {
                        throw new ObjFormatException(lineNo, "a face needs at least three corners");
                    }
                    List<Corner> corners = new ArrayList<>(p.length - 1);
                    for (int i = 1; i < p.length; i++) {
                        corners.add(corner(p[i], positions.size(), uvs.size(), lineNo));
                    }
                    try {
                        Vec normal = Mesh.newell(positions, corners);
                        faces.add(new Face(material, group, corners, normal));
                    } catch (IllegalArgumentException degenerate) {
                        warnings.add("line " + lineNo + ": a face with no area was dropped");
                    }
                }
                default -> warnings.add("line " + lineNo + ": unknown keyword '" + p[0] + "' ignored");
            }
        }
        return new Parsed(Mesh.of(positions, uvs, faces), List.copyOf(warnings));
    }

    private static double number(String[] p, int i, int line) {
        if (i >= p.length) {
            throw new ObjFormatException(line, "too few numbers");
        }
        try {
            double d = Double.parseDouble(p[i]);
            if (!Double.isFinite(d)) {
                throw new ObjFormatException(line, "not a finite number: " + p[i]);
            }
            return d;
        } catch (NumberFormatException e) {
            throw new ObjFormatException(line, "not a number: " + p[i]);
        }
    }

    private static Corner corner(String token, int positions, int uvs, int line) {
        String[] parts = token.split("/", -1);
        int position = index(parts[0], positions, line, "vertex");
        if (parts.length < 2 || parts[1].isEmpty()) {
            throw new ObjFormatException(line, "a corner without a texture coordinate: " + token);
        }
        int uv = index(parts[1], uvs, line, "texture coordinate");
        return new Corner(position, uv);
    }

    private static int index(String s, int count, int line, String what) {
        int i;
        try {
            i = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new ObjFormatException(line, "not an index: " + s);
        }
        if (i == 0) {
            throw new ObjFormatException(line, what + " index 0: OBJ indices start at 1");
        }
        int resolved = i > 0 ? i - 1 : count + i;
        if (resolved < 0 || resolved >= count) {
            throw new ObjFormatException(line, what + " index " + i + " is out of range (" + count + " so far)");
        }
        return resolved;
    }
}
