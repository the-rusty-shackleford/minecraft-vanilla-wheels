/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.gametest;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.garage.GarageDoorBlock;
import com.chunkworks.vanillawheels.garage.GarageDoorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.BooleanOp;
import java.util.ArrayList;
import com.chunkworks.vanillawheels.VanillaWheelsMod;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Partitions: first-panel placement from north/south/east/west through the actual
 * server interaction path; floor/top-down construction; opposite player headings
 * while extending; both width axes; redstone opening/closing and collision for all
 * four facings; legacy states with/without root; saved facings; rotations/mirrors;
 * conflicting facings at a bridge. Players use real inventories and game modes. */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GarageDoorFacingGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        for (int x=0;x<24;x++) for (int z=0;z<24;z++) h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"door-facing"),false);
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,p,cookie);
        p.setGameMode(GameType.SURVIVAL);
        var position=h.absoluteVec(new Vec3(2.5,2,2.5));
        p.teleportTo(position.x,position.y,position.z);
        return p;
    }

    private static void look(ServerPlayer p,Direction direction) {
        float yaw=direction.toYRot();
        p.setYRot(yaw);p.setYHeadRot(yaw);p.setYBodyRot(yaw);
    }

    private static void place(GameTestHelper h,ServerPlayer p,BlockPos support,Direction face) {
        var absolute=h.absolutePos(support);
        var hit=new BlockHitResult(Vec3.atCenterOf(absolute).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,absolute,false);
        p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
        h.assertTrue(h.getBlockState(support.relative(face)).is(ModContent.GARAGE_DOOR.get()),"panel placed through real server interaction");
    }

    @GameTest(template="garage_arena",timeoutTicks=20)
    public void firstPanelHasFourDistinctPlayerChosenFacings(GameTestHelper h) {
        var p=player(h);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModContent.GARAGE_DOOR_ITEM.get(),4));
            var states=new HashSet<BlockState>();
            int i=0;
            for (var direction:Direction.Plane.HORIZONTAL) {
                look(p,direction);
                var pos=new BlockPos(4+i++*4,2,10);
                place(h,p,pos.below(),Direction.UP);
                var state=h.getBlockState(pos);
                h.assertTrue(GarageDoorBlock.facing(state)==direction.getOpposite(),"outside faces the placer from "+direction);
                states.add(state);
            }
            h.assertTrue(p.getMainHandItem().isEmpty(),"Survival spends one item per panel");
            h.assertTrue(states.size()==4,"four viewing directions produce four door facings; got "+states.size());
        } finally {
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

    @GameTest(template="garage_arena",templateNamespace="vanillawheels_chaining",timeoutTicks=65)
    public void allFacingsInheritTopDownAndRunTheSameRedstoneDoor(GameTestHelper h) {
        var p=player(h);
        var origins=new ArrayList<BlockPos>();
        var switches=new ArrayList<BlockPos>();
        var facings=new ArrayList<Direction>();
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModContent.GARAGE_DOOR_ITEM.get(),16));
            int i=0;
            for (var facing:Direction.Plane.HORIZONTAL) {
                var pos=new BlockPos(6+(i%2)*9,2,6+(i/2)*9);i++;
                var across=facing.getAxis()==Direction.Axis.Z?Direction.EAST:Direction.SOUTH;
                look(p,facing.getOpposite());
                // Start at the top on a temporary support, then fill the full door.
                h.setBlock(pos,Blocks.STONE);
                place(h,p,pos,Direction.UP);
                h.setBlock(pos,Blocks.AIR);
                look(p,facing);
                p.setShiftKeyDown(false);
                place(h,p,pos.above(),Direction.DOWN);
                // Ordinary edge clicks extend the door without crouching.
                p.setShiftKeyDown(false);
                place(h,p,pos,across);
                p.setShiftKeyDown(false);
                place(h,p,pos.relative(across),Direction.UP);
                p.setShiftKeyDown(false);
                for (var panel:new BlockPos[]{pos,pos.above(),pos.relative(across),pos.relative(across).above()})
                    h.assertTrue(GarageDoorBlock.facing(h.getBlockState(panel))==facing,"extension retains "+facing+" despite player turning");
                origins.add(pos);switches.add(pos.relative(across.getOpposite()));facings.add(facing);
            }
            h.assertTrue(p.getMainHandItem().isEmpty(),"top-down Survival construction consumes exactly sixteen panels");
        } finally {
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.runAtTickTime(5,()->{
            for (var pos:origins) {
                var door=(GarageDoorBlockEntity)h.getBlockEntity(pos);
                h.assertTrue(door.formed()&&door.width()==2&&door.height()==2,"full rectangle forms from top-down placement");
            }
            switches.forEach(pos->h.setBlock(pos,Blocks.REDSTONE_BLOCK));
        });
        h.runAtTickTime(30,()->{
            for (int i=0;i<origins.size();i++) {
                var door=(GarageDoorBlockEntity)h.getBlockEntity(origins.get(i));
                h.assertTrue(door.lift()==12,"powered "+facings.get(i)+" door opens");
                h.assertTrue(!Shapes.joinIsNotEmpty(door.shape(),Shapes.box(.2,0,.2,.8,.4,.8),BooleanOp.AND),"open doorway has central clearance");
            }
            switches.forEach(pos->h.setBlock(pos,Blocks.AIR));
        });
        h.runAtTickTime(55,()->{
            for (var pos:origins) {
                var door=(GarageDoorBlockEntity)h.getBlockEntity(pos);
                h.assertTrue(door.lift()==0,"unpowered door closes");
                h.assertTrue(Shapes.joinIsNotEmpty(door.shape(),Shapes.box(.2,0,.2,.8,.4,.8),BooleanOp.AND),"closed curtain collides");
            }
            h.succeed();
        });
    }

    @GameTest(template="garage_arena",timeoutTicks=20)
    public void savedLegacyDirectionsAndAllRotationsPreserveOutside(GameTestHelper h) {
        var blocks=h.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK);
        for (var axis:new Direction.Axis[]{Direction.Axis.X,Direction.Axis.Z}) for (boolean root:new boolean[]{false,true}) {
            var legacy=ModContent.GARAGE_DOOR.get().defaultBlockState().setValue(GarageDoorBlock.AXIS,axis).setValue(GarageDoorBlock.ROOT,root);
            var saved=NbtUtils.writeBlockState(legacy);
            saved.getCompound("Properties").remove("reversed");
            var restored=NbtUtils.readBlockState(blocks,saved);
            h.assertTrue(restored.equals(legacy),"legacy palette retains its axis and root");
            var original=axis==Direction.Axis.X?Direction.NORTH:Direction.EAST;
            h.assertTrue(GarageDoorBlock.facing(restored)==original,"legacy outside retains its rendered direction");
            for (boolean reversed:new boolean[]{false,true}) {
                var state=restored.setValue(GarageDoorBlock.REVERSED,reversed);
                var facing=GarageDoorBlock.facing(state);
                h.assertTrue(NbtUtils.readBlockState(blocks,NbtUtils.writeBlockState(state)).equals(state),"all new facings survive palette save/load");
                for (var rotation:Rotation.values())
                    h.assertTrue(GarageDoorBlock.facing(state.rotate(rotation))==rotation.rotate(facing),"rotation preserves outside");
                for (var mirror:Mirror.values())
                    h.assertTrue(GarageDoorBlock.facing(state.mirror(mirror))==mirror.mirror(facing),"mirror preserves outside");
            }
        }
        h.succeed();
    }

    @GameTest(template="garage_arena",timeoutTicks=20)
    public void bridgeDoesNotFlipOppositelyFacingExistingDoors(GameTestHelper h) {
        var p=player(h);
        var pos=new BlockPos(8,2,10);
        var north=ModContent.GARAGE_DOOR.get().defaultBlockState();
        var south=north.setValue(GarageDoorBlock.REVERSED,true);
        h.setBlock(pos.west(),north);h.setBlock(pos.east(),south);
        try {
            look(p,Direction.SOUTH);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModContent.GARAGE_DOOR_ITEM.get()));
            var floor=h.absolutePos(pos.below());
            p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor).add(0,.5,0),Direction.UP,floor,false));
            h.assertTrue(h.getBlockState(pos).isAir()&&p.getMainHandItem().getCount()==1,"conflicting bridge is refused without spending its panel");
            h.assertTrue(GarageDoorBlock.facing(h.getBlockState(pos.west()))==Direction.NORTH
                    &&GarageDoorBlock.facing(h.getBlockState(pos.east()))==Direction.SOUTH,"existing doors do not flip");
        } finally {
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

}
