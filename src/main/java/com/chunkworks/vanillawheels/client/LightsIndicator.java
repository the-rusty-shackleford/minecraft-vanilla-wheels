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
import com.chunkworks.vanillawheels.api.VehicleProfile;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A small readout beside the hotbar, on its left, while riding a vehicle
 * that has headlights: a lamp glyph and the mode -- OFF, ON, or AUTO --
 * the glyph lit whenever the lamps are. Nothing on the truck itself says
 * which mode the key has reached, and nothing shows at all when not aboard.
 */
public final class LightsIndicator {
    private LightsIndicator() {}

    private static final ResourceLocation LAMP_OFF = VanillaWheels.id("textures/gui/lamp_off.png");
    private static final ResourceLocation LAMP_ON = VanillaWheels.id("textures/gui/lamp_on.png");
    private static final int WIDTH = 16;

    /** effects: draws the readout if the local player rides a vehicle with headlights */
    public static void draw(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof Vehicle v)) {
            return;
        }
        VehicleProfile p = v.profile();
        if (p == null || p.headlights().isEmpty()) {
            return;
        }
        Vehicle.Lights mode = v.lights();
        Component label = Component.translatable("vanillawheels.lights." + mode.name().toLowerCase(java.util.Locale.ROOT));
        int labelWidth = mc.font.width(label);
        int x = g.guiWidth() / 2 - 91 - 6 - labelWidth - WIDTH - 2;
        int y = g.guiHeight() - 19;
        g.blit(v.lit() ? LAMP_ON : LAMP_OFF, x, y, 0, 0, WIDTH, WIDTH, WIDTH, WIDTH);
        g.drawString(mc.font, label, x + WIDTH + 2, y + 4, v.lit() ? 0xFFF0C040 : 0xFFDDDDDD, true);
    }
}
