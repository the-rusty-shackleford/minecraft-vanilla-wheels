/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.garage;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.domain.Hatch;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A Floor Hatch's housing (D-0037): a block in a floor that slides a plate over the opening in front of
 * it, {@link FloorHatchBlockEntity#depth()} blocks deep, and draws it back under itself (see
 * {@link Hatch} for when). Laid in a row, its housings work as one: powering any opens all. FACING
 * points from the housing into the opening (the way its placer looked). It wears a solid block given to
 * it (right-click with one) and so does its plate, as the Hidden Door's panels do; an empty hand steps
 * the opening's depth, sneaking down. Its guard rail stands on its open edge, solid, while the hole is
 * open and empty.
 *
 * <p>AF: FACING the opening's direction; LOOKED whether its block entity draws it (as the block it
 * wears) in place of its own model. RI: LOOKED == (the block entity's look != null).
 */
public final class FloorHatchBlock extends BaseEntityBlock {
    public static final MapCodec<FloorHatchBlock> CODEC = simpleCodec(FloorHatchBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LOOKED = BooleanProperty.create("looked");
    /** The guard rail's thickness, on the housing's open edge. */
    private static final double RAIL = 2 / 16.0;
    /** Collision by facing (2D data value) and guard progress: the block, and the rail as high as it has risen. */
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][Hatch.GUARD_TICKS + 1];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int g = 0; g <= Hatch.GUARD_TICKS; g++) {
                double top = 1 + g / (double) Hatch.GUARD_TICKS;
                VoxelShape rail = g == 0 ? Shapes.empty() : switch (facing) {
                    case NORTH -> Shapes.box(0, 1, 0, 1, top, RAIL);
                    case SOUTH -> Shapes.box(0, 1, 1 - RAIL, 1, top, 1);
                    case WEST -> Shapes.box(0, 1, 0, RAIL, top, 1);
                    default -> Shapes.box(1 - RAIL, 1, 0, 1, top, 1);
                };
                SHAPES[facing.get2DDataValue()][g] = Shapes.or(Shapes.block(), rail);
            }
        }
    }

    /** requires: block properties; effects: creates the housing type; throws: registry errors. */
    public FloorHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOOKED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOOKED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(LOOKED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    /** effects: the opening lies the way the placer looks */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloorHatchBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModContent.FLOOR_HATCH_BE.get(), FloorHatchBlockEntity::tick);
    }

    /** effects: the housing's collision is the whole block and its guard rail as far as it has risen */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int guard = level.getBlockEntity(pos) instanceof FloorHatchBlockEntity hatch ? hatch.guard() : 0;
        return SHAPES[state.getValue(FACING).get2DDataValue()][guard];
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /**
     * effects: with a block it can wear in hand (solid, whole, no block entity), draws the housing and
     * its plate as that block, using none of it; with anything else, as any block would
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(stack.getItem() instanceof BlockItem item) || item.getBlock() == this) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        BlockState look = item.getBlock().defaultBlockState();
        if (!HiddenDoorBlock.wearable(level, pos, look)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FloorHatchBlockEntity hatch) {
            hatch.setLook(look);
            level.playSound(null, pos, look.getSoundType(level, pos, player).getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.9f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** effects: an empty hand steps the opening's depth, sneaking down, and shows it over the hotbar */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FloorHatchBlockEntity hatch) {
            boolean down = player.isSecondaryUseActive();
            int depth = Hatch.step(hatch.depth(), down);
            hatch.setDepth(depth);
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3f, down ? 0.5f : 0.55f);
            player.displayClientMessage(Component.translatable("block.vanillawheels.floor_hatch.depth", depth), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** effects: a housing broken or replaced takes its plate away with it */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!replacement.is(this) && !level.isClientSide && level.getBlockEntity(pos) instanceof FloorHatchBlockEntity hatch) {
            hatch.removePlate();
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
