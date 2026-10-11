/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.client.garage;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.garage.FloorHatchBlock;
import com.chunkworks.vanillawheels.garage.FloorHatchBlockEntity;
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
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a Floor Hatch (D-0037) every frame from its housing: the housing as the block it wears (its
 * own model draws it otherwise), the plate as that block cell by cell, drawn back under the housing by
 * its travel, and the guard rail, iron bars along the housing's open edge, risen out of the housing.
 * A shut plate is drawn as a block would be, its hidden faces culled; a moving one a hair thinner and
 * narrower, so where it slides under the floor it never shows through the floor's faces. What has
 * gone back under the housing is inside the floor and hidden by it.
 */
public final class FloorHatchRenderer implements BlockEntityRenderer<FloorHatchBlockEntity> {
    /** A moving plate's shrink: what keeps its faces off the faces of the floor it slides under. */
    private static final float THIN = 0.996f, NARROW = 0.998f;
    /** The rail: iron bars joined along the housing's row (across its facing). */
    private static final BlockState BARS_ALONG_X = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
    private static final BlockState BARS_ALONG_Z = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
    private final BlockRenderDispatcher blocks;
    private final RandomSource random = RandomSource.create();
    private final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();

    /** requires: renderer context; effects: creates the renderer; throws: none. */
    public FloorHatchRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(FloorHatchBlockEntity hatch, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = hatch.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = hatch.getBlockPos();
        Direction facing = hatch.facing();
        BlockState look = hatch.look() != null ? hatch.look()
                : ModContent.FLOOR_HATCH.get().defaultBlockState().setValue(FloorHatchBlock.FACING, facing);
        if (hatch.look() != null) {
            draw(level, pos, look, pose, buffers, true);
        }
        float back = hatch.drawnBack(partial);
        boolean resting = back == 0 && hatch.plated();
        for (int i = 1; i <= hatch.depth(); i++) {
            // Where this row of the plate is now, counted from the housing: at or behind it, it is under the floor.
            if (i - back <= 0) {
                continue;
            }
            BlockPos at = pos.relative(facing, i);
            pose.pushPose();
            pose.translate((i - back) * facing.getStepX(), 0, (i - back) * facing.getStepZ());
            if (!resting) {
                pose.translate(0.5, 0.5, 0.5);
                pose.scale(NARROW, THIN, NARROW);
                pose.translate(-0.5, -0.5, -0.5);
            }
            draw(level, at, look, pose, buffers, resting);
            pose.popPose();
        }
        float risen = hatch.risen(partial);
        if (risen > 0) {
            BlockState bars = facing.getAxis() == Direction.Axis.Z ? BARS_ALONG_X : BARS_ALONG_Z;
            pose.pushPose();
            // The bars' pane is the middle eighth of their cell: moved half a cell less a sixteenth toward
            // the opening, it stands on the housing's open edge, risen out of the housing.
            pose.translate(facing.getStepX() * 7 / 16.0, risen, facing.getStepZ() * 7 / 16.0);
            draw(level, pos.above(), bars, pose, buffers, false);
            pose.popPose();
        }
    }

    /** effects: draws {@code look}'s model at the pose, lit and shaded by the world at {@code at}; with
     * {@code cull}, without the faces a neighbouring block hides, as the world draws a block */
    private void draw(Level level, BlockPos at, BlockState look, PoseStack pose, MultiBufferSource buffers, boolean cull) {
        BakedModel model = blocks.getBlockModel(look);
        long seed = look.getSeed(at);
        random.setSeed(seed);
        for (RenderType type : model.getRenderTypes(look, random, ModelData.EMPTY)) {
            random.setSeed(seed);
            blocks.getModelRenderer().tesselateBlock(level, model, look, cell.set(at), pose,
                    buffers.getBuffer(RenderTypeHelper.getMovingBlockRenderType(type)), cull, random, seed,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
        }
    }

    @Override
    public AABB getRenderBoundingBox(FloorHatchBlockEntity hatch) {
        return hatch.renderBounds();
    }

    @Override
    public boolean shouldRenderOffScreen(FloorHatchBlockEntity hatch) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
