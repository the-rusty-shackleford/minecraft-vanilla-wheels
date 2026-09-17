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
 * along the body's heading; for a lit unpowered one, a point at each lens. This class names Luminance's types, so it is
 * loaded only when Luminance is there (see {@link VanillaWheelsClient}).
 */
final class Headlamps {
    private Headlamps() {}

    /** effects: tells Luminance how a vehicle lights the road */
    static void register() {
        Luminance.forEntityInterpolated(ModContent.VEHICLE_ENTITY.get(), Headlamps::beams);
    }

    private static Collection<? extends Source> beams(Vehicle vehicle, float partialTick) {
        VehicleProfile p = vehicle.profile();
        if (p == null || !vehicle.lit() || p.headlights().isEmpty()) {
            return List.of();
        }
        VehicleProfile.Headlights lights = p.headlights().get();
        List<Source> out = new ArrayList<>(lights.at().size());
        double heading = Math.toRadians(net.minecraft.util.Mth.rotLerp(partialTick, vehicle.yRotO, vehicle.getYRot()));
        double fx = -Math.sin(heading);
        double fz = Math.cos(heading);
        // An unpowered vehicle (a trailer) has no headlamps to aim: its lamps are markers, a point
        // at each lens of luminance twice the range -- Luminance falls one level a block, so a
        // marker of range 3 is at 6 on its lens, 3 at three blocks, gone at six. nfx's rewrite.
        boolean markers = !p.isPowered();
        int level = Math.max(1, Math.min(Source.MAX_LUMINANCE, 2 * lights.range()));
        for (Vec at : lights.at()) {
            Vec local = p.localBlocks(at);
            Vec3 lamp = new Vec3(local.x(), local.y(), local.z()).yRot((float) -heading);
            double x0 = net.minecraft.util.Mth.lerp(partialTick, vehicle.xOld, vehicle.getX()) + lamp.x;
            double y0 = net.minecraft.util.Mth.lerp(partialTick, vehicle.yOld, vehicle.getY()) + lamp.y;
            double z0 = net.minecraft.util.Mth.lerp(partialTick, vehicle.zOld, vehicle.getZ()) + lamp.z;
            if (markers) {
                out.add(new com.chunkworks.luminance.domain.Point(x0, y0, z0, level));
            } else {
                out.add(new Line(x0, y0, z0, x0 + fx * lights.range(), y0 - 0.5, z0 + fz * lights.range(), 15));
            }
        }
        return out;
    }
}
