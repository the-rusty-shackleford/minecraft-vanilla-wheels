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

import com.chunkworks.luminance.api.Luminance;
import com.chunkworks.luminance.domain.Line;
import com.chunkworks.luminance.domain.Source;
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Vec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * The headlamps' light in the world, through Luminance: for a lit vehicle,
 * one line of light per lamp from the lamp to the profile's range ahead,
 * along the body's heading. This class names Luminance's types, so it is
 * loaded only when Luminance is there (see {@link VanillaWheelsClient}).
 */
final class Headlamps {
    private Headlamps() {}

    /** effects: tells Luminance how a vehicle lights the road */
    static void register() {
        Luminance.forEntity(ModContent.VEHICLE_ENTITY.get(), Headlamps::beams);
    }

    private static Collection<? extends Source> beams(Vehicle vehicle) {
        VehicleProfile p = vehicle.profile();
        if (p == null || !vehicle.lit() || p.headlights().isEmpty()) {
            return List.of();
        }
        VehicleProfile.Headlights lights = p.headlights().get();
        List<Line> out = new ArrayList<>(lights.at().size());
        double heading = Math.toRadians(vehicle.getYRot());
        double fx = -Math.sin(heading);
        double fz = Math.cos(heading);
        for (Vec at : lights.at()) {
            Vec3 lamp = vehicle.rotate(p.localBlocks(at));
            double x0 = vehicle.getX() + lamp.x;
            double y0 = vehicle.getY() + lamp.y;
            double z0 = vehicle.getZ() + lamp.z;
            out.add(new Line(x0, y0, z0, x0 + fx * lights.range(), y0 - 0.5, z0 + fz * lights.range(), 15));
        }
        return out;
    }
}
