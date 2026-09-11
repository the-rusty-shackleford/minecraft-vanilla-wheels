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
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Drive;
import com.chunkworks.vanillawheels.domain.Input;
import com.chunkworks.vanillawheels.net.Payloads;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
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
    private static float lastBurn;
    private static boolean hornDown;

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
        float burn = (float) drive.burn(vehicle.tuning());
        boolean changed = Float.isNaN(lastSpeed) || Math.abs(speed - lastSpeed) > 0.004f || Math.abs(steer - lastSteer) > 0.01f
                || in.throttle() != lastThrottle || drive.drifting() != lastDrifting || Math.abs(burn - lastBurn) > 0.03f;
        if (changed) {
            lastSpeed = speed;
            lastSteer = steer;
            lastThrottle = in.throttle();
            lastDrifting = drive.drifting();
            lastBurn = burn;
            PacketDistributor.sendToServer(new Payloads.DriveState(vehicle.getId(), speed, steer, in.throttle(), drive.drifting(), burn));
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
        }
    }

    /**
     * effects: the afterburner, on every client for every vehicle: flames out
     * of the tail every tick a boost burns, more and faster the harder it
     * burns, blue at the core of a full burn
     */
    public static void exhaust(Vehicle vehicle) {
        Minecraft mc = Minecraft.getInstance();
        float burn = vehicle.burn();
        VehicleProfile p = vehicle.profile();
        if (burn <= 0.0f || mc.level == null || p == null) {
            return;
        }
        double heading = Math.toRadians(vehicle.getYRot());
        double back = -p.body().length() / 2.0 - 0.1;
        double bx = -Math.sin(heading), bz = Math.cos(heading);   // the body's forward
        double sx = Math.cos(heading), sz = Math.sin(heading);    // its right
        // The flames travel with the tail: the body's own velocity, less a push out of the pipe, so a
        // boosting truck trails a plume instead of leaving a fire on the road where its tail was.
        Vec3 along = new Vec3(vehicle.getX() - vehicle.xo, 0.0, vehicle.getZ() - vehicle.zo);
        int flames = 2 + Math.round(burn * 6);
        double push = 0.15 + 0.35 * burn;
        for (int pipe = -1; pipe <= 1; pipe += 2) {
            double px = vehicle.getX() + bx * back + sx * pipe * 0.35;
            double pz = vehicle.getZ() + bz * back + sz * pipe * 0.35;
            double py = vehicle.getY() + 0.45;
            for (int i = 0; i < flames; i++) {
                // Spread along the tail's last tick of travel, so the plume is a stream and not a puff a tick.
                double ago = mc.level.random.nextDouble();
                double out = push * (0.5 + mc.level.random.nextDouble());
                double side = (mc.level.random.nextDouble() - 0.5) * 0.08;
                double vx = along.x - bx * out + sx * side, vz = along.z - bz * out + sz * side;
                boolean core = burn > 0.7f && i < 2;
                mc.level.addParticle(core ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, px - along.x * ago, py + (mc.level.random.nextDouble() - 0.5) * 0.1, pz - along.z * ago,
                        vx, 0.005 + mc.level.random.nextDouble() * 0.03, vz);
            }
            if (burn > 0.4f && mc.level.random.nextInt(2) == 0) {
                mc.level.addParticle(ParticleTypes.LARGE_SMOKE, px, py + 0.1, pz, along.x - bx * push * 0.6, 0.04, along.z - bz * push * 0.6);
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

    /** effects: starts the engine and tyre loops for a vehicle that just appeared on this client; the radio is {@link Radio}'s */
    public static void onVehicleJoined(Vehicle vehicle) {
        Minecraft.getInstance().getSoundManager().play(new EngineSound(vehicle));
        Minecraft.getInstance().getSoundManager().play(new SkidSound(vehicle));
    }
}
