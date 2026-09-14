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

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.fml.common.Mod;

/**
 * The test mod: it ships the box car (a profile, two meshes and a texture)
 * as a vehicle mod would, so the protocol is proven against a real vehicle
 * that exists only for the tests; the gametests and the booth ride in it.
 * Never shipped.
 */
@Mod(GameTestMod.MOD_ID)
public final class GameTestMod {
    public static final String MOD_ID = "vanillawheels_gametest";

    public GameTestMod(IEventBus modBus) {
        // The chest-spill gametest's apples vanished, once in a while, between the wrench and five
        // ticks later, and the taker was found by these two hooks (a mock rider's first-tick pickup
        // sweep from the world's origin, see VehicleGameTests.riderAt). They stay: whoever takes an
        // item off the server, or picks one up, leaves their name and a stack trace in the log.
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item) {
                LOG.warn("an item leaves the level at {} ({}), removed as {}", item.position(), item.getItem(), item.getRemovalReason(), new Throwable("who removed the item"));
            }
        });
        NeoForge.EVENT_BUS.addListener((ItemEntityPickupEvent.Pre event) -> {
            net.minecraft.world.entity.Entity vehicle = event.getPlayer().getVehicle();
            LOG.warn("{} picks up {} at {} (delay {}); the player at {} riding {} at {} (removed {}, passengers {})", event.getPlayer().getName().getString(), event.getItemEntity().getItem(), event.getItemEntity().position(), event.getItemEntity().hasPickUpDelay(),
                event.getPlayer().position(), vehicle, vehicle == null ? null : vehicle.position(), vehicle == null ? null : vehicle.isRemoved(), vehicle == null ? null : vehicle.getPassengers());
        });
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Vanilla Wheels gametest");
}
