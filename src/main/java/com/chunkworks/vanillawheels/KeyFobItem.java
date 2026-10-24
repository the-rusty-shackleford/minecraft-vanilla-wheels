/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A key and fob, one per vehicle (D-0026). A blank key used on a motor vehicle pairs it: the key,
 * named for the vehicle and banded in its paint, goes on the player's keyring (D-0041), and the
 * blank is used up. A blank used in the air re-issues a key of the owner's that is gone, onto the
 * ring. A paired key from before the ring, still loose, is gathered onto the ring when used. A
 * paired key is never lost (KeyFobs).
 * AF/RI: item components are claims only; RecoveryData verifies server ownership.
 */
public final class KeyFobItem extends Item {
    /** requires: item properties; effects: constructs the fob; throws: none. */
    public KeyFobItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack key = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            RecoveryData data = RecoveryData.get(server.server);
            // A paired key still loose, from before the ring: onto the ring, and the ring opens.
            if (data.isCurrent(key.get(ModContent.KEY_TOKEN.get()))) {
                KeyringItem.open(server);
                return InteractionResultHolder.consume(key);
            }
            // A blank, or a key whose pairing has moved on: re-issues a key that is gone, onto the ring.
            return data.replacement(server, key) ? InteractionResultHolder.consume(key) : InteractionResultHolder.fail(key);
        }
        return InteractionResultHolder.consume(key);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean paired = stack.has(ModContent.KEY_TOKEN.get());
        tooltip.add(Component.translatable(paired ? "vanillawheels.key.hold" : "vanillawheels.key.bind_help").withStyle(ChatFormatting.GRAY));
        if (paired) {
            tooltip.add(Component.translatable("vanillawheels.key.keeps").withStyle(ChatFormatting.GRAY));
        }
    }
}
