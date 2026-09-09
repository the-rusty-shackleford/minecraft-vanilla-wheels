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
package com.chunkworks.vanillawheels.gametest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * The test mod: it ships the box car (a profile, two meshes and a texture)
 * as a vehicle mod would, so the protocol is proven against a real vehicle
 * that exists only for the tests; the gametests and the booth ride in it.
 * Never shipped.
 */
@Mod(GameTestMod.MOD_ID)
public final class GameTestMod {
    public static final String MOD_ID = "vanillawheels_gametest";

    public GameTestMod(IEventBus modBus) {}
}
