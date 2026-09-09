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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A vehicle's body before its wheels and engine: the lift's main ingredient, named for its vehicle. */
public final class ChassisItem extends Item {
    public ChassisItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return VanillaWheels.vehicleOf(stack)
                .<Component>map(id -> Component.translatable("vanillawheels.chassis", Component.translatable("vehicle." + id.getNamespace() + "." + id.getPath())))
                .orElseGet(() -> super.getName(stack));
    }
}
