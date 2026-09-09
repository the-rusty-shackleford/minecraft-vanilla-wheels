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
 * faces, with the paint, fuel and disc the item carries; the item is
 * spent (unless the player is in creative).
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
        ResourceLocation id = VanillaWheels.vehicleOf(stack).orElse(null);
        if (id == null || context.getPlayer() == null) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (VanillaWheels.profile(level.registryAccess(), id).isEmpty()) {
            context.getPlayer().displayClientMessage(Component.translatable("vanillawheels.no_such_vehicle", id.toString()), true);
            return InteractionResult.FAIL;
        }
        BlockPos above = context.getClickedPos().relative(context.getClickedFace());
        Vec3 at = new Vec3(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
        Vehicle vehicle = Vehicle.create((ServerLevel) level, id, at, context.getPlayer().getYRot());
        if (vehicle == null) {
            return InteractionResult.FAIL;
        }
        vehicle.loadFromItem(stack);
        AABB box = vehicle.getBoundingBox();
        if (!level.noCollision(vehicle, box.deflate(0.05))) {
            context.getPlayer().displayClientMessage(Component.translatable("vanillawheels.no_room"), true);
            return InteractionResult.FAIL;
        }
        level.addFreshEntity(vehicle);
        level.playSound(null, at.x, at.y, at.z, ModContent.WRENCH_CLANK.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.9f);
        if (!context.getPlayer().hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
