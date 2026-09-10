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
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The vehicle item and the chassis item are their vehicle's mesh drawn
 * small: scaled into the unit cube the item model gives us, the nose
 * toward the viewer, with the item's paint on the body; a chassis has no
 * wheels. No vehicle mod draws an icon.
 */
public final class VehicleItemRenderer extends BlockEntityWithoutLevelRenderer {
    public VehicleItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation id = VanillaWheels.vehicleOf(stack).orElse(null);
        if (id == null || mc.level == null) {
            return;
        }
        VehicleProfile p = VanillaWheels.profile(mc.level.registryAccess(), id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
        if (p == null) {
            return;
        }
        Appearance a = Appearance.of(p);
        boolean chassis = stack.is(ModContent.CHASSIS.get());
        int colour = VehicleRenderer.colourOf(stack.get(ModContent.PAINT.get()), p);
        float scale = (float) (0.9 / a.extent);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5 - (p.body().height() * scale) / 2.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(context == ItemDisplayContext.GUI ? 210.0f : 180.0f));
        poseStack.scale(scale, scale, scale);
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(a.texture));
        MeshDrawer.draw(a.rest, poseStack.last(), solid, MeshDrawer.WHITE, light, OverlayTexture.NO_OVERLAY, MeshDrawer.Shading.LIT);
        MeshDrawer.draw(a.body, poseStack.last(), solid, colour, light, OverlayTexture.NO_OVERLAY, MeshDrawer.Shading.LIT);
        MeshDrawer.draw(a.lamps, poseStack.last(), solid, MeshDrawer.WHITE, light, OverlayTexture.NO_OVERLAY, MeshDrawer.Shading.LIT);
        for (Appearance.Needle needle : a.needles) {
            poseStack.pushPose();
            VehicleRenderer.rotateAround(poseStack, needle.dial().rotation(0.0));
            MeshDrawer.draw(needle.mesh(), poseStack.last(), solid, MeshDrawer.WHITE, light, OverlayTexture.NO_OVERLAY, MeshDrawer.Shading.LIT);
            poseStack.popPose();
        }
        for (Appearance.Hinge door : a.doors) {
            MeshDrawer.draw(door.mesh(), poseStack.last(), solid, MeshDrawer.WHITE, light, OverlayTexture.NO_OVERLAY, MeshDrawer.Shading.LIT);
        }
        if (!chassis) {
            for (Appearance.WheelSlot slot : a.wheels) {
                poseStack.pushPose();
                poseStack.translate(slot.at().x(), slot.at().y(), slot.at().z());
                if (slot.right()) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                }
                MeshDrawer.draw(a.wheel, poseStack.last(), solid, MeshDrawer.WHITE, light, OverlayTexture.NO_OVERLAY, MeshDrawer.Shading.LIT);
                poseStack.popPose();
            }
        }
        if (a.glass.quadCount() > 0) {
            VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(a.texture));
            MeshDrawer.draw(a.glass, poseStack.last(), glass, MeshDrawer.WHITE, light, OverlayTexture.NO_OVERLAY, MeshDrawer.Shading.LIT);
        }
        poseStack.popPose();
    }
}
