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

import com.chunkworks.carried.api.Carried;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The keyring (D-0041): every key a player holds lives on one ring. A key is put on the ring as it
 * is paired or re-issued, a loose key on a player is gathered onto it, and a key or ring that comes
 * home ({@link KeyFobs}) lands on it. The ring is made as the first key needs it, with the player
 * as its owner, and is never crafted. A player has one ring: a second one coming to them is
 * absorbed into theirs.
 *
 * <p>AF: {@code KEYRING_KEYS} is the list of keys on the ring, each the key fob stack it would be
 * loose, in the order they were put on; {@code KEY_OWNER} on the ring is whose it is. RI: at most
 * one key per token on a ring. Server thread for every change.
 */
public final class Keyring {
    private Keyring() {}

    /** effects: whether {@code stack} is a keyring */
    public static boolean isRing(ItemStack stack) {
        return stack.is(ModContent.KEYRING.get());
    }

    /** effects: the keys on {@code ring}, in the order they were put on; none for anything but a ring */
    public static List<ItemStack> keys(ItemStack ring) {
        return isRing(ring) ? ring.getOrDefault(ModContent.KEYRING_KEYS.get(), List.of()) : List.of();
    }

    /** effects: the key on {@code ring} with {@code token}, or null */
    @Nullable
    public static ItemStack keyOn(ItemStack ring, @Nullable UUID token) {
        if (token == null) {
            return null;
        }
        for (ItemStack key : keys(ring)) {
            if (token.equals(key.get(ModContent.KEY_TOKEN.get()))) {
                return key;
            }
        }
        return null;
    }

    /** effects: whether {@code ring} carries the key with {@code token} */
    public static boolean holds(ItemStack ring, @Nullable UUID token) {
        return keyOn(ring, token) != null;
    }

    /** effects: whose the ring is: its owner, else the owner of its first key, else null */
    @Nullable
    public static UUID ownerOf(ItemStack ring) {
        UUID owner = ring.get(ModContent.KEY_OWNER.get());
        if (owner != null) {
            return owner;
        }
        for (ItemStack key : keys(ring)) {
            if (key.has(ModContent.KEY_OWNER.get())) {
                return key.get(ModContent.KEY_OWNER.get());
            }
        }
        return null;
    }

    /** effects: where the player's own ring is -- the first ring on them that is theirs or nobody's yet -- or null for none */
    @Nullable
    public static Carried.Place ringOf(Player player) {
        return Carried.find(player, s -> isRing(s) && (ownerOf(s) == null || player.getUUID().equals(ownerOf(s))), false);
    }

    /**
     * effects: puts {@code key} on the player's ring, in place of a key with the same token if one
     * is there, else last; makes the ring if they have none, into their inventory or a bag they
     * carry. Returns whether it is on them now: false when they have no ring and no room for one,
     * for the caller to keep the key waiting.
     */
    public static boolean put(ServerPlayer player, ItemStack key) {
        ItemStack one = key.copyWithCount(1);
        if (ringOf(player) != null) {
            change(player, ring -> withKey(ring, one));
            return true;
        }
        ItemStack ring = new ItemStack(ModContent.KEYRING.get());
        ring.set(ModContent.KEY_OWNER.get(), player.getUUID());
        ring.set(ModContent.KEYRING_KEYS.get(), List.of(one));
        Carried.give(player, ring);
        return ring.isEmpty();
    }

    /**
     * effects: every key on {@code ring} put on the player's own ring ({@link #put}); the given ring
     * is spent. Returns whether all of them are on the player now.
     */
    public static boolean absorb(ServerPlayer player, ItemStack ring) {
        for (ItemStack key : keys(ring)) {
            if (!put(player, key)) {
                return false;
            }
        }
        return true;
    }

    /** effects: moves every live loose key on {@code player} -- inventory or bags -- onto their ring */
    public static void gather(ServerPlayer player) {
        var server = player.server;
        java.util.function.Predicate<ItemStack> loose = s -> s.is(ModContent.KEY_FOB.get()) && KeyFobs.liveToken(server, s) != null;
        int n = Carried.count(player, loose);
        if (n == 0) {
            return;
        }
        List<ItemStack> keys = new ArrayList<>();
        Carried.take(player, loose, n, taken -> keys.add(taken.copy()));
        for (ItemStack key : keys) {
            if (!put(player, key)) {
                KeyFobs.sendHome(server, key);   // no ring and no room for one: it waits
            }
        }
    }

    /** effects: takes the key with {@code token} off the player's ring, if it is there: a key retired by a fresh one is no key */
    public static void drop(ServerPlayer player, @Nullable UUID token) {
        if (token == null || ringOf(player) == null) {
            return;
        }
        change(player, ring -> {
            List<ItemStack> keys = new ArrayList<>(keys(ring));
            if (!keys.removeIf(k -> token.equals(k.get(ModContent.KEY_TOKEN.get())))) {
                return ring;
            }
            ItemStack result = ring.copy();
            result.set(ModContent.KEYRING_KEYS.get(), List.copyOf(keys));
            return result;
        });
    }

    /** effects: rewrites the key with {@code token} on the player's ring as {@code change} leaves it; nothing without such a key */
    public static void edit(ServerPlayer player, UUID token, Consumer<ItemStack> change) {
        if (ringOf(player) == null) {
            return;
        }
        change(player, ring -> {
            ItemStack key = keyOn(ring, token);
            if (key == null) {
                return ring;
            }
            ItemStack changed = key.copy();
            change.accept(changed);
            return withKey(ring, changed);
        });
    }

    /** effects: returns {@code ring} with {@code key} on it, replacing a key with the same token, else last */
    private static ItemStack withKey(ItemStack ring, ItemStack key) {
        UUID token = key.get(ModContent.KEY_TOKEN.get());
        List<ItemStack> keys = new ArrayList<>(keys(ring));
        int at = -1;
        for (int i = 0; i < keys.size() && token != null; i++) {
            if (token.equals(keys.get(i).get(ModContent.KEY_TOKEN.get()))) {
                at = i;
            }
        }
        if (at >= 0) {
            keys.set(at, key);
        } else {
            keys.add(key);
        }
        ItemStack result = ring.copy();
        result.set(ModContent.KEYRING_KEYS.get(), List.copyOf(keys));
        return result;
    }

    /**
     * effects: replaces the player's ring with {@code change} of it: in place in their inventory, or
     * taken out of a bag, changed and given back (a bag's cells are read through Carried)
     */
    private static void change(ServerPlayer player, UnaryOperator<ItemStack> change) {
        Carried.Place place = ringOf(player);
        if (place == null) {
            return;
        }
        if (Carried.INVENTORY.equals(place.store())) {
            player.getInventory().setItem(place.cell(), change.apply(player.getInventory().getItem(place.cell())));
            player.getInventory().setChanged();
            return;
        }
        ItemStack ring = Carried.takeFrom(player, place.store(), place.cell(), 1);
        if (ring.isEmpty()) {
            return;
        }
        ItemStack changed = change.apply(ring);
        Carried.give(player, changed);
        if (!changed.isEmpty()) {
            KeyFobs.sendHome(player.server, changed);   // the bag would not take it back and nothing else fits: it waits
        }
    }
}
