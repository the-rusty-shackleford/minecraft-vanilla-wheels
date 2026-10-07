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
package com.chunkworks.vanillawheels.gametest;

import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The tests' own vehicle kind (D-0030): what a protocol built on this one registers, at its
 * smallest. It makes the box skids, drives as any vehicle does, counting the ticks it spends at
 * the wheel so a test sees the hook is its own, and runs nothing over.
 */
public final class SkidVehicle extends Vehicle {
    /** Ticks this body has spent at the wheel on this side. */
    public int steps;

    public SkidVehicle(EntityType<? extends Vehicle> type, Level level) {
        super(type, level);
    }

    @Override
    protected void stepAtTheWheel(VehicleProfile p) {
        steps++;
        super.stepAtTheWheel(p);
    }

    @Override
    protected boolean runsOver() {
        return false;
    }
}
