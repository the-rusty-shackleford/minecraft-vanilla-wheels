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

import com.chunkworks.vanillawheels.ToolboxMenu;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * A vehicle's toolbox on screen (D-0023): its one slot, a crowbar's outline
 * in it while the crowbar is out, and the player's inventory, laid out as the
 * game lays a hopper. Hovering the empty slot says whose crowbar goes there.
 */
public final class ToolboxScreen extends AbstractContainerScreen<ToolboxMenu> {
    private static final ResourceLocation BACKGROUND = VanillaWheels.id("textures/gui/toolbox.png");
    private static final ResourceLocation HINT = VanillaWheels.id("textures/gui/slot_crowbar.png");

    public ToolboxScreen(ToolboxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 133;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.index == ToolboxMenu.SLOT && !hoveredSlot.hasItem()) {
            g.renderTooltip(font, font.split(Component.translatable("vanillawheels.toolbox.slot"), Math.min(220, width - 16)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (!menu.getSlot(ToolboxMenu.SLOT).hasItem()) {
            g.blit(HINT, leftPos + 80, topPos + 20, 0, 0, 16, 16, 16, 16);
        }
    }
}
