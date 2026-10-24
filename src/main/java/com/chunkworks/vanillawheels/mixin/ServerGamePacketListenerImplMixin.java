/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Chunkworks
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
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server re-runs a move its driver's client reports at the heading the client made it at
 * (D-0039). Every vehicle's tick turns, then moves, and the client reports where it went and its new
 * heading; the game re-runs the move with the heading it had before and takes the new one only
 * after, whatever the re-run said. A footprint or a hull turns with the body, so the server tested
 * it a tick's turn behind the client: a Huey's tail clearing a tree at the new heading met it at the
 * old one, the re-run moved it nowhere, and the game put it back ("moved wrongly").
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Inject(method = "handleMoveVehicle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void vanillawheels$atTheReportedHeading(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (((ServerGamePacketListenerImpl) (Object) this).player.getRootVehicle() instanceof Vehicle v) {
            v.faceReported(packet.getYRot());
        }
    }
}
