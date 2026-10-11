/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.garage;

import com.chunkworks.vanillawheels.domain.Hatch;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One block of a shut Floor Hatch's plate (D-0037): a whole solid block its housing lays in the
 * opening and takes away again, drawn by the housing (as the block it wears) and never by itself. It
 * cannot be mined, drops nothing and has no item: the hatch is taken up by breaking its housing, which
 * takes the plate with it. A plate whose housing is gone removes itself.
 *
 * <p>AF: its housing stands DISTANCE blocks from it toward FACING. RI: 1 <= DISTANCE <= Hatch.MAX_DEPTH.
 */
public final class HatchPlateBlock extends Block {
    public static final MapCodec<HatchPlateBlock> CODEC = simpleCodec(HatchPlateBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty DISTANCE = IntegerProperty.create("distance", 1, Hatch.MAX_DEPTH);

    /** requires: block properties; effects: creates the plate type; throws: registry errors. */
    public HatchPlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DISTANCE, 1));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DISTANCE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** effects: returns the housing position this plate block belongs to */
    public static BlockPos housing(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING), state.getValue(DISTANCE));
    }

    /** effects: returns whether the housing this plate block names stands there, facing it */
    static boolean housed(BlockGetter level, BlockState state, BlockPos pos) {
        BlockState housing = level.getBlockState(housing(state, pos));
        return housing.getBlock() instanceof FloorHatchBlock && housing.getValue(FloorHatchBlock.FACING) == state.getValue(FACING).getOpposite();
    }

    /** effects: a plate block left without its housing (a command, a copied build) goes on the next tick */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moved) {
        if (!level.isClientSide && !housed(level, state, pos)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide && !housed(level, state, pos)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!housed(level, state, pos)) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
