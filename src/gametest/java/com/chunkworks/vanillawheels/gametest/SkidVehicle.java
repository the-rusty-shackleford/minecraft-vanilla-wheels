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
import com.chunkworks.vanillawheels.domain.Crash;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The tests' own vehicle kind (D-0030): what a protocol built on this one registers, at its
 * smallest. It makes the box skids, drives as any vehicle does, counting the ticks it spends at
 * the wheel so a test sees the hook is its own, and runs nothing over. A test may switch on, one
 * at a time, what a body that moves in three dimensions takes (D-0031): vertical controls, a hull,
 * a crash's cost, a fuel rate; each is off, as on a car, until a test sets it.
 */
public final class SkidVehicle extends Vehicle {
    /** Ticks this body has spent at the wheel on this side. */
    public int steps;
    /** Whether its riders work it with the up, down and get-out keys. */
    public boolean vertical;
    /** The hull's points (Hull.points), or null for a car's footprint. */
    @Nullable public double[] hull;
    /** Whether the hull meets the world axis by axis (hullClampAxes) rather than as one move (hullClamp). */
    public boolean axes;
    /** What a crash costs it, or null for none. */
    @Nullable public Crash crash;
    /** Ticks of fuel it burns for each tick it burns. */
    public double rate = 1.0;

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

    @Override
    public boolean verticalControls() {
        return vertical;
    }

    @Nullable
    @Override
    protected double[] hullPoints() {
        return hull;
    }

    @Override
    protected Vec3 footprintClamp(Vec3 delta) {
        return hull == null ? super.footprintClamp(delta) : axes ? hullClampAxes(delta) : hullClamp(delta);
    }

    @Nullable
    @Override
    protected Crash crashes() {
        return crash;
    }

    @Override
    protected double fuelRate() {
        return rate;
    }
}
