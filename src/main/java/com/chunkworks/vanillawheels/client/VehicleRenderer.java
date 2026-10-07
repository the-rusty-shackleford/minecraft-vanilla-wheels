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
 *
 * <p>A protocol built on this one (D-0030) extends it: {@link #appearance}
 * names the parts it moves itself, which are cut out of the body, and
 * {@link #drawExtras} draws them in the body's frame, posed and rocked with
 * it, before the chests and the glass.
 */
public class VehicleRenderer extends EntityRenderer<Vehicle> {
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

    /** effects: returns whether the frame is what someone aboard {@code vehicle} sees through their own eyes */
    private static boolean throughOwnEyes(Vehicle vehicle) {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        return camera != null && camera.getVehicle() == vehicle && mc.options.getCameraType().isFirstPerson();
    }

    /**
     * effects: draws the game's own double chest where the profile puts chest
     * {@code index}, at its scale, its front turned as the profile says, the lid up by that
     * chest's openness -- the left half on the chest's own left, as the game lays
     * a double chest
     */
    private void drawChest(Vehicle vehicle, VehicleProfile p, int index, VehicleProfile.Chest chest, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int overlay) {
        Vec at = p.localBlocks(chest.at());
        float lid = vehicle.lidOpenness(index, partialTick);
        poseStack.pushPose();
        poseStack.translate(at.x(), at.y(), at.z());
        poseStack.mulPose(Axis.YP.rotationDegrees((float) -chest.yaw()));
        float k = (float) chest.scale();
        poseStack.scale(k, k, k);
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
        return p == null ? MISSING : appearance(vehicle, p).texture;
    }

    /** effects: returns what {@code vehicle} of profile {@code p} is drawn from: the profile's appearance, with no extras here */
    protected Appearance appearance(Vehicle vehicle, VehicleProfile p) {
        return Appearance.of(p);
    }

    /**
     * effects: draws what a protocol built on this one moves itself ({@link Appearance#extras}),
     * with the pose stack in the body's frame -- turned, pitched, rolled and rocked as the body is
     * -- into {@code solid}, the body's cutout buffer, at {@code light}; nothing here
     */
    protected void drawExtras(Vehicle vehicle, VehicleProfile p, Appearance a, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffers, VertexConsumer solid, int light, int overlay) {}

    @Override
    public void render(Vehicle vehicle, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        VehicleProfile p = vehicle.profile();
        if (p == null) {
            return;
        }
        Appearance a = appearance(vehicle, p);
        Suspension s = vehicle.suspension(partialTick);
        float yaw = Mth.rotLerp(partialTick, vehicle.yRotO, vehicle.getYRot());
        BodyPose pose = new BodyPose(Math.toRadians(yaw), s.pitch(), s.roll());
        poseStack.pushPose();
        poseStack.translate(0.0, s.lift(), 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotation((float) pose.pitch()));
        poseStack.mulPose(Axis.ZP.rotation((float) pose.roll()));
        // A blow rocks it side to side as one rocks a boat, harder the more knocks it has to shake off (D-0025).
        float rocking = vehicle.getHurtTime() - partialTick;
        if (rocking > 0.0f) {
            float weight = Math.max(0.0f, vehicle.getDamage() - partialTick);
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(rocking) * rocking * weight / 20.0f * vehicle.getHurtDir()));
        }

        // No hurt tint: a vehicle is not a mob. Its condition shows as the wrench row (WrenchBar).
        int overlay = OverlayTexture.NO_OVERLAY;
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(a.texture));
        MeshDrawer.draw(a.rest, poseStack.last(), solid, MeshDrawer.WHITE, packedLight, overlay, MeshDrawer.Shading.LIT);
        if (a.cockpit.quadCount() > 0 && !throughOwnEyes(vehicle)) {
            MeshDrawer.draw(a.cockpit, poseStack.last(), solid, MeshDrawer.WHITE, packedLight, overlay, MeshDrawer.Shading.LIT);
        }
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
            if (door.painted().quadCount() > 0) {
                MeshDrawer.draw(door.painted(), poseStack.last(), solid, paintOf(vehicle, p), packedLight, overlay, MeshDrawer.Shading.LIT);
            }
            if (door.lamps().quadCount() > 0) {
                MeshDrawer.draw(door.lamps(), poseStack.last(), solid, MeshDrawer.WHITE, lit ? LightTexture.FULL_BRIGHT : packedLight, overlay,
                        lit ? MeshDrawer.Shading.LAMP : MeshDrawer.Shading.LIT);
            }
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

        // Wheels with a texture of their own asked the buffer source for another type, which in the
        // level's immediate source ends the body's batch: the extras get the body's buffer asked for
        // again, live. (A Chinook, its own wheel mesh and its rotors, crashed every client without this.)
        if (wheelOut != solid) {
            solid = buffers.getBuffer(RenderType.entityCutoutNoCull(a.texture));
        }
        drawExtras(vehicle, p, a, partialTick, poseStack, buffers, solid, packedLight, overlay);

        if (p.storage().isPresent()) {
            java.util.List<VehicleProfile.Chest> chests = p.storage().get().chests();
            for (int i = 0; i < chests.size(); i++) {
                drawChest(vehicle, p, i, chests.get(i), partialTick, poseStack, buffers, packedLight, overlay);
            }
        }

        // The cockpit's glass (D-0031): a bubble or a port the rider looks out through is not drawn for their own eyes.
        boolean cockpitGlass = a.cockpitGlass.quadCount() > 0 && !throughOwnEyes(vehicle);
        if (a.glass.quadCount() > 0 || cockpitGlass) {
            VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(a.texture));
            Entity camera = Minecraft.getInstance().getCameraEntity();
            int tint = camera != null && camera.getVehicle() == vehicle ? GLASS_FROM_INSIDE : MeshDrawer.WHITE;
            MeshDrawer.draw(a.glass, poseStack.last(), glass, tint, packedLight, overlay, MeshDrawer.Shading.LIT);
            if (cockpitGlass) {
                MeshDrawer.draw(a.cockpitGlass, poseStack.last(), glass, tint, packedLight, overlay, MeshDrawer.Shading.LIT);
            }
        }
        poseStack.popPose();
        super.render(vehicle, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /**
     * effects: returns the body's colour as an ARGB int: the vehicle's dye, lifted; else the profile's factory
     * colour, exactly; else the profile's default dye, lifted; else white
     */
    public static int paintOf(Vehicle vehicle, VehicleProfile p) {
        return colourOf(vehicle.paint(), p);
    }

    static int colourOf(@Nullable DyeColor dye, VehicleProfile p) {
        return Vehicle.colourOf(dye, p);
    }

    /** effects: applies {@code r} to the pose stack: a turn about its axis through its pivot */
    public static void rotateAround(PoseStack poseStack, Rotation r) {
        Vec pivot = r.pivot();
        Vec axis = r.axis();
        Quaternionf q = new Quaternionf().rotationAxis((float) r.radians(), new Vector3f((float) axis.x(), (float) axis.y(), (float) axis.z()));
        poseStack.rotateAround(q, (float) pivot.x(), (float) pivot.y(), (float) pivot.z());
    }
}
