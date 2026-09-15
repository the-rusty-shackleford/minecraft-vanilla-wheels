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

import com.chunkworks.vanillawheels.domain.Tank;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The gas can: a tank's worth of fuel in the hand. Hold use looking at a
 * vehicle with a tank and the can pours into it, {@link #POUR_PER_TICK}
 * ticks of fuel a tick, until the can is empty, the tank is full, or the
 * button is let go; the empty can stays in the hand as {@link
 * ModContent#EMPTY_GAS_CAN}, to be filled again with coal. A creative
 * hand pours without emptying. The fuel is the {@link ModContent#FUEL}
 * component, in furnace burn ticks, {@link #CAPACITY} when new; the bar
 * under the icon shows what is left.
 */
public final class GasCanItem extends Item {
    /** A full can, in ticks of throttle: one tank of the box car or the Trailblazer, twenty minutes. */
    public static final int CAPACITY = 24000;
    /** Fuel poured a tick: a full tank in six seconds. */
    public static final int POUR_PER_TICK = 200;
    /** How far the can reaches, blocks from the eye. */
    private static final double REACH = 4.5;

    public GasCanItem(Properties properties) {
        super(properties);
    }

    /** effects: returns the fuel in {@code can}, ticks */
    public static int fuel(ItemStack can) {
        return Math.max(0, can.getOrDefault(ModContent.FUEL.get(), 0));
    }

    /**
     * effects: returns the vehicle with a tank {@code player} looks at within reach, or null.
     * The body's own box or any of its parts, whichever the eye's line meets first.
     */
    @Nullable
    public static Vehicle aimedAt(Player player) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0f).scale(REACH));
        AABB sweep = player.getBoundingBox().expandTowards(player.getViewVector(1.0f).scale(REACH)).inflate(1.0);
        // The player's own pick: the nearest box along the eye's line, within reach squared.
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, to, sweep,
                e -> (e instanceof Vehicle || e instanceof Vehicle.Part) && !e.isSpectator(), REACH * REACH);
        if (hit == null) {
            return null;
        }
        Entity e = hit.getEntity();
        Vehicle v = e instanceof Vehicle.Part part ? part.getParent() : (Vehicle) e;
        return v.profile() != null && v.profile().fuel().isPresent() ? v : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack can = player.getItemInHand(hand);
        Vehicle v = aimedAt(player);
        if (v == null || fuel(can) <= 0) {
            return InteractionResultHolder.pass(can);
        }
        if (!v.tank().accepts(1)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("vanillawheels.tank_full"), true);
            }
            return InteractionResultHolder.fail(can);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(can);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack can, int remaining) {
        if (level.isClientSide() || !(user instanceof Player player)) {
            return;
        }
        Vehicle v = aimedAt(player);
        if (v == null) {
            player.stopUsingItem();
            return;
        }
        Tank tank = v.tank();
        int room = tank.capacity() - tank.ticks();
        int pour = Math.min(POUR_PER_TICK, Math.min(room, fuel(can)));
        if (pour <= 0) {
            player.displayClientMessage(Component.translatable(room <= 0 ? "vanillawheels.tank_full" : "vanillawheels.can_empty"), true);
            player.stopUsingItem();
            return;
        }
        v.setFuel(tank.ticks() + pour);
        player.displayClientMessage(Component.translatable("vanillawheels.fuel", Math.round(v.tank().fraction() * 100)), true);
        if (remaining % 8 == 0) {
            level.playSound(null, v.getX(), v.getY(), v.getZ(), ModContent.FUEL_POUR.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
        }
        if (player.hasInfiniteMaterials()) {
            return;   // creative pours from a can that never empties
        }
        int left = fuel(can) - pour;
        if (left > 0) {
            can.set(ModContent.FUEL.get(), left);
        } else {
            player.setItemInHand(player.getUsedItemHand(), new ItemStack(ModContent.EMPTY_GAS_CAN.get()));
            player.stopUsingItem();
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return fuel(stack) < CAPACITY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * fuel(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xD03030;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("vanillawheels.gas_can.fuel", Math.round(100.0 * fuel(stack) / CAPACITY)).withStyle(ChatFormatting.GRAY));
    }
}
