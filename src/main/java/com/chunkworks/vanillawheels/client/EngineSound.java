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
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The engine, looping while the vehicle exists: silent with nobody at the
 * wheel, idling with a driver, and rising in pitch and volume with speed.
 * Starts silent, as the game's own vehicle loops do, so the engine cannot
 * refuse it for being inaudible.
 */
public final class EngineSound extends AbstractTickableSoundInstance {
    private final Vehicle vehicle;

    public EngineSound(Vehicle vehicle) {
        super(soundOf(vehicle), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.vehicle = vehicle;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0f;
        this.x = vehicle.getX();
        this.y = vehicle.getY();
        this.z = vehicle.getZ();
    }

    private static SoundEvent soundOf(Vehicle vehicle) {
        VehicleProfile p = vehicle.profile();
        if (p != null) {
            return p.sounds().engine().flatMap(BuiltInRegistries.SOUND_EVENT::getOptional).orElse(ModContent.ENGINE_PETROL.get());
        }
        return ModContent.ENGINE_PETROL.get();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return !vehicle.isSilent();
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved()) {
            stop();
            return;
        }
        x = vehicle.getX();
        y = vehicle.getY();
        z = vehicle.getZ();
        VehicleProfile p = vehicle.profile();
        boolean running = p != null && p.isPowered() && vehicle.getControllingPassenger() != null && vehicle.hasFuel();
        if (!running) {
            volume = Math.max(0.0f, volume - 0.05f);
            return;
        }
        float fraction = Math.min(1.0f, Math.abs(vehicle.speed()) / (float) vehicle.tuning().maxSpeed());
        pitch = Mth.lerp(fraction, 0.75f, 1.6f);
        volume = Mth.lerp(fraction, 0.35f, 0.9f);
    }
}
