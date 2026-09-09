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
package com.chunkworks.vanillawheels;

import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.net.Payloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * A vehicle protocol. A vehicle is a datapack entry and a mesh; this mod
 * owns everything that happens to it. The entry point registers the
 * game objects ({@link ModContent}), the profile registry, the network
 * payloads and the config, and nothing else.
 */
@Mod(VanillaWheelsMod.MOD_ID)
public final class VanillaWheelsMod {
    public static final String MOD_ID = VanillaWheels.NAMESPACE;

    public VanillaWheelsMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, WheelsConfig.SPEC);
        ModContent.register(modBus);
        modBus.addListener(Payloads::register);
        modBus.addListener((DataPackRegistryEvent.NewRegistry event) ->
                event.dataPackRegistry(VanillaWheels.VEHICLES, VehicleProfile.CODEC, VehicleProfile.CODEC));
        modBus.addListener(ModContent::buildCreativeTabs);
        NeoForge.EVENT_BUS.addListener(Vehicle::onEntityJoin);
    }
}
