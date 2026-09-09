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
package com.chunkworks.vanillawheels.client;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.domain.Drive;
import com.chunkworks.vanillawheels.domain.Input;
import com.chunkworks.vanillawheels.net.Payloads;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The driver's side of driving: turns the local player's keys into an
 * {@link Input} for the vehicle they control, tells the server what
 * changed (one payload per change, never per tick), and makes the local
 * effects the pure layer asks for -- skid smoke and its sound, the boost.
 * Also starts the sounds a vehicle carries when it appears.
 */
public final class Controls {
    private Controls() {}

    private static float lastSpeed = Float.NaN;
    private static float lastSteer;
    private static int lastThrottle;
    private static boolean lastDrifting;
    private static boolean hornDown;
    private static int skidTicks;

    /** effects: returns what the local player is doing at the wheel of {@code vehicle} */
    public static Input input(Vehicle vehicle) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || vehicle.getControllingPassenger() != player) {
            return Input.coasting(vehicle.onGround(), vehicle.hasFuel());
        }
        int throttle = player.input.up ? 1 : player.input.down ? -1 : 0;
        int steer = player.input.right ? 1 : player.input.left ? -1 : 0;
        return new Input(throttle, steer, Keys.driftDown(), vehicle.onGround(), vehicle.hasFuel());
    }

    /** effects: sends the server what changed, and shows the driver the effects */
    public static void report(Vehicle vehicle, Drive drive, Input in, Set<Drive.Effect> effects) {
        float speed = (float) drive.speed();
        float steer = (float) drive.steer();
        boolean changed = Float.isNaN(lastSpeed) || Math.abs(speed - lastSpeed) > 0.004f || Math.abs(steer - lastSteer) > 0.01f
                || in.throttle() != lastThrottle || drive.drifting() != lastDrifting;
        if (changed) {
            lastSpeed = speed;
            lastSteer = steer;
            lastThrottle = in.throttle();
            lastDrifting = drive.drifting();
            PacketDistributor.sendToServer(new Payloads.DriveState(vehicle.getId(), speed, steer, in.throttle(), drive.drifting()));
        }
        Minecraft mc = Minecraft.getInstance();
        if (effects.contains(Drive.Effect.SKID) && mc.level != null) {
            double heading = drive.heading();
            for (int i = 0; i < 2; i++) {
                double side = i == 0 ? -0.6 : 0.6;
                double x = vehicle.getX() + Math.cos(heading) * side - Math.sin(heading) * -1.0;
                double z = vehicle.getZ() + Math.sin(heading) * side + Math.cos(heading) * -1.0;
                mc.level.addParticle(ParticleTypes.CLOUD, x, vehicle.getY() + 0.1, z, 0.0, 0.02, 0.0);
            }
            if (skidTicks++ % 12 == 0) {
                mc.level.playLocalSound(vehicle.getX(), vehicle.getY(), vehicle.getZ(), ModContent.SKID.get(), SoundSource.NEUTRAL, 0.6f, 1.0f, false);
            }
        } else {
            skidTicks = 0;
        }
        if (effects.contains(Drive.Effect.BOOST) && mc.level != null) {
            for (int i = 0; i < 12; i++) {
                mc.level.addParticle(ParticleTypes.FLAME, vehicle.getX() + (mc.level.random.nextDouble() - 0.5), vehicle.getY() + 0.3, vehicle.getZ() + (mc.level.random.nextDouble() - 0.5), 0.0, 0.05, 0.0);
            }
        }
    }

    /** The horn and headlight keys, each tick the player is at a wheel. */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            hornDown = false;
            return;
        }
        Vehicle driven = mc.player.getVehicle() instanceof Vehicle v && v.getControllingPassenger() == mc.player ? v : null;
        if (driven == null) {
            if (hornDown) {
                hornDown = false;
            }
            lastSpeed = Float.NaN;
            while (Keys.LIGHTS.consumeClick()) { }
            return;
        }
        boolean horn = Keys.HORN.isDown() && mc.screen == null;
        if (horn != hornDown) {
            hornDown = horn;
            PacketDistributor.sendToServer(new Payloads.Horn(driven.getId(), horn));
        }
        while (Keys.LIGHTS.consumeClick()) {
            PacketDistributor.sendToServer(new Payloads.Lights(driven.getId()));
        }
    }

    /** effects: starts the engine loop for a vehicle that just appeared on this client; the radio is {@link Radio}'s */
    public static void onVehicleJoined(Vehicle vehicle) {
        Minecraft.getInstance().getSoundManager().play(new EngineSound(vehicle));
    }
}
