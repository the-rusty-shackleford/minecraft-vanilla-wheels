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

import com.chunkworks.vanillawheels.keyring.KeyringMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The keyring (D-0041, {@link Keyring}): every key a player holds, on one ring that is never
 * lost -- it cannot be dropped, comes back from any container that is not on its owner, and
 * comes back after a death. A bag the owner carries may hold it (1.17.2: Carried sees a bag's
 * cells, a dropped bag gives it up, and the menu guard looks inside); it fits in no bundle or
 * shulker box only as far as the game's own rule sends it home from there. Use opens it: each
 * vehicle with where it is and what a recall would cost, and a Recall button.
 */
public final class KeyringItem extends Item {
    /** How many vehicles the tooltip names before it counts the rest. */
    private static final int NAMED = 6;

    public KeyringItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            open(sp);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    /** effects: gathers any loose key onto {@code player}'s ring and opens the ring's screen */
    public static void open(ServerPlayer player) {
        Keyring.gather(player);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new KeyringMenu(id, inventory), Component.translatable("item.vanillawheels.keyring")));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<ItemStack> keys = Keyring.keys(stack);
        if (keys.isEmpty()) {
            tooltip.add(Component.translatable("vanillawheels.ring.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(keys.size() == 1 ? "vanillawheels.ring.one" : "vanillawheels.ring.vehicles", keys.size()).withStyle(ChatFormatting.GRAY));
            for (int i = 0; i < Math.min(NAMED, keys.size()); i++) {
                tooltip.add(Component.literal("  ").append(keys.get(i).getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (keys.size() > NAMED) {
                tooltip.add(Component.translatable("vanillawheels.ring.and_more", keys.size() - NAMED).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        tooltip.add(Component.translatable("vanillawheels.ring.open").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("vanillawheels.ring.keeps").withStyle(ChatFormatting.DARK_GRAY));
    }
}
