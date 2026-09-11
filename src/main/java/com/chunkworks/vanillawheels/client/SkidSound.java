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
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The tyres, looping while the vehicle exists: silent on rails, and a
 * squeal that swells while the tail is out, louder and higher with speed,
 * and dies away over a few ticks when the drift ends. One loop that rises
 * and falls, in place of a screech fired every twelfth tick.
 */
public final class SkidSound extends AbstractTickableSoundInstance {
    private final Vehicle vehicle;

    public SkidSound(Vehicle vehicle) {
        super(ModContent.SKID.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.vehicle = vehicle;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0f;
        this.x = vehicle.getX();
        this.y = vehicle.getY();
        this.z = vehicle.getZ();
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
        float fraction = Math.min(1.0f, Math.abs(vehicle.speed()) / (float) vehicle.tuning().maxSpeed());
        float want = vehicle.drifting() && vehicle.onGround() ? Mth.lerp(fraction, 0.25f, 0.7f) : 0.0f;
        volume = want > volume ? Math.min(want, volume + 0.12f) : Math.max(want, volume - 0.08f);
        pitch = Mth.lerp(fraction, 0.9f, 1.15f);
    }
}
