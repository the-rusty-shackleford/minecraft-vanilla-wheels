/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Chunkworks
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
package com.chunkworks.vanillawheels;

import com.chunkworks.vanillawheels.domain.Landing;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * A vehicle's crew across a logout (D-0038). The game keeps a vehicle in a player's save only while
 * they are the one player aboard; with others aboard it leaves it in the world and saves them
 * standing where their seat was, in the air under a helicopter's rotor or under water in a
 * submarine. OtatopMalloy logged back in so on 2026-10-11, the Huey gone in Bobandy_'s save, and
 * fell fifty blocks.
 *
 * <p>So every player aboard keeps, in their own save, which vehicle and seat they are in, and drops
 * it when they get out. Logging back in not aboard with that still kept, they are put back in that
 * vehicle wherever it is now, if it is loaded and has a seat, their own while it is free; else stood
 * where they are safe ({@link Landing}): on the ground under where they left, at the surface over
 * them under water, or in the nearest such column within {@link #REACH} blocks. Nowhere there, a
 * minute of slow falling and water breathing lets them find their own way.
 */
public final class Crew {
    private Crew() {}

    /** Where a player aboard keeps their vehicle and seat, in the game's own per-entity data. */
    static final String KEY = "vanillawheels:aboard";
    /** How far across a rider is looked for somewhere to stand, blocks either way. */
    static final int REACH = 8;
    /** Ticks of the effects that see a rider down, or up, when nowhere within reach will do. */
    private static final int FIND_YOUR_WAY = 1200;
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /** effects: keeps in {@code player}'s save that they ride {@code vehicle} in {@code seat} */
    static void remember(ServerPlayer player, Vehicle vehicle, int seat) {
        CompoundTag record = new CompoundTag();
        record.putUUID("Vehicle", vehicle.getUUID());
        record.putInt("Seat", seat);
        player.getPersistentData().put(KEY, record);
    }

    /** effects: drops from {@code player}'s save the vehicle they rode */
    static void forget(ServerPlayer player) {
        player.getPersistentData().remove(KEY);
    }

    /**
     * effects: on a player logging in with a vehicle kept and not aboard it (the game brought it
     * back only for a lone rider), seats them in it again wherever it is now, or stands them
     * somewhere safe
     */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getPersistentData().contains(KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        if (player.getVehicle() instanceof Vehicle) {
            return;   // the game brought it back with them, and they keep it again
        }
        CompoundTag record = player.getPersistentData().getCompound(KEY);
        forget(player);
        ServerLevel level = player.serverLevel();
        UUID id = record.hasUUID("Vehicle") ? record.getUUID("Vehicle") : null;
        if (id != null && level.getEntity(id) instanceof Vehicle vehicle && vehicle.isAlive() && vehicle.hasSeatFor(player)) {
            // Over to it first: the game's teleport takes a rider off whatever they ride.
            player.teleportTo(level, vehicle.getX(), vehicle.getY(), vehicle.getZ(), player.getYRot(), player.getXRot());
            if (vehicle.reseat(player, record.getInt("Seat"))) {
                LOG.info("Vanilla Wheels: {} logged back in aboard {} in seat {}", player.getScoreboardName(), vehicle.getName().getString(), vehicle.seatOf(player));
                return;
            }
        }
        setDown(player);
    }

    /** requires: server thread; effects: stands {@code player} somewhere safe at or near where they are, as {@link Crew} says */
    static void setDown(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight();
        double feet = Mth.clamp(player.getY(), minY, maxY - 1);
        int bx = Mth.floor(player.getX()), bz = Mth.floor(player.getZ());
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= REACH; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = bx + dx, z = bz + dz;
                    OptionalDouble y = Landing.standAt(h -> cell(level, at.set(x, h, z)), feet, minY, maxY, player.getBbHeight());
                    if (y.isEmpty()) {
                        continue;
                    }
                    // Their own column where they were in it; a column's middle where that would put them in a wall.
                    Vec3 spot = new Vec3(player.getX(), y.getAsDouble(), player.getZ());
                    if (r > 0 || !fits(player, spot)) {
                        spot = new Vec3(x + 0.5, y.getAsDouble(), z + 0.5);
                        if (!fits(player, spot)) {
                            continue;
                        }
                    }
                    LOG.info("Vanilla Wheels: {} logged back in to the vehicle they left gone; stood from {} at {}", player.getScoreboardName(), player.position(), spot);
                    player.teleportTo(level, spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
                    player.resetFallDistance();
                    return;
                }
            }
        }
        LOG.info("Vanilla Wheels: {} logged back in to the vehicle they left gone, nowhere to stand within {} blocks of {}", player.getScoreboardName(), REACH, player.position());
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, FIND_YOUR_WAY));
        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, FIND_YOUR_WAY));
    }

    /** effects: returns whether {@code player}, standing, fits at {@code spot} */
    private static boolean fits(ServerPlayer player, Vec3 spot) {
        return player.level().noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(spot).deflate(1.0e-6));
    }

    /** effects: returns what the block at {@code pos} is to someone standing in it */
    static Landing.Cell cell(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.LAVA) || harms(state)) {
            return Landing.Cell.HARM;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (!shape.isEmpty()) {
            double top = shape.max(Direction.Axis.Y);
            if (top > 0.0) {
                return Landing.Cell.solid(Math.min(top, 1.5));
            }
        }
        return state.getFluidState().is(FluidTags.WATER) ? Landing.Cell.WATER : Landing.Cell.OPEN;
    }

    /** effects: returns whether standing in or on {@code state} hurts */
    private static boolean harms(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE);
    }
}
