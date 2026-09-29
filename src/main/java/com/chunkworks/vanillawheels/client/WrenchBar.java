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

import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.domain.WrenchRow;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A vehicle's condition as a row of wrenches above the hunger bar
 * ({@link WrenchRow}), in place of the red flash a hurt vehicle used to
 * give like a mob. While riding: the vehicle's row, and above it the
 * trailer it tows. On foot: the row of the vehicle under the crosshair, or
 * of the vehicle what is under the crosshair rides in.
 * Drawn only where the game draws its survival bars, and stacked on the
 * HUD's right column ({@code Gui.rightHeight}), so air bubbles and a
 * mount's hearts keep their places.
 */
public final class WrenchBar {
    private WrenchBar() {}

    private static final ResourceLocation CONTAINER = VanillaWheels.id("textures/gui/wrench_container.png");
    private static final ResourceLocation CONTAINER_BLINK = VanillaWheels.id("textures/gui/wrench_container_blink.png");
    private static final ResourceLocation FULL = VanillaWheels.id("textures/gui/wrench_full.png");
    private static final ResourceLocation HALF = VanillaWheels.id("textures/gui/wrench_half.png");
    private static final ResourceLocation LOST_FULL = VanillaWheels.id("textures/gui/wrench_lost_full.png");
    private static final ResourceLocation LOST_HALF = VanillaWheels.id("textures/gui/wrench_lost_half.png");
    private static final int SIZE = 9;

    /** effects: draws the rows for the vehicle ridden and its trailer, or for the vehicle looked at */
    public static void draw(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        Vehicle first;
        Vehicle second = null;
        if (mc.player.getVehicle() instanceof Vehicle ridden) {
            first = ridden;
            second = ridden.trailer();
        } else if (mc.hitResult instanceof EntityHitResult hit && vehicleOf(hit.getEntity()) instanceof Vehicle seen) {
            first = seen;
        } else {
            return;
        }
        RenderSystem.enableBlend();
        row(g, mc, first);
        if (second != null) {
            row(g, mc, second);
        }
        RenderSystem.disableBlend();
    }

    /**
     * effects: returns the vehicle {@code e} is, is a hit box of, or rides in; null for anything
     * else. A cow aboard stands with its box through the trailer's wall and is often what the
     * crosshair meets first on a loaded trailer.
     */
    private static @Nullable Vehicle vehicleOf(Entity e) {
        if (e instanceof Vehicle v) {
            return v;
        }
        if (e instanceof Vehicle.Part part) {
            return part.getParent();
        }
        return e.getVehicle() instanceof Vehicle v ? v : null;
    }

    /** effects: draws {@code v}'s row at the top of the HUD's right column, filling from the right as the hunger bar does, and raises the column */
    private static void row(GuiGraphics g, Minecraft mc, Vehicle v) {
        int right = g.guiWidth() / 2 + 91;
        int top = g.guiHeight() - mc.gui.rightHeight;
        mc.gui.rightHeight += 10;
        int condition = v.condition();
        WrenchRow.Watch watch = v.wrenchWatch();
        boolean lit = watch.lit();
        int now = WrenchRow.halves(condition);
        int pale = WrenchRow.halves(watch.shown());
        boolean jiggles = WrenchRow.jiggles(condition);
        int tick = mc.gui.getGuiTicks();
        for (int i = 0; i < WrenchRow.ICONS; i++) {
            int x = right - i * 8 - SIZE;
            int y = top + (jiggles ? WrenchRow.jiggle(tick, i) : 0);
            blit(g, lit ? CONTAINER_BLINK : CONTAINER, x, y);
            if (lit) {
                switch (WrenchRow.fill(pale, i)) {
                    case FULL -> blit(g, LOST_FULL, x, y);
                    case HALF -> blit(g, LOST_HALF, x, y);
                    case EMPTY -> {}
                }
            }
            switch (WrenchRow.fill(now, i)) {
                case FULL -> blit(g, FULL, x, y);
                case HALF -> blit(g, HALF, x, y);
                case EMPTY -> {}
            }
        }
    }

    private static void blit(GuiGraphics g, ResourceLocation sprite, int x, int y) {
        g.blit(sprite, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
    }
}
