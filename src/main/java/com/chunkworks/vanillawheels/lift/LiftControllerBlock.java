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
package com.chunkworks.vanillawheels.lift;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.domain.Footprint;
import com.chunkworks.vanillawheels.domain.Footprint.Cell;
import com.chunkworks.vanillawheels.domain.Footprint.Heading;
import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The Mechanic Lift's front-centre block: the one with the block entity,
 * the menu and the renderer. Placing it lays the {@link Footprint}'s
 * other cells as {@link LiftPartBlock}s facing the same way; removing it,
 * by any means, removes them all; breaking it drops the lift item once.
 * Invisible, since the block entity renderer draws the whole lift.
 */
public final class LiftControllerBlock extends BaseEntityBlock {
    public static final MapCodec<LiftControllerBlock> CODEC = simpleCodec(LiftControllerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public LiftControllerBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** effects: returns the way a lift facing {@code d} looks, in the footprint's terms */
    public static Heading heading(Direction d) {
        return switch (d) {
            case EAST -> Heading.EAST;
            case SOUTH -> Heading.SOUTH;
            case WEST -> Heading.WEST;
            default -> Heading.NORTH;
        };
    }

    /** effects: returns the world position of {@code cell} for a lift whose controller is at {@code controller} facing {@code facing} */
    public static BlockPos at(BlockPos controller, Direction facing, Cell cell) {
        int[] o = heading(facing).offset(cell);
        return controller.offset(o[0], o[1], o[2]);
    }

    /**
     * effects: returns the state for a lift facing the player, or null --
     * so nothing is placed -- when a cell of the footprint is not free or
     * a deck cell has nothing solid under it. Entities in the way count
     * as not free, as they do for any block.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        return blocked(ctx.getLevel(), ctx.getClickedPos(), facing) == null ? defaultBlockState().setValue(FACING, facing) : null;
    }

    /**
     * effects: returns the first cell that keeps a lift with its controller
     * at {@code controller} facing {@code facing} from standing in
     * {@code level}, or null when it may
     */
    @Nullable
    public static Cell blocked(Level level, BlockPos controller, Direction facing) {
        return Footprint.firstBlocked(
                c -> {
                    BlockPos p = at(controller, facing, c);
                    return level.getBlockState(p).canBeReplaced() && level.getEntities(null, new AABB(p)).isEmpty();
                },
                c -> {
                    BlockPos below = at(controller, facing, c).below();
                    return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
                });
    }

    /** effects: lays every part of the footprint around the controller just placed */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        List<Cell> cells = Footprint.cells();
        for (int i = 1; i <= Footprint.PARTS; i++) {
            BlockState part = ModContent.LIFT_PART.get().defaultBlockState()
                    .setValue(LiftPartBlock.FACING, facing)
                    .setValue(LiftPartBlock.INDEX, i);
            level.setBlock(at(pos, facing, cells.get(i)), part, Block.UPDATE_ALL);
        }
    }

    /** effects: with the controller gone for good, takes every part that is still there with it */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            Direction facing = state.getValue(FACING);
            for (int i = 1; i <= Footprint.PARTS; i++) {
                BlockPos p = at(pos, facing, Footprint.cell(i));
                if (level.getBlockState(p).is(ModContent.LIFT_PART.get())) {
                    level.removeBlock(p, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        dropLift(level, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** effects: pops one lift item at {@code pos} unless {@code player} has infinite materials */
    static void dropLift(Level level, BlockPos pos, @Nullable Player player) {
        if (!level.isClientSide() && (player == null || !player.hasInfiniteMaterials())) {
            popResource(level, pos, new ItemStack(ModContent.LIFT_ITEM.get()));
        }
    }

    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion, java.util.function.BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (!level.isClientSide()) {
            dropConsumer.accept(new ItemStack(ModContent.LIFT_ITEM.get()), pos);
        }
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(ModContent.LIFT_ITEM.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return open(level, pos, player);
    }

    /** effects: opens the lift's menu at the controller at {@code pos} for {@code player} */
    static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof LiftBlockEntity lift && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.openMenu(lift);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LiftBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModContent.LIFT_BE.get(), (lvl, p, s, be) -> be.tick());
    }
}
