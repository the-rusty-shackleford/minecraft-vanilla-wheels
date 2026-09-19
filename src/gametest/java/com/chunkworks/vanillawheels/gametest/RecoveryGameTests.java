/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.gametest;

import com.chunkworks.vanillawheels.*;
import com.chunkworks.vanillawheels.domain.Condition;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Real-server partitions: wrench/destruction/recall; complete item components;
 * two chests/eight double chests; deployed/dropped/collected/stale copies;
 * full inventory/passengers/creative; fuel shortfall; replacement fob; saved index.
 * Uses actual item/entity/menu paths and Minecraft inventories, no backend mocks.
 */
@GameTestHolder(VanillaWheelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecoveryGameTests {
    private static final ResourceLocation CAR = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_car");
    private static final ResourceLocation TRAILER = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_cargo_trailer");

    private static ServerPlayer player(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "key-tester"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 position = helper.absoluteVec(new Vec3(12.5, 3, 12.5));
        player.teleportTo(position.x, position.y, position.z);
        return player;
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static Vehicle car(GameTestHelper helper) {
        floor(helper);
        Vehicle vehicle = Vehicle.create(helper.getLevel(), CAR, helper.absoluteVec(new Vec3(7.5, 2, 7.5)), 0);
        helper.getLevel().addFreshEntity(vehicle);
        vehicle.setFuel(vehicle.tank().capacity());
        return vehicle;
    }

    private static ItemStack namedCargo() {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.setDamageValue(17);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Borrowed, definitely returning it"));
        return stack;
    }

    private static ItemStack pair(GameTestHelper helper, ServerPlayer player, Vehicle vehicle) {
        ItemStack fob = new ItemStack(ModContent.KEY_FOB.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, fob);
        vehicle.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(fob.has(ModContent.KEY_TOKEN.get()), "real interaction paired the key");
        return fob;
    }

    private static void recall(GameTestHelper helper, ServerPlayer player) {
        var result = ModContent.KEY_FOB.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction(), "real fob use started recall");
    }

    private static List<ItemStack> packed(ServerPlayer player) {
        return player.getInventory().items.stream().filter(s -> s.is(ModContent.VEHICLE_ITEM.get())).toList();
    }

    @GameTest(template = "arena", timeoutTicks = 600)
    public void recallLoadsPersistedUnloadedVehicleAndTrailerThenTransfersActualCargo(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = player(helper);
        var level = helper.getLevel();
        BlockPos remote = helper.absolutePos(new BlockPos(8192, 2, 0));
        var chunk = new net.minecraft.world.level.ChunkPos(remote);
        level.setChunkForced(chunk.x, chunk.z, true);
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
            level.setBlockAndUpdate(remote.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        Vehicle vehicle = Vehicle.create(level, CAR, Vec3.atBottomCenterOf(remote), 0);
        Vehicle trailer = Vehicle.create(level, TRAILER, vehicle.position().add(0, 0, -4), 0);
        level.addFreshEntity(vehicle); level.addFreshEntity(trailer); vehicle.hitch(trailer);
        vehicle.setFuel(24000); vehicle.setItem(53, namedCargo()); trailer.setItem(53, new ItemStack(Items.EMERALD, 19));
        pair(helper, player, vehicle);
        helper.runAtTickTime(50, () -> level.setChunkForced(chunk.x, chunk.z, false));
        boolean[] requested = {false};
        for (int tick : new int[]{100, 300, 550}) helper.runAtTickTime(tick, () ->
            org.slf4j.LoggerFactory.getLogger("Recovery fixture").info("unloaded recall at {}: requested={} removed={} reason={} entity={} chunk={} packed={} using={} trailerRemoved={}",
                tick, requested[0], vehicle.isRemoved(), vehicle.getRemovalReason(), level.getEntity(vehicle.getUUID()) != null,
                level.getChunkSource().hasChunk(chunk.x, chunk.z), packed(player).size(), player.isUsingItem(), trailer.isRemoved()));
        helper.onEachTick(() -> {
            if (!requested[0] && vehicle.isRemoved()) {
                helper.assertValueEqual(vehicle.getRemovalReason(), Entity.RemovalReason.UNLOADED_TO_CHUNK, "real chunk unload, not discard");
                helper.assertTrue(level.getEntity(vehicle.getUUID()) == null, "vehicle no longer exists in loaded world");
                helper.assertTrue(level.getEntity(trailer.getUUID()) == null, "trailer also unloaded");
                requested[0] = true; recall(helper, player);
            }
            if (requested[0] && packed(player).size() == 2) {
                ItemStack car = packed(player).stream().filter(s -> CAR.equals(s.get(ModContent.VEHICLE.get()))).findFirst().orElseThrow();
                ItemStack towed = packed(player).stream().filter(s -> TRAILER.equals(s.get(ModContent.VEHICLE.get()))).findFirst().orElseThrow();
                helper.assertTrue(ItemStack.matches(VehicleCargo.unpack(car.get(ModContent.CARGO.get())).get(53), namedCargo()), "persisted named cargo returned");
                helper.assertValueEqual(VehicleCargo.unpack(towed.get(ModContent.CARGO.get())).get(53).getCount(), 19, "persisted trailer cargo returned");
                helper.assertValueEqual(car.get(ModContent.FUEL.get()), 0, "distant recall paid capped full tank bill");
                helper.assertTrue(level.getEntity(vehicle.getUUID()) == null && level.getEntity(trailer.getUUID()) == null, "no deployed duplicates remain");
                player.connection.disconnect(Component.literal("test complete")); helper.succeed();
            }
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void destructionKeepsCompleteCargoAndDropsOneBrokenVehicle(GameTestHelper helper) {
        Vehicle vehicle = car(helper);
        ItemStack cargo = namedCargo();
        vehicle.setItem(53, cargo.copy());
        vehicle.hurt(helper.getLevel().damageSources().generic(), 5);
        helper.assertTrue(vehicle.isRemoved(), "fatal damage removes the deployed vehicle");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds());
        helper.assertValueEqual(drops.size(), 1, "one physical drop, no loose cargo");
        ItemStack stack = drops.getFirst().getItem();
        helper.assertValueEqual(stack.get(ModContent.CONDITION.get()), 0, "broken is zero condition, not a deleted item");
        helper.assertTrue(ItemStack.matches(VehicleCargo.unpack(stack.get(ModContent.CARGO.get())).get(53), cargo), "cargo components survive");
        Vehicle restored = Vehicle.create(helper.getLevel(), CAR, vehicle.position(), 0);
        helper.assertTrue(restored.loadFromItem(stack), "broken item restores");
        helper.assertTrue(!restored.hasFuel(), "a fueled wreck cannot power its engine");
        helper.assertTrue(ItemStack.matches(restored.getItem(53), cargo), "restored cargo matches");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void canceledPackedDropKeepsTheVehicleAndItsCargo(GameTestHelper helper) {
        Vehicle vehicle = car(helper); vehicle.setItem(0, namedCargo());
        ServerPlayer player = player(helper); pair(helper, player, vehicle);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> cancelDrop = event -> {
            if (event.getEntity() instanceof ItemEntity item && vehicle.binding().equals(item.getItem().get(ModContent.BINDING.get())))
                event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancelDrop);
        try {
            vehicle.hurt(helper.getLevel().damageSources().generic(), 5);
            helper.assertTrue(!vehicle.isRemoved(), "a rejected physical drop leaves the original vehicle intact");
            helper.assertTrue(ItemStack.matches(vehicle.getItem(0), namedCargo()), "rejected transfer preserves original cargo");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancelDrop);
            player.connection.disconnect(Component.literal("test complete"));
        }
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void creativeWrenchWithFullInventoryDropsCargoInsteadOfDeletingIt(GameTestHelper helper) {
        Vehicle vehicle = car(helper); vehicle.setItem(0, namedCargo());
        ServerPlayer player = player(helper); player.setGameMode(GameType.CREATIVE);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.WRENCH.get()));
        player.setShiftKeyDown(true); vehicle.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(vehicle.isRemoved(), "wrench removed vehicle");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds());
        helper.assertValueEqual(drops.size(), 1, "one packed drop despite creative overflow");
        helper.assertTrue(ItemStack.matches(VehicleCargo.unpack(drops.getFirst().getItem().get(ModContent.CARGO.get())).getFirst(), namedCargo()), "cargo survives");
        player.connection.disconnect(Component.literal("test complete")); helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void collectedWreckCannotBeRecalledFromAnotherPlayersInventory(GameTestHelper helper) {
        Vehicle vehicle = car(helper); vehicle.setItem(0, namedCargo());
        ServerPlayer owner = player(helper); pair(helper, owner, vehicle); vehicle.kill();
        ItemEntity drop = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).getFirst();
        ServerPlayer collector = player(helper); drop.setNoPickUpDelay(); drop.playerTouch(collector);
        helper.assertTrue(drop.isRemoved(), "real player pickup removed physical drop");
        helper.assertValueEqual(packed(collector).size(), 1, "collector holds the sole wreck");
        helper.assertTrue(!ModContent.KEY_FOB.get().use(helper.getLevel(), owner, InteractionHand.MAIN_HAND).getResult().consumesAction(), "owner cannot duplicate collected property");
        helper.assertTrue(packed(owner).isEmpty(), "owner receives no copy");
        owner.connection.disconnect(Component.literal("test complete")); collector.connection.disconnect(Component.literal("test complete")); helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public void disconnectDuringRecallLeavesPropertyInWorld(GameTestHelper helper) {
        Vehicle vehicle = car(helper); vehicle.setItem(0, namedCargo());
        ServerPlayer player = player(helper); pair(helper, player, vehicle); recall(helper, player);
        helper.runAtTickTime(5, () -> player.connection.disconnect(Component.literal("disconnect during recall")));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(!vehicle.isRemoved(), "disconnected player's already-saved inventory cannot receive a transfer");
            helper.assertValueEqual(vehicle.tank().ticks(), 24000, "canceled recall charges no fuel");
            helper.assertTrue(ItemStack.matches(vehicle.getItem(0), namedCargo()), "cargo remains in world");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public void creativeRecallPreservesFuelAndCondition(GameTestHelper helper) {
        Vehicle vehicle = car(helper); vehicle.setFuel(12); vehicle.setCondition(3456);
        ServerPlayer player = player(helper); player.setGameMode(GameType.CREATIVE);
        pair(helper, player, vehicle); recall(helper, player);
        helper.runAtTickTime(60, () -> {
            ItemStack item = packed(player).getFirst();
            helper.assertValueEqual(item.get(ModContent.FUEL.get()), 12, "creative pays no fuel");
            helper.assertValueEqual(item.get(ModContent.CONDITION.get()), 3456, "creative pays no wear");
            player.connection.disconnect(Component.literal("test complete")); helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void eightDoubleChestsSurviveItemAndEntitySerialization(GameTestHelper helper) {
        floor(helper);
        ResourceLocation profile = ResourceLocation.fromNamespaceAndPath("vanillawheels_gametest", "box_cargo");
        Vehicle vehicle = Vehicle.create(helper.getLevel(), profile, helper.absoluteVec(new Vec3(7.5, 2, 7.5)), 0);
        helper.assertValueEqual(vehicle.getContainerSize(), 432, "all eight double chests");
        ItemStack cargo = namedCargo();
        vehicle.setItem(431, cargo.copy()); vehicle.setItem(256, new ItemStack(Items.EMERALD, 23));
        ItemStack serialized = ItemStack.parse(helper.getLevel().registryAccess(), vehicle.toItem().save(helper.getLevel().registryAccess())).orElseThrow();
        Vehicle fromItem = Vehicle.create(helper.getLevel(), profile, vehicle.position(), 0);
        helper.assertTrue(fromItem.loadFromItem(serialized), "all cargo fits");
        helper.assertTrue(ItemStack.matches(fromItem.getItem(431), cargo), "last slot survives item codec");
        CompoundTag tag = new CompoundTag(); vehicle.saveWithoutId(tag);
        Vehicle fromWorld = new Vehicle(ModContent.VEHICLE_ENTITY.get(), helper.getLevel()); fromWorld.load(tag);
        helper.assertValueEqual(fromWorld.getItem(256).getCount(), 23, "slot 256 did not wrap to zero");
        helper.assertTrue(ItemStack.matches(fromWorld.getItem(431), cargo), "last slot survives world save");
        helper.assertTrue(fromWorld.getItem(0).isEmpty(), "no wrapped duplicate in slot zero");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public void recallTransfersVehicleTrailerAndBothInventoriesExactlyOnce(GameTestHelper helper) {
        Vehicle vehicle = car(helper);
        ServerPlayer player = player(helper);
        Vehicle trailer = Vehicle.create(helper.getLevel(), TRAILER, vehicle.position().add(0, 0, -4), 0);
        helper.getLevel().addFreshEntity(trailer); vehicle.hitch(trailer);
        helper.assertTrue(vehicle.trailer() == trailer, "hitched fixture");
        vehicle.setItem(0, new ItemStack(Items.APPLE, 7));
        trailer.setCondition(6500);
        trailer.setItem(0, new ItemStack(Items.EMERALD, 3));
        pair(helper, player, vehicle); recall(helper, player);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(vehicle.isRemoved() && trailer.isRemoved(), "both physical vehicles returned");
            helper.assertValueEqual(packed(player).size(), 2, "exactly two inventory items");
            ItemStack car = packed(player).stream().filter(s -> CAR.equals(s.get(ModContent.VEHICLE.get()))).findFirst().orElseThrow();
            helper.assertValueEqual(car.get(ModContent.FUEL.get()), 23760, "one minimum-distance fuel charge");
            helper.assertValueEqual(VehicleCargo.unpack(car.get(ModContent.CARGO.get())).getFirst().getCount(), 7, "cargo returned");
            ItemStack towed = packed(player).stream().filter(s -> TRAILER.equals(s.get(ModContent.VEHICLE.get()))).findFirst().orElseThrow();
            helper.assertValueEqual(towed.get(ModContent.CONDITION.get()), 6500, "trailer condition preserved");
            helper.assertValueEqual(VehicleCargo.unpack(towed.get(ModContent.CARGO.get())).getFirst().getCount(), 3, "trailer cargo returned");
            ModContent.KEY_FOB.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(packed(player).size(), 2, "repeat recall cannot make copies");
            player.connection.disconnect(Component.literal("test complete")); helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public void emptyTankPaysHalfDurabilityAndRecallMayReturnAWreck(GameTestHelper helper) {
        Vehicle vehicle = car(helper); vehicle.setFuel(0); vehicle.setCondition(25);
        ServerPlayer player = player(helper); pair(helper, player, vehicle); recall(helper, player);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(vehicle.isRemoved(), "recall succeeds without fuel");
            ItemStack stack = packed(player).getFirst();
            helper.assertValueEqual(stack.get(ModContent.FUEL.get()), 0, "empty tank");
            helper.assertValueEqual(stack.get(ModContent.CONDITION.get()), 0, "shortfall can break the recovered vehicle");
            player.connection.disconnect(Component.literal("test complete")); helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160)
    public void fullInventoryAndOccupiedTrailerRefuseWithoutCharging(GameTestHelper helper) {
        Vehicle vehicle = car(helper); ServerPlayer player = player(helper);
        pair(helper, player, vehicle);
        for (int slot = 1; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        recall(helper, player);
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(!vehicle.isRemoved(), "full inventory leaves vehicle in place");
            helper.assertValueEqual(vehicle.tank().ticks(), 24000, "no fuel charged for refusal");
            player.getInventory().setItem(1, ItemStack.EMPTY);
            player.getInventory().setItem(2, ItemStack.EMPTY);
            Vehicle trailer = Vehicle.create(helper.getLevel(), TRAILER, vehicle.position().add(0, 0, -4), 0);
            helper.getLevel().addFreshEntity(trailer); vehicle.hitch(trailer);
            Entity cow = EntityType.COW.create(helper.getLevel()); cow.setPos(trailer.position());
            helper.getLevel().addFreshEntity(cow); cow.startRiding(trailer, true);
            recall(helper, player);
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(!vehicle.isRemoved(), "occupied vehicle remains");
            helper.assertValueEqual(vehicle.tank().ticks(), 24000, "no fuel charged for passengers");
            player.connection.disconnect(Component.literal("test complete")); helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public void replacementInvalidatesTheOldFobAndSavedIndexKeepsOnePairing(GameTestHelper helper) {
        Vehicle vehicle = car(helper); ServerPlayer player = player(helper);
        ItemStack old = pair(helper, player, vehicle).copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.KEY_FOB.get()));
        ModContent.KEY_FOB.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        ItemStack replacement = player.getMainHandItem().copy();
        RecoveryData data = RecoveryData.get(player.server);
        RecoveryData restored = RecoveryData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(!restored.begin(player, old), "serialized index rejects the old key");
        player.setItemInHand(InteractionHand.MAIN_HAND, old);
        helper.assertTrue(!ModContent.KEY_FOB.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "old physical fob rejected");
        player.setItemInHand(InteractionHand.MAIN_HAND, replacement); recall(helper, player);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(vehicle.isRemoved(), "replacement recalls the original vehicle");
            player.connection.disconnect(Component.literal("test complete")); helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160)
    public void brokenDropRemainsRecoverableAndCollectedCopiesCannotDeployTwice(GameTestHelper helper) {
        Vehicle vehicle = car(helper); ServerPlayer player = player(helper);
        pair(helper, player, vehicle); vehicle.hurt(helper.getLevel().damageSources().generic(), 5);
        ItemEntity drop = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).getFirst();
        helper.assertTrue(drop.isInvulnerable(), "bound wreck survives hostile surroundings");
        ItemStack stale = drop.getItem().copy();
        recall(helper, player);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(drop.isRemoved(), "the actual wreck was recovered");
            helper.assertValueEqual(packed(player).size(), 1, "one wreck in inventory");
            helper.assertTrue(!RecoveryData.get(player.server).canPlace(stale), "old physical-drop incarnation invalidated");
            ItemStack item = packed(player).getFirst();
            ItemStack duplicate = item.copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
            BlockPos floor = helper.absolutePos(new BlockPos(7, 1, 7));
            var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false);
            helper.assertTrue(ModContent.VEHICLE_ITEM.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "wreck places through real item use");
            helper.assertTrue(!RecoveryData.get(player.server).canPlace(duplicate), "copied packed item cannot deploy a second vehicle");
            player.connection.disconnect(Component.literal("test complete")); helper.succeed();
        });
    }
}
