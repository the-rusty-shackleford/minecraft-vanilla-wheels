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
package com.chunkworks.vanillawheels.client.lift;

import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.domain.LiftMotion;
import com.chunkworks.vanillawheels.domain.LiftStatus;
import com.chunkworks.vanillawheels.lift.LiftMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The lift's screen: the four slots, Build and Paint, a line saying what
 * the greyed button waits for, and a bar while a job runs. Every state
 * shown is the server's, read from the menu's data slots.
 */
public final class LiftScreen extends AbstractContainerScreen<LiftMenu> {
    private static final ResourceLocation BACKGROUND = VanillaWheels.id("textures/gui/mechanic_lift.png");
    private Button build;
    private Button paint;

    public LiftScreen(LiftMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 184;
        inventoryLabelY = 90;
    }

    @Override
    protected void init() {
        super.init();
        build = addRenderableWidget(Button.builder(Component.translatable("vanillawheels.lift.build"), b -> press(LiftMenu.BUILD_BUTTON))
                .bounds(leftPos + 26, topPos + 46, 60, 20).build());
        paint = addRenderableWidget(Button.builder(Component.translatable("vanillawheels.lift.paint"), b -> press(LiftMenu.PAINT_BUTTON))
                .bounds(leftPos + 98, topPos + 46, 60, 20).build());
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        build.active = menu.buildStatus() == LiftStatus.Build.READY;
        paint.active = menu.paintStatus() == LiftStatus.Paint.READY;
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int ticks = menu.jobTicks();
        if (ticks > 0) {
            int width = (int) Math.round(140.0 * (LiftMotion.JOB - ticks) / LiftMotion.JOB);
            g.fill(leftPos + 18, topPos + 81, leftPos + 18 + width, topPos + 85, 0xFFE0B040);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        Component status = menu.jobTicks() > 0 ? Component.translatable("vanillawheels.lift.status.busy")
                : menu.buildStatus() == LiftStatus.Build.READY ? Component.translatable("vanillawheels.lift.status.build_ready")
                : menu.paintStatus() == LiftStatus.Paint.READY ? Component.translatable("vanillawheels.lift.status.paint_ready")
                : menu.paintStatus() == LiftStatus.Paint.NO_DYE ? Component.translatable("vanillawheels.lift.status.no_dye")
                : menu.buildStatus() == LiftStatus.Build.OCCUPIED ? Component.translatable("vanillawheels.lift.status.occupied")
                : Component.translatable("vanillawheels.lift.status.parts");
        g.drawString(font, status, 8, 69, 0x404040, false);
    }
}
