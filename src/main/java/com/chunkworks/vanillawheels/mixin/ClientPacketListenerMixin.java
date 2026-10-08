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
package com.chunkworks.vanillawheels.mixin;

import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.client.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The line the game shows as you board ("Press Left Shift to Dismount") names the sneak key.
 * Aboard a body with {@link Vehicle#verticalControls} (D-0031) Shift goes down and the get-out key
 * lets you out, so there the line names the get-out key, in the game's own words. A car's line is
 * left alone: Shift is a car's way out.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @ModifyVariable(method = "handleSetEntityPassengersPacket", at = @At("STORE"))
    private Component vanillawheels$getOutKey(Component line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof Vehicle v && v.verticalControls()) {
            return Component.translatable("mount.onboard", Keys.GET_OUT.getTranslatedKeyMessage());
        }
        return line;
    }
}
