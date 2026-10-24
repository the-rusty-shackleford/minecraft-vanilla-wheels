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
package com.chunkworks.vanillawheels.net;

import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * What the driver's client tells the server beyond where the vehicle is
 * (vanilla carries that): the drive state, so the server can burn fuel,
 * hurt what is run over and show other players the wheels; the horn; the
 * headlight mode; and, from any seat aboard a body that moves in three
 * dimensions, the get-out key (D-0031); from a seat other than the driver's,
 * the take-the-controls key (D-0038). Each is sent on change or on the
 * press, never per tick. And one the server sends: the keyring screen's rows
 * (D-0041), on open and on change.
 */
public final class Payloads {
    private Payloads() {}

    /**
     * Bumped when a payload's shape changes, or when a block's states do (a client with the other
     * side's state table reads every block state after it wrong); a mismatch refuses the
     * connection early. 10: the Mechanic Lift's blocks gained their shape flag (D-0042).
     */
    private static final String VERSION = "10";

    /** The driver's state of the vehicle it drives. */
    public record DriveState(int vehicle, float speed, float steer, int throttle, boolean drifting, float burn) implements CustomPacketPayload {
        public static final Type<DriveState> TYPE = new Type<>(VanillaWheels.id("drive_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DriveState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DriveState::vehicle,
                ByteBufCodecs.FLOAT, DriveState::speed,
                ByteBufCodecs.FLOAT, DriveState::steer,
                ByteBufCodecs.VAR_INT, DriveState::throttle,
                ByteBufCodecs.BOOL, DriveState::drifting,
                ByteBufCodecs.FLOAT, DriveState::burn,
                DriveState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * The pose the driver's client drew a vehicle with this tick: its own, or a trailer's behind it.
     * Everyone else -- the server, the passengers' clients, the onlookers -- draws and seats with
     * these numbers rather than computing their own, so every side agrees where a rider sits.
     */
    public record Pose(int vehicle, float lift, float pitch, float roll) implements CustomPacketPayload {
        public static final Type<Pose> TYPE = new Type<>(VanillaWheels.id("pose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pose> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Pose::vehicle,
                ByteBufCodecs.FLOAT, Pose::lift,
                ByteBufCodecs.FLOAT, Pose::pitch,
                ByteBufCodecs.FLOAT, Pose::roll,
                Pose::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server-owned contact velocity delivered to the driver for their next simulated move. */
    public record ContactVelocity(int vehicle, double x, double z) implements CustomPacketPayload {
        public static final Type<ContactVelocity> TYPE = new Type<>(VanillaWheels.id("contact_velocity"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ContactVelocity> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ContactVelocity::vehicle, ByteBufCodecs.DOUBLE, ContactVelocity::x,
                ByteBufCodecs.DOUBLE, ContactVelocity::z, ContactVelocity::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** The horn key went down or up. */
    public record Horn(int vehicle, boolean held) implements CustomPacketPayload {
        public static final Type<Horn> TYPE = new Type<>(VanillaWheels.id("horn"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Horn> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Horn::vehicle, ByteBufCodecs.BOOL, Horn::held, Horn::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The headlight key was pressed: cycle the mode. */
    public record Lights(int vehicle) implements CustomPacketPayload {
        public static final Type<Lights> TYPE = new Type<>(VanillaWheels.id("lights"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Lights> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Lights::vehicle, Lights::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The get-out key, from any seat aboard a body that moves in three dimensions (D-0031). */
    public record GetOut(int vehicle) implements CustomPacketPayload {
        public static final Type<GetOut> TYPE = new Type<>(VanillaWheels.id("get_out"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GetOut> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GetOut::vehicle, GetOut::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The take-the-controls key, from a seat other than the driver's (D-0038). */
    public record TakeControls(int vehicle) implements CustomPacketPayload {
        public static final Type<TakeControls> TYPE = new Type<>(VanillaWheels.id("take_controls"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TakeControls> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TakeControls::vehicle, TakeControls::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * The keyring screen's rows (D-0041): one per pairing of the player's, the standing recall
     * request, and the last word said to the screen. Sent whenever any of it changes.
     */
    public record KeyringRows(java.util.List<Row> rows, Request request, net.minecraft.network.chat.Component notice) implements CustomPacketPayload {
        public static final Type<KeyringRows> TYPE = new Type<>(VanillaWheels.id("keyring_rows"));

        /** One vehicle: its name and paint, whether its key is on the player, where it is (a Whereabouts kind), how far, and the share of a tank a recall costs. */
        public record Row(java.util.UUID binding, net.minecraft.network.chat.Component name, int colour, boolean onRing, int kind,
                          boolean sameDimension, long about, float share) {}

        /** The player's recall request: none; locating; or quoted, with the fuel as a percentage of the tank and the condition lost in percent. */
        public record Request(boolean present, java.util.UUID binding, boolean quoted, float fuelPercent, float conditionLoss) {
            public static final Request NONE = new Request(false, new java.util.UUID(0, 0), false, 0.0f, 0.0f);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, KeyringRows> STREAM_CODEC = StreamCodec.of(KeyringRows::write, KeyringRows::read);

        private static void write(RegistryFriendlyByteBuf buf, KeyringRows p) {
            buf.writeVarInt(p.rows.size());
            for (Row r : p.rows) {
                buf.writeUUID(r.binding());
                net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, r.name());
                buf.writeVarInt(r.colour());
                buf.writeBoolean(r.onRing());
                buf.writeVarInt(r.kind());
                buf.writeBoolean(r.sameDimension());
                buf.writeVarLong(r.about());
                buf.writeFloat(r.share());
            }
            buf.writeBoolean(p.request.present());
            buf.writeUUID(p.request.binding());
            buf.writeBoolean(p.request.quoted());
            buf.writeFloat(p.request.fuelPercent());
            buf.writeFloat(p.request.conditionLoss());
            net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, p.notice);
        }

        private static KeyringRows read(RegistryFriendlyByteBuf buf) {
            int n = buf.readVarInt();
            java.util.List<Row> rows = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                rows.add(new Row(buf.readUUID(), net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readBoolean(),
                        buf.readVarInt(), buf.readBoolean(), buf.readVarLong(), buf.readFloat()));
            }
            Request request = new Request(buf.readBoolean(), buf.readUUID(), buf.readBoolean(), buf.readFloat(), buf.readFloat());
            return new KeyringRows(java.util.List.copyOf(rows), request, net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(KeyringRows.TYPE, KeyringRows.STREAM_CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof com.chunkworks.vanillawheels.keyring.KeyringMenu menu) {
                menu.accept(payload);
            }
        });
        registrar.playToClient(ContactVelocity.TYPE, ContactVelocity.STREAM_CODEC, (payload, context) -> {
            Entity entity = context.player().level().getEntity(payload.vehicle());
            if (entity instanceof Vehicle v && v.getControllingPassenger() == context.player()) {
                v.onContactVelocity(payload.x(), payload.z());
            }
        });
        registrar.playToServer(DriveState.TYPE, DriveState.STREAM_CODEC, (payload, context) ->
                driven(context, payload.vehicle()).ifPresent(v -> v.onDriveState(payload.speed(), payload.steer(), payload.throttle(), payload.drifting(), payload.burn())));
        registrar.playToServer(Horn.TYPE, Horn.STREAM_CODEC, (payload, context) ->
                driven(context, payload.vehicle()).ifPresent(v -> v.setHorn(payload.held())));
        registrar.playToServer(Lights.TYPE, Lights.STREAM_CODEC, (payload, context) ->
                driven(context, payload.vehicle()).ifPresent(Vehicle::cycleLights));
        registrar.playToServer(Pose.TYPE, Pose.STREAM_CODEC, (payload, context) ->
                towedOrDriven(context, payload.vehicle()).ifPresent(v -> v.onPose(payload.lift(), payload.pitch(), payload.roll())));
        registrar.playToServer(GetOut.TYPE, GetOut.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player && player.level().getEntity(payload.vehicle()) instanceof Vehicle v
                    && player.getVehicle() == v && v.verticalControls()) {
                v.getOut(player);
            }
        });
        registrar.playToServer(TakeControls.TYPE, TakeControls.STREAM_CODEC, (payload, context) -> {
            if (context.player().level().getEntity(payload.vehicle()) instanceof Vehicle v && context.player().getVehicle() == v) {
                v.takeControls(context.player());
            }
        });
    }

    /** effects: returns the vehicle {@code id} names if the sending player drives it or the head of its tow chain */
    private static java.util.Optional<Vehicle> towedOrDriven(IPayloadContext context, int id) {
        Player player = context.player();
        if (!(player.level().getEntity(id) instanceof Vehicle vehicle)) {
            return java.util.Optional.empty();
        }
        Vehicle head = vehicle;
        for (int i = 0; i < 8 && head.tower() != null; i++) {
            head = head.tower();
        }
        return head.getControllingPassenger() == player ? java.util.Optional.of(vehicle) : java.util.Optional.empty();
    }

    /** effects: returns the vehicle {@code id} names if the sending player is driving it; empty otherwise, so a stray packet does nothing */
    private static java.util.Optional<Vehicle> driven(IPayloadContext context, int id) {
        Player player = context.player();
        Entity entity = player.level().getEntity(id);
        if (entity instanceof Vehicle vehicle && vehicle.getControllingPassenger() == player) {
            return java.util.Optional.of(vehicle);
        }
        return java.util.Optional.empty();
    }
}
