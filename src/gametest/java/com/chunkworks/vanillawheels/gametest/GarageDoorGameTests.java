/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.gametest;

import com.chunkworks.vanillawheels.*;
import com.chunkworks.vanillawheels.garage.*;
import com.chunkworks.vanillawheels.domain.Shutter;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Partitions: real item placement/size rejection; both axes; power on/off at a
 * follower; collision closed/open; reversal; vehicle/mob/player obstruction;
 * break/split/rejoin; saved moving block entities; one item per broken panel. */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GarageDoorGameTests {
    private static final BlockPos START = new BlockPos(4,2,10);
    private static final BlockPos SWITCH = START.offset(4,0,-1);
    private static final ResourceLocation CAR = ResourceLocation.parse("vanillawheels_gametest:box_car");

    private static Player prepare(GameTestHelper h) {
        for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(new Vec3(6.5,2,6.5))); player.setYRot(0);
        return player;
    }
    private static boolean place(GameTestHelper h,Player p,BlockPos pos) {
        var item=new ItemStack(ModContent.GARAGE_DOOR_ITEM.get());
        p.setItemInHand(InteractionHand.MAIN_HAND,item);
        var support=h.absolutePos(pos.below());
        return item.useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false))).consumesAction();
    }
    private static void door(GameTestHelper h,Player p,int width,int height) {
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)
            h.assertTrue(place(h,p,START.offset(x,y,0)),"real item places panel "+x+","+y);
        h.setBlock(SWITCH,Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE,AttachFace.FLOOR));
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    }
    private static GarageDoorBlockEntity root(GameTestHelper h) { return (GarageDoorBlockEntity)h.getBlockEntity(START); }
    private static void toggle(GameTestHelper h,Player p) { h.useBlock(SWITCH,p); }

    @GameTest(template="garage_arena",timeoutTicks=120)
    public void realLeverAtFollowerOpensClosesAndReversesOneDoor(GameTestHelper h) {
        var p=prepare(h);door(h,p,5,4);
        h.runAtTickTime(4,()->{
            h.assertTrue(root(h).formed()&&root(h).width()==5&&root(h).height()==4,"five by four joined door");
            var follower=(GarageDoorBlockEntity)h.getBlockEntity(START.offset(3,0,0));
            h.assertTrue(follower.controller()==root(h),"one elected controller");
            h.assertTrue(!follower.shape().isEmpty(),"closed curtain collides");toggle(h,p);
        });
        h.runAtTickTime(40,()->{
            h.assertTrue(root(h).lift()==28,"power at far follower opens whole door");
            h.assertTrue(((GarageDoorBlockEntity)h.getBlockEntity(START.offset(2,1,0))).shape().isEmpty(),"open passage has no invisible collision");
            toggle(h,p);
        });
        h.runAtTickTime(48,()->{
            h.assertTrue(root(h).lift()>0&&root(h).lift()<28,"power off closes gradually");toggle(h,p);
        });
        h.runAtTickTime(65,()->{h.assertTrue(root(h).lift()==28,"mid-motion power reversal reopens");toggle(h,p);});
        h.runAtTickTime(100,()->{h.assertTrue(root(h).lift()==0,"power off closes fully");h.succeed();});
    }

    @GameTest(template="garage_arena",timeoutTicks=170)
    public void closingWaitsForVehicleCowAndPlayerAndThenResumes(GameTestHelper h) {
        var p=prepare(h);door(h,p,5,4);
        final Vehicle[] vehicle=new Vehicle[1]; final Cow[] cow=new Cow[1];
        final net.minecraft.server.level.ServerPlayer[] person=new net.minecraft.server.level.ServerPlayer[1];
        h.runAtTickTime(4,()->toggle(h,p));
        h.runAtTickTime(40,()->{
            vehicle[0]=Vehicle.create(h.getLevel(),CAR,h.absoluteVec(new Vec3(6.5,2,10.5)),0);
            vehicle[0].setNoGravity(true);h.getLevel().addFreshEntity(vehicle[0]);toggle(h,p);
        });
        h.runAtTickTime(55,()->{
            h.assertTrue(root(h).lift()==28,"vehicle under shutter pauses closure");vehicle[0].discard();
            cow[0]=h.spawn(EntityType.COW,new BlockPos(6,2,10));cow[0].setNoAi(true);
        });
        h.runAtTickTime(70,()->{
            h.assertTrue(root(h).lift()==28,"mob under shutter pauses closure");cow[0].discard();
            person[0]=h.makeMockServerPlayerInLevel();person[0].moveTo(h.absoluteVec(new Vec3(6.5,2,10.5)));
        });
        h.runAtTickTime(85,()->{
            h.assertTrue(root(h).lift()==28,"player under shutter pauses closure");
            person[0].moveTo(h.absoluteVec(new Vec3(6.5,2,6.5)));
            h.getLevel().getServer().getPlayerList().remove(person[0]);
        });
        h.runAtTickTime(120,()->{h.assertTrue(root(h).lift()==0,"closing resumes when clear");h.succeed();});
    }

    @GameTest(template="garage_arena",timeoutTicks=95)
    public void removingColumnSplitsDoorAndRepairRejoinsWithoutExtraDrops(GameTestHelper h) {
        var p=prepare(h);door(h,p,5,4);
        h.runAtTickTime(5,()->{
            for(int y=0;y<4;y++)h.getLevel().destroyBlock(h.absolutePos(START.offset(2,y,0)),true);
        });
        h.runAtTickTime(10,()->{
            var right=(GarageDoorBlockEntity)h.getBlockEntity(START.offset(3,0,0));
            h.assertTrue(root(h).formed()&&root(h).width()==2&&right.formed()&&right.width()==2,"two independent complete rectangles");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds(),e->e.getItem().is(ModContent.GARAGE_DOOR_ITEM.get()));
            h.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==4,"one recoverable item per removed panel");
            drops.forEach(ItemEntity::discard);toggle(h,p);
        });
        h.runAtTickTime(45,()->{
            h.assertTrue(root(h).lift()==0&&((GarageDoorBlockEntity)h.getBlockEntity(START.offset(3,0,0))).lift()==28,
                    "right switch powers only connected door");
            toggle(h,p);
        });
        h.runAtTickTime(80,()->{for(int y=0;y<4;y++)h.assertTrue(place(h,p,START.offset(2,y,0)),"repair panel");});
        h.runAtTickTime(85,()->{h.assertTrue(root(h).width()==5&&root(h).formed(),"completed rectangle rejoins");h.succeed();});
    }

    @GameTest(template="garage_arena",timeoutTicks=100)
    public void movingDoorRestoresItsActualSavedBlockEntities(GameTestHelper h) {
        var p=prepare(h);door(h,p,5,4);
        h.runAtTickTime(4,()->toggle(h,p));
        h.runAtTickTime(12,()->{
            int before=root(h).lift();h.assertTrue(before>0&&before<28,"door moving before save");
            var saved=new LinkedHashMap<BlockPos,CompoundTag>();
            for(int y=0;y<4;y++)for(int x=0;x<5;x++){
                var pos=h.absolutePos(START.offset(x,y,0));
                saved.put(pos,h.getLevel().getBlockEntity(pos).saveWithFullMetadata(h.getLevel().registryAccess()));
            }
            for(var e:saved.entrySet()){
                var state=h.getLevel().getBlockState(e.getKey());h.getLevel().removeBlockEntity(e.getKey());
                var restored=BlockEntity.loadStatic(e.getKey(),state,e.getValue(),h.getLevel().registryAccess());
                h.getLevel().setBlockEntity(restored);restored.onLoad();
            }
            h.assertTrue(root(h).lift()==before,"saved travel restored exactly");
        });
        h.runAtTickTime(55,()->{h.assertTrue(root(h).lift()==28,"restored door finishes opening");toggle(h,p);});
        h.runAtTickTime(90,()->{h.assertTrue(root(h).lift()==0,"restored power control closes door");h.succeed();});
    }

    @GameTest(template="garage_arena",timeoutTicks=50)
    public void placementBoundsIncompleteShapesAndPerpendicularPanels(GameTestHelper h) {
        var p=prepare(h);
        for(int x=0;x<16;x++)h.assertTrue(place(h,p,START.offset(x,0,0)),"bounded row places");
        h.assertTrue(!place(h,p,START.offset(16,0,0))&&p.getMainHandItem().getCount()==1,"seventeenth panel refused without consumption");
        var other=START.offset(0,0,5);
        h.setBlock(other,ModContent.GARAGE_DOOR.get().defaultBlockState().setValue(GarageDoorBlock.AXIS,Direction.Axis.Z));
        h.setBlock(other.south(),ModContent.GARAGE_DOOR.get().defaultBlockState().setValue(GarageDoorBlock.AXIS,Direction.Axis.Z));
        h.setBlock(other.above(),ModContent.GARAGE_DOOR.get().defaultBlockState().setValue(GarageDoorBlock.AXIS,Direction.Axis.Z));
        h.runAtTickTime(5,()->{
            h.assertTrue(!((GarageDoorBlockEntity)h.getBlockEntity(other)).formed(),"L shape waits for completion");
            h.setBlock(other.above().south(),ModContent.GARAGE_DOOR.get().defaultBlockState().setValue(GarageDoorBlock.AXIS,Direction.Axis.Z));
        });
        h.runAtTickTime(10,()->{
            var root=(GarageDoorBlockEntity)h.getBlockEntity(other);
            h.assertTrue(root.formed()&&root.width()==2&&root.axis()==Direction.Axis.Z,"other axis forms independently");
            h.setBlock(other.north(),Blocks.REDSTONE_BLOCK);
        });
        h.runAtTickTime(30,()->{
            h.assertTrue(((GarageDoorBlockEntity)h.getBlockEntity(other)).lift()==12,"perpendicular door opens");
            h.assertTrue(root(h).lift()==0,"unconnected row remains closed");h.succeed();
        });
    }
    @GameTest(template="garage_arena",timeoutTicks=60)
    public void incompleteOpenDoorKeepsItsExistingClearance(GameTestHelper h) {
        var p=prepare(h);door(h,p,5,4);
        h.runAtTickTime(4,()->toggle(h,p));
        h.runAtTickTime(40,()->h.getLevel().destroyBlock(h.absolutePos(START.offset(4,3,0)),false));
        h.runAtTickTime(45,()->{
            var middle=(GarageDoorBlockEntity)h.getBlockEntity(START.offset(2,1,0));
            h.assertTrue(!middle.formed(),"missing corner leaves incomplete component");
            h.assertTrue(middle.shape().isEmpty(),"dismantling open door adds no collision inside existing clearance");
            h.succeed();
        });
    }

}
