/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.garage;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.domain.Hatch;
import com.chunkworks.vanillawheels.domain.Shutter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A Floor Hatch housing's state and watch (D-0037; the rules are {@link Hatch}). A row of housings is
 * one hatch, driven by its first housing: every {@link Hatch#SCAN_PERIOD} ticks it looks at the row's
 * openings (empty, plated, filled), at what stands on the plate, below for anything not alive rising
 * toward it (a lift's car, a cart), and at the row's redstone; every tick it moves each housing's
 * guard and plate a step by that look.
 *
 * <p>AF: {@code travel} eighth-blocks the plate has drawn back under the housing (0: over the opening);
 * {@code plated} whether the plate's blocks stand in the opening; {@code guard} the rail's progress;
 * {@code look} the block the housing and plate are drawn as (null: the housing's own). The watch,
 * meaningful in a row's first housing:
 * {@code waiting} looks left to wait for what was seen rising, {@code lastTop} the highest top seen
 * below at the last look (NaN: nothing), {@code wanted}, {@code occupied}, {@code openingEmpty},
 * {@code layerClear}, {@code someoneOn}, {@code gap} what the last look made of it.
 * RI: 0 <= travel <= Hatch.travel(depth); plated implies travel == 0; 0 <= guard <= GUARD_TICKS;
 * 0 <= waiting <= FORGET; the block's LOOKED == (look != null).
 */
public final class FloorHatchBlockEntity extends BlockEntity {
    private static final EntityTypeTest<Entity, Entity> ANY = EntityTypeTest.forClass(Entity.class);
    /** What the hatch waits for and gives way to: anything not alive that is not an item, an orb or a shot. */
    private static final Predicate<Entity> THING = e -> !e.isSpectator() && !(e instanceof LivingEntity)
            && !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb) && !(e instanceof Projectile);
    private static final Predicate<Entity> SOMEONE = e -> !e.isSpectator() && e instanceof LivingEntity;
    private static final Predicate<Entity> IN_THE_WAY = e -> THING.test(e) || SOMEONE.test(e);

    private @Nullable BlockState look;
    private int depth = Hatch.DEFAULT_DEPTH;
    private int travel, previousTravel, guard, previousGuard;
    private boolean plated;
    private int waiting;
    private double lastTop = Double.NaN;
    // Nothing is laid or drawn back before the first look (a new or freshly loaded housing).
    private boolean wanted, occupied, openingEmpty, layerClear, someoneOn;
    private double gap = Double.POSITIVE_INFINITY;
    private final List<Entity> found = new ArrayList<>();
    private final List<FloorHatchBlockEntity> row = new ArrayList<>();
    private @Nullable AABB footprint;

    /** requires: a housing's position and state; effects: creates its state, shut without a plate yet */
    public FloorHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.FLOOR_HATCH_BE.get(), pos, state);
    }

    /** effects: returns the block the housing and its plate are drawn as, or null for the housing's own */
    public @Nullable BlockState look() {
        return look;
    }

    /** effects: returns the opening's depth in blocks */
    public int depth() {
        return depth;
    }

    /** effects: returns the guard rail's progress, 0 (down) to GUARD_TICKS (up) */
    public int guard() {
        return guard;
    }

    /** effects: returns the plate's travel in eighth-blocks, 0 over the opening */
    public int travel() {
        return travel;
    }

    /** effects: returns whether the plate's blocks stand in the opening */
    public boolean plated() {
        return plated;
    }

    /** requires: 0 <= partial <= 1; effects: returns how far the plate has drawn back, in blocks, between ticks */
    public float drawnBack(float partial) {
        return (previousTravel + (travel - previousTravel) * partial) / Shutter.UNITS;
    }

    /** requires: 0 <= partial <= 1; effects: returns how far the rail has risen, 0 to 1, between ticks */
    public float risen(float partial) {
        return (previousGuard + (guard - previousGuard) * partial) / Hatch.GUARD_TICKS;
    }

    /** effects: returns the direction from the housing into its opening */
    public Direction facing() {
        return getBlockState().getValue(FloorHatchBlock.FACING);
    }

    /** requires: server side; effects: draws the housing and its plate as {@code state}, for every viewer */
    public void setLook(BlockState state) {
        look = state;
        if (level != null && !getBlockState().getValue(FloorHatchBlock.LOOKED)) {
            level.setBlock(worldPosition, getBlockState().setValue(FloorHatchBlock.LOOKED, true), Block.UPDATE_CLIENTS);
        }
        changed();
    }

    /** requires: server side, depth in range; effects: makes the opening {@code depth} deep: a plate
     * standing is taken up, and laid again at the new depth when the hatch next shuts */
    public void setDepth(int depth) {
        Hatch.travel(depth);
        if (plated) {
            removePlate();
        }
        this.depth = depth;
        travel = previousTravel = Math.min(travel, Hatch.travel(depth));
        footprint = null;
        changed();
    }

    /** requires: server thread; effects: one tick. A row of housings side by side, facing the same
     * way, is one hatch, driven by its first housing (counterclockwise end, seen from the housing
     * looking into the opening): every SCAN_PERIOD ticks it looks for the whole row, and every tick it
     * moves each housing's guard and plate a step, so the row moves as one */
    public static void tick(Level level, BlockPos pos, BlockState state, FloorHatchBlockEntity hatch) {
        hatch.previousTravel = hatch.travel;
        hatch.previousGuard = hatch.guard;
        if (level.isClientSide) {
            return;
        }
        Direction facing = state.getValue(FloorHatchBlock.FACING);
        if (inRow(level, pos.relative(facing.getCounterClockWise()), facing)) {
            return;
        }
        List<FloorHatchBlockEntity> row = hatch.row;
        row.clear();
        BlockPos at = pos;
        for (int i = 0; i < Hatch.MAX_DEPTH && inRow(level, at, facing); i++, at = at.relative(facing.getClockWise())) {
            if (level.getBlockEntity(at) instanceof FloorHatchBlockEntity member) {
                row.add(member);
            }
        }
        if (level.getGameTime() % Hatch.SCAN_PERIOD == 0) {
            hatch.look(level, pos, facing, row);
        }
        for (FloorHatchBlockEntity member : row) {
            member.step(level, hatch);
        }
        row.clear();
    }

    /** effects: returns whether a housing facing {@code facing} stands at {@code at} */
    private static boolean inRow(Level level, BlockPos at, Direction facing) {
        BlockState there = level.getBlockState(at);
        return there.is(ModContent.FLOOR_HATCH.get()) && there.getValue(FloorHatchBlock.FACING) == facing;
    }

    /** effects: the row's watch: its openings, the plate's top and layer over all of them, below them,
     * and the row's redstone */
    private void look(Level level, BlockPos pos, Direction facing, List<FloorHatchBlockEntity> row) {
        occupied = false;
        openingEmpty = true;
        boolean powered = false;
        int deepest = Hatch.MIN_DEPTH;
        for (FloorHatchBlockEntity member : row) {
            boolean whole = true;
            for (int i = 1; i <= member.depth; i++) {
                BlockState there = level.getBlockState(member.worldPosition.relative(facing, i));
                if (member.isOurs(there, i)) {
                    continue;
                }
                if (!there.isAir()) {
                    occupied = true;
                    openingEmpty = false;
                }
                whole = false;
            }
            if (member.plated && !whole) {
                // A plate block was taken (a creative player, a command): take the rest, and lay it again when it may.
                member.removePlate();
            }
            powered |= level.hasNeighborSignal(member.worldPosition);
            deepest = Math.max(deepest, member.depth);
        }
        BlockPos far = row.getLast().worldPosition.relative(facing, deepest);
        BlockPos near = pos.relative(facing);
        double y = pos.getY();
        double minX = Math.min(near.getX(), far.getX()), maxX = Math.max(near.getX(), far.getX()) + 1;
        double minZ = Math.min(near.getZ(), far.getZ()), maxZ = Math.max(near.getZ(), far.getZ()) + 1;
        double top = Double.NaN;
        level.getEntities(ANY, new AABB(minX, y - Hatch.RANGE, minZ, maxX, y + 1, maxZ), THING, found, Integer.MAX_VALUE);
        for (Entity e : found) {
            top = Double.isNaN(top) ? e.getBoundingBox().maxY : Math.max(top, e.getBoundingBox().maxY);
        }
        found.clear();
        Hatch.Motion motion = Hatch.motion(lastTop, top);
        lastTop = top;
        waiting = Hatch.expect(waiting, motion, occupied);
        gap = Double.isNaN(top) ? Double.POSITIVE_INFINITY : Math.max(0, y - top);
        level.getEntities(ANY, new AABB(minX, y + 1, minZ, maxX, y + 1.25, maxZ), SOMEONE, found, 1);
        someoneOn = !found.isEmpty();
        found.clear();
        level.getEntities(ANY, new AABB(minX, y, minZ, maxX, y + 1, maxZ), IN_THE_WAY, found, 1);
        layerClear = found.isEmpty();
        found.clear();
        wanted = Hatch.wanted(powered, waiting > 0, occupied);
    }

    /** effects: a step of this housing's guard, then of its plate, by its row's watch: it opens once the
     * guard is up and the plate may go, and shuts over an empty opening, laying its blocks at the end */
    private void step(Level level, FloorHatchBlockEntity watch) {
        int wasTravel = travel, wasGuard = guard;
        boolean wasPlated = plated;
        guard = Hatch.guard(guard, Hatch.guardWanted(travel, watch.wanted, watch.occupied));
        if (watch.wanted) {
            if (plated) {
                if ((guard == Hatch.GUARD_TICKS || watch.occupied) && Hatch.mayOpen(watch.someoneOn, watch.gap)) {
                    removePlate();
                    travel = Hatch.advance(depth, travel, true);
                    level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.4f, 0.7f);
                }
            } else {
                travel = Hatch.advance(depth, travel, true);
            }
        } else if (!plated && Hatch.mayShut(watch.openingEmpty, watch.layerClear)) {
            if (travel == Hatch.travel(depth)) {
                level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4f, 0.7f);
            }
            travel = Hatch.advance(depth, travel, false);
            if (travel == 0 && !layPlate(level, worldPosition)) {
                // Something came into the opening in the last tick: stay drawn back and look again.
                travel = Hatch.STEP;
            }
        }
        if (travel != wasTravel || guard != wasGuard || plated != wasPlated) {
            changed();
        }
    }

    /** effects: lays the plate's blocks in the opening; returns false, laying none, if a cell is not empty */
    private boolean layPlate(Level level, BlockPos pos) {
        Direction facing = facing();
        for (int i = 1; i <= depth; i++) {
            BlockState there = level.getBlockState(pos.relative(facing, i));
            if (!there.isAir() && !isOurs(there, i)) {
                return false;
            }
        }
        BlockState plate = ModContent.HATCH_PLATE.get().defaultBlockState().setValue(HatchPlateBlock.FACING, facing.getOpposite());
        for (int i = 1; i <= depth; i++) {
            level.setBlock(pos.relative(facing, i), plate.setValue(HatchPlateBlock.DISTANCE, i), Block.UPDATE_ALL);
        }
        plated = true;
        return true;
    }

    /** requires: server side; effects: takes the plate's blocks out of the opening (only this housing's) */
    public void removePlate() {
        if (level == null) {
            return;
        }
        Direction facing = facing();
        for (int i = 1; i <= depth; i++) {
            BlockPos cell = worldPosition.relative(facing, i);
            if (isOurs(level.getBlockState(cell), i)) {
                level.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        plated = false;
    }

    /** effects: returns whether {@code state} is this housing's plate block {@code i} cells into the opening */
    private boolean isOurs(BlockState state, int i) {
        return state.is(ModContent.HATCH_PLATE.get()) && state.getValue(HatchPlateBlock.DISTANCE) == i
                && state.getValue(HatchPlateBlock.FACING) == facing().getOpposite();
    }

    /** effects: returns the opening's cells as a box (cached until the depth changes) */
    private AABB footprint() {
        if (footprint == null) {
            Direction facing = facing();
            BlockPos first = worldPosition.relative(facing), last = worldPosition.relative(facing, depth);
            footprint = new AABB(Math.min(first.getX(), last.getX()), first.getY(), Math.min(first.getZ(), last.getZ()),
                    Math.max(first.getX(), last.getX()) + 1, first.getY() + 1, Math.max(first.getZ(), last.getZ()) + 1);
        }
        return footprint;
    }

    /** effects: returns the box the renderer draws in: the housing, its opening, and the rail over it */
    public AABB renderBounds() {
        return footprint().minmax(new AABB(worldPosition)).expandTowards(0, 1, 0);
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Depth", depth);
        tag.putInt("Travel", travel);
        tag.putInt("Guard", guard);
        tag.putBoolean("Plated", plated);
        if (look != null) {
            tag.put("Look", NbtUtils.writeBlockState(look));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        depth = tag.contains("Depth") ? Math.clamp(tag.getInt("Depth"), Hatch.MIN_DEPTH, Hatch.MAX_DEPTH) : Hatch.DEFAULT_DEPTH;
        travel = Math.clamp(tag.getInt("Travel"), 0, Hatch.travel(depth));
        guard = Math.clamp(tag.getInt("Guard"), 0, Hatch.GUARD_TICKS);
        plated = tag.getBoolean("Plated") && travel == 0;
        look = tag.contains("Look") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Look")) : null;
        footprint = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
