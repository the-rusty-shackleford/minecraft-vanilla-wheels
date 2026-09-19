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
import net.minecraft.client.gui.components.Tooltip;
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
    private static final String[] SLOT_NAMES = {"chassis", "wheels", "engine", "dye"};
    private static final ResourceLocation[] SLOT_HINTS = java.util.Arrays.stream(SLOT_NAMES)
            .map(name -> VanillaWheels.id("textures/gui/slot_" + name + ".png"))
            .toArray(ResourceLocation[]::new);
    private Button build;
    private Button paint;
    private Button repair;

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
                .bounds(leftPos + 26, topPos + 46, 60, 20)
                .tooltip(Tooltip.create(Component.translatable("vanillawheels.lift.build.help"))).build());
        paint = addRenderableWidget(Button.builder(Component.translatable("vanillawheels.lift.paint"), b -> press(LiftMenu.PAINT_BUTTON))
                .bounds(leftPos + 98, topPos + 46, 60, 20)
                .tooltip(Tooltip.create(Component.translatable("vanillawheels.lift.paint.help"))).build());
        repair = addRenderableWidget(Button.builder(Component.translatable("vanillawheels.lift.repair"), b -> press(LiftMenu.REPAIR_BUTTON))
                .bounds(leftPos + 190, topPos + 91, 92, 20).build());
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        imageWidth = menu.repairVisible() ? 294 : 176;
        leftPos = (width - imageWidth) / 2;
        build.setX(leftPos + 26); paint.setX(leftPos + 98); repair.setX(leftPos + 190);
        repair.visible = menu.repairVisible();
        repair.active = menu.repairReady();
        build.active = menu.buildStatus() == LiftStatus.Build.READY;
        paint.active = menu.paintStatus() == LiftStatus.Paint.READY;
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.index < SLOT_NAMES.length && !hoveredSlot.hasItem()) {
            g.renderTooltip(font, font.split(Component.translatable(
                    "vanillawheels.lift.slot." + SLOT_NAMES[hoveredSlot.index]), Math.min(220, width - 16)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, 176, imageHeight);
        if (menu.repairVisible()) {
            int x = leftPos + 182;
            g.fill(x, topPos, x + 112, topPos + 120, 0xFF373737);
            g.fill(x + 1, topPos + 1, x + 111, topPos + 119, 0xFFFFFFFF);
            g.fill(x + 3, topPos + 3, x + 109, topPos + 117, 0xFFC6C6C6);
            g.fill(leftPos + 191, topPos + 46, leftPos + 209, topPos + 64, 0xFF373737);
            g.fill(leftPos + 192, topPos + 47, leftPos + 208, topPos + 63, 0xFF8B8B8B);
            if (!menu.getSlot(LiftMenu.REPAIR).hasItem()) {
                g.renderItem(menu.repairMaterial(), leftPos + 192, topPos + 47);
                g.fill(leftPos + 192, topPos + 47, leftPos + 208, topPos + 63, 0x708B8B8B);
            }
        }
        for (int i = 0; i < SLOT_HINTS.length; i++) {
            var slot = menu.getSlot(i);
            if (!slot.hasItem()) {
                g.blit(SLOT_HINTS[i], leftPos + slot.x, topPos + slot.y, 0, 0, 16, 16, 16, 16);
            }
        }
        int ticks = menu.jobTicks();
        if (ticks > 0) {
            int width = (int) Math.round(140.0 * (LiftMotion.JOB - ticks) / LiftMotion.JOB);
            g.fill(leftPos + 18, topPos + 81, leftPos + 18 + width, topPos + 85, 0xFFE0B040);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        if (menu.repairVisible()) {
            g.drawString(font, Component.translatable("vanillawheels.lift.repair"), 190, 10, 0x404040, false);
            g.drawString(font, Component.translatable("vanillawheels.condition", String.format(java.util.Locale.ROOT, "%.1f", menu.repairCondition() / 100.0)), 190, 27, 0x404040, false);
            g.drawString(font, Component.translatable("vanillawheels.lift.repair_cost", menu.repairCost()), 215, 51, 0x404040, false);
            g.drawString(font, Component.translatable(menu.repairReady() ? "vanillawheels.lift.repair_ready" : "vanillawheels.lift.repair_material"), 190, 73, 0x404040, false);
        }
        Component status = menu.jobTicks() > 0 ? Component.translatable("vanillawheels.lift.status.busy")
                : menu.buildStatus() == LiftStatus.Build.READY ? Component.translatable("vanillawheels.lift.status.build_ready")
                : menu.paintStatus() == LiftStatus.Paint.READY ? Component.translatable("vanillawheels.lift.status.paint_ready")
                : menu.paintStatus() == LiftStatus.Paint.NO_DYE ? Component.translatable("vanillawheels.lift.status.no_dye")
                : menu.buildStatus() == LiftStatus.Build.OCCUPIED ? Component.translatable("vanillawheels.lift.status.occupied")
                : Component.translatable("vanillawheels.lift.status.parts");
        g.drawString(font, status, 8, 69, 0x404040, false);
    }
}
