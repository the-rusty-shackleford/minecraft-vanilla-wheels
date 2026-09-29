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

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Right-click a vehicle with it, crouching or not, to pry the vehicle loose
 * into the hand, cargo, fuel, wear and key kept; the vehicle does the work
 * ({@link Vehicle#interactAt}). It took over from the wrench, whose icon now
 * means a vehicle's condition (D-0020); an old wrench loads as a crowbar.
 */
public final class CrowbarItem extends Item {
    public CrowbarItem(Properties properties) {
        super(properties);
    }

    /** effects: how it is used; for a vehicle's own crowbar, whose it is and that it goes home when lost (D-0023) */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.vanillawheels.crowbar.use").withStyle(ChatFormatting.GRAY));
        OwnCrowbars.Owner owner = stack.get(ModContent.CROWBAR_OF.get());
        if (owner != null) {
            Component vehicle = Component.translatable("vehicle." + owner.profile().getNamespace() + "." + owner.profile().getPath());
            tooltip.add(Component.translatable("item.vanillawheels.crowbar.own", vehicle).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.vanillawheels.crowbar.home").withStyle(ChatFormatting.GRAY));
        }
    }
}
