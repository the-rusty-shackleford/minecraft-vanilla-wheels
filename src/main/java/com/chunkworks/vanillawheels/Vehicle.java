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
package com.chunkworks.vanillawheels;

import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.client.Controls;
import com.chunkworks.vanillawheels.domain.Drive;
import com.chunkworks.vanillawheels.domain.Impact;
import com.chunkworks.vanillawheels.domain.Input;
import com.chunkworks.vanillawheels.domain.Suspension;
import com.chunkworks.vanillawheels.domain.Cargo;
import com.chunkworks.vanillawheels.domain.Tank;
import com.chunkworks.vanillawheels.domain.Tow;
import com.chunkworks.vanillawheels.domain.Tuning;
import com.chunkworks.vanillawheels.domain.Vec;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Every vehicle in the world is one of these; which vehicle, its
 * {@link VehicleProfile} says, and the profile's id is the first thing the
 * entity syncs. The class turns the pure layer's decisions into the game's
 * doings: the driver's client runs {@link Drive} and moves the entity (the
 * boat's pattern -- the server validates the move it is told), the server
 * burns fuel, hurts what is run over, and keeps the state every client
 * needs to draw the thing; the drawn body rides a {@link Suspension} so a
 * two-block step is a glide.
 *
 * <p>Riders sit at the profile's seats; the driver is whoever is in the
 * driver's seat. A crouching click on the chest region opens the chest, a
 * rider opens it with the inventory key, a fuel in hand fills the tank, a
 * disc in hand loads the radio, the wrench takes the vehicle back into the
 * hand. Hit boxes beyond the square the game gives an entity are
 * {@link Part}s, so the hood and bed of a long truck are clickable.
 */
