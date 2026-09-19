/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Packed inventories use up to eight immutable pages of 54 slots. This covers the
 * profile's eight double chests without vanilla's single-component 256-slot limit.
 * AF/RI: list order and page offsets preserve all slots; every read/write copies stacks.
 */
public final class VehicleCargo {
    private VehicleCargo() {}
    public static final Codec<List<ItemContainerContents>> CODEC = ItemContainerContents.CODEC.validate(page -> page.getSlots() <= 54
            ? com.mojang.serialization.DataResult.success(page) : com.mojang.serialization.DataResult.error(() -> "Vehicle cargo page exceeds 54 slots")).listOf(0, 8)
            .xmap(List::copyOf, List::copyOf);
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemContainerContents>> STREAM_CODEC =
            ItemContainerContents.STREAM_CODEC.apply(ByteBufCodecs.list(8));

    /** requires: at most 432 slots; effects: returns immutable defensive pages; throws: IllegalArgumentException for excessive cargo. */
    public static List<ItemContainerContents> capture(List<ItemStack> items) {
        if (items.size() > 432) throw new IllegalArgumentException("Too many vehicle slots");
        List<ItemContainerContents> result = new ArrayList<>();
        for (int first = 0; first < items.size(); first += 54)
            result.add(ItemContainerContents.fromItems(items.subList(first, Math.min(first + 54, items.size()))));
        return List.copyOf(result);
    }

    /** requires: valid pages; effects: returns a defensive flat inventory, including empty slots; throws: none. */
    public static NonNullList<ItemStack> unpack(List<ItemContainerContents> pages) {
        if (pages.size() > 8 || pages.stream().anyMatch(page -> page.getSlots() > 54)) throw new IllegalArgumentException("Invalid cargo pages");
        NonNullList<ItemStack> result = NonNullList.withSize(pages.size() * 54, ItemStack.EMPTY);
        for (int page = 0; page < pages.size(); page++) {
            NonNullList<ItemStack> contents = NonNullList.withSize(54, ItemStack.EMPTY);
            pages.get(page).copyInto(contents);
            for (int slot = 0; slot < 54; slot++) result.set(page * 54 + slot, contents.get(slot));
        }
        return result;
    }

    /** requires: nonnegative capacity; effects: reports whether every occupied slot fits; throws: none. */
    public static boolean fits(List<ItemContainerContents> pages, int capacity) {
        var items = unpack(pages);
        for (int i = capacity; i < items.size(); i++) if (!items.get(i).isEmpty()) return false;
        return true;
    }
}
