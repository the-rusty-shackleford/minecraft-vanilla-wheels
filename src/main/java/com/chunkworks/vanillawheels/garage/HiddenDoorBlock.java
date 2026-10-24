/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.garage;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.domain.Shutter;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Hidden Door panel (D-0036): built and powered as the rolling garage door is, but its curtain
 * slides down whole into the sill, a block thick, and each panel is drawn as the solid block it was
 * given (right-click it with one), so a shut door is a face of stone, rock or anything. Drawn by its
 * renderer every frame and never swapped for other blocks, it moves without a seam under shaders.
 */
public final class HiddenDoorBlock extends GarageDoorBlock {
    public static final MapCodec<HiddenDoorBlock> CODEC = simpleCodec(HiddenDoorBlock::new);

    /** requires: block properties; effects: creates the panel type; throws: registry errors. */
    public HiddenDoorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public Shutter.Kind kind() {
        return Shutter.Kind.SINKING;
    }

    @Override
    public BlockEntityType<GarageDoorBlockEntity> panelType() {
        return ModContent.HIDDEN_DOOR_BE.get();
    }

    /**
     * effects: with a block a panel can wear in hand (solid, a whole block, no block entity), draws
     * this panel as that block, using none of it; with anything else, as any block would. Sneaking
     * places the held block against the door instead, as it does everywhere.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem item) || item.getBlock() == this) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockState look = item.getBlock().defaultBlockState();
        if (!wearable(level, pos, look)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GarageDoorBlockEntity panel) {
            panel.setLook(look);
            level.playSound(null, pos, look.getSoundType(level, pos, player).getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.9f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** effects: returns whether a panel may be drawn as {@code look}: a solid whole block with no block entity */
    public static boolean wearable(Level level, BlockPos pos, BlockState look) {
        return !(look.getBlock() instanceof EntityBlock) && look.isSolidRender(level, pos) && !look.isAir();
    }
}
