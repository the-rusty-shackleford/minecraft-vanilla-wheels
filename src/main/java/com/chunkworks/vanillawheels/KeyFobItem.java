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
 * A key and fob, one per vehicle (D-0026). A blank key used on a motor vehicle pairs it and
 * takes its mark (its name, and a band in its paint); held in the air, a paired key quotes
 * and recalls its vehicle; a blank used in the air replaces a key of the owner's that is gone.
 * A paired key is never lost (KeyFobs).
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
        boolean paired = stack.has(ModContent.KEY_TOKEN.get());
        tooltip.add(Component.translatable(paired ? "vanillawheels.key.hold" : "vanillawheels.key.bind_help").withStyle(ChatFormatting.GRAY));
        if (paired) {
            tooltip.add(Component.translatable("vanillawheels.key.keeps").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("vanillawheels.key.cost_help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
