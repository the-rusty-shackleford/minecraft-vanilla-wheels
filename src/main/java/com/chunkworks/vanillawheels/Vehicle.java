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
import com.chunkworks.vanillawheels.domain.RayBox;
import com.chunkworks.vanillawheels.domain.Suspension;
import com.chunkworks.vanillawheels.domain.Terrain;
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
    /** How hard the boost burns, 0..1, for the flames every client draws. */
    private static final EntityDataAccessor<Float> DATA_BURN = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.FLOAT);
    /** The drawn pose as the simulating side computed it, for every other side to draw and seat with. */
    private static final EntityDataAccessor<Float> DATA_LIFT = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<ItemStack> DATA_DISC = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.ITEM_STACK);
    /** The entity id of the vehicle towing this one, -1 for none. */
    private static final EntityDataAccessor<Integer> DATA_TOWER = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
    /** The entity id of the trailer this one tows, -1 for none. */
    private static final EntityDataAccessor<Integer> DATA_TRAILER = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DOORS = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.BOOLEAN);
    /** Which chests are open, a bit each: a chest's lid is up while its bit is set. The server counts the openers per chest. */
    private static final EntityDataAccessor<Integer> DATA_OPEN = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
    private final int[] openers = new int[8];
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
    /** Whether the last tick stepped the drive here: the wheel was this instance's. */
    private boolean wasAtTheWheel;
    private Suspension suspension = Suspension.LEVEL;
    private Suspension suspensionO = Suspension.LEVEL;
    /** The terrain pose's memory: the eased body and its springs, absolute height. Null until first posed. */
    @Nullable private Terrain.Pose pose;
    private Terrain.TowedPose towedPose = Terrain.TowedPose.LEVEL;
    /** Towing's bookkeeping: the pass of this body's last own tick, the pass its tower last moved it, and the old pose to restore (see tick). */
    long lastOwnPass = Long.MIN_VALUE;
    private long lastFollowedPass = Long.MIN_VALUE;
    @Nullable private double[] preFollow;
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
    /** The ground under the axles and the sides as last probed, relative to the body. */
    /** The chest lid on the client, 0 shut to 1 open, eased toward whether anyone has the storage open. */
    /** The chests' lids on the client, 0 shut to 1 open each, eased toward whether anyone has the chest open. */
    private final float[] lid = new float[8];
    private final float[] lidO = new float[8];
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
            int slots = p.storage().map(VehicleProfile.Storage::slots).orElse(0);
            if (items.size() != slots) {
                items = NonNullList.withSize(slots, ItemStack.EMPTY);
            }
            // A profile with a factory colour leaves a new vehicle unpainted, wearing that colour until a dye replaces it.
            if (p.paint().isPresent() && p.paint().get().factory().isEmpty() && entityData.get(DATA_PAINT) < 0) {
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
        builder.define(DATA_BURN, 0.0f);
        builder.define(DATA_LIFT, 0.0f);
        builder.define(DATA_PITCH, 0.0f);
        builder.define(DATA_ROLL, 0.0f);
        builder.define(DATA_DISC, ItemStack.EMPTY);
        builder.define(DATA_TOWER, -1);
        builder.define(DATA_TRAILER, -1);
        builder.define(DATA_DOORS, false);
        builder.define(DATA_OPEN, 0);
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

        /** effects: returns whether walkers meet this box: only a seatless vehicle's (a trailer's), so nobody walks through its body; a seated vehicle's riders dismount beside the body and would land inside a solid box */
        @Override
        public boolean canBeCollidedWith() {
            VehicleProfile p = getParent().profile();
            return box != null && p != null && p.seats().isEmpty();
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
    /** The scale modifier everyone aboard wears: the profile's rider scale, as a multiplier on the game's own. */
    private static final ResourceLocation RIDER_SCALE_ID = VanillaWheels.id("rider_scale");

    /**
     * effects: on the server, sizes a living passenger to the profile's
     * rider scale for as long as it is aboard -- the game's scale attribute,
     * which scales the model, the box and the eye together and is synced to
     * every client -- and takes the size back off as it leaves. A transient
     * modifier, never saved: a passenger loaded aboard is sized again as it
     * is re-added, and one that leaves by any door is unsized.
     */
    private void sizeRider(Entity passenger, boolean aboard) {
        VehicleProfile p = profile();
        if (level().isClientSide() || !(passenger instanceof LivingEntity living) || p == null) {
            return;
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance scale = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (scale == null) {
            return;
        }
        scale.removeModifier(RIDER_SCALE_ID);
        if (aboard && p.riderScale() != 1.0) {
            scale.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RIDER_SCALE_ID, p.riderScale() - 1.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        sizeRider(passenger, true);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        sizeRider(passenger, false);
    }

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

    /** effects: returns how far chest {@code index}'s lid is open, 0 shut to 1 open, eased the way the game eases a chest's */
    public float lidOpenness(int index, float partialTick) {
        float o = 1.0f - Mth.lerp(partialTick, lidO[index], lid[index]);
        return 1.0f - o * o * o;
    }

    /** effects: on the server, counts a player in at chest {@code index}: its lid rises, with the chest's sound the first time */
    void startOpen(int index) {
        if (!level().isClientSide()) {
            if (openers[index]++ == 0) {
                entityData.set(DATA_OPEN, entityData.get(DATA_OPEN) | (1 << index));
                Vec3 at = chestWorld(index);
                level().playSound(null, at.x, at.y, at.z, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f, level().random.nextFloat() * 0.1f + 0.9f);
            }
        }
    }

    /** effects: on the server, counts a player out of chest {@code index}: the last one shuts the lid, with its sound */
    void stopOpen(int index) {
        if (!level().isClientSide()) {
            openers[index] = Math.max(0, openers[index] - 1);
            if (openers[index] == 0) {
                entityData.set(DATA_OPEN, entityData.get(DATA_OPEN) & ~(1 << index));
                Vec3 at = chestWorld(index);
                level().playSound(null, at.x, at.y, at.z, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, level().random.nextFloat() * 0.1f + 0.9f);
            }
        }
    }

    /** The game's container hooks, for the whole vehicle as one container (a hopper, a loot fill): nothing to show */
    @Override
    public void startOpen(Player player) {}

    @Override
    public void stopOpen(Player player) {}

    /** effects: returns where chest {@code index} sits in the world */
    private Vec3 chestWorld(int index) {
        VehicleProfile p = profile();
        if (p == null || p.storage().isEmpty() || index >= p.storage().get().chests().size()) {
            return position();
        }
        return position().add(rotate(p.localBlocks(p.storage().get().chests().get(index).at())));
    }

    /** How far past the hit point a click's ray is followed for a chest, blocks: into a bed, not across the body. */
    private static final double CHEST_REACH = 1.5;

    /**
     * effects: returns the index of the first chest the click's ray meets, from {@code hit}
     * (relative to the body's position, the world's frame) onward along the line from
     * {@code eye} through it, within {@code reach} blocks: each chest the game's double chest's
     * own box at the profile's scale, turned by the chest's yaw and the body's; -1 for none.
     * With no reach, the chest the hit itself lands in. The ray, not only the point: the hit
     * lands on the hull's box, and a chest in a bed sits inside it, so a click aimed down at
     * the chest from outside lands on the hull over it.
     */
    private int chestAt(VehicleProfile p, Vec3 hit, Vec3 eye, double reach) {
        if (p.storage().isEmpty()) {
            return -1;
        }
        Vec3 ray = hit.add(position()).subtract(eye);
        Vec3 dir = ray.lengthSqr() < 1.0e-6 ? new Vec3(0.0, -1.0, 0.0) : ray.normalize();
        float body = getYRot() * Mth.DEG_TO_RAD;
        Vec3 o = hit.yRot(body), d = dir.yRot(body);   // into the body's frame
        List<VehicleProfile.Chest> chests = p.storage().get().chests();
        int found = -1;
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < chests.size(); i++) {
            VehicleProfile.Chest c = chests.get(i);
            Vec at = p.localBlocks(c.at());
            float yaw = (float) Math.toRadians(c.yaw());
            Vec3 oc = new Vec3(o.x - at.x(), o.y - at.y(), o.z - at.z()).yRot(yaw), dc = d.yRot(yaw);
            double hw = VehicleProfile.Chest.WIDTH * c.scale() / 2.0 + 0.05, hd = VehicleProfile.Chest.DEPTH * c.scale() / 2.0 + 0.05;
            double t = RayBox.enter(new Vec(oc.x, oc.y, oc.z), new Vec(dc.x, dc.y, dc.z), new Vec(-hw, -0.1, -hd), new Vec(hw, VehicleProfile.Chest.HEIGHT * c.scale() + 0.15, hd));
            if (t >= 0.0 && t <= reach && t < nearest) {
                nearest = t;
                found = i;
            }
        }
        return found;
    }

    /** effects: opens chest {@code index} for {@code player}: its own rows of the vehicle's items, as a chest screen */
    public void openChest(Player player, int index) {
        VehicleProfile p = profile();
        if (p == null || p.storage().isEmpty() || index < 0 || index >= p.storage().get().chests().size()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        unpackChestVehicleLootTable(player);
        VehicleProfile.Chest c = p.storage().get().chests().get(index);
        ChestSlice slice = new ChestSlice(index, p.storage().get().firstSlot(index), c.rows() * 9);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, pl) -> new ChestMenu(switch (c.rows()) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        }, id, inventory, slice, c.rows()), getName()));
        gameEvent(GameEvent.CONTAINER_OPEN, player);
    }

    /** One chest's rows of the vehicle's items, as a container of its own: what its screen edits. */
    private final class ChestSlice implements net.minecraft.world.Container {
        private final int index, first, size;

        ChestSlice(int index, int first, int size) {
            this.index = index;
            this.first = first;
            this.size = size;
        }

        @Override public int getContainerSize() { return size; }
        @Override public boolean isEmpty() { for (int i = 0; i < size; i++) { if (!getItem(i).isEmpty()) return false; } return true; }
        @Override public ItemStack getItem(int slot) { return Vehicle.this.getItem(first + slot); }
        @Override public ItemStack removeItem(int slot, int amount) { return Vehicle.this.removeItem(first + slot, amount); }
        @Override public ItemStack removeItemNoUpdate(int slot) { return Vehicle.this.removeItemNoUpdate(first + slot); }
        @Override public void setItem(int slot, ItemStack stack) { Vehicle.this.setItem(first + slot, stack); }
        @Override public void setChanged() { Vehicle.this.setChanged(); }
        @Override public boolean stillValid(Player player) { return Vehicle.this.stillValid(player); }
        @Override public void clearContent() { for (int i = 0; i < size; i++) { Vehicle.this.setItem(first + i, ItemStack.EMPTY); } }
        @Override public void startOpen(Player player) { Vehicle.this.startOpen(index); }
        @Override public void stopOpen(Player player) { Vehicle.this.stopOpen(index); }
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
            // Anchored on the eye, nfx's way: the game hangs a rider's eye a fixed height straight up
            // from the attachment, so on a tilted body an upright rider's head left the cabin. The
            // eye point is what turns with the body, and the attachment hangs back down from it.
            VehicleProfile.Seat seat0 = p.seats().get(seat);
            double e = eyeOver(entity);
            Vec eye = seat0.eye().map(p::localBlocks).orElseGet(() -> p.localBlocks(seat0.at()).plus(new Vec(0.0, e, 0.0)));
            Suspension s = suspension(partialTick);
            Vec3 at = posed(eye, s);
            return new Vec3(at.x, at.y - e + s.lift(), at.z);
        }
        Suspension s = suspension(partialTick);
        Vec3 at = posed(local, s);
        return new Vec3(at.x, at.y + s.lift(), at.z);
    }

    /** effects: returns how far {@code rider}'s eye sits over its vehicle attachment, blocks, at its present size */
    static double eyeOver(Entity rider) {
        return rider instanceof LivingEntity ? Math.max(0.0, rider.getEyeHeight() - rider.getVehicleAttachmentPoint(rider.getVehicle() == null ? rider : rider.getVehicle()).y) : 0.0;
    }

    /**
     * effects: returns how far, in the world's frame, {@code rider} is drawn
     * from where its entity is: zero for a seat without an eye, else from the
     * seat's eye to the seat itself, so the body sits in the seat while the
     * entity, and so the camera, is at the eye
     */
    public Vec3 drawOffset(Entity rider, float partialTick) {
        VehicleProfile p = profile();
        int seat = p == null ? -1 : seatOf(rider);
        if (seat < 0 || seat >= p.seats().size() || p.seats().get(seat).eye().isEmpty()) {
            return Vec3.ZERO;
        }
        VehicleProfile.Seat at = p.seats().get(seat);
        Suspension s = suspension(partialTick);
        double e = eyeOver(rider);
        return posed(p.localBlocks(at.at()).plus(new Vec(0.0, e, 0.0)), s).subtract(posed(p.localBlocks(at.eye().get()), s));
    }

    /**
     * effects: returns {@code local} (blocks, the body's frame) turned as the
     * body is drawn under {@code s}: rolled, pitched, then yawed -- so a seat
     * rides up with the nose on a climb and leans with the body in a bank
     */
    public Vec3 posed(Vec local, Suspension s) {
        // Vec3's xRot and zRot turn the other way from the renderer's Axis.XP and Axis.ZP rotations.
        return new Vec3(local.x(), local.y(), local.z()).zRot((float) -s.roll()).xRot((float) -s.pitch()).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        // Riders turn with the body, as a boat's do: the view goes round with the drift instead of
        // sitting still in the world while the body spins under it to the clamp.
        float turn = Mth.wrapDegrees(getYRot() - yRotO);
        passenger.setYRot(passenger.getYRot() + turn);
        passenger.setYHeadRot(passenger.getYHeadRot() + turn);
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

    /** True where this side simulates this body's tow behind {@code tower}: the server, or the driver's client. */
    private boolean simulatesTow(Vehicle tower) {
        return tower.trailer() == this && (!level().isClientSide() || isControlledByLocalInstance());
    }

    /**
     * effects: called from {@code tower}'s tick: moves this body behind it
     * unless it already caught up in its own tick this pass, or ticks later
     * this pass and will catch up then -- after the level's snapshot of its
     * old position, so the old and new bracket one tick of motion. A body
     * moved from its tower's tick before its own is drawn in steps otherwise:
     * the snapshot overwrites "old" with the moved position (nfx's finding).
     */
    void followFrom(Vehicle tower, long pass) {
        if (!simulatesTow(tower) || lastFollowedPass == pass) {
            return;
        }
        if (lastOwnPass == pass - 1) {
            return;   // ticks after the tower this pass: it catches up at the top of its own tick
        }
        preFollow = new double[] {pass, xOld, yOld, zOld, yRotO};
        moveBehind(tower, pass);
        VehicleProfile p = profile();
        if (lastOwnPass == pass && p != null) {
            poseTowed(p, tower);   // it ticked before the tower: pose it here, at the position it was moved to
        }
    }

    /**
     * effects: poses this towed body, where the pose is this side's to compute: a driver's client
     * for the chain it drives, the server for a chain nobody drives. The server moves its copy of a
     * player's trailer but takes the pose the driver's client shares, so the two never fight.
     */
    private void poseTowed(VehicleProfile p, Vehicle tower) {
        if (!level().isClientSide() && playerAtTheHead()) {
            takeSharedPose();
            return;
        }
        if (!rideTowed(p, tower)) {
            rideTheGround(p);
        }
        sharePose();
    }

    /** effects: returns whether a player drives the head of this body's tow chain */
    private boolean playerAtTheHead() {
        Vehicle head = this;
        for (int i = 0; i < 8 && head.tower() != null; i++) {
            head = head.tower();
        }
        return head.getControllingPassenger() instanceof Player;
    }

    /** effects: draws and seats with the pose the simulating side shared */
    private void takeSharedPose() {
        suspension = new Suspension(entityData.get(DATA_LIFT), entityData.get(DATA_PITCH), entityData.get(DATA_ROLL));
    }

    /** The last pose shared, to share only a change worth a packet. */
    private float sharedLift = Float.NaN, sharedPitch, sharedRoll;

    /**
     * effects: gives the pose this side just computed to every other side: the server sets the
     * synced data straight, a client tells the server, which sets it for all; only when it moved
     * more than a hair since the last share
     */
    private void sharePose() {
        float lift = (float) suspension.lift(), pitch = (float) suspension.pitch(), roll = (float) suspension.roll();
        if (!Float.isNaN(sharedLift) && Math.abs(lift - sharedLift) < 0.005f && Math.abs(pitch - sharedPitch) < 0.003f && Math.abs(roll - sharedRoll) < 0.003f) {
            return;
        }
        sharedLift = lift;
        sharedPitch = pitch;
        sharedRoll = roll;
        if (level().isClientSide()) {
            Controls.sharePose(this, lift, pitch, roll);
        } else {
            onPose(lift, pitch, roll);
        }
    }

    /** effects: takes the pose the simulating side computed (a driver's client, or this server for its own) */
    public void onPose(float lift, float pitch, float roll) {
        entityData.set(DATA_LIFT, lift);
        entityData.set(DATA_PITCH, pitch);
        entityData.set(DATA_ROLL, roll);
        if (!level().isClientSide()) {
            suspension = new Suspension(lift, pitch, roll);
        }
    }

    private void moveBehind(Vehicle tower, long pass) {
        follow(tower);
        lastFollowedPass = pass;
    }

    /**
     * effects: one tick of being towed by {@code tower}: the tongue is put
     * on the tower's hitch by {@link Tow}, the body moved there through the
     * world (so it climbs and falls like anything else), turned to the new
     * heading, its wheels rolled; if the world held it back so far that the
     * tongue is STRETCH from the hitch in the ground plane, the server lets
     * go. Tows its own trailer on in turn. Distances are horizontal: a
     * drawbar is rigid only in the ground plane, each body keeps its own
     * height, and a 3-D test spent the catch radius on the height between a
     * low coupler and a high ball and broke the chain at a one-block step.
     */
    private void follow(Vehicle tower) {
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
        drive = Drive.onRails(f.travelled(), f.heading(), 0.0, 0);
        wheelTravel += f.travelled();
        entityData.set(DATA_SPEED, (float) f.travelled());
        entityData.set(DATA_STEER, 0.0f);
        if (!level().isClientSide()) {
            Vec3 tongue = tongue();
            if (tongue != null && flatDistance(tongue, tower.hitchPoint()) > STRETCH) {
                unhitch();
            }
        }
        Vehicle next = trailer();
        if (next != null) {
            next.followFrom(this, TickClock.now(level()));
        }
    }

    /** effects: returns the distance between {@code a} and {@code b} in the ground plane */
    public static double flatDistance(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** effects: returns the profile id of the trailer {@code stack} holds -- a built vehicle with a front hitch -- or null */
    @Nullable
    private ResourceLocation trailerProfile(ItemStack stack) {
        if (!(stack.getItem() instanceof VehicleItem)) {
            return null;
        }
        ResourceLocation id = VanillaWheels.vehicleOf(stack).orElse(null);
        if (id == null) {
            return null;
        }
        VehicleProfile tp = VanillaWheels.profile(level().registryAccess(), id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
        return tp != null && tp.hitch().front().isPresent() ? id : null;
    }

    /**
     * effects: on the server, puts the trailer in {@code stack} behind this vehicle with its
     * coupler on the hitch ball, facing the same way, hitched; as the item's own placement, the
     * item's saved state is loaded, the spot must be clear (told on the screen otherwise), and
     * survival consumes the item. A trailer that fails the room check was never added, and is not
     * discarded: removing it would spill its loaded contents.
     */
    private void placeTrailer(ServerLevel server, ItemStack stack, Player player) {
        ResourceLocation id = trailerProfile(stack);
        Vec3 ball = hitchPoint();
        if (id == null || ball == null || trailer() != null) {
            return;
        }
        Vehicle t = Vehicle.create(server, id, ball, getYRot());
        if (t == null) {
            return;
        }
        t.loadFromItem(stack);
        VehicleProfile tp = t.profile();
        Vec3 coupler = t.rotate(tp.localBlocks(tp.hitch().front().get()));
        t.setPos(ball.x - coupler.x, getY(), ball.z - coupler.z);
        t.setYRot(getYRot());
        t.yRotO = getYRot();
        if (!server.noCollision(t, t.getBoundingBox().deflate(0.05))) {
            player.displayClientMessage(Component.translatable("vanillawheels.no_room"), true);
            return;
        }
        server.addFreshEntity(t);
        hitch(t);
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
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
            if (tongue != null && flatDistance(tongue, hitch) < CATCH && other.trailer() != this) {
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
        System.arraycopy(lid, 0, lidO, 0, lid.length);
        if (p == null) {
            return;
        }
        if (level().isClientSide()) {
            doorSwing = Mth.clamp(doorSwing + (doorsOpen() ? 0.15f : -0.15f), 0.0f, 1.0f);
            int open = entityData.get(DATA_OPEN);
            for (int i = 0; i < lid.length; i++) {
                lid[i] = Mth.clamp(lid[i] + ((open & (1 << i)) != 0 ? 0.1f : -0.1f), 0.0f, 1.0f);
            }
        }
        // Towing's bookkeeping: which pass this is, and whether the tower has moved this body yet.
        long pass = TickClock.now(level());
        lastOwnPass = pass;
        if (preFollow != null && (long) preFollow[0] == pass) {
            // The tower's tick moved this body before this, its own, tick: the level then snapshotted
            // "old" as the new position, so the renderer would draw no motion. Put the old back.
            xOld = xo = preFollow[1];
            yOld = yo = preFollow[2];
            zOld = zo = preFollow[3];
            yRotO = (float) preFollow[4];
            preFollow = null;
        }
        Vehicle tower = tower();
        boolean towedHere = tower != null && simulatesTow(tower);
        if (towedHere && lastFollowedPass != pass && tower.lastOwnPass == pass) {
            moveBehind(tower, pass);   // the tower ticked first: catch up now, and pose below at the new position
        }
        // The wheel is this side's when the game says so on a client, and on the server whenever no
        // player holds it: a player's own client drives, and the server only mirrors. (The game's own
        // test asks the player, whose test double answers "local" everywhere.)
        boolean atTheWheel = tower == null && (level().isClientSide() ? isControlledByLocalInstance() : !(getControllingPassenger() instanceof Player));
        if (atTheWheel && !wasAtTheWheel) {
            // Taking the wheel: drive on from where the world has this, not from where this copy was born.
            // A client boards a copy it may have seen for a single tick, or none, so there is no earlier
            // tick to have kept the model current.
            drive = synced();
        }
        wasAtTheWheel = atTheWheel;
        if (atTheWheel) {
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
            if (moveKept < 0.999) {
                // A wall: the speed the world refused is gone, not spent spinning the wheels against
                // it -- all of it met square, a little scraped along at a slant.
                drive = drive.slowed(moveKept);
            }
            wheelTravel += drive.speed();
            if (level().isClientSide()) {
                entityData.set(DATA_SPEED, (float) drive.speed());
                entityData.set(DATA_STEER, (float) drive.steer());
                entityData.set(DATA_DRIFTING, drive.drifting());
                entityData.set(DATA_BURN, (float) drive.burn(tuning));
            } else {
                syncDriveData();
            }
        } else if (!towedHere) {
            setDeltaMovement(Vec3.ZERO);
            wheelTravel += entityData.get(DATA_SPEED);
            drive = synced();
        }
        if (towedHere) {
            if (lastFollowedPass == pass) {
                poseTowed(p, tower);
            }
            // else: the tower ticks later this pass and moves and poses this body then
        } else if (atTheWheel) {
            rideTheGround(p);
            sharePose();
        } else {
            takeSharedPose();
        }
        // The trailer goes where this went, this very pass, wherever this is moved: the driver's client or the server.
        Vehicle trailer = trailer();
        if (trailer != null && (!level().isClientSide() || isControlledByLocalInstance())) {
            trailer.followFrom(this, pass);
        }
        placeParts();
        if (level().isClientSide()) {
            Controls.exhaust(this);   // after the move, so the tail's travel this tick is known
        } else {
            serverTick(p);
        }
    }

    /** effects: returns the drive as the world tells it: the synced speed, steer and drift, on rails along the yaw */
    private Drive synced() {
        float steer = entityData.get(DATA_STEER);
        boolean drifting = entityData.get(DATA_DRIFTING);
        return Drive.onRails(entityData.get(DATA_SPEED), Math.toRadians(getYRot()), steer, drifting ? (steer < 0 ? -1 : 1) : 0);
    }

    /**
     * effects: poses the body on the ground under it, nfx's way ({@link Terrain}): the wheels
     * probed as discs from where the last pose drew them, one plane fitted through the terrain
     * over the whole footprint, pitch and roll on critically damped springs toward it, the height
     * on the plane within the sink and float bounds, the descending end kept clear -- so a
     * staircase is one steady angle, a wall flattens the fit, and a physics step-up never shows
     */
    void rideTheGround(VehicleProfile p) {
        pose = Terrain.step(columns(), frame(), shape(p), getY(), pose == null ? Terrain.Pose.level(getY() + suspension.lift()) : pose);
        suspension = pose.suspension(getY());
    }

    /**
     * effects: poses this body as towed by {@code tower}: a lever on its axle hung from the
     * tower's drawn hitch ball, the axle on a line along its own tracks ({@link Terrain#towed});
     * returns false, leaving the pose to {@link #rideTheGround}, when either end lacks a hitch
     */
    private boolean rideTowed(VehicleProfile p, Vehicle tower) {
        VehicleProfile tp = tower.profile();
        if (tp == null || tp.hitch().rear().isEmpty() || p.hitch().front().isEmpty()) {
            return false;
        }
        Suspension ts = tower.suspension(1.0f);
        Vec ball = tp.localBlocks(tp.hitch().rear().get());
        double ballY = tower.getY() + ts.lift()
                + (ball.x() * Math.sin(ts.roll()) + ball.y() * Math.cos(ts.roll())) * Math.cos(ts.pitch()) - ball.z() * Math.sin(ts.pitch());
        Vec coupler = p.localBlocks(p.hitch().front().get());
        Terrain.Towed t = Terrain.towed(columns(), frame(), shape(p), getY(), p.axleForward(), coupler.z(),
                new Terrain.Ball(ballY - getY(), ball.y()), towedPose);
        towedPose = t.next();
        suspension = t.suspension();
        pose = new Terrain.Pose(suspension.pitch(), suspension.roll(), getY() + suspension.lift(), 0.0, 0.0);
        return true;
    }

    /** effects: returns the ground as {@link Terrain} reads it: block collision tops in a column, relative to this body's y */
    private Terrain.Columns columns() {
        double y = getY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return (x, z, lo, hi) -> {
            int bx = Mth.floor(x), bz = Mth.floor(z);
            int floor = Mth.floor(y + lo);
            for (int by = Mth.floor(y + hi); by >= floor; by--) {
                net.minecraft.world.phys.shapes.VoxelShape shape = level().getBlockState(pos.set(bx, by, bz)).getCollisionShape(level(), pos);
                if (shape.isEmpty()) {
                    continue;
                }
                double top = by + shape.max(net.minecraft.core.Direction.Axis.Y) - y;
                // Ground stands on something: the solid run this block belongs to must reach down to
                // the body's own floor level, or as far down as the sensing looks. A run with air
                // under it -- a canopy, a bridge, a lintel -- is a ceiling, and the ground is below it.
                int b = by;
                net.minecraft.world.phys.shapes.VoxelShape lowest = shape;
                while (b - 1 >= floor - 1) {
                    net.minecraft.world.phys.shapes.VoxelShape under = level().getBlockState(pos.set(bx, b - 1, bz)).getCollisionShape(level(), pos);
                    if (under.isEmpty()) {
                        break;
                    }
                    b--;
                    lowest = under;
                }
                double bottom = b + lowest.min(net.minecraft.core.Direction.Axis.Y) - y;
                if (bottom <= 0.01 || b <= floor) {
                    return top;
                }
                by = b;   // a ceiling: carry on below its run
            }
            return Double.NEGATIVE_INFINITY;
        };
    }

    private Terrain.Frame frame() {
        return new Terrain.Frame(getX(), getZ(), Math.toRadians(getYRot()));
    }

    /** effects: returns what the terrain pose needs of {@code p}: the wheels in the body's frame (+x is the mesh's left, so a wheel's x is minus its "right") */
    private static Terrain.Shape shape(VehicleProfile p) {
        List<VehicleProfile.WheelPosition> wheels = p.wheels().positions();
        double[] wx = new double[wheels.size()], wz = new double[wheels.size()];
        for (int i = 0; i < wheels.size(); i++) {
            wx[i] = -p.blocks(wheels.get(i).right());
            wz[i] = p.blocks(wheels.get(i).forward());
        }
        return new Terrain.Shape(p.blocks(p.wheels().radius()), Math.max(0.0, p.climb()), p.body().length(), p.track(), wx, wz);
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
        entityData.set(DATA_BURN, (float) drive.burn(tuning));
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
        catchTrailer(p);
        if (tickCount % 20 == 0) {
            relink();
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
            drive = new Drive(slowest, drive.heading(), drive.motion(), drive.steer(), drive.driftCharge(), drive.drifting(), drive.driftSide(), drive.boostTicks(), drive.boostPower());
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
    public void onDriveState(float speed, float steer, int throttle, boolean drifting, float burn) {
        this.throttle = throttle;
        entityData.set(DATA_SPEED, speed);
        entityData.set(DATA_STEER, steer);
        entityData.set(DATA_DRIFTING, drifting);
        entityData.set(DATA_BURN, burn);
        drive = new Drive(speed, Math.toRadians(getYRot()), drive.motion(), steer, drive.driftCharge(), drifting, drifting ? (drive.driftSide() == 0 ? (steer < 0 ? -1 : 1) : drive.driftSide()) : 0,
                drive.boostTicks(), drive.boostPower());
    }

    /**
     * effects: moves as the game does, except that a move the driver's
     * client reported (the server re-running it) starts from the ground when
     * this stands on any: the client presses its copy down a little before
     * every move, so it is always on the ground and may always step up,
     * while the delta it reports carries the rise of every step, which by the
     * game's rule leaves this copy airborne after it and unable to step --
     * and the game also shaves a millionth off the reported rise, which sinks
     * this copy into the next block's top just far enough to count as a wall.
     * Without this, a ramp of half steps at speed has the server refuse the
     * client's move every tick and snap the vehicle back.
     */
    @Override
    public void move(MoverType type, Vec3 delta) {
        if (type == MoverType.PLAYER && !level().isClientSide() && !onGround()
                && !level().noCollision(this, getBoundingBox().move(0.0, -0.05, 0.0))) {
            setOnGround(true);
        }
        Vec3 clamped = footprintClamp(delta);
        double x0 = getX(), z0 = getZ();
        super.move(type, clamped);
        double asked = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double got = Math.sqrt((getX() - x0) * (getX() - x0) + (getZ() - z0) * (getZ() - z0));
        moveKept = asked < 1.0E-6 ? 1.0 : Mth.clamp(got / asked, 0.0, 1.0);
    }

    /**
     * Set by {@link #move}: the share of the last move's ground distance the world let through,
     * the footprint's walls and the box's collisions together; 1 for a free move.
     */
    private double moveKept = 1.0;

    /** effects: returns the share of the last move the world let through (see {@link #move}) */
    public double moveKept() {
        return moveKept;
    }

    /** The footprint's points are checked from this far over the climb up to the hull's top; a step the box climbs is no wall. */
    private static final double OVER_CLIMB = 0.05;

    /**
     * effects: returns {@code delta} cut short so that none of the footprint's
     * points -- the nose's and tail's corners and centres, on the body's own
     * rectangle, turned to its yaw -- ends inside a block taller than the
     * climb. The collision box is a square of the body's width, so the
     * overhangs beyond it had no collision at all and a stalled nose ended
     * up inside a wall; this is the collision the overhangs lacked. A move
     * that starts with a point already inside a wall is let through, so a
     * body can always back out. Both sides run it, so the server's re-run of
     * a driver's move agrees with the client's.
     */
    private Vec3 footprintClamp(Vec3 delta) {
        VehicleProfile p = profile();
        if (p == null || (Math.abs(delta.x) < 1.0E-7 && Math.abs(delta.z) < 1.0E-7) || footprintBlockedAt(p, getX(), getZ())) {
            return delta;
        }
        if (!footprintBlockedAt(p, getX() + delta.x, getZ() + delta.z)) {
            return delta;
        }
        // As far as it goes along the move, or along either axis alone -- whichever carries it
        // furthest: a wall met at a slant is slid along, as the game slides a box, instead of
        // stopping the body dead at a corner's graze.
        double t = clampAlong(p, delta.x, delta.z);
        double tx = clampAlong(p, delta.x, 0.0);
        double tz = clampAlong(p, 0.0, delta.z);
        double along = t * Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double xOnly = tx * Math.abs(delta.x), zOnly = tz * Math.abs(delta.z);
        if (xOnly > along && xOnly >= zOnly) {
            return new Vec3(delta.x * tx, delta.y, 0.0);
        }
        if (zOnly > along) {
            return new Vec3(0.0, delta.y, delta.z * tz);
        }
        return new Vec3(delta.x * t, delta.y, delta.z * t);
    }

    /** effects: returns the share of the move ({@code dx}, {@code dz}) from here that leaves every footprint point clear of a wall, a little short of the wall itself */
    private double clampAlong(VehicleProfile p, double dx, double dz) {
        if (Math.abs(dx) < 1.0E-7 && Math.abs(dz) < 1.0E-7) {
            return 0.0;
        }
        if (!footprintBlockedAt(p, getX() + dx, getZ() + dz)) {
            return 1.0;
        }
        double lo = 0.0, hi = 1.0;
        for (int i = 0; i < 7; i++) {
            double mid = (lo + hi) / 2.0;
            if (footprintBlockedAt(p, getX() + dx * mid, getZ() + dz * mid)) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return Math.max(0.0, lo - 0.02);
    }

    /** The walk out to a footprint point samples the ground at most this far apart, blocks. */
    private static final double WALK = 1.0;
    /** The ground is followed down at most this far per sample; a deeper hole is no ground at all. */
    private static final int DROP = 2;

    /**
     * effects: returns whether any of the footprint's points, with the body's origin at (x, z), meets a
     * wall. Each point is reached by a walk from the origin in samples at most a block apart, the
     * ground followed under each -- the top of the highest solid block within the climb of the
     * ground before it, up or down -- and a wall is a solid block that spans the climb line over
     * that ground: its bottom at or under it, its top above it. So a hillside of risers each within
     * the climb of the last is no wall, however many the nose overhangs (a riser every block once
     * stopped a truck whose nose reached the second riser before its box had climbed the first),
     * while a riser taller than the climb is one; and a lintel the hull passes under or a canopy over
     * it is none, since only a block spanning the line counts.
     */
    private boolean footprintBlockedAt(VehicleProfile p, double x, double z) {
        double hw = p.body().width() / 2.0, hl = p.body().length() / 2.0;
        double yaw = Math.toRadians(getYRot());
        double c = Math.cos(yaw), s = Math.sin(yaw);
        double[][] points = {{-hw, hl}, {hw, hl}, {0.0, hl}, {-hw, -hl}, {hw, -hl}, {0.0, -hl}};
        double climb = Math.max(0.0, p.climb()) + OVER_CLIMB;
        double height = p.body().height();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double[] pt : points) {
            double dx = pt[0] * c - pt[1] * s;
            double dz = pt[1] * c + pt[0] * s;
            int steps = Math.max(1, Mth.ceil(Math.sqrt(dx * dx + dz * dz) / WALK));
            double ground = getY();
            for (int i = 1; i <= steps; i++) {
                double sx = x + dx * i / steps, sz = z + dz * i / steps;
                double line = ground + climb;
                int bx = Mth.floor(sx), bz = Mth.floor(sz);
                double top = Double.NEGATIVE_INFINITY;
                for (int by = Mth.floor(line + height); by >= Mth.floor(ground) - DROP; by--) {
                    net.minecraft.world.phys.shapes.VoxelShape shape = level().getBlockState(pos.set(bx, by, bz)).getCollisionShape(level(), pos);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    net.minecraft.world.phys.AABB bounds = shape.bounds().move(bx, by, bz);
                    if (!(bounds.minX <= sx && sx <= bounds.maxX && bounds.minZ <= sz && sz <= bounds.maxZ)) {
                        continue;
                    }
                    if (bounds.maxY > line && bounds.minY <= line) {
                        return true;
                    }
                    if (bounds.maxY <= line) {
                        top = Math.max(top, bounds.maxY);
                    }
                }
                if (top > Double.NEGATIVE_INFINITY) {
                    ground = top;
                }
            }
        }
        return false;
    }

    @Override
    public float maxUpStep() {
        return (float) tuning.climb();
    }

    /**
     * effects: returns whether {@code entity} is a wall to this one's move:
     * only another vehicle that is neither its trailer nor its tower. A boat
     * stops at anything pushable, and a car that did would stop dead at every
     * cow and every bystander -- and stall, since the driver's client and the
     * server never quite agree where a mob stands, so the server would refuse
     * the move the client made. The living are run over instead.
     */
    @Override
    public boolean canCollideWith(Entity entity) {
        return entity instanceof Vehicle v && v != this && v != trailer() && v != tower();
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

    /** effects: returns how hard the boost burns, 0..1, as synced */
    public float burn() {
        return entityData.get(DATA_BURN);
    }

    /** effects: returns the suspension state {@code partialTick} of the way through this tick, standing on the lift's deck if it is raised */
    public Suspension suspension(float partialTick) {
        return new Suspension(Mth.lerp(partialTick, suspensionO.lift(), suspension.lift()) + deckRaise(partialTick),
                Mth.lerp(partialTick, suspensionO.pitch(), suspension.pitch()),
                Mth.lerp(partialTick, suspensionO.roll(), suspension.roll()));
    }

    /**
     * effects: returns how far the Mechanic Lift under this vehicle has its deck raised, blocks:
     * the deck is drawn rising half a block through a job, and a vehicle standing on it rises
     * with it, riders and all. Zero off a lift, or with no job running.
     */
    public double deckRaise(float partialTick) {
        BlockPos under = BlockPos.containing(getX(), getY() - 0.01, getZ());
        net.minecraft.world.level.block.state.BlockState state = level().getBlockState(under);
        BlockPos controller;
        if (state.is(ModContent.LIFT_PART.get())) {
            controller = com.chunkworks.vanillawheels.lift.LiftPartBlock.controller(state, under);
        } else if (state.is(ModContent.LIFT_CONTROLLER.get())) {
            controller = under;
        } else {
            return 0.0;
        }
        return level().getBlockEntity(controller) instanceof com.chunkworks.vanillawheels.lift.LiftBlockEntity lift && lift.busy() ? lift.raise(partialTick) : 0.0;
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
        // A towed body shows its tower's lights: nothing can cycle a seatless body's own (H acts on
        // the vehicle you ride), so a trailer's markers follow the head of its chain.
        Vehicle head = this;
        for (int i = 0; i < 8; i++) {
            Vehicle t = head.tower();
            if (t == null || t == this) {
                break;
            }
            head = t;
        }
        return head.entityData.get(DATA_LIT);
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
        boolean wrenching = player.isSecondaryUseActive() && held.is(ModContent.WRENCH.get());
        // A chest the click lands in: opens, crouching or not, before anything else the click could
        // mean. A chest further along the click's line waits its turn, after every other gesture,
        // so a click on the radio ejects the disc even with a chest behind it.
        int chest = chestAt(p, hit, player.getEyePosition(), 0.0);
        if (chest >= 0 && !wrenching) {
            openChest(player, chest);
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        InteractionResult gesture = gestureAt(p, player, held, hit);
        if (gesture != InteractionResult.PASS) {
            return gesture;
        }
        chest = chestAt(p, hit, player.getEyePosition(), CHEST_REACH);
        if (chest >= 0 && !wrenching) {
            openChest(player, chest);
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    /** effects: the crouching gestures at {@code hit}: the tongue, a door, unloading, the wrench, the radio; PASS for none */
    private InteractionResult gestureAt(VehicleProfile p, Player player, ItemStack held, Vec3 hit) {
        if (player.isSecondaryUseActive()) {
            // The tongue, when hitched: let go.
            if (held.isEmpty() && tower() != null && p.hitch().front().isPresent() && inRegion(p, p.hitch().front().get(), 0.9, hit)) {
                if (!level().isClientSide()) {
                    unhitch();
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
            // A door, empty-handed: toggles it, and only that. Unloading is its own gesture (a lead in
            // hand, below): when the door click also unloaded, open doors could never be shut on a load.
            if (held.isEmpty() && nearADoor(p, hit)) {
                if (!level().isClientSide()) {
                    toggleDoors();
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
            // Crouch with a lead in hand at open doors with animals aboard: set them down behind.
            if (held.is(Items.LEAD) && p.cargo().isPresent() && doorsOpen() && !animals().isEmpty()) {
                if (!level().isClientSide()) {
                    unload();
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
        // A trailer in hand, on a vehicle with a rear hitch and nothing behind it: the trailer is put
        // down coupler on the ball and hitched, instead of the click seating the player.
        if (hitchPoint() != null && trailer() == null && trailerProfile(held) != null) {
            if (level() instanceof ServerLevel server) {
                placeTrailer(server, held, player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
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
     * blocks boards, nearest first, one by one, while the cargo has room
     * and the doors are open; each lead comes back to the player; the
     * player is told when the doors are shut or the trailer is full
     */
    private void load(Player player) {
        if (!doorsOpen()) {
            player.displayClientMessage(Component.translatable("vanillawheels.doors_shut"), true);
            return;
        }
        List<Animal> led = level().getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(10.0), a -> a.getLeashHolder() == player);
        // Nearest first: the level hands them back in entity-section order, which is the
        // world's business, and who fits depends on who comes first.
        led.sort(java.util.Comparator.comparingDouble(a -> a.distanceToSqr(player)));
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

    /** effects: the rider's inventory key: opens the first chest, since a rider cannot crouch to reach one */
    @Override
    public void openCustomInventoryScreen(Player player) {
        openChest(player, 0);
    }

    /** The vehicle as one container is never a menu of its own: each chest opens its own slice. */
    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return null;
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
    /**
     * effects: spares anyone aboard a vehicle the wall's damage: the body's
     * box stops at the hull, so a rider's head passes through a low canopy or
     * lintel the truck drives under, and a passenger who cannot move is not
     * to be crushed for it
     */
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) && event.getEntity().getVehicle() instanceof Vehicle) {
            event.setCanceled(true);
        }
    }

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
