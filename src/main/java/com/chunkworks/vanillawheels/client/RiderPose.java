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
import com.chunkworks.vanillawheels.domain.Suspension;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Leans everyone aboard with the body, draws a rider whose entity is at
 * the seat's eye back in the seat, and backs the camera off for it.
 * The body is drawn pitched and
 * rolled by its {@link Suspension}; a rider drawn upright on a nose-up
 * truck looks bolted to the world, so before a living entity that rides a
 * {@link Vehicle} is drawn, its pose is turned by the body's pitch and
 * roll about the point where it sits, in the body's frame, and the turn is
 * undone after. The pre-handler runs last of all so it never pushes for a
 * render another mod cancelled; the post-handler runs first so it pops
 * before anyone else reads the stack.
 */
public final class RiderPose {
    private RiderPose() {}

    private static boolean pushed;

    public static void onPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity rider = event.getEntity();
        if (!(rider.getVehicle() instanceof Vehicle v)) {
            return;
        }
        Suspension s = v.suspension(event.getPartialTick());
        Vec3 shift = v.drawOffset(rider, event.getPartialTick());
        if (s.pitch() == 0.0 && s.roll() == 0.0 && shift.equals(Vec3.ZERO)) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        float yaw = Mth.rotLerp(event.getPartialTick(), v.yRotO, v.getYRot());
        // The seat point is where the rider's vehicle attachment meets the body: above the feet, where the stack's origin is.
        Vec3 seat = rider.getVehicleAttachmentPoint(v);
        pose.pushPose();
        // A rider whose entity sits at the seat's eye is drawn back in the seat.
        pose.translate(shift.x, shift.y, shift.z);
        pose.translate(seat.x, seat.y, seat.z);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotation((float) s.pitch()));
        pose.mulPose(Axis.ZP.rotation((float) s.roll()));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-seat.x, -seat.y, -seat.z);
        pushed = true;
    }

    public static void onPost(RenderLivingEvent.Post<?, ?> event) {
        if (pushed) {
            pushed = false;
            event.getPoseStack().popPose();
        }
    }

    /** Blocks of camera distance for each block of a vehicle's length, in third person; the game's own is four blocks, for a person. */
    private static final float CAMERA_PER_BLOCK = 1.5f;
    private static final float CAMERA_BASE = 1.5f;

    /**
     * effects: backs the third-person camera off in proportion to the
     * vehicle the viewer rides, so a truck fills the frame the way a
     * person does at the game's four blocks, instead of the camera sitting
     * on its tailgate
     */
    public static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (event.getCamera().getEntity().getVehicle() instanceof Vehicle v && v.profile() != null) {
            float want = CAMERA_BASE + CAMERA_PER_BLOCK * (float) v.profile().body().length();
            event.setDistance(Math.max(event.getDistance(), want));
        }
    }

    static void register(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class, RiderPose::onPre);
        bus.addListener(EventPriority.HIGHEST, false, RenderLivingEvent.Post.class, RiderPose::onPost);
        bus.addListener(RiderPose::onCameraDistance);
    }
}
