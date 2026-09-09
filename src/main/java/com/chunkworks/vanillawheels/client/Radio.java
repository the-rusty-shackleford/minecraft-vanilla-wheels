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
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Every vehicle's radio on this client: once a tick, for each vehicle in
 * view, the disc in it is compared with the disc playing; a new disc
 * starts its song (and the game's "now playing" toast), a removed one
 * stops it, a finished one goes quiet until the disc is changed. Cost: one
 * pass over the rendered entities per tick.
 */
public final class Radio {
    private Radio() {}

    /** A song under way: the disc, its sound, and the tick it ends. */
    private record Playing(ItemStack disc, RadioSound sound, long endsAt) {}

    private static final Int2ObjectMap<Playing> PLAYING = new Int2ObjectOpenHashMap<>();
    private static long ticks;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        ticks++;
        if (level == null) {
            PLAYING.clear();
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Vehicle vehicle)) {
                continue;
            }
            ItemStack disc = vehicle.disc();
            Playing playing = PLAYING.get(vehicle.getId());
            boolean same = playing != null && ItemStack.isSameItemSameComponents(playing.disc(), disc);
            if (same) {
                if (ticks >= playing.endsAt() && playing.sound() != null) {
                    playing.sound().end();
                    PLAYING.put(vehicle.getId(), new Playing(disc, null, Long.MAX_VALUE));
                }
                continue;
            }
            if (playing != null && playing.sound() != null) {
                playing.sound().end();
            }
            if (disc.isEmpty()) {
                PLAYING.remove(vehicle.getId());
                continue;
            }
            JukeboxSong song = vehicle.song().orElse(null);
            if (song == null) {
                PLAYING.put(vehicle.getId(), new Playing(disc, null, Long.MAX_VALUE));
                continue;
            }
            RadioSound sound = new RadioSound(vehicle, song.soundEvent().value(), disc);
            mc.getSoundManager().play(sound);
            mc.gui.setNowPlaying(song.description());
            PLAYING.put(vehicle.getId(), new Playing(disc, sound, ticks + song.lengthInTicks() + 20));
        }
        Iterator<Int2ObjectMap.Entry<Playing>> it = PLAYING.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            Int2ObjectMap.Entry<Playing> e = it.next();
            Entity entity = level.getEntity(e.getIntKey());
            if (!(entity instanceof Vehicle)) {
                if (e.getValue().sound() != null) {
                    e.getValue().sound().end();
                }
                it.remove();
            }
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PLAYING.clear();
    }
}
