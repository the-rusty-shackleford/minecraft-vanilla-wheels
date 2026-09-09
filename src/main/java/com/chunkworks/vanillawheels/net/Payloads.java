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
 * headlight mode. Each is sent on change, never per tick.
 */
public final class Payloads {
    private Payloads() {}

    /** Bumped when a payload's shape changes; a mismatch refuses the connection early. */
    private static final String VERSION = "1";

    /** The driver's state of the vehicle it drives. */
    public record DriveState(int vehicle, float speed, float steer, int throttle, boolean drifting) implements CustomPacketPayload {
        public static final Type<DriveState> TYPE = new Type<>(VanillaWheels.id("drive_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DriveState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DriveState::vehicle,
                ByteBufCodecs.FLOAT, DriveState::speed,
                ByteBufCodecs.FLOAT, DriveState::steer,
                ByteBufCodecs.VAR_INT, DriveState::throttle,
                ByteBufCodecs.BOOL, DriveState::drifting,
                DriveState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
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

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(DriveState.TYPE, DriveState.STREAM_CODEC, (payload, context) ->
                driven(context, payload.vehicle()).ifPresent(v -> v.onDriveState(payload.speed(), payload.steer(), payload.throttle(), payload.drifting())));
        registrar.playToServer(Horn.TYPE, Horn.STREAM_CODEC, (payload, context) ->
                driven(context, payload.vehicle()).ifPresent(v -> v.setHorn(payload.held())));
        registrar.playToServer(Lights.TYPE, Lights.STREAM_CODEC, (payload, context) ->
                driven(context, payload.vehicle()).ifPresent(Vehicle::cycleLights));
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
