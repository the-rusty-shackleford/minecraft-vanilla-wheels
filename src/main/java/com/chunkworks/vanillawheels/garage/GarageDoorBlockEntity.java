/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.garage;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.domain.Shutter;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import java.util.List;

/** Loaded shutter metadata. AF: origin/size describe the shared door, lift its
 * clearance in eighth-blocks. RI: bounded dimensions and travel; only origin owns
 * the cached panel list and ticks. Followers persist clearance but never tick.
 * Neither discovery nor controller lookup loads chunks. */
public final class GarageDoorBlockEntity extends BlockEntity {
    private BlockPos origin;
    private int width = 1, height = 1, lift, previousLift;
    private boolean formed, powered, moving;
    private List<GarageDoorBlockEntity> panels = List.of();
    private AABB bounds, aperture;
    private Shutter.Rectangle motion;
    private int shapeLift = -1;
    private VoxelShape cachedShape = Shapes.empty();

    /** requires: registered panel state; effects: creates closed metadata; throws: none. */
    public GarageDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.GARAGE_DOOR_BE.get(), pos, state);
        origin = pos; updateBounds();
    }
    /** requires: none; effects: returns assembly origin; throws: none. */
    public BlockPos origin() { return origin; }
    /** requires: none; effects: returns door width; throws: none. */
    public int width() { return width; }
    /** requires: none; effects: returns door height including header; throws: none. */
    public int height() { return height; }
    /** requires: none; effects: returns whether a complete rectangle is assembled; throws: none. */
    public boolean formed() { return formed; }
    /** requires: none; effects: returns authoritative eighth-block clearance; throws: none. */
    public int lift() { return lift; }
    /** requires: none; effects: returns whether this panel renders the assembly; throws: none. */
    public boolean renders() { return !formed || worldPosition.equals(origin); }
    /** requires: partial in 0..1; effects: returns interpolated clearance in blocks; throws: none. */
    public float clearance(float partial) { return (previousLift+(lift-previousLift)*partial)/Shutter.UNITS; }
    /** requires: none; effects: returns horizontal width axis; throws: none. */
    public Direction.Axis axis() { return getBlockState().getValue(GarageDoorBlock.AXIS); }

    /** requires: loaded metadata; effects: resolves root without loading chunks, or
     * returns null if incomplete/unloaded; throws: none. */
    public GarageDoorBlockEntity controller() {
        if (!formed || level == null || !level.hasChunkAt(origin)) return null;
        return level.getBlockEntity(origin) instanceof GarageDoorBlockEntity root
                && root.formed && root.origin.equals(origin) && root.worldPosition.equals(origin) ? root : null;
    }

    void configure(BlockPos origin, int width, int height, boolean formed, int lift, List<GarageDoorBlockEntity> panels) {
        new Shutter.Rectangle(0,0,width,height);
        this.origin = origin.immutable(); this.width = width; this.height = height;
        this.formed = formed; this.lift = this.previousLift = lift; this.panels = panels;
        this.moving = false; shapeLift = -1; updateBounds();
        var state = getBlockState();
        boolean root = formed && worldPosition.equals(origin);
        if (state.getValue(GarageDoorBlock.ROOT) != root) level.setBlock(worldPosition, state.setValue(GarageDoorBlock.ROOT, root), Block.UPDATE_CLIENTS);
        changed();
    }

    void disassemble() {
        // Retain each panel's original row and clearance. Moving the header to
        // every remaining cell would create new colliders in an open doorway.
        formed = false; moving = false; panels = List.of(); previousLift = lift;
        var state = getBlockState();
        if (state.getValue(GarageDoorBlock.ROOT)) level.setBlock(worldPosition,
                state.setValue(GarageDoorBlock.ROOT,false),Block.UPDATE_CLIENTS);
        changed();
    }

    private void updateBounds() {
        motion = new Shutter.Rectangle(0,0,width,height);
        bounds = axis() == Direction.Axis.X
                ? new AABB(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+width,origin.getY()+height,origin.getZ()+1)
                : new AABB(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+1,origin.getY()+height,origin.getZ()+width);
        aperture = axis() == Direction.Axis.X
                ? new AABB(bounds.minX+.1,bounds.minY,bounds.minZ+.1,bounds.maxX-.1,bounds.maxY-Shutter.HEADER,bounds.maxZ-.1)
                : new AABB(bounds.minX+.1,bounds.minY,bounds.minZ+.1,bounds.maxX-.1,bounds.maxY-Shutter.HEADER,bounds.maxZ-.1);
    }

    private boolean loaded() {
        if (level == null || panels.size() != width*height || !level.hasChunksAt(BlockPos.containing(bounds.minX,bounds.minY,bounds.minZ),
                BlockPos.containing(bounds.maxX-1,bounds.maxY-1,bounds.maxZ-1))) return false;
        for (var panel : panels) if (panel.isRemoved() || !panel.origin.equals(origin)) return false;
        return true;
    }

    /** requires: server thread; effects: samples redstone from all loaded panels;
     * power at any section requests opening; throws: none. */
    public void readPower() {
        if (!formed || !worldPosition.equals(origin) || !loaded()) return;
        boolean next = false;
        for (var panel : panels) if (level.hasNeighborSignal(panel.worldPosition)) { next = true; break; }
        if (next != powered) { powered = next; changed(); }
    }

    /** requires: server tick and matching root; effects: advances safely or pauses
     * for unloaded chunks/obstructions; throws: none. */
    public static void tick(Level level, BlockPos pos, BlockState state, GarageDoorBlockEntity door) {
        door.previousLift = door.lift;
        if (level.isClientSide) return;
        if (!door.formed || !pos.equals(door.origin)) return;
        int limit = door.height*Shutter.UNITS-4;
        if ((door.powered && door.lift == limit) || (!door.powered && door.lift == 0)) {
            door.moving = false; return;
        }
        if (!door.loaded()) { door.moving = false; return; }
        boolean obstructed = !door.powered && !level.getEntities((net.minecraft.world.entity.Entity)null,door.aperture,
                e -> !e.isSpectator() && (e instanceof LivingEntity || e instanceof Vehicle
                        || e instanceof AbstractMinecart || e instanceof Boat
                        || e instanceof net.neoforged.neoforge.entity.PartEntity<?>)).isEmpty();
        int next = Shutter.advance(door.motion,door.lift,door.powered,obstructed);
        if (next == door.lift) { door.moving = false; return; }
        if (!door.moving) level.playSound(null,pos,door.powered ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.BLOCKS,.55f,.65f);
        door.moving = true;
        for (var panel : door.panels) {
            panel.previousLift = panel.lift;
            panel.lift = next;
            panel.setChanged();
        }
        door.changed();
    }

    /** requires: none; effects: returns cached per-cell collision matching the curtain,
     * side tracks and half-block header; throws: none. */
    public VoxelShape shape() {
        var root = controller();
        int travel = root == null ? lift : root.lift;
        if (shapeLift == travel) return cachedShape;
        shapeLift = travel;
        double y = worldPosition.getY()-origin.getY();
        int across = axis() == Direction.Axis.X ? worldPosition.getX()-origin.getX() : worldPosition.getZ()-origin.getZ();
        double bottom = Math.max(0,travel/(double)Shutter.UNITS-y);
        double top = Math.min(1,height-Shutter.HEADER-y);
        var shape = Shapes.empty();
        if (top > bottom) shape = Shapes.or(shape, box(0,bottom,.43,1,top,.57));
        if (y == height-1) shape = Shapes.or(shape,box(0,.5,.2,1,1,.8));
        if (across == 0) shape = Shapes.or(shape,box(0,0,.35,.1,1,.65));
        if (across == width-1) shape = Shapes.or(shape,box(.9,0,.35,1,1,.65));
        cachedShape = shape; return shape;
    }
    private VoxelShape box(double x0,double y0,double z0,double x1,double y1,double z1) {
        return axis() == Direction.Axis.X ? Shapes.box(x0,y0,z0,x1,y1,z1) : Shapes.box(z0,y0,x0,z1,y1,x1);
    }
    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) level.scheduleTick(worldPosition,ModContent.GARAGE_DOOR.get(),1);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        tag.putLong("Origin",origin.asLong()); tag.putInt("Width",width); tag.putInt("Height",height);
        tag.putInt("Lift",lift); tag.putBoolean("Formed",formed); tag.putBoolean("Powered",powered);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        origin = tag.contains("Origin") ? BlockPos.of(tag.getLong("Origin")) : worldPosition;
        width = Math.clamp(tag.getInt("Width"),1,Shutter.MAX_SIZE);
        height = Math.clamp(tag.getInt("Height"),1,Shutter.MAX_SIZE);
        previousLift = lift;
        lift = Math.clamp(tag.getInt("Lift"),0,height*Shutter.UNITS-4);
        formed = tag.getBoolean("Formed"); powered = tag.getBoolean("Powered");
        shapeLift = -1; updateBounds();
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    /** requires: none; effects: returns cached world render bounds; throws: none. */
    public AABB renderBounds() { return bounds; }
}
