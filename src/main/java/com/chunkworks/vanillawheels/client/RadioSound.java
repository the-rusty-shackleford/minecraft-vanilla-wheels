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

import com.chunkworks.vanillawheels.Vehicle;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * One disc playing from one vehicle: a jukebox's sound at a jukebox's
 * volume and range, riding the vehicle as it moves. Stops when the disc
 * it started for is no longer the one in the radio, when the vehicle
 * goes, or when {@link Radio} says the song is over.
 */
public final class RadioSound extends AbstractTickableSoundInstance {
    private final Vehicle vehicle;
    private final ItemStack disc;

    public RadioSound(Vehicle vehicle, SoundEvent sound, ItemStack disc) {
        super(sound, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.vehicle = vehicle;
        this.disc = disc.copy();
        this.looping = false;
        this.delay = 0;
        this.volume = 4.0f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = vehicle.getX();
        this.y = vehicle.getY();
        this.z = vehicle.getZ();
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved() || !ItemStack.isSameItemSameComponents(vehicle.disc(), disc)) {
            stop();
            return;
        }
        x = vehicle.getX();
        y = vehicle.getY();
        z = vehicle.getZ();
    }

    /** effects: stops this sound; the radio manager calls it when the song ends */
    public void end() {
        stop();
    }
}
