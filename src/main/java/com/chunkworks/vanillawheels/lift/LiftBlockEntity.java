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
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Footprint;
import com.chunkworks.vanillawheels.domain.Footprint.Heading;
import com.chunkworks.vanillawheels.domain.LiftMotion;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Mechanic Lift's one block entity, in its controller: the ticks left
 * in the running job (persisted, and synced once when a job starts so both
 * sides count it down), what stands on the deck, and the two jobs the menu
 * asks for -- building a vehicle from the parts in its slots and painting
 * the one on the deck.
 */
public final class LiftBlockEntity extends BlockEntity implements MenuProvider {
    private int jobTicks = 0;

    public LiftBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.LIFT_BE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(LiftControllerBlock.FACING);
    }

    public Heading heading() {
        return LiftControllerBlock.heading(facing());
    }

    /** effects: returns whether a job is running */
    public boolean busy() {
        return jobTicks > 0;
    }

    public int jobTicks() {
        return jobTicks;
    }

    /** effects: returns how far the deck is drawn up, blocks, {@code partial} of a tick on */
    public double raise(float partial) {
        return LiftMotion.raise(jobTicks, partial);
    }

    /** effects: counts the running job down; ends it with the deck settling sound */
    void tick() {
        if (jobTicks > 0) {
            jobTicks--;
            if (jobTicks == 0 && level != null && !level.isClientSide()) {
                level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.8f, 0.7f);
                setChanged();
            }
        }
    }

    /** effects: starts a job: the deck rises, the buttons read busy, everyone hears the rams */
    private void startJob() {
        jobTicks = LiftMotion.JOB;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.8f, 0.7f);
        }
    }

    /** effects: returns the box above the deck, in the world */
    public AABB deckBox() {
        double[][] b = Footprint.deckBox(heading());
        return new AABB(worldPosition.getX() + b[0][0], worldPosition.getY() + b[0][1], worldPosition.getZ() + b[0][2],
                worldPosition.getX() + b[1][0], worldPosition.getY() + b[1][1], worldPosition.getZ() + b[1][2]);
    }

    /** effects: returns where a built vehicle stands, in the world */
    public Vec3 spawn() {
        double[] s = Footprint.spawn(heading());
        return new Vec3(worldPosition.getX() + s[0], worldPosition.getY() + s[1], worldPosition.getZ() + s[2]);
    }

    /** effects: returns the vehicle standing on the deck, if one does: its feet inside the deck box */
    @Nullable
    public Vehicle vehicleOnDeck() {
        if (level == null) {
            return null;
        }
        AABB deck = deckBox();
        List<Vehicle> on = level.getEntitiesOfClass(Vehicle.class, deck, v -> deck.contains(v.position()));
        return on.isEmpty() ? null : on.get(0);
    }

    /** effects: returns whether a {@code p} spawned on the deck would meet a block or an entity */
    public boolean occupied(VehicleProfile p) {
        if (level == null) {
            return true;
        }
        Vec3 at = spawn();
        double w = p.body().width() / 2.0;
        AABB box = new AABB(at.x - w, at.y + 0.01, at.z - w, at.x + w, at.y + p.body().height(), at.z + w);
        return !level.noCollision(box) || !level.getEntities(null, box).isEmpty();
    }

    /**
     * effects: spawns a {@code vehicle} on the deck facing the front and
     * starts the job; the caller has checked the parts and the deck
     */
    void build(ResourceLocation vehicle) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Vehicle v = Vehicle.create(server, vehicle, spawn(), heading().yaw());
        if (v != null) {
            server.addFreshEntity(v);
            startJob();
        }
    }

    /** effects: paints the vehicle on the deck {@code color} and starts the job */
    void paint(Vehicle v, DyeColor color) {
        v.setPaint(color);
        startJob();
    }

    // --- persistence and sync ---------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("JobTicks", jobTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        jobTicks = Math.max(0, Math.min(LiftMotion.JOB, tag.getInt("JobTicks")));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- the menu ----------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.vanillawheels.mechanic_lift");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new LiftMenu(id, inventory, ContainerLevelAccess.create(level, worldPosition));
    }

    /** effects: returns the profile of the vehicle {@code id} names in this level, if any */
    @Nullable
    public VehicleProfile profile(ResourceLocation id) {
        return level == null ? null : VanillaWheels.profile(level.registryAccess(), id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }
}
