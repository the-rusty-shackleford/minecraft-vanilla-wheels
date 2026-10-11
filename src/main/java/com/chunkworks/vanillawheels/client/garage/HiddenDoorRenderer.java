/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.client.garage;

import com.chunkworks.vanillawheels.garage.GarageDoorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a Hidden Door (D-0036) from its root, every frame: each panel as the block it wears (stone
 * if none), sunk by the door's drop, lit by the world at its own cell the way the game lights a
 * falling block, so a shut door shades as the rock beside it. The part of a panel below the sill is
 * inside the ground and hidden by it. Nothing is ever swapped for real blocks, so there is no seam
 * at the start or the end of a move. A fully open door draws nothing.
 */
public final class HiddenDoorRenderer implements BlockEntityRenderer<GarageDoorBlockEntity> {
    private static final BlockState DEFAULT_LOOK = Blocks.STONE.defaultBlockState();
    private final BlockRenderDispatcher blocks;
    private final RandomSource random = RandomSource.create();
    private final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();

    /** requires: renderer context; effects: creates the renderer; throws: none. */
    public HiddenDoorRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(GarageDoorBlockEntity door, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = door.getLevel();
        if (!door.renders() || level == null) {
            return;
        }
        if (!door.formed()) {
            // A panel not yet part of a whole rectangle stands as itself, where it is.
            draw(level, door.getBlockPos(), look(level, door.getBlockPos()), pose, buffers);
            return;
        }
        float drop = door.clearance(partial);
        if (drop >= door.height()) {
            return;
        }
        BlockPos origin = door.origin();
        boolean alongX = door.axis() == Direction.Axis.X;
        for (int across = 0; across < door.width(); across++) {
            for (int up = 0; up < door.height(); up++) {
                // A row wholly below the sill is inside the ground.
                if (up + 1 - drop <= 0) {
                    continue;
                }
                BlockPos at = alongX ? origin.offset(across, up, 0) : origin.offset(0, up, across);
                pose.pushPose();
                pose.translate(at.getX() - origin.getX(), at.getY() - origin.getY() - drop, at.getZ() - origin.getZ());
                draw(level, at, look(level, at), pose, buffers);
                pose.popPose();
            }
        }
    }

    /** effects: returns the block the panel at {@code at} wears, or stone */
    private BlockState look(Level level, BlockPos at) {
        return level.getBlockEntity(at) instanceof GarageDoorBlockEntity panel && panel.look() != null ? panel.look() : DEFAULT_LOOK;
    }

    /** effects: draws {@code look}'s model at the pose, lit and shaded by the world at {@code at} */
    private void draw(Level level, BlockPos at, BlockState look, PoseStack pose, MultiBufferSource buffers) {
        BakedModel model = blocks.getBlockModel(look);
        long seed = look.getSeed(at);
        random.setSeed(seed);
        for (RenderType type : model.getRenderTypes(look, random, ModelData.EMPTY)) {
            random.setSeed(seed);
            blocks.getModelRenderer().tesselateBlock(level, model, look, cell.set(at), pose,
                    buffers.getBuffer(RenderTypeHelper.getMovingBlockRenderType(type)), false, random, seed,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
        }
    }

    @Override
    public AABB getRenderBoundingBox(GarageDoorBlockEntity door) {
        return door.renderBounds();
    }

    @Override
    public boolean shouldRenderOffScreen(GarageDoorBlockEntity door) {
        return door.renders();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
