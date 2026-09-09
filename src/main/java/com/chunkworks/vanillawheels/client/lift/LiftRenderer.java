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
package com.chunkworks.vanillawheels.client.lift;

import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.domain.Footprint;
import com.chunkworks.vanillawheels.domain.Footprint.Cell;
import com.chunkworks.vanillawheels.domain.Footprint.Heading;
import com.chunkworks.vanillawheels.lift.LiftBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Draws a whole Mechanic Lift from its controller: a cube per deck cell and
 * a slim column per post cell on a steel-plate texture, a hazard band along
 * the deck's outer edge, the lot raised
 * by the block entity's {@code raise} while a job runs. The blocks
 * themselves are invisible; this is all anyone sees of the lift. Light is
 * sampled above the controller, since the controller sits inside a solid
 * cell of its own.
 */
public final class LiftRenderer implements BlockEntityRenderer<LiftBlockEntity> {
    private static final ResourceLocation TEXTURE = VanillaWheels.id("textures/block/mechanic_lift.png");
    private static final ResourceLocation DECK_TEXTURE = VanillaWheels.id("textures/block/mechanic_lift_deck.png");
    private static final ResourceLocation STRIPE_TEXTURE = VanillaWheels.id("textures/block/mechanic_lift_stripe.png");
    /** The hazard band's width across the deck's edge, blocks. */
    private static final float BAND = 2.0f / 16.0f;
    private static final float POST_MIN = 5.0f / 16.0f;
    private static final float POST_MAX = 11.0f / 16.0f;

    public LiftRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(LiftBlockEntity lift, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (lift.getLevel() == null) {
            return;
        }
        BlockPos pos = lift.getBlockPos();
        int light = LevelRenderer.getLightColor(lift.getLevel(), pos.above());
        Heading h = lift.heading();
        double raise = lift.raise(partialTick);
        pose.pushPose();
        pose.translate(0.0, raise, 0.0);
        // One render type at a time: a buffer source ends the current
        // buffer when another type is asked for, so the two textures are
        // two passes, not two interleaved buffers.
        VertexConsumer deck = buffers.getBuffer(RenderType.entityCutoutNoCull(DECK_TEXTURE));
        for (Cell c : Footprint.cells()) {
            if (c.isDeck()) {
                int[] o = h.offset(c);
                top(pose, deck, o[0], o[1], o[2], 0.0f, 1.0f, light);
            }
        }
        VertexConsumer plate = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (Cell c : Footprint.cells()) {
            int[] o = h.offset(c);
            if (c.isDeck()) {
                sides(pose, plate, o[0], o[1], o[2], 0.0f, 1.0f, light);
            } else {
                top(pose, plate, o[0], o[1], o[2], POST_MIN, POST_MAX, light);
                sides(pose, plate, o[0], o[1], o[2], POST_MIN, POST_MAX, light);
            }
        }
        // The hazard band: along every deck edge with no deck cell beyond it, a hair above the plate.
        VertexConsumer stripe = buffers.getBuffer(RenderType.entityCutoutNoCull(STRIPE_TEXTURE));
        Set<Long> deckCells = new HashSet<>();
        for (Cell c : Footprint.cells()) {
            if (c.isDeck()) {
                int[] o = h.offset(c);
                deckCells.add(key(o[0], o[2]));
            }
        }
        float y = 1.0f + 0.002f;
        for (Cell c : Footprint.cells()) {
            if (!c.isDeck()) {
                continue;
            }
            int[] o = h.offset(c);
            int x = o[0], z = o[2];
            if (!deckCells.contains(key(x, z - 1))) {
                quad(pose, stripe, x, y, z + BAND, x + 1, y, z + BAND, x + 1, y, z, x, y, z, 0, 1, 0, light);
            }
            if (!deckCells.contains(key(x, z + 1))) {
                quad(pose, stripe, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z + 1 - BAND, x, y, z + 1 - BAND, 0, 1, 0, light);
            }
            if (!deckCells.contains(key(x - 1, z))) {
                quad(pose, stripe, x, y, z + 1, x, y, z, x + BAND, y, z, x + BAND, y, z + 1, 0, 1, 0, light);
            }
            if (!deckCells.contains(key(x + 1, z))) {
                quad(pose, stripe, x + 1 - BAND, y, z + 1, x + 1 - BAND, y, z, x + 1, y, z, x + 1, y, z + 1, 0, 1, 0, light);
            }
        }
        pose.popPose();
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** The top face of a cube at cell (x, y, z) spanning {@code min}..{@code max} across. */
    private static void top(PoseStack pose, VertexConsumer out, int x, int y, int z, float min, float max, int light) {
        float x0 = x + min, x1 = x + max, z0 = z + min, z1 = z + max, y1 = y + 1;
        quad(pose, out, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, light);
    }

    /** The bottom and the four sides of a cube at cell (x, y, z) spanning {@code min}..{@code max} across, full height. */
    private static void sides(PoseStack pose, VertexConsumer out, int x, int y, int z, float min, float max, int light) {
        float x0 = x + min, x1 = x + max, z0 = z + min, z1 = z + max, y0 = y, y1 = y + 1;
        quad(pose, out, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, light);
        // north (-z), south (+z)
        quad(pose, out, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, -1, light);
        quad(pose, out, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0, 0, 1, light);
        // west (-x), east (+x)
        quad(pose, out, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1, 0, 0, light);
        quad(pose, out, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1, 0, 0, light);
    }

    private static void quad(PoseStack pose, VertexConsumer out,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vector3f normal = n.transform(new Vector3f(nx, ny, nz));
        vertex(out, m, ax, ay, az, 0, 0, normal, light);
        vertex(out, m, bx, by, bz, 1, 0, normal, light);
        vertex(out, m, cx, cy, cz, 1, 1, normal, light);
        vertex(out, m, dx, dy, dz, 0, 1, normal, light);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float z, float u, float v, Vector3f n, int light) {
        Vector4f p = m.transform(new Vector4f(x, y, z, 1.0f));
        out.addVertex(p.x(), p.y(), p.z(), 0xFFFFFFFF, u, v, OverlayTexture.NO_OVERLAY, light, n.x(), n.y(), n.z());
    }

    @Override
    public AABB getRenderBoundingBox(LiftBlockEntity lift) {
        int[][] b = Footprint.bounds(lift.heading());
        BlockPos p = lift.getBlockPos();
        return new AABB(p.getX() + b[0][0], p.getY() + b[0][1], p.getZ() + b[0][2],
                p.getX() + b[1][0] + 1.0, p.getY() + b[1][1] + 1.0 + 0.5, p.getZ() + b[1][2] + 1.0);
    }

    @Override
    public boolean shouldRenderOffScreen(LiftBlockEntity lift) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
