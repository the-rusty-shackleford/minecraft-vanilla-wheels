/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.garage;

import com.chunkworks.vanillawheels.ModContent;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** One recoverable shutter panel. AF: adjacent panels in the same plane form a
 * rectangular redstone door. RI: axis is horizontal; only the elected root ticks.
 * Shape, collision and rendering use the same server-owned travel. */
public final class GarageDoorBlock extends BaseEntityBlock {
    public static final MapCodec<GarageDoorBlock> CODEC = simpleCodec(GarageDoorBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty ROOT = BooleanProperty.create("root");

    /** requires: block properties; effects: creates panel type; throws: registry errors. */
    public GarageDoorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(ROOT, false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(AXIS, ROOT); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GarageDoorBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return state.getValue(ROOT)
                ? createTickerHelper(type, ModContent.GARAGE_DOOR_BE.get(), GarageDoorBlockEntity::tick) : null;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var against = level.getBlockState(pos.relative(context.getClickedFace().getOpposite()));
        var axis = against.is(this) ? against.getValue(AXIS)
                : context.getHorizontalDirection().getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
        if (!GarageDoorAssembly.canAdd(level, pos, axis)) return null;
        return defaultBlockState().setValue(AXIS, axis);
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && (!old.is(this) || old.getValue(AXIS) != state.getValue(AXIS))) {
            GarageDoorAssembly.schedule(level, pos, state.getValue(AXIS));
            if (old.is(this)) GarageDoorAssembly.schedule(level, pos, old.getValue(AXIS));
        }
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!replacement.is(this) && !level.isClientSide) GarageDoorAssembly.schedule(level, pos, state.getValue(AXIS));
        super.onRemove(state, level, pos, replacement, moved);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        GarageDoorAssembly.rebuild(level, pos);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean moved) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GarageDoorBlockEntity panel) {
            var root = panel.controller();
            if (root != null) root.readPower();
        }
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof GarageDoorBlockEntity panel) return panel.shape();
        return state.getValue(AXIS) == Direction.Axis.X ? Block.box(0,0,6,16,16,10) : Block.box(6,0,0,10,16,16);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GarageDoorBlockEntity panel)
            player.displayClientMessage(Component.translatable(panel.formed() ? "garage.vanillawheels.power" : "garage.vanillawheels.rectangle"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                ? state.cycle(AXIS) : state;
    }
}
