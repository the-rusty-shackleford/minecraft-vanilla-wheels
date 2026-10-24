/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.garage;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.domain.Sensing;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Vehicle Sensor (D-0035): a block that gives full redstone power on every side, strongly, while
 * a vehicle is within its range, so it can lie buried beside a door and open it for a car coming from
 * either side. Nothing but a vehicle turns it on (see {@link VehicleSensorBlockEntity}). Clicked with
 * an empty hand its range steps up the ladder, clicked sneaking it steps down; the range is shown over
 * the hotbar. Clicked with anything in hand, it is any other block: a road can be laid against it.
 *
 * <p>AF: RANGE is the range in blocks, POWERED whether it is on. RI: Sensing.MIN_RANGE <= RANGE <=
 * Sensing.MAX_RANGE; POWERED changes only through {@link #power}, which tells the neighbours.
 */
public final class VehicleSensorBlock extends Block implements EntityBlock {
    public static final MapCodec<VehicleSensorBlock> CODEC = simpleCodec(VehicleSensorBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty RANGE = IntegerProperty.create("range", Sensing.MIN_RANGE, Sensing.MAX_RANGE);

    /** requires: block properties; effects: creates the sensor, off, at the default range; throws: registry errors. */
    public VehicleSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false).setValue(RANGE, Sensing.DEFAULT_RANGE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, RANGE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VehicleSensorBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModContent.VEHICLE_SENSOR_BE.get()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<VehicleSensorBlockEntity>) VehicleSensorBlockEntity::serverTick;
        return ticker;
    }

    /** requires: server side, a sensor at {@code pos}; effects: turns it on or off and tells its
     * neighbours, and theirs, since it powers them strongly */
    static void power(Level level, BlockPos pos, BlockState state, boolean on) {
        level.setBlock(pos, state.setValue(POWERED, on), Block.UPDATE_ALL);
        changed(level, pos);
    }

    /** effects: only an empty hand sets the range; anything held is used as against any block */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** effects: steps the range up the ladder, or down when sneaking, and shows it over the hotbar */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean down = player.isSecondaryUseActive();
            int range = Sensing.step(state.getValue(RANGE), down);
            level.setBlock(pos, state.setValue(RANGE, range), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3f, down ? 0.5f : 0.55f);
            player.displayClientMessage(Component.translatable("block.vanillawheels.vehicle_sensor.range", range), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** effects: a powered sensor broken or replaced lets go of what it was powering */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        super.onRemove(state, level, pos, replacement, moved);
        if (!moved && !replacement.is(this) && state.getValue(POWERED)) {
            changed(level, pos);
        }
    }

    /** effects: tells its neighbours, and theirs, that its power changed: it powers them strongly */
    private static void changed(Level level, BlockPos pos) {
        Block self = ModContent.VEHICLE_SENSOR.get();
        level.updateNeighborsAt(pos, self);
        for (Direction d : Direction.values()) {
            level.updateNeighborsAt(pos.relative(d), self);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }
}
