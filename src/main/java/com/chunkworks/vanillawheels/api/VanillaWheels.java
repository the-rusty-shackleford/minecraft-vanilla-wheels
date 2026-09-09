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
package com.chunkworks.vanillawheels.api;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;

/**
 * The protocol's public names: the registry every vehicle profile lives
 * in, the damage a vehicle does, and the lookups a vehicle mod or another
 * consumer needs. Everything else is this mod's business.
 */
public final class VanillaWheels {
    private VanillaWheels() {}

    public static final String NAMESPACE = "vanillawheels";

    /** The synced datapack registry of vehicle profiles: {@code data/<ns>/vanillawheels/vehicle/<name>.json}. */
    public static final ResourceKey<Registry<VehicleProfile>> VEHICLES = ResourceKey.createRegistryKey(id("vehicle"));

    /** What being run over is. */
    public static final ResourceKey<DamageType> RUN_OVER = ResourceKey.create(Registries.DAMAGE_TYPE, id("run_over"));

    /** effects: returns {@code vanillawheels:path} */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    /** effects: returns the profile {@code vehicle} names, if the registries have it */
    public static Optional<Holder.Reference<VehicleProfile>> profile(HolderLookup.Provider registries, ResourceLocation vehicle) {
        return registries.lookupOrThrow(VEHICLES).get(ResourceKey.create(VEHICLES, vehicle));
    }

    /** effects: returns the vehicle a vehicle or chassis item stands for, if it is one */
    public static Optional<ResourceLocation> vehicleOf(ItemStack stack) {
        return Optional.ofNullable(stack.get(com.chunkworks.vanillawheels.ModContent.VEHICLE.get()));
    }
}
