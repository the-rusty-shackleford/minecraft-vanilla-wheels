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
import com.chunkworks.vanillawheels.domain.Paint;
import com.chunkworks.vanillawheels.domain.BodyPose;
import com.chunkworks.vanillawheels.domain.Rotation;
import com.chunkworks.vanillawheels.domain.Suspension;
import com.chunkworks.vanillawheels.domain.Vec;
import com.chunkworks.vanillawheels.domain.WheelSpin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a vehicle: the body turned to its yaw and tilted by its
 * suspension, the paint on the body's part, the lamps bright when lit,
 * each needle turned by what its gauge shows, each wheel at its slot
 * spinning with the distance rolled and turned with the steer on the
 * steering pair, the doors swung when open, and the glass last, through
 * the translucent type -- faded to a third of its alpha while the camera
 * rides this vehicle, so a driver sees the road and not the pane. Two
 * draw calls a vehicle.
 */
public final class VehicleRenderer extends EntityRenderer<Vehicle> {
    private static final ResourceLocation MISSING = ResourceLocation.withDefaultNamespace("textures/misc/unknown_server.png");
    /** White at a third of the alpha: what the glass is multiplied by for whoever is aboard. */
    static final int GLASS_FROM_INSIDE = 0x55FFFFFF;

    private final ModelPart leftLid;
    private final ModelPart leftLock;
    private final ModelPart leftBottom;
    private final ModelPart rightLid;
    private final ModelPart rightLock;
    private final ModelPart rightBottom;

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2f;
        ModelPart left = context.bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT);
        leftLid = left.getChild("lid");
        leftLock = left.getChild("lock");
        leftBottom = left.getChild("bottom");
        ModelPart right = context.bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT);
        rightLid = right.getChild("lid");
        rightLock = right.getChild("lock");
        rightBottom = right.getChild("bottom");
    }

    /**
     * effects: draws the game's own double chest where the profile puts it,
     * its front turned as the profile says, the lid up by the vehicle's
     * openness -- the left half on the chest's own left, as the game lays
     * a double chest
     */
    private void drawChest(Vehicle vehicle, VehicleProfile p, VehicleProfile.Chest chest, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int overlay) {
        Vec at = p.localBlocks(chest.at());
        float lid = vehicle.lidOpenness(partialTick);
        poseStack.pushPose();
        poseStack.translate(at.x(), at.y(), at.z());
        poseStack.mulPose(Axis.YP.rotationDegrees((float) -chest.yaw()));
        VertexConsumer left = Sheets.CHEST_LOCATION_LEFT.buffer(buffers, RenderType::entityCutout);
        poseStack.pushPose();
        poseStack.translate(0.0, 0.0, -0.5);
        chestHalf(poseStack, left, leftLid, leftLock, leftBottom, lid, packedLight, overlay);
        poseStack.popPose();
        VertexConsumer right = Sheets.CHEST_LOCATION_RIGHT.buffer(buffers, RenderType::entityCutout);
        poseStack.pushPose();
        poseStack.translate(-1.0, 0.0, -0.5);
        chestHalf(poseStack, right, rightLid, rightLock, rightBottom, lid, packedLight, overlay);
        poseStack.popPose();
        poseStack.popPose();
    }

    private static void chestHalf(PoseStack poseStack, VertexConsumer out, ModelPart lid, ModelPart lock, ModelPart bottom, float openness, int light, int overlay) {
        lid.xRot = -(openness * (float) (Math.PI / 2));
        lock.xRot = lid.xRot;
        lid.render(poseStack, out, light, overlay);
        lock.render(poseStack, out, light, overlay);
        bottom.render(poseStack, out, light, overlay);
    }

    @Override
    public ResourceLocation getTextureLocation(Vehicle vehicle) {
        VehicleProfile p = vehicle.profile();
        return p == null ? MISSING : Appearance.of(p).texture;
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
        VertexConsumer wheelOut = a.wheelTexture.equals(a.texture) ? solid : buffers.getBuffer(RenderType.entityCutoutNoCull(a.wheelTexture));
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
            MeshDrawer.draw(a.wheel, poseStack.last(), wheelOut, MeshDrawer.WHITE, packedLight, overlay, MeshDrawer.Shading.LIT);
            poseStack.popPose();
        }

        p.storage().flatMap(VehicleProfile.Storage::chest).ifPresent(chest -> drawChest(vehicle, p, chest, partialTick, poseStack, buffers, packedLight, overlay));

        if (a.glass.quadCount() > 0) {
            VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(a.texture));
            Entity camera = Minecraft.getInstance().getCameraEntity();
            int tint = camera != null && camera.getVehicle() == vehicle ? GLASS_FROM_INSIDE : MeshDrawer.WHITE;
            MeshDrawer.draw(a.glass, poseStack.last(), glass, tint, packedLight, overlay, MeshDrawer.Shading.LIT);
        }
        poseStack.popPose();
        super.render(vehicle, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /**
     * effects: returns the body's colour as an ARGB int: the vehicle's dye, lifted; else the profile's factory
     * colour, exactly; else the profile's default dye, lifted; else white
     */
    static int paintOf(Vehicle vehicle, VehicleProfile p) {
        return colourOf(vehicle.paint(), p);
    }

    static int colourOf(@Nullable DyeColor dye, VehicleProfile p) {
        if (dye != null) {
            return 0xFF000000 | Paint.lift(dye.getTextureDiffuseColor());
        }
        return p.paint().map(pp -> pp.factory().map(rgb -> 0xFF000000 | rgb)
                .orElseGet(() -> 0xFF000000 | Paint.lift(pp.defaultColor().getTextureDiffuseColor()))).orElse(MeshDrawer.WHITE);
    }

    /** effects: applies {@code r} to the pose stack: a turn about its axis through its pivot */
    static void rotateAround(PoseStack poseStack, Rotation r) {
        Vec pivot = r.pivot();
        Vec axis = r.axis();
        Quaternionf q = new Quaternionf().rotationAxis((float) r.radians(), new Vector3f((float) axis.x(), (float) axis.y(), (float) axis.z()));
        poseStack.rotateAround(q, (float) pivot.x(), (float) pivot.y(), (float) pivot.z());
    }
}
