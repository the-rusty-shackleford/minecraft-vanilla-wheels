/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
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
 * A paired key and fob. Use on a motor vehicle to pair; hold in air to quote and
 * recall. A blank replacement used in air invalidates the owner's old fob.
 * AF/RI: item components are claims only; RecoveryData verifies server ownership.
 */
public final class KeyFobItem extends Item {
    /** requires: item properties; effects: constructs the fob; throws: none. */
    public KeyFobItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack key = player.getItemInHand(hand);
        if (!key.has(ModContent.KEY_TOKEN.get())) {
            if (player instanceof ServerPlayer server && !RecoveryData.get(server.server).replacement(server, key))
                return InteractionResultHolder.fail(key);
            return InteractionResultHolder.consume(key);
        }
        if (player instanceof ServerPlayer server) {
            RecoveryData data = RecoveryData.get(server.server);
            if (!data.begin(server, key)) return InteractionResultHolder.fail(key);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(key);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer player) RecoveryData.get(player.server).cancel(player.getUUID());
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(stack.has(ModContent.KEY_TOKEN.get()) ? "vanillawheels.key.hold" : "vanillawheels.key.bind_help")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("vanillawheels.key.cost_help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
