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
package com.chunkworks.vanillawheels.client;

import com.chunkworks.vanillawheels.domain.BakedMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Puts a baked mesh into a vertex consumer: four vertices per quad, each
 * with the pose's position, the colour, the texture coordinate, the
 * overlay, the light, and the quad's normal turned by the pose. Lamps are
 * given a world-up normal so the entity shader's directional term
 * saturates and they read as lit.
 */
public final class MeshDrawer {
    private MeshDrawer() {}

    public static final int WHITE = 0xFFFFFFFF;

    /** How a part's normals are handed to the shader. */
    public enum Shading { LIT, LAMP }

    /** effects: emits every quad of {@code mesh} through {@code out} under {@code pose} */
    public static void draw(BakedMesh mesh, PoseStack.Pose pose, VertexConsumer out, int argb, int light, int overlay, Shading shading) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        Vector3f p = new Vector3f();
        Vector3f nv = new Vector3f();
        int quads = mesh.quadCount();
        for (int i = 0; i < quads; i++) {
            if (shading == Shading.LAMP) {
                nv.set(0.0f, 1.0f, 0.0f);
            } else {
                nv.set(mesh.normal(i, 0), mesh.normal(i, 1), mesh.normal(i, 2));
                n.transform(nv);
            }
            for (int k = 0; k < 4; k++) {
                p.set(mesh.position(i, k, 0), mesh.position(i, k, 1), mesh.position(i, k, 2));
                m.transformPosition(p);
                out.addVertex(p.x(), p.y(), p.z(), argb, mesh.uv(i, k, 0), mesh.uv(i, k, 1), overlay, light, nv.x(), nv.y(), nv.z());
            }
        }
    }
}
