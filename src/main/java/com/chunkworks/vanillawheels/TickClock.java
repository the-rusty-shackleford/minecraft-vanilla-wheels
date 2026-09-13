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
package com.chunkworks.vanillawheels;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Which entity pass this is, per side. Towing has to know whether a
 * trailer's own tick has run yet this pass (see {@link Vehicle#tick}), and
 * the level's game time cannot say: on the client it advances only after
 * the entity pass and is overwritten by the server's time packet every
 * twenty ticks, so as a pass id it repeated or skipped -- a trailer that
 * froze a tick and jumped two, once a second. A counter bumped before each
 * pass is exact. nfx's {@code TowClock}, from his patch.
 */
public final class TickClock {
    private TickClock() {}

    private static long server;
    private static long client;

    /** effects: returns the pass {@code level}'s side is in */
    public static long now(Level level) {
        return level.isClientSide() ? client : server;
    }

    /** effects: on the server, one bump per server level per pass; the first level's bump is the pass */
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (!event.getLevel().isClientSide() && event.getLevel().dimension() == Level.OVERWORLD) {
            server++;
        }
    }

    /** effects: the client's pass, bumped from the client tick's start */
    public static void clientPass() {
        client++;
    }
}
