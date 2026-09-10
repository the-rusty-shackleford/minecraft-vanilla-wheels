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

import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Partitions. A project with a cube in a folder, using the second
 * texture on one face and the first on the rest: six faces, the
 * material per face by texture name, the group as the folder path, UVs
 * as fractions of the resolution with rows counted from the bottom, the
 * north face's texture running to the viewer's right, every normal out
 * of the cube. A rotated cube: its corners turn about the origin.
 * An invisible cube: skipped. A mesh element: a face per polygon with a
 * UV per corner. The embedded texture: decoded bytes and size. Not JSON,
 * and JSON without elements: refused.
 */
final class BbModelTest {
    private static final String PNG = Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});

    private static final String PROJECT = """
        {"meta": {"format_version": "4.10", "model_format": "free"}, "resolution": {"width": 64, "height": 32},
         "elements": [
           {"name": "shell", "type": "cube", "uuid": "a", "from": [0, 0, 0], "to": [4, 2, 6], "origin": [0, 0, 0], "rotation": [0, 0, 0],
            "faces": {"north": {"uv": [0, 0, 4, 2], "texture": 0}, "south": {"uv": [4, 0, 8, 2], "texture": 1}, "east": {"uv": [0, 2, 6, 4], "texture": 0},
                      "west": {"uv": [0, 2, 6, 4], "texture": 0}, "up": {"uv": [8, 0, 12, 6], "texture": 0}, "down": {"uv": [8, 0, 12, 6], "texture": 0}}},
           {"name": "post", "type": "cube", "uuid": "b", "from": [0, 0, 0], "to": [1, 10, 1], "origin": [0, 0, 0], "rotation": [0, 0, 90],
            "faces": {"up": {"uv": [0, 0, 1, 1], "texture": 0}}},
           {"name": "ghost", "type": "cube", "uuid": "c", "visibility": false, "from": [0, 0, 0], "to": [1, 1, 1],
            "faces": {"up": {"uv": [0, 0, 1, 1], "texture": 0}}},
           {"name": "wedge", "type": "mesh", "uuid": "d", "origin": [10, 0, 0], "rotation": [0, 0, 0],
            "vertices": {"p": [0, 0, 0], "q": [2, 0, 0], "r": [2, 2, 0]},
            "faces": {"f": {"vertices": ["p", "q", "r"], "uv": {"p": [0, 0], "q": [2, 0], "r": [2, 2]}, "texture": 1}}}
         ],
         "outliner": [{"name": "body", "uuid": "g", "children": ["a", {"name": "cage", "uuid": "h", "children": ["b"]}]}, "c", "d"],
         "textures": [{"name": "truck.png", "width": 64, "height": 32, "source": "data:image/png;base64,%s"},
                      {"name": "glass.png", "width": 8, "height": 8}]}
        """.formatted(PNG);

    @Test
    void cubesBecomeSixFacesWithMaterialsGroupsAndUvsAsFractions() {
        BbModel.Parsed p = BbModel.parse(PROJECT);
        Mesh m = p.mesh();
        List<Face> shell = m.faces().stream().filter(f -> f.group().equals("body/shell")).toList();
        assertEquals(6, shell.size(), "a cube is six faces");
        assertEquals(5, shell.stream().filter(f -> f.material().equals("truck")).count(), "five faces on the first texture");
        assertEquals(1, shell.stream().filter(f -> f.material().equals("glass")).count(), "one on the second, by its name without the extension");
        Face north = shell.stream().filter(f -> m.positions().get(f.corners().get(0).position()).z() == 0.0 && f.material().equals("truck")
                && f.corners().stream().allMatch(c -> m.positions().get(c.position()).z() == 0.0)).findFirst().orElseThrow();
        // Seen from the north, the viewer's left is +X: the first (top-left) corner sits at x = 4, and its uv is the rect's left edge;
        // the corners then run down that edge and back along the bottom, counter-clockwise from outside.
        assertEquals(4.0, m.positions().get(north.corners().get(0).position()).x());
        assertEquals(0.0, m.uvs().get(north.corners().get(0).uv()).u());
        assertEquals(0.0, m.positions().get(north.corners().get(1).position()).y(), "second corner: the bottom of the left edge");
        assertEquals(4.0 / 64, m.uvs().get(north.corners().get(2).uv()).u(), 1e-9, "uv as a fraction of the resolution");
        assertEquals(1.0 - 2.0 / 32, m.uvs().get(north.corners().get(2).uv()).v(), 1e-9, "rows from the bottom, as a mesh counts them");
        assertEquals(new Vec(0, 0, -1), north.normal(), "the north face looks north");
        Face up = shell.stream().filter(f -> f.corners().stream().allMatch(c -> m.positions().get(c.position()).y() == 2.0)).findFirst().orElseThrow();
        assertEquals(new Vec(0, 1, 0), up.normal(), "the top looks up, whatever the winding Blockbench's texture order implies");
        assertTrue(m.faces().stream().noneMatch(f -> f.group().contains("ghost")), "an invisible cube is left out");
        Selector cage = new Selector(java.util.Set.of(), java.util.Set.of("cage"), Region.ALL);
        assertEquals(1, m.faces().stream().filter(f -> cage.matches(f, Vec.ZERO)).count(), "a folder on the path selects what is in it");
    }

    @Test
    void aRotatedCubeTurnsAboutItsOrigin() {
        Mesh m = BbModel.parse(PROJECT).mesh();
        Face top = m.faces().stream().filter(f -> f.group().equals("body/cage/post")).findFirst().orElseThrow();
        // The post's top face, ten up, turned 90 degrees about z: it lies ten to the -x side.
        for (Corner c : top.corners()) {
            Vec v = m.positions().get(c.position());
            assertEquals(-10.0, v.x(), 1e-9, "x after the turn: " + v);
            assertTrue(v.y() >= -1e-9 && v.y() <= 1.0 + 1e-9, "within the post's width: " + v);
        }
        assertEquals(-1.0, top.normal().x(), 1e-9, "its normal turned with it, and still points out of the post");
        assertEquals(0.0, top.normal().y(), 1e-9);
    }

    @Test
    void meshElementsAndTheEmbeddedTexture() {
        BbModel.Parsed p = BbModel.parse(PROJECT);
        Face wedge = p.mesh().faces().stream().filter(f -> f.group().equals("wedge")).findFirst().orElseThrow();
        assertEquals(3, wedge.corners().size());
        assertEquals("glass", wedge.material());
        assertEquals(12.0, p.mesh().positions().get(wedge.corners().get(1).position()).x(), 1e-9, "offset by the element's origin");
        assertEquals(2.0 / 64, p.mesh().uvs().get(wedge.corners().get(1).uv()).u(), 1e-9);
        assertEquals(1.0, p.mesh().uvs().get(wedge.corners().get(1).uv()).v(), 1e-9, "a row of zero is the top");
        assertTrue(p.texture().isPresent(), "the first texture's image");
        assertEquals(8, p.texture().get().length);
        assertEquals(64, p.textureWidth());
        assertEquals(32, p.textureHeight());
    }

    @Test
    void refusesWhatIsNotAProject() {
        assertThrows(IllegalArgumentException.class, () -> BbModel.parse("not json"));
        assertThrows(IllegalArgumentException.class, () -> BbModel.parse("{\"elements\": []}"));
    }
}
