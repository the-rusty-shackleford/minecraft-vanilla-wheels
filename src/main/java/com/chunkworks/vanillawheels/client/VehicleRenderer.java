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
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.BodyPose;
import com.chunkworks.vanillawheels.domain.Rotation;
import com.chunkworks.vanillawheels.domain.Suspension;
import com.chunkworks.vanillawheels.domain.Vec;
import com.chunkworks.vanillawheels.domain.WheelSpin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a vehicle: the body turned to its yaw and tilted by its
 * suspension, the paint on the body's part, the lamps bright when lit,
 * each needle turned by what its gauge shows, each wheel at its slot
 * spinning with the distance rolled and turned with the steer on the
 * steering pair, the doors swung when open, and the glass last, through
 * the translucent type. Two draw calls a vehicle.
 */
public final class VehicleRenderer extends EntityRenderer<Vehicle> {
    private static final ResourceLocation MISSING = ResourceLocation.withDefaultNamespace("textures/misc/unknown_server.png");

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2f;
    }

    @Override
    public ResourceLocation getTextureLocation(Vehicle vehicle) {
        VehicleProfile p = vehicle.profile();
        return p == null ? MISSING : p.texture();
    }

    @Override
    public void render(Vehicle vehicle, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        VehicleProfile p = vehicle.profile();
        if (p == null) {
            return;
        }
        Appearance a = Appearance.of(p);
        Suspension s = vehicle.suspension(partialTick);
        float yaw = Mth.rotLerp(partialTick, vehicle.yRotO, vehicle.getYRot());
        BodyPose pose = new BodyPose(Math.toRadians(yaw), s.pitch(), s.roll());
        poseStack.pushPose();
        poseStack.translate(0.0, s.lift(), 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotation((float) pose.pitch()));
        poseStack.mulPose(Axis.ZP.rotation((float) pose.roll()));

        int overlay = vehicle.getHurtTime() > 0 ? OverlayTexture.pack(0, true) : OverlayTexture.NO_OVERLAY;
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(a.texture));
        MeshDrawer.draw(a.rest, poseStack.last(), solid, MeshDrawer.WHITE, packedLight, overlay, MeshDrawer.Shading.LIT);
        MeshDrawer.draw(a.body, poseStack.last(), solid, paintOf(vehicle, p), packedLight, overlay, MeshDrawer.Shading.LIT);
        boolean lit = vehicle.lit();
        MeshDrawer.draw(a.lamps, poseStack.last(), solid, MeshDrawer.WHITE, lit ? LightTexture.FULL_BRIGHT : packedLight, overlay,
                lit ? MeshDrawer.Shading.LAMP : MeshDrawer.Shading.LIT);

        double speedFraction = Math.min(1.0, Math.abs(vehicle.speed()) / vehicle.tuning().maxSpeed());
        for (Appearance.Needle needle : a.needles) {
            double fraction = needle.kind() == VehicleProfile.GaugeKind.SPEED ? speedFraction : vehicle.fuelFraction();
            Rotation r = needle.dial().rotation(fraction);
            poseStack.pushPose();
            rotateAround(poseStack, r);
            MeshDrawer.draw(needle.mesh(), poseStack.last(), solid, MeshDrawer.WHITE, packedLight, overlay, MeshDrawer.Shading.LIT);
            poseStack.popPose();
        }
        float swing = vehicle.doorSwing(partialTick);
        for (Appearance.Hinge door : a.doors) {
            poseStack.pushPose();
            if (swing > 0.0f) {
                Rotation open = door.open();
                rotateAround(poseStack, new Rotation(open.pivot(), open.axis(), open.radians() * swing));
            }
            MeshDrawer.draw(door.mesh(), poseStack.last(), solid, MeshDrawer.WHITE, packedLight, overlay, MeshDrawer.Shading.LIT);
            poseStack.popPose();
        }

        double spin = WheelSpin.radians(vehicle.wheelTravel(partialTick), a.wheelRadius);
        float steer = vehicle.steer();
        for (Appearance.WheelSlot slot : a.wheels) {
            poseStack.pushPose();
            poseStack.translate(slot.at().x(), slot.at().y(), slot.at().z());
            if (slot.steers()) {
                poseStack.mulPose(Axis.YP.rotation(-steer));
            }
            if (slot.right()) {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                poseStack.mulPose(Axis.XP.rotation((float) -spin));
            } else {
                poseStack.mulPose(Axis.XP.rotation((float) spin));
            }
            MeshDrawer.draw(a.wheel, poseStack.last(), solid, MeshDrawer.WHITE, packedLight, overlay, MeshDrawer.Shading.LIT);
            poseStack.popPose();
        }

        if (a.glass.quadCount() > 0) {
            VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(a.texture));
            MeshDrawer.draw(a.glass, poseStack.last(), glass, MeshDrawer.WHITE, packedLight, overlay, MeshDrawer.Shading.LIT);
        }
        poseStack.popPose();
        super.render(vehicle, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /** effects: returns the body's colour as an ARGB int: the vehicle's paint, else the profile's default, else white */
    static int paintOf(Vehicle vehicle, VehicleProfile p) {
        DyeColor paint = vehicle.paint();
        if (paint == null) {
            paint = p.paint().map(VehicleProfile.Paint::defaultColor).orElse(null);
        }
        return paint == null ? MeshDrawer.WHITE : 0xFF000000 | paint.getTextureDiffuseColor();
    }

    /** effects: applies {@code r} to the pose stack: a turn about its axis through its pivot */
    static void rotateAround(PoseStack poseStack, Rotation r) {
        Vec pivot = r.pivot();
        Vec axis = r.axis();
        Quaternionf q = new Quaternionf().rotationAxis((float) r.radians(), new Vector3f((float) axis.x(), (float) axis.y(), (float) axis.z()));
        poseStack.rotateAround(q, (float) pivot.x(), (float) pivot.y(), (float) pivot.z());
    }
}
