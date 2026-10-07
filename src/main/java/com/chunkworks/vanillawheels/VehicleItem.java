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

import com.chunkworks.vanillawheels.api.VanillaWheels;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A vehicle in the hand, named for the vehicle its component says it is.
 * Used on the ground it becomes the vehicle, facing the way the player
 * faces, with its cargo, condition, paint, fuel and disc. Packed property
 * transfers once even in creative; fresh catalog templates remain reusable.
 */
public final class VehicleItem extends Item {
    public VehicleItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return VanillaWheels.vehicleOf(stack)
                .<Component>map(id -> Component.translatable("vehicle." + id.getNamespace() + "." + id.getPath()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        if (VanillaWheels.vehicleOf(stack).isEmpty() || context.getPlayer() == null) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos above = context.getClickedPos().relative(context.getClickedFace());
        Vec3 at = new Vec3(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
        return place((ServerLevel) level, context.getPlayer(), stack, at, context.getPlayer().getYRot()) != null
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /**
     * requires: server thread; {@code stack} is a vehicle item in {@code player}'s hand
     * effects: sets the vehicle {@code stack} holds down at {@code at} facing {@code yaw} degrees,
     *     with its cargo, condition, paint, fuel and disc, made by the kind that claims its profile;
     *     a packed one is used up even in creative, a fresh one in survival only; returns it, or null
     *     with the reason above the hotbar (no such vehicle, a stale packed vehicle, no room). What a
     *     click on a block face does, and where a protocol sets one down elsewhere (D-0031: a
     *     submarine on the water's surface).
     * throws: none
     */
    @org.jetbrains.annotations.Nullable
    public static Vehicle place(ServerLevel level, net.minecraft.world.entity.player.Player player, ItemStack stack, Vec3 at, float yaw) {
        ResourceLocation id = VanillaWheels.vehicleOf(stack).orElse(null);
        if (id == null) {
            return null;
        }
        if (VanillaWheels.profile(level.registryAccess(), id).isEmpty()) {
            player.displayClientMessage(Component.translatable("vanillawheels.no_such_vehicle", id.toString()), true);
            return null;
        }
        Vehicle vehicle = Vehicle.create(level, id, at, yaw);
        if (vehicle == null) {
            return null;
        }
        RecoveryData recovery = RecoveryData.get(level.getServer());
        if (!recovery.canPlace(stack) || !vehicle.loadFromItem(stack)) {
            player.displayClientMessage(Component.translatable("vanillawheels.key.stale_vehicle"), true);
            return null;
        }
        AABB box = vehicle.getBoundingBox();
        if (!level.noCollision(vehicle, box.deflate(0.05))) {
            player.displayClientMessage(Component.translatable("vanillawheels.no_room"), true);
            return null;
        }
        vehicle.placingFromItem(true);
        if (!level.addFreshEntity(vehicle)) return null;
        vehicle.placingFromItem(false);
        recovery.deployed(vehicle, stack);
        level.playSound(null, at.x, at.y, at.z, ModContent.CLANK.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.9f);
        if (!player.hasInfiniteMaterials() || stack.has(ModContent.PACKED_TOKEN.get())) {
            stack.shrink(1);
        }
        return vehicle;
    }

    @Override public boolean isBarVisible(ItemStack stack) { return stack.getOrDefault(ModContent.CONDITION.get(), 10000) < 10000; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13f * stack.getOrDefault(ModContent.CONDITION.get(), 10000) / 10000f); }
    @Override public int getBarColor(ItemStack stack) { return stack.getOrDefault(ModContent.CONDITION.get(), 10000) == 0 ? 0xAA3333 : 0x55AA55; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        int condition = stack.getOrDefault(ModContent.CONDITION.get(), 10000);
        tooltip.add(Component.translatable("vanillawheels.condition", String.format(java.util.Locale.ROOT, "%.1f", condition / 100.0)));
        if (condition == 0) tooltip.add(Component.translatable("vanillawheels.broken").withStyle(net.minecraft.ChatFormatting.RED));
        long occupied = stack.getOrDefault(ModContent.CARGO.get(), java.util.List.<net.minecraft.world.item.component.ItemContainerContents>of()).stream().flatMap(net.minecraft.world.item.component.ItemContainerContents::nonEmptyStream).count();
        if (occupied > 0) tooltip.add(Component.translatable("vanillawheels.cargo", occupied).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
