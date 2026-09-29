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

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * An item stack held as a data component: a packed vehicle's radio disc, its
 * own crowbar. The game requires a component to be an immutable value with
 * equals and hashCode, which an ItemStack is not (the dev check refused a
 * packed vehicle carrying either). Saved and synced exactly as the bare stack
 * was, so packed vehicles from before read the same.
 * AF: the stack {@code stack}. RI: {@code stack} is never handed out; copies are.
 */
public final class HeldStack {
    public static final Codec<HeldStack> CODEC = ItemStack.OPTIONAL_CODEC.xmap(HeldStack::new, h -> h.stack);
    public static final StreamCodec<RegistryFriendlyByteBuf, HeldStack> STREAM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC.map(HeldStack::new, h -> h.stack);

    private final ItemStack stack;

    /** effects: holds a copy of {@code stack} */
    public HeldStack(ItemStack stack) {
        this.stack = stack.copy();
    }

    /** effects: returns a copy of the stack held */
    public ItemStack copy() {
        return stack.copy();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HeldStack h && ItemStack.matches(stack, h.stack);
    }

    @Override
    public int hashCode() {
        return 31 * ItemStack.hashItemAndComponents(stack) + stack.getCount();
    }
}