public class Vehicle extends VehicleEntity implements HasCustomInventoryScreen, ContainerEntity {
    private static final EntityDataAccessor<String> DATA_PROFILE = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_PAINT = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_LIGHTS = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_LIT = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HORN = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STEER = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DRIFTING = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> DATA_DISC = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.ITEM_STACK);
    /** The entity id of the vehicle towing this one, -1 for none. */
    private static final EntityDataAccessor<Integer> DATA_TOWER = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
    /** The entity id of the trailer this one tows, -1 for none. */
    private static final EntityDataAccessor<Integer> DATA_TRAILER = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DOORS = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.BOOLEAN);
    /** How close a tongue must come to a hitch to catch, blocks; how far it may stretch before it lets go. */
    public static final double CATCH = 0.5;
    public static final double STRETCH = 1.0;

    /** Headlight modes, in the order the key cycles them. */
    public enum Lights { OFF, ON, AUTO }

    /** The profile's id, resolved lazily against the level's registries. */
    @Nullable private VehicleProfile profile;
    private ResourceLocation profileId = VanillaWheels.id("none");
    private Tuning tuning = Tuning.pickup();

    private Drive drive = Drive.atRest(0.0);
    private Suspension suspension = Suspension.LEVEL;
    private Suspension suspensionO = Suspension.LEVEL;
    private double wheelTravel;
    private double wheelTravelO;
    /** The driver's throttle as the server last heard it: what burns fuel. */
    private int throttle;
    /**
     * An input the server drives with when nobody is at the wheel: what a
     * gametest, and one day an autopilot, steers by. Null for none.
     */
    @Nullable private Input scripted;
    private int hornTicks;
    private int lightCheckTicks;
    /** Who was run over when, so nobody is hit twice in a moment. */
    /** Ticks between two hits on the same victim. */
    private static final int HIT_COOLDOWN = 10;
    /** Victim entity id to the tick it was last run over, pruned as the cooldown passes. */
    private final Int2IntOpenHashMap hitAt = new Int2IntOpenHashMap();
    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);
    @Nullable private ResourceKey<LootTable> lootTable;
    /** The tow links as saved: entity ids do not survive a reload, so the server re-finds them by these. */
    @Nullable private UUID towerUuid;
    @Nullable private UUID trailerUuid;
    /** The doors' swing on the client, 0 shut to 1 open, eased toward the synced state. */
    private float doorSwing;
    private float doorSwingO;
    private long lootTableSeed;
    /** The most hit boxes a profile may add beyond the body's own square. */
    public static final int MAX_PARTS = 4;
    /**
     * The extra hit boxes, made with the entity so their ids follow its own
     * on both sides (the dragon's rule), configured once the profile is
     * known; an unused one is a speck that cannot be picked.
     */
    private final Part[] parts = new Part[MAX_PARTS];

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;

    public Vehicle(EntityType<? extends Vehicle> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        for (int i = 0; i < MAX_PARTS; i++) {
            parts[i] = new Part(this);
        }
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < parts.length; i++) {
            parts[i].setId(id + i + 1);
        }
    }

    /**
     * effects: returns a new vehicle of profile {@code id} at {@code at}
     * facing {@code yaw} degrees, not yet in the level; null if the level
     * has no such profile
     */
    @Nullable
    public static Vehicle create(ServerLevel level, ResourceLocation id, Vec3 at, float yaw) {
        Vehicle v = ModContent.VEHICLE_ENTITY.get().create(level);
        if (v == null || VanillaWheels.profile(level.registryAccess(), id).isEmpty()) {
            return null;
        }
        v.setProfile(id);
        v.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        v.setYRot(yaw);
        v.yRotO = yaw;
        v.drive = Drive.atRest(Math.toRadians(yaw));
        return v;
    }

    // --- the profile -----------------------------------------------------

    /** effects: makes this vehicle a {@code id}: sizes it, gives it its chest and its paint */
    public void setProfile(ResourceLocation id) {
        entityData.set(DATA_PROFILE, id.toString());
        profileId = id;
        profile = null;
        VehicleProfile p = profile();
        if (p != null) {
            tuning = p.tuning();
            int slots = p.storage().map(s -> s.rows() * 9).orElse(0);
            if (items.size() != slots) {
                items = NonNullList.withSize(slots, ItemStack.EMPTY);
            }
            if (p.paint().isPresent() && entityData.get(DATA_PAINT) < 0) {
                entityData.set(DATA_PAINT, p.paint().get().defaultColor().getId());
            }
            entityData.set(DATA_FUEL, Math.min(entityData.get(DATA_FUEL), p.fuel().map(VehicleProfile.Fuel::capacity).orElse(0)));
            configureParts(p);
            refreshDimensions();
        }
    }

    /** effects: returns this vehicle's profile, or null while the registries do not have it (a client that has not received it yet) */
    @Nullable
    public VehicleProfile profile() {
        if (profile == null) {
            String id = entityData.get(DATA_PROFILE);
            if (!id.isEmpty()) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) {
                    profileId = rl;
                    profile = VanillaWheels.profile(level().registryAccess(), rl).map(net.minecraft.core.Holder.Reference::value).orElse(null);
                    if (profile != null) {
                        tuning = profile.tuning();
                        configureParts(profile);
                    }
                }
            }
        }
        return profile;
    }

    public ResourceLocation profileId() {
        return profileId;
    }

    public Tuning tuning() {
        return tuning;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PROFILE, "");
        builder.define(DATA_PAINT, -1);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_LIGHTS, (byte) 0);
        builder.define(DATA_LIT, false);
        builder.define(DATA_HORN, false);
        builder.define(DATA_SPEED, 0.0f);
        builder.define(DATA_STEER, 0.0f);
        builder.define(DATA_DRIFTING, false);
        builder.define(DATA_DISC, ItemStack.EMPTY);
        builder.define(DATA_TOWER, -1);
        builder.define(DATA_TRAILER, -1);
        builder.define(DATA_DOORS, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PROFILE.equals(key)) {
            profile = null;
            if (profile() != null) {
                refreshDimensions();
            }
        }
    }

    // --- size and parts --------------------------------------------------

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        VehicleProfile p = profile();
        if (p == null) {
            return super.getDimensions(pose);
        }
        return EntityDimensions.scalable((float) p.body().width(), (float) p.body().height());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        VehicleProfile p = profile();
        if (p == null) {
            return super.getBoundingBoxForCulling();
        }
        double r = Math.hypot(p.body().length() / 2, p.body().width() / 2) + 0.5;
        return new AABB(getX() - r, getY() - 1.0, getZ() - r, getX() + r, getY() + p.body().height() + 2.0, getZ() + r);
    }

    /** effects: gives the first parts the profile's boxes and switches the rest off; more boxes than parts are dropped with a log line */
    private void configureParts(VehicleProfile p) {
        List<VehicleProfile.HitBox> boxes = p.body().parts();
        if (boxes.size() > MAX_PARTS) {
            com.mojang.logging.LogUtils.getLogger().warn("vanillawheels: {} names {} hit boxes; only {} are used", profileId, boxes.size(), MAX_PARTS);
        }
        for (int i = 0; i < parts.length; i++) {
            parts[i].configure(i < boxes.size() ? boxes.get(i) : null);
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    /** effects: puts every part where the body is, turned with it */
    private void placeParts() {
        VehicleProfile p = profile();
        if (p == null) {
            return;
        }
        for (Part part : parts) {
            VehicleProfile.HitBox box = part.box();
            Vec3 world = box == null ? Vec3.ZERO : rotate(p.localBlocks(box.at()));
            part.setPos(getX() + world.x, getY() + world.y, getZ() + world.z);
            part.xo = part.getX();
            part.yo = part.getY();
            part.zo = part.getZ();
        }
    }

    /** effects: returns {@code local} (blocks, the body's frame: +Z forward, +X left) turned by the body's yaw */
    public Vec3 rotate(Vec local) {
        return new Vec3(local.x(), local.y(), local.z()).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    /** A hit box away from the body's own square: the hood, the bed. Unconfigured, it is a speck nothing can hit. */
    public static final class Part extends PartEntity<Vehicle> {
        @Nullable private VehicleProfile.HitBox box;

        Part(Vehicle parent) {
            super(parent);
            refreshDimensions();
        }

        void configure(@Nullable VehicleProfile.HitBox box) {
            this.box = box;
            refreshDimensions();
        }

        @Nullable
        public VehicleProfile.HitBox box() {
            return box;
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {}

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {}

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {}

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return box == null ? EntityDimensions.fixed(0.01f, 0.01f) : EntityDimensions.scalable((float) box.width(), (float) box.height());
        }

        @Override
        public boolean isPickable() {
            return box != null;
        }

        @Override
        public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
            return getParent().hurt(source, amount);
        }

        @Override
        public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
            Vec3 hit = vec.add(position()).subtract(getParent().position());
            return getParent().interactAt(player, hit, hand);
        }

        @Override
        public InteractionResult interact(Player player, InteractionHand hand) {
            return getParent().interact(player, hand);
        }

        @Override
        public boolean is(Entity entity) {
            return this == entity || getParent() == entity;
        }
    }

    // --- riders ----------------------------------------------------------

    /**
     * effects: a person may board while a seat is free; an animal while the
     * doors are open and the cargo has room for it; nothing else boards
     */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        VehicleProfile p = profile();
        if (p == null) {
            return false;
        }
        if (passenger instanceof Animal animal) {
            return p.cargo().isPresent() && doorsOpen() && cargo().accepts(animal.isBaby());
        }
        return riders().size() < p.seats().size();
    }

    /** effects: returns the passengers in seats, in boarding order: everyone who is not an animal in the cargo */
    private List<Entity> riders() {
        return getPassengers().stream().filter(e -> !(e instanceof Animal)).toList();
    }

    /** effects: returns the animals aboard, in boarding order */
    public List<Animal> animals() {
        return getPassengers().stream().filter(Animal.class::isInstance).map(Animal.class::cast).toList();
    }

    /** effects: returns what the cargo holds now, against the profile's room; a vehicle with no cargo holds nothing in no room */
    public Cargo cargo() {
        VehicleProfile p = profile();
        if (p == null || p.cargo().isEmpty()) {
            return Cargo.empty(1, 1).with(false);
        }
        VehicleProfile.Cargo room = p.cargo().get();
        int adults = 0, young = 0;
        for (Animal a : animals()) {
            if (a.isBaby()) {
                young++;
            } else {
                adults++;
            }
        }
        try {
            return new Cargo(room.adults(), room.young(), adults, young);
        } catch (IllegalArgumentException overfull) {
            return new Cargo(room.adults(), room.young(), room.adults(), 0);
        }
    }

    public boolean doorsOpen() {
        return entityData.get(DATA_DOORS);
    }

    /** effects: returns the doors' swing, 0 shut to 1 open, for drawing */
    public float doorSwing(float partialTick) {
        return Mth.lerp(partialTick, doorSwingO, doorSwing);
    }

    /** effects: opens the doors if shut, shuts them if open */
    public void toggleDoors() {
        boolean open = !doorsOpen();
        entityData.set(DATA_DOORS, open);
        level().playSound(null, getX(), getY(), getZ(), open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.8f, 1.1f);
    }

    /** effects: returns the seat {@code passenger} is in, by order of boarding; the driver's seat goes to whoever boards first if it is free */
    private int seatOf(Entity passenger) {
        return riders().indexOf(passenger);
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        VehicleProfile p = profile();
        if (p == null || !p.isPowered()) {
            return null;
        }
        int driverSeat = driverSeat(p);
        List<Entity> riders = riders();
        if (driverSeat >= 0 && driverSeat < riders.size() && riders.get(driverSeat) instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private static int driverSeat(VehicleProfile p) {
        for (int i = 0; i < p.seats().size(); i++) {
            if (p.seats().get(i).driver()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        VehicleProfile p = profile();
        if (p == null) {
            return super.getPassengerAttachmentPoint(entity, dimensions, partialTick);
        }
        Vec local;
        if (entity instanceof Animal && p.cargo().isPresent()) {
            // Animals stand on the cargo slots in boarding order; past the last slot they double up, a little to the side.
            List<Vec> slots = p.cargo().get().slots();
            int k = Math.max(0, animals().indexOf(entity));
            Vec slot = p.localBlocks(slots.get(k % slots.size()));
            local = k < slots.size() ? slot : slot.plus(new Vec(-0.35, 0.0, 0.0));
        } else {
            int seat = seatOf(entity);
            if (seat < 0 || seat >= p.seats().size()) {
                return super.getPassengerAttachmentPoint(entity, dimensions, partialTick);
            }
            local = p.localBlocks(p.seats().get(seat).at());
        }
        Vec3 at = rotate(local);
        Suspension s = suspension(partialTick);
        return new Vec3(at.x, at.y + s.lift(), at.z);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        clampRotation(passenger);
    }

    @Override
    public void onPassengerTurned(Entity entity) {
        clampRotation(entity);
    }

    /** effects: keeps {@code rider} looking within a right angle and a bit of the way the body points, as a boat does */
    private void clampRotation(Entity rider) {
        rider.setYBodyRot(getYRot());
        float f = Mth.wrapDegrees(rider.getYRot() - getYRot());
        float f1 = Mth.clamp(f, -105.0f, 105.0f);
        rider.yRotO += f1 - f;
        rider.setYRot(rider.getYRot() + f1 - f);
        rider.setYHeadRot(rider.getYRot());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        Vec3 escape = getCollisionHorizontalEscapeVector(getBbWidth() * Mth.SQRT_OF_TWO, rider.getBbWidth(), rider.getYRot());
        double x = getX() + escape.x;
        double z = getZ() + escape.z;
        BlockPos pos = BlockPos.containing(x, getBoundingBox().maxY, z);
        BlockPos below = pos.below();
        if (!level().isWaterAt(below)) {
            List<Vec3> spots = Lists.newArrayList();
            double floor = level().getBlockFloorHeight(pos);
            if (DismountHelper.isBlockFloorValid(floor)) {
                spots.add(new Vec3(x, pos.getY() + floor, z));
            }
            double floorBelow = level().getBlockFloorHeight(below);
            if (DismountHelper.isBlockFloorValid(floorBelow)) {
                spots.add(new Vec3(x, below.getY() + floorBelow, z));
            }
            for (Pose pose : rider.getDismountPoses()) {
                for (Vec3 spot : spots) {
                    if (DismountHelper.canDismountTo(level(), spot, rider, pose)) {
                        rider.setPose(pose);
                        return spot;
                    }
                }
            }
        }
        return super.getDismountLocationForPassenger(rider);
    }

    // --- towing ----------------------------------------------------------

    /** effects: returns the vehicle towing this one, if it is loaded here */
    @Nullable
    public Vehicle tower() {
        int id = entityData.get(DATA_TOWER);
        return id < 0 ? null : level().getEntity(id) instanceof Vehicle v ? v : null;
    }

    /** effects: returns the trailer this one tows, if it is loaded here */
    @Nullable
    public Vehicle trailer() {
        int id = entityData.get(DATA_TRAILER);
        return id < 0 ? null : level().getEntity(id) instanceof Vehicle v ? v : null;
    }

    /** effects: returns whether this can be towed: it has a tongue */
    public boolean hasTongue() {
        VehicleProfile p = profile();
        return p != null && p.hitch().front().isPresent();
    }

    /** effects: returns where the tongue is in the world, or null without one */
    @Nullable
    public Vec3 tongue() {
        VehicleProfile p = profile();
        return p == null || p.hitch().front().isEmpty() ? null : position().add(rotate(p.localBlocks(p.hitch().front().get())));
    }

    /** effects: returns where the rear hitch is in the world, or null without one */
    @Nullable
    public Vec3 hitchPoint() {
        VehicleProfile p = profile();
        return p == null || p.hitch().rear().isEmpty() ? null : position().add(rotate(p.localBlocks(p.hitch().rear().get())));
    }

    /**
     * A trailer goes where its tower goes, so it is controlled by whoever
     * controls its tower: the driver's client moves it and the server moves
     * its own copy from its own copy of the tower.
     */
    @Override
    public boolean isControlledByLocalInstance() {
        Vehicle tower = tower();
        return tower != null ? tower.isControlledByLocalInstance() : super.isControlledByLocalInstance();
    }

    /** effects: links {@code trailer} behind this vehicle, both ways, with a clunk */
    public void hitch(Vehicle trailer) {
        entityData.set(DATA_TRAILER, trailer.getId());
        trailer.entityData.set(DATA_TOWER, getId());
        trailerUuid = trailer.getUUID();
        trailer.towerUuid = getUUID();
        Vec3 at = trailer.tongue() == null ? trailer.position() : trailer.tongue();
        level().playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0f, 0.8f);
    }

    /** effects: lets go of the tower, if any; the trailer rolls on and stops by itself */
    public void unhitch() {
        Vehicle tower = tower();
        if (tower != null) {
            tower.entityData.set(DATA_TRAILER, -1);
            tower.trailerUuid = null;
        }
        if (entityData.get(DATA_TOWER) >= 0 || towerUuid != null) {
            entityData.set(DATA_TOWER, -1);
            towerUuid = null;
            Vec3 at = tongue() == null ? position() : tongue();
            level().playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0f, 0.8f);
        }
    }

    /**
     * effects: one tick of being towed by {@code tower}: the tongue is put
     * on the tower's hitch by {@link Tow}, the body moved there through the
     * world (so it climbs and falls like anything else), turned to the new
     * heading, its wheels rolled; if the world held it back so far that the
     * tongue is STRETCH from the hitch, the server lets go. Tows its own
     * trailer on in turn.
     */
    void follow(Vehicle tower) {
        VehicleProfile p = profile();
        Vec3 hitch = tower.hitchPoint();
        if (p == null || hitch == null || p.hitch().front().isEmpty()) {
            return;
        }
        Vec tongueLocal = p.localBlocks(p.hitch().front().get());
        double axleZ = p.axleForward();
        double length = Math.hypot(tongueLocal.x(), tongueLocal.z() - axleZ);
        if (length <= 0.05) {
            return;
        }
        double yBefore = getY();
        Vec3 axle = position().add(rotate(new Vec(0.0, 0.0, axleZ)));
        Tow.Follow f = Tow.follow(hitch.x, hitch.z, axle.x, axle.z, Math.toRadians(getYRot()), length, Math.toRadians(tower.getYRot()));
        Vec3 axleOffset = new Vec3(0.0, 0.0, axleZ).yRot((float) -f.heading());
        double nx = f.axleX() - axleOffset.x;
        double nz = f.axleZ() - axleOffset.z;
        Vec3 motion = getDeltaMovement();
        double vy = onGround() && motion.y <= 0 ? -0.04 : motion.y - 0.08;
        setDeltaMovement(nx - getX(), vy, nz - getZ());
        move(MoverType.SELF, getDeltaMovement());
        if (onGround() && getDeltaMovement().y < 0) {
            setDeltaMovement(getDeltaMovement().x, 0, getDeltaMovement().z);
        }
        setYRot((float) Math.toDegrees(f.heading()));
        drive = new Drive(f.travelled(), f.heading(), f.heading(), 0.0, 0.0, false);
        wheelTravel += f.travelled();
        entityData.set(DATA_SPEED, (float) f.travelled());
        entityData.set(DATA_STEER, 0.0f);
        double dy = getY() - yBefore;
        if (Math.abs(dy) > 0.3 && Math.abs(dy) <= Suspension.MAX_LIFT) {
            suspension = suspension.jumped(dy);
        }
        if (!level().isClientSide()) {
            Vec3 tongue = tongue();
            if (tongue != null && tongue.distanceTo(tower.hitchPoint()) > STRETCH) {
                unhitch();
            }
        }
        Vehicle next = trailer();
        if (next != null) {
            next.follow(this);
        }
    }

    /** effects: on the server, catches an unhitched trailer whose tongue has come within CATCH of this vehicle's hitch */
    private void catchTrailer(VehicleProfile p) {
        Vec3 hitch = hitchPoint();
        if (hitch == null || trailer() != null || Math.abs(entityData.get(DATA_SPEED)) < 0.02) {
            return;
        }
        for (Vehicle other : level().getEntitiesOfClass(Vehicle.class, getBoundingBox().inflate(4.0), v -> v != this && v.hasTongue() && v.tower() == null && v.towerUuid == null)) {
            Vec3 tongue = other.tongue();
            if (tongue != null && tongue.distanceTo(hitch) < CATCH && other.trailer() != this) {
                hitch(other);
                return;
            }
        }
    }

    /** effects: on the server, re-finds a tow link saved by UUID whose entity id was lost to a reload */
    private void relink() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (towerUuid != null && tower() == null && server.getEntity(towerUuid) instanceof Vehicle t) {
            entityData.set(DATA_TOWER, t.getId());
            t.entityData.set(DATA_TRAILER, getId());
            t.trailerUuid = getUUID();
        }
        if (trailerUuid != null && trailer() == null && server.getEntity(trailerUuid) instanceof Vehicle t) {
            entityData.set(DATA_TRAILER, t.getId());
            t.entityData.set(DATA_TOWER, getId());
            t.towerUuid = getUUID();
        }
    }

    // --- the tick --------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        VehicleProfile p = profile();
        tickLerp();
        suspensionO = suspension;
        wheelTravelO = wheelTravel;
        doorSwingO = doorSwing;
        if (p == null) {
            return;
        }
        if (level().isClientSide()) {
            doorSwing = Mth.clamp(doorSwing + (doorsOpen() ? 0.15f : -0.15f), 0.0f, 1.0f);
        }
        double yBefore = getY();
        Vehicle tower = tower();
        boolean towedHere = tower != null && (!level().isClientSide() || isControlledByLocalInstance());
        if (tower == null && isControlledByLocalInstance()) {
            Input in = level().isClientSide() ? Controls.input(this)
                    : scripted != null ? new Input(scripted.throttle(), scripted.steer(), scripted.drift(), onGround(), hasFuel())
                    : Input.coasting(onGround(), hasFuel());
            if (!level().isClientSide()) {
                throttle = in.throttle();
            }
            Drive.Step step = drive.step(in, tuning);
            drive = step.next();
            if (level().isClientSide()) {
                Controls.report(this, drive, in, step.effects());
            }
            setYRot((float) Math.toDegrees(drive.heading()));
            Vec3 motion = getDeltaMovement();
            double vy = onGround() && motion.y <= 0 ? -0.04 : motion.y - 0.08;
            setDeltaMovement(drive.velocityX(), vy, drive.velocityZ());
            move(MoverType.SELF, getDeltaMovement());
            if (onGround() && getDeltaMovement().y < 0) {
                setDeltaMovement(getDeltaMovement().x, 0, getDeltaMovement().z);
            }
            wheelTravel += drive.speed();
            if (level().isClientSide()) {
                entityData.set(DATA_SPEED, (float) drive.speed());
                entityData.set(DATA_STEER, (float) drive.steer());
                entityData.set(DATA_DRIFTING, drive.drifting());
            } else {
                syncDriveData();
            }
        } else if (!towedHere) {
            setDeltaMovement(Vec3.ZERO);
            wheelTravel += entityData.get(DATA_SPEED);
        }
        double dy = getY() - yBefore;
        if (Math.abs(dy) > 0.3 && Math.abs(dy) <= Suspension.MAX_LIFT) {
            suspension = suspension.jumped(dy);
        }
        suspension = suspension.step(0.0, 0.0, 0.0, 0.0, tuning.wheelBase(), p.track());
        // The trailer goes where this went, this very tick, wherever this is moved: the driver's client or the server.
        Vehicle trailer = trailer();
        if (trailer != null && tower == null && (!level().isClientSide() || isControlledByLocalInstance())) {
            trailer.follow(this);
        }
        placeParts();
        if (!level().isClientSide()) {
            serverTick(p);
        }
    }

    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, getXRot());
            lerpSteps--;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = 10;
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public float lerpTargetYRot() {
        return lerpSteps > 0 ? (float) lerpYRot : getYRot();
    }

    private void syncDriveData() {
        entityData.set(DATA_SPEED, (float) drive.speed());
        entityData.set(DATA_STEER, (float) drive.steer());
        entityData.set(DATA_DRIFTING, drive.drifting());
    }

    /** The server's per-tick duties: fuel, lights, the horn, running things over. */
    private void serverTick(VehicleProfile p) {
        if (throttle != 0 && (getControllingPassenger() != null || scripted != null) && WheelsConfig.FUEL_REQUIRED.get()) {
            Tank tank = tank().burn(1);
            entityData.set(DATA_FUEL, tank.ticks());
        }
        if (++lightCheckTicks >= 20) {
            lightCheckTicks = 0;
            Lights mode = lights();
            boolean lit = mode == Lights.ON || (mode == Lights.AUTO && level().getMaxLocalRawBrightness(blockPosition().above()) < WheelsConfig.AUTO_LIGHTS_BELOW.get());
            entityData.set(DATA_LIT, lit);
        }
        if (entityData.get(DATA_HORN)) {
            if (hornTicks-- <= 0) {
                hornTicks = 15;
                p.horn().flatMap(id -> net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(id))
                        .ifPresent(sound -> level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, 1.2f, 1.0f));
            }
        } else {
            hornTicks = 0;
        }
        runOver(p);
        if (tickCount % 5 == 0) {
            relink();
            catchTrailer(p);
        }
    }

    /** effects: hurts and shoves every living thing in the body's path this tick, once each per half second */
    private void runOver(VehicleProfile p) {
        double speed = entityData.get(DATA_SPEED);
        if (!WheelsConfig.RUN_OVER.get() || Math.abs(speed) < Impact.PACE) {
            return;
        }
        AABB sweep = getBoundingBox().expandTowards(getDeltaMovement().scale(2.0)).inflate(0.3, 0.0, 0.3);
        for (Part part : parts) {
            if (part.box() != null) {
                sweep = sweep.minmax(part.getBoundingBox());
            }
        }
        double heading = Math.toRadians(getYRot());
        double dirX = -Math.sin(heading) * Math.signum(speed);
        double dirZ = Math.cos(heading) * Math.signum(speed);
        double slowest = speed;
        hitAt.int2IntEntrySet().removeIf(hit -> tickCount - hit.getIntValue() >= HIT_COOLDOWN);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, sweep, v -> !hasPassenger(v) && v.isAlive() && !(v instanceof Player pl && pl.isSpectator()))) {
            if (hitAt.containsKey(victim.getId())) {
                continue;
            }
            hitAt.put(victim.getId(), tickCount);
            float damage = (float) (Impact.damage(speed, tuning.maxSpeed(), tuning.mass()) * WheelsConfig.DAMAGE_SCALE.get());
            victim.hurt(level().damageSources().source(VanillaWheels.RUN_OVER, getControllingPassenger(), this), damage);
            victim.knockback(Impact.knockback(speed, tuning.maxSpeed()), -dirX, -dirZ);
            level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModContent.THUD.get(), SoundSource.NEUTRAL, 0.9f, 0.9f);
            slowest = Impact.speedAfter(slowest, victim.getBbWidth());
        }
        if (slowest != speed) {
            entityData.set(DATA_SPEED, (float) slowest);
            drive = new Drive(slowest, drive.heading(), drive.motion(), drive.steer(), drive.driftCharge(), drive.drifting());
        }
    }

    /**
     * effects: from now on, with nobody at the wheel, the server drives by
     * {@code input} (its ground and fuel flags are replaced by the truth
     * each tick); null hands the wheel back
     */
    public void setScriptedInput(@Nullable Input input) {
        this.scripted = input;
    }

    /** effects: takes the driver's state as their client reports it */
    public void onDriveState(float speed, float steer, int throttle, boolean drifting) {
        this.throttle = throttle;
        entityData.set(DATA_SPEED, speed);
        entityData.set(DATA_STEER, steer);
        entityData.set(DATA_DRIFTING, drifting);
        drive = new Drive(speed, Math.toRadians(getYRot()), drive.motion(), steer, drive.driftCharge(), drifting);
    }

    @Override
    public float maxUpStep() {
        return (float) tuning.climb();
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return (entity.canBeCollidedWith() || entity.isPushable()) && !isPassengerOfSameVehicle(entity);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    // --- what the renderer and the sounds read ---------------------------

    /** effects: returns the drive as the controlling instance knows it; other instances have the synced speed and steer only */
    public Drive drive() {
        return drive;
    }

    /** effects: returns the body's speed this tick, blocks per tick, as synced */
    public float speed() {
        return entityData.get(DATA_SPEED);
    }

    public float steer() {
        return entityData.get(DATA_STEER);
    }

    public boolean drifting() {
        return entityData.get(DATA_DRIFTING);
    }

    /** effects: returns the suspension state {@code partialTick} of the way through this tick */
    public Suspension suspension(float partialTick) {
        return new Suspension(Mth.lerp(partialTick, suspensionO.lift(), suspension.lift()),
                Mth.lerp(partialTick, suspensionO.pitch(), suspension.pitch()),
                Mth.lerp(partialTick, suspensionO.roll(), suspension.roll()));
    }

    /** effects: returns how far the wheels have rolled, blocks, interpolated */
    public double wheelTravel(float partialTick) {
        return Mth.lerp(partialTick, wheelTravelO, wheelTravel);
    }

    public Lights lights() {
        return Lights.values()[Math.min(2, entityData.get(DATA_LIGHTS))];
    }

    /** effects: returns whether the headlamps are lit right now */
    public boolean lit() {
        return entityData.get(DATA_LIT);
    }

    /** effects: sets the mode to the next: off, on, auto */
    public void cycleLights() {
        Lights next = Lights.values()[(lights().ordinal() + 1) % 3];
        entityData.set(DATA_LIGHTS, (byte) next.ordinal());
        lightCheckTicks = 20;
    }

    public void setHorn(boolean held) {
        entityData.set(DATA_HORN, held);
    }

    public boolean horn() {
        return entityData.get(DATA_HORN);
    }

    @Nullable
    public DyeColor paint() {
        int id = entityData.get(DATA_PAINT);
        return id < 0 ? null : DyeColor.byId(id);
    }

    public void setPaint(DyeColor color) {
        entityData.set(DATA_PAINT, color.getId());
    }

    public ItemStack disc() {
        return entityData.get(DATA_DISC);
    }

    // --- fuel ------------------------------------------------------------

    /** effects: returns the tank as synced; a vehicle without one holds nothing */
    public Tank tank() {
        VehicleProfile p = profile();
        int capacity = p == null ? 0 : p.fuel().map(VehicleProfile.Fuel::capacity).orElse(0);
        return new Tank(Math.min(capacity, Math.max(0, entityData.get(DATA_FUEL))), capacity);
    }

    /** effects: puts {@code ticks} of fuel in the tank, clamped to 0..capacity */
    public void setFuel(int ticks) {
        entityData.set(DATA_FUEL, Math.max(0, Math.min(tank().capacity(), ticks)));
    }

    /** effects: returns whether the engine may run: fuel in the tank, or fuel not required */
    public boolean hasFuel() {
        return !WheelsConfig.FUEL_REQUIRED.get() || tank().hasFuel();
    }

    /** effects: returns how full the tank is for the gauge, 1 when fuel is not required */
    public double fuelFraction() {
        return WheelsConfig.FUEL_REQUIRED.get() ? tank().fraction() : 1.0;
    }

    // --- interaction -----------------------------------------------------

    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        VehicleProfile p = profile();
        if (p == null) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            // The tongue, when hitched: let go.
            if (held.isEmpty() && tower() != null && p.hitch().front().isPresent() && inRegion(p, p.hitch().front().get(), 0.9, hit)) {
                if (!level().isClientSide()) {
                    unhitch();
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
            // A door, empty-handed: open it; open with animals aboard, let them out; open and empty, shut it.
            if (held.isEmpty() && nearADoor(p, hit)) {
                if (!level().isClientSide()) {
                    if (!doorsOpen()) {
                        toggleDoors();
                    } else if (!animals().isEmpty()) {
                        unload();
                    } else {
                        toggleDoors();
                    }
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
            if (held.is(ModContent.WRENCH.get())) {
                if (!level().isClientSide()) {
                    pickUp(player);
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
            if (p.radio().isPresent() && isDisc(held) && disc().isEmpty()) {
                if (!level().isClientSide()) {
                    ItemStack one = held.copyWithCount(1);
                    entityData.set(DATA_DISC, one);
                    held.consume(1, player);
                    level().gameEvent(GameEvent.BLOCK_CHANGE, blockPosition(), GameEvent.Context.of(player));
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
            if (p.radio().isPresent() && held.isEmpty() && !disc().isEmpty() && inRegion(p, p.radio().get().at(), 1.2, hit)) {
                if (!level().isClientSide()) {
                    player.getInventory().placeItemBackInInventory(disc());
                    entityData.set(DATA_DISC, ItemStack.EMPTY);
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
            if (p.storage().isPresent() && inStorageRegion(p, hit)) {
                if (!level().isClientSide()) {
                    openCustomInventoryScreen(player);
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
            return InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        VehicleProfile p = profile();
        if (p == null) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        // Animals on a lead board through the open doors.
        if (p.cargo().isPresent() && held.is(Items.LEAD)) {
            if (!level().isClientSide()) {
                load(player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (p.fuel().isPresent() && !held.isEmpty()) {
            int burn = held.getBurnTime(null);
            if (burn > 0) {
                if (!level().isClientSide()) {
                    Tank tank = tank();
                    if (tank.accepts(burn)) {
                        entityData.set(DATA_FUEL, tank.fill(burn).ticks());
                        ItemStack remainder = held.getCraftingRemainingItem();
                        held.consume(1, player);
                        if (!remainder.isEmpty()) {
                            player.getInventory().placeItemBackInInventory(remainder);
                        }
                        level().playSound(null, getX(), getY(), getZ(), ModContent.FUEL_POUR.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
                        player.displayClientMessage(Component.translatable("vanillawheels.fuel", Math.round(tank().fraction() * 100)), true);
                    } else {
                        player.displayClientMessage(Component.translatable("vanillawheels.tank_full"), true);
                    }
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
        }
        if (!level().isClientSide()) {
            if (canAddPassenger(player)) {
                player.startRiding(this);
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        return canAddPassenger(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** effects: returns whether {@code hit} is within a block and a half of any door's hinge */
    private boolean nearADoor(VehicleProfile p, Vec3 hit) {
        for (VehicleProfile.Door d : p.doors()) {
            if (inRegion(p, d.hinge(), 1.5, hit)) {
                return true;
            }
        }
        return false;
    }

    /**
     * effects: every animal {@code player} holds on a lead within ten
     * blocks boards, one by one, while the cargo has room and the doors
     * are open; each lead comes back to the player; the player is told
     * when the doors are shut or the trailer is full
     */
    private void load(Player player) {
        if (!doorsOpen()) {
            player.displayClientMessage(Component.translatable("vanillawheels.doors_shut"), true);
            return;
        }
        List<Animal> led = level().getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(10.0), a -> a.getLeashHolder() == player);
        int boarded = 0;
        for (Animal a : led) {
            if (!canAddPassenger(a)) {
                continue;
            }
            a.dropLeash(true, false);
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.LEAD));
            if (a.startRiding(this, true)) {
                boarded++;
            }
        }
        if (boarded == 0 && !led.isEmpty()) {
            player.displayClientMessage(Component.translatable("vanillawheels.trailer_full"), true);
        }
    }

    /** effects: every animal aboard steps off behind the vehicle, spread across its width */
    private void unload() {
        VehicleProfile p = profile();
        List<Animal> aboard = animals();
        if (p == null || aboard.isEmpty()) {
            return;
        }
        double back = -(p.body().length() / 2.0 + 1.0);
        for (int i = 0; i < aboard.size(); i++) {
            Animal a = aboard.get(i);
            double across = (i - (aboard.size() - 1) / 2.0) * 0.9;
            Vec3 at = position().add(rotate(new Vec(across, 0.0, back - (i / 3) * 1.2)));
            a.stopRiding();
            a.setPos(at.x, at.y + 0.1, at.z);
        }
    }

    /** effects: returns whether {@code hit} (relative to the body's position) is inside the storage's region, or anywhere if none is named */
    private boolean inStorageRegion(VehicleProfile p, Vec3 hit) {
        VehicleProfile.Storage s = p.storage().orElseThrow();
        if (s.region().isEmpty()) {
            return true;
        }
        Vec3 local = hit.yRot(getYRot() * Mth.DEG_TO_RAD);
        Vec mesh = p.toLocal().apply(new Vec(local.x / p.scale(), local.y / p.scale(), local.z / p.scale()));
        return s.region().get().selector().region().contains(mesh);
    }

    /** effects: returns whether {@code hit} is within {@code radius} blocks of the profile point {@code at} */
    private boolean inRegion(VehicleProfile p, Vec at, double radius, Vec3 hit) {
        Vec3 where = rotate(p.localBlocks(at));
        return where.distanceTo(hit) <= radius;
    }

    /** effects: takes this vehicle back into {@code player}'s hand as an item, spilling the chest */
    private void pickUp(Player player) {
        ItemStack item = toItem();
        if (!player.getInventory().add(item)) {
            spawnAtLocation(item);
        }
        Containers.dropContents(level(), this, this);
        level().playSound(null, getX(), getY(), getZ(), ModContent.WRENCH_CLANK.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        ejectPassengers();
        discard();
    }

    /** effects: returns this vehicle as an item: its profile, paint, fuel and disc */
    public ItemStack toItem() {
        ItemStack stack = ModContent.vehicleStack(profileId);
        DyeColor paint = paint();
        if (paint != null) {
            stack.set(ModContent.PAINT.get(), paint);
        }
        stack.set(ModContent.FUEL.get(), tank().ticks());
        if (!disc().isEmpty()) {
            stack.set(ModContent.DISC.get(), disc().copy());
        }
        return stack;
    }

    /** effects: takes the paint, fuel and disc {@code stack} carries */
    public void loadFromItem(ItemStack stack) {
        DyeColor paint = stack.get(ModContent.PAINT.get());
        if (paint != null) {
            setPaint(paint);
        }
        Integer fuel = stack.get(ModContent.FUEL.get());
        if (fuel != null) {
            entityData.set(DATA_FUEL, Math.min(fuel, tank().capacity()));
        }
        ItemStack disc = stack.get(ModContent.DISC.get());
        if (disc != null && !disc.isEmpty()) {
            entityData.set(DATA_DISC, disc.copy());
        }
    }

    // --- the chest -------------------------------------------------------

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (profile() == null || profile().storage().isEmpty()) {
            return;
        }
        player.openMenu(this);
        if (!player.level().isClientSide()) {
            gameEvent(GameEvent.CONTAINER_OPEN, player);
        }
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        VehicleProfile p = profile();
        if (p == null || p.storage().isEmpty()) {
            return null;
        }
        unpackChestVehicleLootTable(player);
        int rows = p.storage().get().rows();
        MenuType<ChestMenu> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        return new ChestMenu(type, id, inventory, this, rows);
    }

    @Override
    public Component getDisplayName() {
        return getName();
    }

    @Override
    public NonNullList<ItemStack> getItemStacks() {
        return items;
    }

    @Override
    public void clearItemStacks() {
        items.clear();
    }

    @Override
    @Nullable
    public ResourceKey<LootTable> getLootTable() {
        return lootTable;
    }

    @Override
    public void setLootTable(@Nullable ResourceKey<LootTable> lootTable) {
        this.lootTable = lootTable;
    }

    @Override
    public long getLootTableSeed() {
        return lootTableSeed;
    }

    @Override
    public void setLootTableSeed(long seed) {
        this.lootTableSeed = seed;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public ItemStack getItem(int slot) {
        return getChestVehicleItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return removeChestVehicleItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeChestVehicleItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        setChestVehicleItem(slot, stack);
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return isChestVehicleStillValid(player);
    }

    @Override
    public void clearContent() {
        clearChestVehicleContent();
    }

    @Override
    public boolean isEmpty() {
        return isChestVehicleEmpty();
    }

    // --- life and death --------------------------------------------------

    @Override
    protected Item getDropItem() {
        return ModContent.VEHICLE_ITEM.get();
    }

    @Override
    protected void destroy(net.minecraft.world.damagesource.DamageSource source) {
        kill();
        if (level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            spawnAtLocation(toItem());
        }
        chestVehicleDestroyed(source, level(), this);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy()) {
            Containers.dropContents(level(), this, this);
            // Gone for good: whatever was hitched to it is on its own.
            unhitch();
            Vehicle trailer = trailer();
            if (trailer != null) {
                trailer.unhitch();
            }
            trailerUuid = null;
        }
        super.remove(reason);
    }

    @Override
    public Component getName() {
        Component custom = getCustomName();
        if (custom != null) {
            return custom;
        }
        return Component.translatable("vehicle." + profileId.getNamespace() + "." + profileId.getPath());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Profile", profileId.toString());
        tag.putInt("Paint", entityData.get(DATA_PAINT));
        tag.putInt("Fuel", entityData.get(DATA_FUEL));
        tag.putByte("Lights", entityData.get(DATA_LIGHTS));
        if (!disc().isEmpty()) {
            tag.put("Disc", disc().save(registryAccess()));
        }
        if (towerUuid != null) {
            tag.putUUID("Tower", towerUuid);
        }
        if (trailerUuid != null) {
            tag.putUUID("Trailer", trailerUuid);
        }
        tag.putBoolean("DoorsOpen", doorsOpen());
        addChestVehicleSaveData(tag, registryAccess());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Profile"));
        entityData.set(DATA_PAINT, tag.getInt("Paint"));
        entityData.set(DATA_FUEL, tag.getInt("Fuel"));
        entityData.set(DATA_LIGHTS, tag.getByte("Lights"));
        if (tag.contains("Disc")) {
            entityData.set(DATA_DISC, ItemStack.parse(registryAccess(), tag.getCompound("Disc")).orElse(ItemStack.EMPTY));
        }
        towerUuid = tag.hasUUID("Tower") ? tag.getUUID("Tower") : null;
        trailerUuid = tag.hasUUID("Trailer") ? tag.getUUID("Trailer") : null;
        entityData.set(DATA_DOORS, tag.getBoolean("DoorsOpen"));
        doorSwing = doorsOpen() ? 1.0f : 0.0f;
        doorSwingO = doorSwing;
        if (id != null) {
            setProfile(id);
        }
        readChestVehicleSaveData(tag, registryAccess());
        drive = Drive.atRest(Math.toRadians(getYRot()));
    }

    /** effects: when a vehicle joins a client level, starts its sounds */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Vehicle vehicle) {
            Controls.onVehicleJoined(vehicle);
        }
    }

    /** effects: returns the song the loaded disc plays, if any */
    public Optional<JukeboxSong> song() {
        ItemStack disc = disc();
        if (disc.isEmpty()) {
            return Optional.empty();
        }
        HolderLookup.Provider registries = registryAccess();
        return JukeboxSong.fromStack(registries, disc).map(net.minecraft.core.Holder::value);
    }

    /** effects: returns whether {@code stack} is a disc a radio takes: anything a jukebox plays */
    public static boolean isDisc(ItemStack stack) {
        return stack.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE);
    }
}
