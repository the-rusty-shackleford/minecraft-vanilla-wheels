/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels;

import com.chunkworks.vanillawheels.domain.Condition;
import com.chunkworks.vanillawheels.domain.RecallCost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * One active vehicle and fob per player, indexed in overworld SavedData.
 * AF: each entry names the actual deployed vehicle, physical drop, or already-held
 * item. There are no cached cargo copies from which recall could manufacture property.
 * RI: owner/binding indexes agree; one owner per binding; a packed token can deploy
 * only once. Server thread only. Pending requests are transient, bounded to 200 ticks,
 * and release their temporary chunk ticket on every completion/cancellation path.
 */
public final class RecoveryData extends SavedData {
    private enum Place { VEHICLE, DROP, HELD }
    private record Entry(UUID owner, UUID binding, UUID key, Place place, ResourceLocation dimension,
                         BlockPos position, UUID entity, UUID packed) {}
    private static final UUID NONE = new UUID(0, 0);
    private static final Factory<RecoveryData> FACTORY = new Factory<>(RecoveryData::new, RecoveryData::load);
    private static final TicketType<UUID> TICKET = TicketType.create("vanillawheels_recall", UUID::compareTo, 220);
    private final Map<UUID, Entry> owners = new HashMap<>();
    private final Map<UUID, Entry> bindings = new HashMap<>();
    private final Map<UUID, Entry> located = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>();

    private static final class Request {
        final ServerPlayer player;
        final UUID key;
        final ServerLevel level;
        final ChunkPos chunk;
        final long started;
        long quotedAt = -1;
        @Nullable RecallCost quote;
        Request(ServerPlayer player, Entry entry, ServerLevel level, long started) {
            this.player = player; this.key = entry.key(); this.level = level;
            this.chunk = new ChunkPos(entry.position()); this.started = started;
        }
    }

