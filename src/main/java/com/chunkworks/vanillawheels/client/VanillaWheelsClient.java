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
import com.chunkworks.vanillawheels.VanillaWheelsMod;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The client's registrations: the renderer, the keys, the meshes, the item icons, the headlamps, the ticks. */
@EventBusSubscriber(modid = VanillaWheelsMod.MOD_ID, value = Dist.CLIENT)
public final class VanillaWheelsClient {
    private VanillaWheelsClient() {}

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(Controls::onClientTick);
        NeoForge.EVENT_BUS.addListener(Radio::onClientTick);
        NeoForge.EVENT_BUS.addListener(Radio::onLoggingOut);
        if (ModList.get().isLoaded("luminance")) {
            event.enqueueWork(Headlamps::register);
        }
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.VEHICLE_ENTITY.get(), VehicleRenderer::new);
        event.registerBlockEntityRenderer(ModContent.LIFT_BE.get(), com.chunkworks.vanillawheels.client.lift.LiftRenderer::new);
    }

    @SubscribeEvent
    public static void onScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(ModContent.LIFT_MENU.get(), com.chunkworks.vanillawheels.client.lift.LiftScreen::new);
    }

    @SubscribeEvent
    public static void onGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.chunkworks.vanillawheels.api.VanillaWheels.id("lights"), LightsIndicator::draw);
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(Keys.HORN);
        event.register(Keys.LIGHTS);
    }

    @SubscribeEvent
    public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(MeshLibrary.INSTANCE);
    }

    @SubscribeEvent
    public static void onClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions drawn = new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new VehicleItemRenderer();
                }
                return renderer;
            }
        };
        event.registerItem(drawn, ModContent.VEHICLE_ITEM.get(), ModContent.CHASSIS.get());
    }
}
