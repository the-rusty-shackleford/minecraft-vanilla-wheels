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
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift is "down" aboard a body that moves in three dimensions ({@link Vehicle#verticalControls},
 * D-0031), never a way out: the game dismounts a rider whose sneak key is down, and a pilot holding
 * it to go down would drop out of the sky or out into the sea. The get-out key lets riders out
 * instead. And a rider holding Shift is not drawn crouching: the crouch would drop the eye and with
 * it the seat, which hangs from the eye. Rotorcraft had this for its aircraft (its D-0001); it moved
 * here when a second protocol needed it.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void vanillawheels$shiftIsDown(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof Vehicle v && v.verticalControls()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "updatePlayerPose", at = @At("TAIL"))
    private void vanillawheels$seatedNotCrouching(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.getVehicle() instanceof Vehicle v && v.verticalControls() && self.getPose() == Pose.CROUCHING) {
            self.setPose(Pose.STANDING);
        }
    }
}