    /** requires: server thread; effects: loads/creates the world's index; throws: storage errors. */
    public static RecoveryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "vanillawheels_recovery");
    }

    private void put(Entry entry) {
        Entry old = owners.put(entry.owner(), entry);
        if (old != null) { bindings.remove(old.binding()); located.remove(old.entity()); }
        bindings.put(entry.binding(), entry);
        if (entry.place() != Place.HELD) located.put(entry.entity(), entry);
        setDirty();
    }

    /** requires: valid saved NBT; effects: restores immutable entries, never requests; throws: invalid saved data errors. */
    public static RecoveryData load(CompoundTag tag, HolderLookup.Provider registries) {
        RecoveryData result = new RecoveryData();
        for (Tag row : tag.getList("Bindings", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) row;
            ResourceLocation dimension = ResourceLocation.tryParse(value.getString("Dimension"));
            if (dimension == null || !value.hasUUID("Owner") || !value.hasUUID("Binding") || !value.hasUUID("Key")) continue;
            int place = value.getInt("Place");
            if (place < 0 || place >= Place.values().length) continue;
            result.put(new Entry(value.getUUID("Owner"), value.getUUID("Binding"), value.getUUID("Key"), Place.values()[place],
                    dimension, BlockPos.of(value.getLong("Position")), value.getUUID("Entity"), value.getUUID("Packed")));
        }
        return result;
    }

    /** requires: server thread; effects: serializes indexes without cargo snapshots; throws: none. */
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : owners.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Owner", entry.owner()); row.putUUID("Binding", entry.binding()); row.putUUID("Key", entry.key());
            row.putInt("Place", entry.place().ordinal()); row.putString("Dimension", entry.dimension().toString());
            row.putLong("Position", entry.position().asLong()); row.putUUID("Entity", entry.entity()); row.putUUID("Packed", entry.packed());
            list.add(row);
        }
        tag.put("Bindings", list);
        return tag;
    }

    /** requires: real server interaction; effects: inserts/pairs the key, replacing this owner's previous pairing; throws: none. */
    public boolean bind(ServerPlayer player, Vehicle vehicle, ItemStack key) {
        if (vehicle.tank().capacity() == 0) return fail(player, "motor_vehicle");
        Entry existing = bindings.get(vehicle.binding());
        if (existing != null && !existing.owner().equals(player.getUUID())) return fail(player, "other_owner");
        cancel(player.getUUID());
        UUID binding = existing == null ? UUID.randomUUID() : existing.binding();
        UUID token = UUID.randomUUID();
        put(new Entry(player.getUUID(), binding, token, Place.VEHICLE, vehicle.level().dimension().location(),
                vehicle.blockPosition(), vehicle.getUUID(), NONE));
        vehicle.binding(binding);
        key.set(ModContent.KEY_OWNER.get(), player.getUUID()); key.set(ModContent.KEY_TOKEN.get(), token);
        key.set(ModContent.VEHICLE.get(), vehicle.profileId());
        tell(player, "paired", vehicle.getName());
        return true;
    }

    /** requires: unpaired key; effects: replaces a lost fob for the owner's existing pairing; throws: none. */
    public boolean replacement(ServerPlayer player, ItemStack key) {
        Entry old = owners.get(player.getUUID());
        if (old == null) return fail(player, "unpaired");
        UUID token = UUID.randomUUID();
        put(new Entry(old.owner(), old.binding(), token, old.place(), old.dimension(), old.position(), old.entity(), old.packed()));
        key.set(ModContent.KEY_OWNER.get(), player.getUUID()); key.set(ModContent.KEY_TOKEN.get(), token);
        tell(player, "replacement");
        return true;
    }

    private boolean validKey(ServerPlayer player, ItemStack key, @Nullable Entry entry) {
        return entry != null && key.is(ModContent.KEY_FOB.get()) && player.getUUID().equals(key.get(ModContent.KEY_OWNER.get()))
                && entry.key().equals(key.get(ModContent.KEY_TOKEN.get()));
    }

    /** requires: server thread, key in hand; effects: starts a cancellable hold-to-recall, temporarily loading only the target area; throws: none. */
    public boolean begin(ServerPlayer player, ItemStack key) {
        Entry entry = owners.get(player.getUUID());
        if (!validKey(player, key, entry)) return fail(player, "stale");
        if (entry.place() == Place.HELD) return fail(player, "collected");
        ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, entry.dimension()));
        if (level == null) return fail(player, "missing");
        cancel(player.getUUID());
        Request request = new Request(player, entry, level, player.server.overworld().getGameTime());
        requests.put(player.getUUID(), request);
        level.getChunkSource().addRegionTicket(TICKET, request.chunk, 2, player.getUUID());
        tell(player, "locating");
        return true;
    }

    /** requires: server thread; effects: ends a request and releases its chunk ticket; throws: none. */
    public void cancel(UUID owner) {
        Request request = requests.remove(owner);
        if (request != null) request.level.getChunkSource().removeRegionTicket(TICKET, request.chunk, 2, owner);
    }

    /** requires: server thread; effects: remembers a bound vehicle only when it enters a different chunk/dimension; throws: none. */
    public void track(Vehicle vehicle) { track(vehicle, vehicle.binding(), Place.VEHICLE); }

    private void track(Entity entity, @Nullable UUID binding, Place place) {
        Entry entry = bindings.get(binding);
        if (entry == null || entry.place() != place || !entry.entity().equals(entity.getUUID())) return;
        if (entry.position().getX() >> 4 != entity.getBlockX() >> 4 || entry.position().getZ() >> 4 != entity.getBlockZ() >> 4
                || !entry.dimension().equals(entity.level().dimension().location()))
            put(new Entry(entry.owner(), entry.binding(), entry.key(), place, entity.level().dimension().location(),
                    entity.blockPosition(), entry.entity(), entry.packed()));
    }

    /** requires: server thread; effects: marks the exact packed stack as held, invalidating earlier incarnations; throws: none. */
    public void held(ItemStack stack) {
        Entry entry = bindings.get(stack.get(ModContent.BINDING.get()));
        UUID token = stack.get(ModContent.PACKED_TOKEN.get());
        if (entry != null && token != null)
            put(new Entry(entry.owner(), entry.binding(), entry.key(), Place.HELD, entry.dimension(), entry.position(), entry.entity(), token));
    }

    /** requires: server thread and an actual drop; effects: protects a recoverable drop and records its location; throws: none. */
    public void dropped(ItemEntity drop) {
        ItemStack stack = drop.getItem();
        Entry entry = bindings.get(stack.get(ModContent.BINDING.get()));
        UUID token = stack.get(ModContent.PACKED_TOKEN.get());
        if (entry == null || token == null) return;
        drop.setInvulnerable(true); drop.setUnlimitedLifetime();
        put(new Entry(entry.owner(), entry.binding(), entry.key(), Place.DROP, drop.level().dimension().location(),
                drop.blockPosition(), drop.getUUID(), token));
    }

    /** requires: server thread; effects: rejects a copied/stale packed incarnation; an old unpaired vehicle remains usable; throws: none. */
    public boolean canPlace(ItemStack stack) {
        Entry entry = bindings.get(stack.get(ModContent.BINDING.get()));
        return entry == null || entry.place() != Place.VEHICLE && entry.packed().equals(stack.get(ModContent.PACKED_TOKEN.get()));
    }

    /** requires: authorized placement already accepted by the level; effects: transfers the binding to the new entity and invalidates packed copies; throws: none. */
    public void deployed(Vehicle vehicle, ItemStack stack) {
        Entry entry = bindings.get(stack.get(ModContent.BINDING.get()));
        if (entry == null) { vehicle.binding(null); return; }
        if (entry.place() == Place.DROP) {
            ServerLevel oldLevel = vehicle.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, entry.dimension()));
            if (oldLevel != null && oldLevel.getEntity(entry.entity()) instanceof ItemEntity oldDrop) {
                oldDrop.setItem(ItemStack.EMPTY); oldDrop.discard();
            }
        }
        put(new Entry(entry.owner(), entry.binding(), entry.key(), Place.VEHICLE, vehicle.level().dimension().location(),
                vehicle.blockPosition(), vehicle.getUUID(), NONE));
    }

    /** requires: server event; effects: refuses stale world copies and tracks legitimate dropped packed vehicles; throws: none. */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Vehicle) && !(event.getEntity() instanceof ItemEntity item
                && item.getItem().has(ModContent.BINDING.get()))) return;
        RecoveryData data = get(level.getServer());
        if (event.getEntity() instanceof Vehicle vehicle && !vehicle.placingFromItem()) {
            Entry entry = data.bindings.get(vehicle.binding());
            if (entry != null && (entry.place() != Place.VEHICLE || !entry.entity().equals(vehicle.getUUID()))) event.setCanceled(true);
        } else if (event.getEntity() instanceof ItemEntity item && item.getItem().is(ModContent.VEHICLE_ITEM.get())) {
            Entry entry = data.bindings.get(item.getItem().get(ModContent.BINDING.get()));
            if (entry == null) return;
            boolean correct = entry.packed().equals(item.getItem().get(ModContent.PACKED_TOKEN.get()))
                    && (entry.place() == Place.HELD || entry.place() == Place.DROP && entry.entity().equals(item.getUUID()));
            if (!correct) event.setCanceled(true);
            else data.dropped(item);
        }
    }

    /** requires: server event; effects: marks physical pickup/hopper removal as already collected; unloading keeps its locator; throws: none. */
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ItemEntity item)
                || item.getRemovalReason() == null || !item.getRemovalReason().shouldDestroy()) return;
        RecoveryData data = get(level.getServer());
        // The stack may already be empty after pickup. Resolve by entity id in constant time.
        Entry entry = data.located.get(item.getUUID());
        if (entry != null && entry.place() == Place.DROP)
            data.put(new Entry(entry.owner(), entry.binding(), entry.key(), Place.HELD, entry.dimension(), entry.position(), entry.entity(), entry.packed()));
    }

    /** requires: server event; effects: updates a bound drop's locator only on crossing a chunk boundary; throws: none. */
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof ItemEntity item && item.level() instanceof ServerLevel level) {
            UUID binding = item.getItem().get(ModContent.BINDING.get());
            if (binding != null) get(level.getServer()).track(item, binding, Place.DROP);
        }
    }

    /** requires: server event; effects: advances only active recall requests; throws: none. */
    public static void onTick(ServerTickEvent.Post event) { get(event.getServer()).tick(event.getServer()); }

    private void tick(MinecraftServer server) {
        if (requests.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Request request : List.copyOf(requests.values())) {
            ServerPlayer player = request.player;
            Entry entry = owners.get(player.getUUID());
            if (!player.isAlive() || !player.isUsingItem() || !validKey(player, player.getUseItem(), entry) || !request.key.equals(entry.key())) {
                cancel(player.getUUID()); continue;
            }
            if (now - request.started > 200) { finish(request, "missing"); continue; }
            if (entry.place() == Place.HELD) { finish(request, "collected"); continue; }
            Entity target = request.level.getEntity(entry.entity());
            if (target == null) continue; // Entity loading follows the temporary chunk ticket asynchronously.
            if (target instanceof Vehicle vehicle) recallVehicle(request, vehicle, now);
            else if (target instanceof ItemEntity item) recallDrop(request, item, now);
            else finish(request, "missing");
        }
    }

    private RecallCost quote(Request request, Entity target, int capacity, int fuel, int condition) {
        double distance = request.player.level() == target.level() ? request.player.position().distanceTo(target.position()) : 30_000_000;
        return RecallCost.quote(distance, capacity, fuel, new Condition(condition), request.player.hasInfiniteMaterials());
    }

    private boolean confirmed(Request request, RecallCost cost, int oldCondition, int capacity, long now) {
        if (!cost.equals(request.quote)) {
            request.quote = cost; request.quotedAt = now;
            tell(request.player, "quote", String.format(java.util.Locale.ROOT, "%.1f", 100.0 * cost.fuelCost() / capacity),
                    String.format(java.util.Locale.ROOT, "%.1f", (oldCondition - cost.conditionAfter().remaining()) / 100.0));
            return false;
        }
        return now - request.quotedAt >= 20;
    }

    private List<Integer> freeSlots(ServerPlayer player, int count) {
        List<Integer> slots = new ArrayList<>(count);
        for (int i = 0; i < player.getInventory().items.size() && slots.size() < count; i++)
            if (player.getInventory().items.get(i).isEmpty()) slots.add(i);
        return slots;
    }

    private void recallVehicle(Request request, Vehicle vehicle, long now) {
        List<Vehicle> chain = new ArrayList<>();
        Vehicle next = vehicle;
        while (next != null && chain.size() < 8) {
            if (chain.contains(next)) { finish(request, "missing"); return; }
            chain.add(next);
            if (!next.getPassengers().isEmpty() || next.storageOpen()) { finish(request, "occupied"); return; }
            Vehicle trailer = next.recoveryTrailer();
            if (next.hasSavedTrailer() && trailer == null) return;
            next = trailer;
        }
        if (next != null) { finish(request, "missing"); return; }
        List<Integer> slots = freeSlots(request.player, chain.size());
        if (slots.size() < chain.size()) { finish(request, "full"); return; }
        RecallCost cost = quote(request, vehicle, vehicle.tank().capacity(), vehicle.tank().ticks(), vehicle.condition());
        if (!confirmed(request, cost, vehicle.condition(), vehicle.tank().capacity(), now)) return;
        vehicle.setFuel(cost.fuelAfter()); vehicle.setCondition(cost.conditionAfter().remaining());
        List<ItemStack> packed = chain.stream().map(Vehicle::toItem).toList();
        for (int i = 0; i < chain.size(); i++) {
            held(packed.get(i));
            request.player.getInventory().setItem(slots.get(i), packed.get(i));
            chain.get(i).packAway();
        }
        request.player.getInventory().setChanged(); request.player.containerMenu.broadcastChanges();
        finish(request, "recalled");
    }

    private void recallDrop(Request request, ItemEntity item, long now) {
        ItemStack stack = item.getItem();
        Entry entry = owners.get(request.player.getUUID());
        if (!stack.is(ModContent.VEHICLE_ITEM.get()) || !entry.binding().equals(stack.get(ModContent.BINDING.get()))
                || !entry.packed().equals(stack.get(ModContent.PACKED_TOKEN.get()))) { finish(request, "missing"); return; }
        var profile = com.chunkworks.vanillawheels.api.VanillaWheels.vehicleOf(stack)
                .flatMap(id -> com.chunkworks.vanillawheels.api.VanillaWheels.profile(request.level.registryAccess(), id));
        int capacity = profile.flatMap(p -> p.value().fuel()).map(p -> p.capacity()).orElse(0);
        if (capacity == 0) { finish(request, "motor_vehicle"); return; }
        List<Integer> slots = freeSlots(request.player, 1);
        if (slots.isEmpty()) { finish(request, "full"); return; }
        int condition = stack.getOrDefault(ModContent.CONDITION.get(), Condition.MAX);
        RecallCost cost = quote(request, item, capacity, Math.min(capacity, stack.getOrDefault(ModContent.FUEL.get(), 0)), condition);
        if (!confirmed(request, cost, condition, capacity, now)) return;
        ItemStack packed = stack.copy();
        packed.set(ModContent.FUEL.get(), cost.fuelAfter()); packed.set(ModContent.CONDITION.get(), cost.conditionAfter().remaining());
        packed.set(ModContent.PACKED_TOKEN.get(), UUID.randomUUID());
        held(packed);
        item.setItem(ItemStack.EMPTY); item.discard();
        request.player.getInventory().setItem(slots.getFirst(), packed);
        request.player.getInventory().setChanged(); request.player.containerMenu.broadcastChanges();
        finish(request, "recalled");
    }

    private void finish(Request request, String message) {
        cancel(request.player.getUUID()); request.player.stopUsingItem(); tell(request.player, message);
    }
    private static boolean fail(ServerPlayer player, String message) { tell(player, message); return false; }
    private static void tell(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("vanillawheels.key." + key, args), true);
    }
}
