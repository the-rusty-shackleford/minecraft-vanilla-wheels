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
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The driver's keys beyond the movement keys the game already has: the
 * horn on Left Control and the headlights on H. Both live in a conflict
 * context that is active only while the player drives one of our
 * vehicles, so Control does not fight sprint and H fights nothing anywhere
 * else.
 */
public final class Keys {
    private Keys() {}

    /** Active while the local player drives a vehicle of ours. */
    public static final IKeyConflictContext DRIVING = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.screen == null && mc.player.getVehicle() instanceof Vehicle v && v.getControllingPassenger() == mc.player;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return this == other;
        }
    };

    public static final String CATEGORY = "key.categories.vanillawheels";
    public static final KeyMapping HORN = new KeyMapping("key.vanillawheels.horn", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);
    public static final KeyMapping LIGHTS = new KeyMapping("key.vanillawheels.lights", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    /** effects: returns whether the drift key -- the game's jump key -- is down */
    public static boolean driftDown() {
        return Minecraft.getInstance().options.keyJump.isDown();
    }

    static {
        // Never universal: outside a vehicle these keys are nobody's.
        assert HORN.getKeyConflictContext() != KeyConflictContext.UNIVERSAL;
    }
}
