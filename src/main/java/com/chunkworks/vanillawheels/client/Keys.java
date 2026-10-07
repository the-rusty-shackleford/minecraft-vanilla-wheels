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
 * The riders' keys beyond the movement keys the game already has. The driver's: the horn on Left
 * Control and the headlights on H, live only while the player drives one of our vehicles, so
 * Control does not fight sprint and H fights nothing anywhere else. Aboard a body that moves in
 * three dimensions ({@link Vehicle#verticalControls}, D-0031): up on Space, down on Left Shift and
 * get out on R, Immersive Aircraft's keys, which the friends fly with; live in any seat aboard one,
 * so Space and Shift keep their jobs everywhere else. A protocol makes its own keys with
 * {@link RidingKey}.
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

    /** Active while the local player rides, in any seat, a vehicle of ours that moves in three dimensions, with no screen open. */
    public static final IKeyConflictContext ABOARD = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.screen == null && mc.player.getVehicle() instanceof Vehicle v && v.verticalControls();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return this == other;
        }
    };

    public static final String CATEGORY = "key.categories.vanillawheels";
    public static final KeyMapping HORN = new RidingKey("key.vanillawheels.horn", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);
    public static final KeyMapping LIGHTS = new RidingKey("key.vanillawheels.lights", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping UP = new RidingKey("key.vanillawheels.up", ABOARD, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);
    public static final KeyMapping DOWN = new RidingKey("key.vanillawheels.down", ABOARD, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, CATEGORY);
    public static final KeyMapping GET_OUT = new RidingKey("key.vanillawheels.get_out", ABOARD, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    /**
     * effects: lets go of the driver's keys. A key's release is passed only to keys whose context
     * is live, so a Control let go after getting out would hold the horn on into the next drive.
     */
    public static void releaseAll() {
        HORN.setDown(false);
        LIGHTS.setDown(false);
    }

    /**
     * effects: lets go of the keys aboard a body that moves in three dimensions, for the same
     * reason: a Shift held to the ground and let go after getting out would stay down and take the
     * next one boarded down with it.
     */
    public static void releaseAboard() {
        UP.setDown(false);
        DOWN.setDown(false);
        GET_OUT.setDown(false);
    }

    /**
     * A key that acts once a press (D-0032). The game clicks a key on each of the keyboard's repeats
     * as well as on its press, so a key held past the repeat delay (two thirds of a second on X)
     * acted again and again: the lights cycled through every mode while H was held, and a held hook
     * key caught and let go of its load. A press is a click while the key was not already down at
     * the last look: the repeats come while it is.
     */
    public static final class Press {
        private final KeyMapping key;
        private boolean held;

        public Press(KeyMapping key) {
            this.key = key;
        }

        /** effects: drains the key's clicks and returns whether one was a press; call it every client tick, aboard or not */
        public boolean consume() {
            boolean clicked = false;
            while (key.consumeClick()) {
                clicked = true;
            }
            boolean press = clicked && !held;
            held = key.isDown();
            return press;
        }
    }

    public static final Press LIGHTS_PRESS = new Press(LIGHTS);
    public static final Press GET_OUT_PRESS = new Press(GET_OUT);

    /** effects: returns which way the rider asks to go: 1 up, -1 down, 0 neither; up wins when both are held */
    public static int lift() {
        return UP.isDown() ? 1 : DOWN.isDown() ? -1 : 0;
    }

    /**
     * A rider's key, live only in its own context. NeoForge judges a key bound with no modifier as
     * up while Shift, Control or Alt is held, in every context but the game's own; the horn is Left
     * Control, so holding it switched the horn off (it never sounded from 1.0.0 to 1.11.0), H under
     * a held Control did nothing, and Rotorcraft's descend on Left Shift switched itself off. A
     * rider's key judges its modifier as the game's own keys do: with none, it is down whatever else
     * is held.
     */
    public static class RidingKey extends KeyMapping {
        public RidingKey(String name, IKeyConflictContext context, InputConstants.Type type, int key, String category) {
            super(name, context, type, key, category);
        }

        @Override
        public boolean isConflictContextAndModifierActive() {
            return getKeyConflictContext().isActive() && getKeyModifier().isActive(KeyConflictContext.IN_GAME);
        }

        @Override
        public boolean isActiveAndMatches(InputConstants.Key keyCode) {
            return keyCode != InputConstants.UNKNOWN && keyCode.equals(getKey()) && isConflictContextAndModifierActive();
        }
    }

    /** effects: returns whether the drift key -- the game's jump key -- is down */
    public static boolean driftDown() {
        return Minecraft.getInstance().options.keyJump.isDown();
    }

    static {
        // Never universal: outside a vehicle these keys are nobody's.
        assert HORN.getKeyConflictContext() != KeyConflictContext.UNIVERSAL;
    }
}
