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
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Every cell of a Mechanic Lift but the controller: a deck cell is a full
 * cube to stand a vehicle on, a post cell a slim column. The state carries
 * the lift's facing and the cell's index, which is enough to find the
 * controller with no level lookup, so every hook here forwards there.
 */
public final class LiftPartBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<LiftPartBlock> CODEC = simpleCodec(LiftPartBlock::new);
    public static final IntegerProperty INDEX = IntegerProperty.create("index", 1, Footprint.PARTS);
    private static final VoxelShape POST = Block.box(5, 0, 5, 11, 16, 11);

    public LiftPartBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(INDEX, 1));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INDEX);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** effects: returns the controller's position for the part in {@code state} at {@code pos} */
    public static BlockPos controller(BlockState state, BlockPos pos) {
        int[] o = Footprint.toController(state.getValue(INDEX), LiftControllerBlock.heading(state.getValue(FACING)));
        return pos.offset(o[0], o[1], o[2]);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Footprint.cell(state.getValue(INDEX)).isDeck() ? net.minecraft.world.phys.shapes.Shapes.block() : POST;
    }

    /** effects: with this part gone for good, takes the controller with it, which takes the rest */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockPos c = controller(state, pos);
            if (level.getBlockState(c).is(ModContent.LIFT_CONTROLLER.get())) {
                level.removeBlock(c, false);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos c = controller(state, pos);
        if (level.getBlockState(c).is(ModContent.LIFT_CONTROLLER.get())) {
            LiftControllerBlock.dropLift(level, c, player);
            level.removeBlock(c, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion, java.util.function.BiConsumer<ItemStack, BlockPos> dropConsumer) {
        BlockPos c = controller(state, pos);
        if (!level.isClientSide() && level.getBlockState(c).is(ModContent.LIFT_CONTROLLER.get())) {
            dropConsumer.accept(new ItemStack(ModContent.LIFT_ITEM.get()), c);
            level.removeBlock(c, false);
        }
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(ModContent.LIFT_ITEM.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return LiftControllerBlock.open(level, controller(state, pos), player);
    }
}
