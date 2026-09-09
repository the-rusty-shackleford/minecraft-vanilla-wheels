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
package com.chunkworks.vanillawheels.api;

import com.chunkworks.vanillawheels.domain.Dial;
import com.chunkworks.vanillawheels.domain.Region;
import com.chunkworks.vanillawheels.domain.Selector;
import com.chunkworks.vanillawheels.domain.Transform;
import com.chunkworks.vanillawheels.domain.Tuning;
import com.chunkworks.vanillawheels.domain.Vec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

/**
 * Everything the protocol needs to know about one kind of vehicle: what it
 * looks like (a mesh, a texture, the units it is drawn in and which hand
 * it was modelled with), what it is (its body, seats, wheels), how it
 * drives (an engine, if it has one, and its handling), and what it carries
 * (a tank, a chest, gauges, headlamps, a horn, a radio, a hitch, cargo,
 * doors, paint, glass). Decoded from
 * {@code data/<ns>/vanillawheels/vehicle/<name>.json} into a synced
 * datapack registry; every number is bounded here, in the records'
 * constructors, and the same bounds sit in the codecs so a bad file is
 * refused at load naming the field.
 *
 * <p>Lengths in a profile are in <em>mesh units</em>; {@link #scale} is the
 * factor to blocks (1/16 for a mesh in pixels). {@link #blocks(double)}
 * converts. Vectors are the mesh's frame, +Z forward, +Y up, before the
 * {@link Handedness} mirror; {@link #toLocal()} mirrors everything at once.
 *
 * <p>RI: scale > 0; body positive; at least one seat when there is an
 *     engine, and exactly one driver then; no driver without an engine;
 *     wheels non-empty with a positive radius; climb >= 0; mass > 0.
 */
public record VehicleProfile(Look look, Body body, List<Seat> seats, Wheels wheels, Optional<Engine> engine,
                             Handling handling, double climb, double mass, Kit kit) {

    /** What the vehicle looks like: its meshes, texture, units, hand, paint, glass and sounds. One flat object in the JSON. */
    public record Look(ResourceLocation mesh, Optional<ResourceLocation> wheelMesh, ResourceLocation texture, double scale,
                       Handedness handedness, Optional<Paint> paint, Optional<PartSelector> glass, Sounds sounds) {
        public static final MapCodec<Look> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("mesh").forGetter(Look::mesh),
                ResourceLocation.CODEC.optionalFieldOf("wheel_mesh").forGetter(Look::wheelMesh),
                ResourceLocation.CODEC.fieldOf("texture").forGetter(Look::texture),
                Codec.doubleRange(0.0001, 100.0).optionalFieldOf("scale", 1.0).forGetter(Look::scale),
                Handedness.CODEC.optionalFieldOf("handedness", Handedness.RIGHT).forGetter(Look::handedness),
                Paint.CODEC.optionalFieldOf("paint").forGetter(Look::paint),
                PartSelector.CODEC.optionalFieldOf("glass").forGetter(Look::glass),
                Sounds.CODEC.optionalFieldOf("sounds", Sounds.NONE).forGetter(Look::sounds)
        ).apply(i, Look::new));
    }

    /** What the vehicle carries: tank, chest, gauges, lamps, horn, radio, hitch, cargo, doors. One flat object in the JSON. */
    public record Kit(Optional<Fuel> fuel, Optional<Storage> storage, List<Gauge> gauges, Optional<Headlights> headlights,
                      Optional<ResourceLocation> horn, Optional<Radio> radio, Hitch hitch, Optional<Cargo> cargo, List<Door> doors) {
        public static final MapCodec<Kit> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Fuel.CODEC.optionalFieldOf("fuel").forGetter(Kit::fuel),
                Storage.CODEC.optionalFieldOf("storage").forGetter(Kit::storage),
                Gauge.CODEC.listOf().optionalFieldOf("gauges", List.of()).forGetter(Kit::gauges),
                Headlights.CODEC.optionalFieldOf("headlights").forGetter(Kit::headlights),
                ResourceLocation.CODEC.optionalFieldOf("horn").forGetter(Kit::horn),
                Radio.CODEC.optionalFieldOf("radio").forGetter(Kit::radio),
                Hitch.CODEC.optionalFieldOf("hitch", Hitch.NONE).forGetter(Kit::hitch),
                Cargo.CODEC.optionalFieldOf("cargo").forGetter(Kit::cargo),
                Door.CODEC.listOf().optionalFieldOf("doors", List.of()).forGetter(Kit::doors)
        ).apply(i, Kit::new));

        public Kit {
            gauges = List.copyOf(gauges);
            doors = List.copyOf(doors);
        }
    }

    // The look's and the kit's fields, as if they were the profile's own.
    public ResourceLocation mesh() { return look.mesh(); }
    public Optional<ResourceLocation> wheelMesh() { return look.wheelMesh(); }
    public ResourceLocation texture() { return look.texture(); }
    public double scale() { return look.scale(); }
    public Handedness handedness() { return look.handedness(); }
    public Optional<Paint> paint() { return look.paint(); }
    public Optional<PartSelector> glass() { return look.glass(); }
    public Sounds sounds() { return look.sounds(); }
    public Optional<Fuel> fuel() { return kit.fuel(); }
    public Optional<Storage> storage() { return kit.storage(); }
    public List<Gauge> gauges() { return kit.gauges(); }
    public Optional<Headlights> headlights() { return kit.headlights(); }
    public Optional<ResourceLocation> horn() { return kit.horn(); }
    public Optional<Radio> radio() { return kit.radio(); }
    public Hitch hitch() { return kit.hitch(); }
    public Optional<Cargo> cargo() { return kit.cargo(); }
    public List<Door> doors() { return kit.doors(); }

    /** A vector in mesh units, written {@code [x, y, z]}. */
    public static final Codec<Vec> VEC = Codec.DOUBLE.listOf(3, 3).xmap(l -> new Vec(l.get(0), l.get(1), l.get(2)),
            v -> List.of(v.x(), v.y(), v.z()));

    /** Which hand the mesh was modelled with. The game's frame is right-handed. */
    public enum Handedness implements StringRepresentable {
        RIGHT, LEFT;

        public static final Codec<Handedness> CODEC = StringRepresentable.fromEnum(Handedness::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }

        /** effects: returns the map into the game's frame: a mirror for a left-handed mesh, nothing for a right */
        public Transform toLocal() {
            return this == LEFT ? Transform.MIRROR_X : Transform.IDENTITY;
        }
    }

    /**
     * Which faces of the mesh a part is: by material, by group, and by a
     * box on the mesh's axes; each absent means "any".
     */
    public record PartSelector(List<String> materials, List<String> groups, Optional<Double> xMin, Optional<Double> xMax,
                               Optional<Double> yMin, Optional<Double> yMax, Optional<Double> zMin, Optional<Double> zMax) {
        public static final Codec<PartSelector> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(e -> e.map(List::of, l -> l), l -> l.size() == 1 ? com.mojang.datafixers.util.Either.left(l.get(0)) : com.mojang.datafixers.util.Either.right(l))
                        .optionalFieldOf("material", List.of()).forGetter(PartSelector::materials),
                Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(e -> e.map(List::of, l -> l), l -> l.size() == 1 ? com.mojang.datafixers.util.Either.left(l.get(0)) : com.mojang.datafixers.util.Either.right(l))
                        .optionalFieldOf("group", List.of()).forGetter(PartSelector::groups),
                Codec.DOUBLE.optionalFieldOf("x_min").forGetter(PartSelector::xMin),
                Codec.DOUBLE.optionalFieldOf("x_max").forGetter(PartSelector::xMax),
                Codec.DOUBLE.optionalFieldOf("y_min").forGetter(PartSelector::yMin),
                Codec.DOUBLE.optionalFieldOf("y_max").forGetter(PartSelector::yMax),
                Codec.DOUBLE.optionalFieldOf("z_min").forGetter(PartSelector::zMin),
                Codec.DOUBLE.optionalFieldOf("z_max").forGetter(PartSelector::zMax)
        ).apply(i, PartSelector::new));

        public PartSelector {
            materials = List.copyOf(materials);
            groups = List.copyOf(groups);
            if (materials.isEmpty() && groups.isEmpty() && xMin.isEmpty() && xMax.isEmpty() && yMin.isEmpty() && yMax.isEmpty() && zMin.isEmpty() && zMax.isEmpty()) {
                throw new IllegalArgumentException("a part selector selects something: a material, a group or a box");
            }
        }

        /** effects: returns the selector in the mesh's frame */
        public Selector selector() {
            Region r = new Region(new Vec(xMin.orElse(-Region.OPEN), yMin.orElse(-Region.OPEN), zMin.orElse(-Region.OPEN)),
                    new Vec(xMax.orElse(Region.OPEN), yMax.orElse(Region.OPEN), zMax.orElse(Region.OPEN)));
            return new Selector(Set.copyOf(materials), Set.copyOf(groups), r);
        }

        /**
         * effects: returns the selector under {@code t}: a mirror swaps
         * and negates the x bounds, so a needle picked by "x at most -5"
         * in a left-handed mesh is picked by "x at least 5" once mirrored
         */
        public PartSelector transformed(Transform t) {
            if (!t.isReflection()) {
                return this;
            }
            return new PartSelector(materials, groups, xMax.map(v -> -v), xMin.map(v -> -v), yMin, yMax, zMin, zMax);
        }
    }

    /** The body's size in blocks and its hitboxes in mesh units. */
    public record Body(double width, double length, double height, List<HitBox> parts) {
        public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.3, 16.0).fieldOf("width").forGetter(Body::width),
                Codec.doubleRange(0.3, 32.0).fieldOf("length").forGetter(Body::length),
                Codec.doubleRange(0.3, 16.0).fieldOf("height").forGetter(Body::height),
                HitBox.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(Body::parts)
        ).apply(i, Body::new));

        public Body {
            parts = List.copyOf(parts);
        }
    }

    /** One box, for hits and clicks: centred at {@code at} (mesh units, bottom centre), {@code width} square and {@code height} tall in blocks. */
    public record HitBox(Vec at, double width, double height) {
        public static final Codec<HitBox> CODEC = RecordCodecBuilder.create(i -> i.group(
                VEC.fieldOf("at").forGetter(HitBox::at),
                Codec.doubleRange(0.1, 16.0).fieldOf("width").forGetter(HitBox::width),
                Codec.doubleRange(0.1, 16.0).fieldOf("height").forGetter(HitBox::height)
        ).apply(i, HitBox::new));
    }

    /** Where a rider sits, mesh units; the driver's seat is the one that steers. */
    public record Seat(Vec at, boolean driver) {
        public static final Codec<Seat> CODEC = RecordCodecBuilder.create(i -> i.group(
                VEC.fieldOf("at").forGetter(Seat::at),
                Codec.BOOL.optionalFieldOf("driver", false).forGetter(Seat::driver)
        ).apply(i, Seat::new));
    }

    /** The wheels: one mesh drawn at each position, spinning; the steering ones turn. */
    public record Wheels(double radius, List<WheelPosition> positions) {
        public static final Codec<Wheels> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.01, 64.0).fieldOf("radius").forGetter(Wheels::radius),
                WheelPosition.CODEC.listOf(1, 16).fieldOf("positions").forGetter(Wheels::positions)
        ).apply(i, Wheels::new));

        public Wheels {
            positions = List.copyOf(positions);
        }
    }

    /**
     * A wheel's axle centre: {@code forward} along +Z and {@code right} along
     * the vehicle's right, mesh units, {@code up} above the ground the vehicle
     * stands on -- absent, one radius, a wheel that touches the ground.
     */
    public record WheelPosition(double forward, double right, Optional<Double> up, boolean steers) {
        public static final Codec<WheelPosition> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("forward").forGetter(WheelPosition::forward),
                Codec.DOUBLE.fieldOf("right").forGetter(WheelPosition::right),
                Codec.DOUBLE.optionalFieldOf("up").forGetter(WheelPosition::up),
                Codec.BOOL.optionalFieldOf("steers", false).forGetter(WheelPosition::steers)
        ).apply(i, WheelPosition::new));

        /** effects: returns the axle's height above the ground for a wheel of {@code radius}: {@code up} if given, else the radius */
        public double up(double radius) {
            return up.orElse(radius);
        }
    }

    /** What moves the vehicle, in blocks per tick; absent on a trailer. */
    public record Engine(double maxSpeed, double acceleration, double reverseSpeed, double brake, double drag) {
        public static final Codec<Engine> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.05, 3.0).fieldOf("max_speed").forGetter(Engine::maxSpeed),
                Codec.doubleRange(0.001, 1.0).fieldOf("acceleration").forGetter(Engine::acceleration),
                Codec.doubleRange(0.0, 3.0).optionalFieldOf("reverse_speed", 0.3).forGetter(Engine::reverseSpeed),
                Codec.doubleRange(0.001, 1.0).optionalFieldOf("brake", 0.05).forGetter(Engine::brake),
                Codec.doubleRange(0.0, 0.5).optionalFieldOf("drag", 0.01).forGetter(Engine::drag)
        ).apply(i, Engine::new));
    }

    /** How it corners and drifts. */
    public record Handling(double grip, double steerDegrees, double driftGrip, double driftBoost, int driftChargeTicks) {
        public static final Codec<Handling> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.01, 1.0).optionalFieldOf("grip", 0.85).forGetter(Handling::grip),
                Codec.doubleRange(1.0, 89.0).optionalFieldOf("steer_degrees", 32.0).forGetter(Handling::steerDegrees),
                Codec.doubleRange(0.01, 1.0).optionalFieldOf("drift_grip", 0.4).forGetter(Handling::driftGrip),
                Codec.doubleRange(0.0, 3.0).optionalFieldOf("drift_boost", 0.3).forGetter(Handling::driftBoost),
                Codec.intRange(1, 400).optionalFieldOf("drift_charge_ticks", 40).forGetter(Handling::driftChargeTicks)
        ).apply(i, Handling::new));

        public static final Handling DEFAULT = new Handling(0.85, 32.0, 0.4, 0.3, 40);
    }

    /** A tank, in burn ticks. */
    public record Fuel(int capacity) {
        public static final Codec<Fuel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1_000_000).fieldOf("capacity").forGetter(Fuel::capacity)
        ).apply(i, Fuel::new));
    }

    /** A chest of {@code rows} rows of nine, opened by clicking inside {@code region} (mesh units). */
    public record Storage(int rows, Optional<PartSelector> region) {
        public static final Codec<Storage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 6).fieldOf("rows").forGetter(Storage::rows),
                PartSelector.CODEC.optionalFieldOf("region").forGetter(Storage::region)
        ).apply(i, Storage::new));
    }

    /** What a gauge shows. */
    public enum GaugeKind implements StringRepresentable {
        SPEED, FUEL;

        public static final Codec<GaugeKind> CODEC = StringRepresentable.fromEnum(GaugeKind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    /** A needle: the faces that are it, and the turn it makes about {@code axis} through {@code pivot}, from {@code zero} radians over {@code sweep}. */
    public record Gauge(GaugeKind kind, PartSelector part, Vec pivot, Vec axis, double zero, double sweep) {
        public static final Codec<Gauge> CODEC = RecordCodecBuilder.create(i -> i.group(
                GaugeKind.CODEC.fieldOf("kind").forGetter(Gauge::kind),
                PartSelector.CODEC.fieldOf("part").forGetter(Gauge::part),
                VEC.fieldOf("pivot").forGetter(Gauge::pivot),
                VEC.optionalFieldOf("axis", Vec.Z).forGetter(Gauge::axis),
                Codec.DOUBLE.optionalFieldOf("zero", 0.0).forGetter(Gauge::zero),
                Codec.DOUBLE.fieldOf("sweep").forGetter(Gauge::sweep)
        ).apply(i, Gauge::new));

        public Gauge {
            if (axis.length() < 1e-9) {
                throw new IllegalArgumentException("a gauge's axis has a direction");
            }
            axis = axis.normalized();
        }

        /** effects: returns the needle's dial in blocks after {@code t} */
        public Dial dial(Transform t) {
            return new Dial(pivot, axis, zero, sweep).transformed(t);
        }
    }

    /** The lamps: where each is (mesh units), the faces that glow, how far the beam reaches in blocks. */
    public record Headlights(List<Vec> at, Optional<PartSelector> part, int range) {
        public static final Codec<Headlights> CODEC = RecordCodecBuilder.create(i -> i.group(
                VEC.listOf(1, 8).fieldOf("at").forGetter(Headlights::at),
                PartSelector.CODEC.optionalFieldOf("part").forGetter(Headlights::part),
                Codec.intRange(1, 32).optionalFieldOf("range", 10).forGetter(Headlights::range)
        ).apply(i, Headlights::new));

        public Headlights {
            at = List.copyOf(at);
        }
    }

    /** A disc player at {@code at}. */
    public record Radio(Vec at) {
        public static final Codec<Radio> CODEC = RecordCodecBuilder.create(i -> i.group(
                VEC.fieldOf("at").forGetter(Radio::at)
        ).apply(i, Radio::new));
    }

    /** Where a trailer is hooked at the back, and where this vehicle's tongue is if it is one. */
    public record Hitch(Optional<Vec> rear, Optional<Vec> front) {
        public static final Codec<Hitch> CODEC = RecordCodecBuilder.create(i -> i.group(
                VEC.optionalFieldOf("rear").forGetter(Hitch::rear),
                VEC.optionalFieldOf("front").forGetter(Hitch::front)
        ).apply(i, Hitch::new));

        public static final Hitch NONE = new Hitch(Optional.empty(), Optional.empty());
    }

    /** Animals aboard: how many adults, how many young, where they stand. */
    public record Cargo(int adults, int young, List<Vec> slots) {
        public static final Codec<Cargo> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 16).fieldOf("adults").forGetter(Cargo::adults),
                Codec.intRange(1, 32).fieldOf("young").forGetter(Cargo::young),
                VEC.listOf(1, 16).fieldOf("slots").forGetter(Cargo::slots)
        ).apply(i, Cargo::new));

        public Cargo {
            slots = List.copyOf(slots);
        }
    }

    /** A part that swings open: about {@code axis} through {@code hinge}, by {@code open} radians. */
    public record Door(PartSelector part, Vec hinge, Vec axis, double open) {
        public static final Codec<Door> CODEC = RecordCodecBuilder.create(i -> i.group(
                PartSelector.CODEC.fieldOf("part").forGetter(Door::part),
                VEC.fieldOf("hinge").forGetter(Door::hinge),
                VEC.optionalFieldOf("axis", Vec.Y).forGetter(Door::axis),
                Codec.DOUBLE.fieldOf("open").forGetter(Door::open)
        ).apply(i, Door::new));

        public Door {
            axis = axis.normalized();
        }
    }

    /** The faces the dye colours, and the colour a new vehicle wears. */
    public record Paint(PartSelector part, DyeColor defaultColor) {
        public static final Codec<Paint> CODEC = RecordCodecBuilder.create(i -> i.group(
                PartSelector.CODEC.fieldOf("part").forGetter(Paint::part),
                DyeColor.CODEC.optionalFieldOf("default", DyeColor.WHITE).forGetter(Paint::defaultColor)
        ).apply(i, Paint::new));
    }

    /** The sounds the vehicle makes by itself. */
    public record Sounds(Optional<ResourceLocation> engine) {
        public static final Codec<Sounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("engine").forGetter(Sounds::engine)
        ).apply(i, Sounds::new));

        public static final Sounds NONE = new Sounds(Optional.empty());
    }

    public static final Codec<VehicleProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Look.MAP_CODEC.forGetter(VehicleProfile::look),
            Body.CODEC.fieldOf("body").forGetter(VehicleProfile::body),
            Seat.CODEC.listOf().optionalFieldOf("seats", List.of()).forGetter(VehicleProfile::seats),
            Wheels.CODEC.fieldOf("wheels").forGetter(VehicleProfile::wheels),
            Engine.CODEC.optionalFieldOf("engine").forGetter(VehicleProfile::engine),
            Handling.CODEC.optionalFieldOf("handling", Handling.DEFAULT).forGetter(VehicleProfile::handling),
            Codec.doubleRange(0.0, 4.0).optionalFieldOf("climb", 1.0).forGetter(VehicleProfile::climb),
            Codec.doubleRange(0.05, 50.0).optionalFieldOf("mass", 1.0).forGetter(VehicleProfile::mass),
            Kit.MAP_CODEC.forGetter(VehicleProfile::kit)
    ).apply(i, VehicleProfile::new));

    public VehicleProfile {
        seats = List.copyOf(seats);
        long drivers = seats.stream().filter(Seat::driver).count();
        if (engine.isPresent()) {
            if (seats.isEmpty()) {
                throw new IllegalArgumentException("a vehicle with an engine needs a seat");
            }
            if (drivers != 1) {
                throw new IllegalArgumentException("a vehicle with an engine has exactly one driver's seat, has " + drivers);
            }
        } else if (drivers != 0) {
            throw new IllegalArgumentException("a vehicle without an engine has no driver's seat");
        }
        if (engine.isPresent() && kit.fuel().isEmpty()) {
            throw new IllegalArgumentException("a vehicle with an engine needs a fuel tank");
        }
    }

    /** effects: returns {@code units} of the mesh in blocks */
    public double blocks(double units) {
        return units * scale();
    }

    /** effects: returns the map from the mesh's frame to the game's local frame: the handedness mirror */
    public Transform toLocal() {
        return handedness().toLocal();
    }

    /** effects: returns {@code v} (mesh units, mesh frame) as blocks in the local frame */
    /** effects: returns how far ahead of the origin the wheels' axles are on average, blocks, in the local frame */
    public double axleForward() {
        double sum = 0.0;
        for (WheelPosition w : wheels().positions()) {
            sum += w.forward();
        }
        return blocks(sum / wheels().positions().size());
    }

    public Vec localBlocks(Vec v) {
        return toLocal().apply(v).times(scale());
    }

    /** effects: returns whether this vehicle drives itself */
    public boolean isPowered() {
        return engine.isPresent();
    }

    /** effects: returns the distance between the axles in blocks: the spread of the wheels' forward positions, or the body's length if one axle */
    public double wheelBase() {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (WheelPosition w : wheels.positions()) {
            min = Math.min(min, w.forward());
            max = Math.max(max, w.forward());
        }
        double base = blocks(max - min);
        return base > 0.1 ? base : body.length();
    }

    /** effects: returns the distance between the left and right wheels in blocks, or the body's width if one track */
    public double track() {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (WheelPosition w : wheels.positions()) {
            min = Math.min(min, w.right());
            max = Math.max(max, w.right());
        }
        double t = blocks(max - min);
        return t > 0.1 ? t : body.width();
    }

    /**
     * effects: returns the physics numbers this profile drives with; a
     * vehicle without an engine gets a tuning it never receives throttle for
     */
    public Tuning tuning() {
        Engine e = engine.orElse(new Engine(0.9, 0.02, 0.3, 0.05, 0.01));
        return new Tuning(e.maxSpeed(), Math.min(e.reverseSpeed(), e.maxSpeed()), e.acceleration(), e.brake(), e.drag(),
                handling.grip(), Math.min(handling.driftGrip(), handling.grip()), Math.toRadians(handling.steerDegrees()),
                handling.driftBoost(), handling.driftChargeTicks(), wheelBase(), climb, mass);
    }
}
