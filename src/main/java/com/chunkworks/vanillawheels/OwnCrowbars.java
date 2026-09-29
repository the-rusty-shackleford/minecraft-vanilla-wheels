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

import com.chunkworks.carried.api.Carried;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Every vehicle's own crowbar (Rusty, 2026-09-28; D-0023). A vehicle is born
 * with a crowbar marked with its id, kept in a slot of its own (its toolbox).
 * That crowbar is only ever in its slot or on a person: their inventory, or a
 * bag Carried says they carry. Dropped, lost or put down anywhere else, it
 * goes home: into its vehicle's slot if the vehicle is loaded, or into this
 * record until the vehicle next loads or is placed. Spare crowbars carry no
 * mark and none of this.
 *
 * <p>AF: {@code pending.get(v)} is the crowbar waiting for vehicle {@code v};
 * {@code loaded} maps each vehicle in a loaded level, by its id, to itself
 * (never saved); {@code watch} holds the blocks right-clicked this tick by a
 * player holding an own crowbar. RI: every pending stack is an own crowbar of
 * its key, one per vehicle. Server thread only.
 */
public final class OwnCrowbars extends SavedData {
    /** The mark on an own crowbar: its vehicle's id and profile (the profile names it in the tooltip). */
    public record Owner(UUID vehicle, ResourceLocation profile) {
        public static final Codec<Owner> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("vehicle").forGetter(Owner::vehicle),
                ResourceLocation.CODEC.fieldOf("profile").forGetter(Owner::profile)).apply(i, Owner::new));
        public static final StreamCodec<ByteBuf, Owner> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Owner::vehicle, ResourceLocation.STREAM_CODEC, Owner::profile, Owner::new);
    }

    private static final Factory<OwnCrowbars> FACTORY = new Factory<>(OwnCrowbars::new, OwnCrowbars::load);
    private final Map<UUID, ItemStack> pending = new HashMap<>();
    private final Map<UUID, Vehicle> loaded = new HashMap<>();
    private final List<Watch> watch = new ArrayList<>();

    /** A block right-clicked by a player holding an own crowbar: checked once the click is over. */
    private record Watch(ServerPlayer player, UUID vehicle, ServerLevel level, BlockPos pos) {}

    /** requires: server thread; effects: returns the world's record, made on first use */
    public static OwnCrowbars get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "vanillawheels_own_crowbars");
    }

    // --- the mark ---------------------------------------------------------

    /** effects: returns whether {@code stack} is some vehicle's own crowbar */
    public static boolean isOwn(ItemStack stack) {
        return stack.has(ModContent.CROWBAR_OF.get());
    }

    /** effects: returns whether {@code stack} is the own crowbar of vehicle {@code vehicle} */
    public static boolean belongsTo(ItemStack stack, @Nullable UUID vehicle) {
        Owner o = stack.get(ModContent.CROWBAR_OF.get());
        return o != null && vehicle != null && o.vehicle().equals(vehicle);
    }

    /** effects: returns a new crowbar marked as vehicle {@code vehicle}'s, a {@code profile} */
    public static ItemStack mint(UUID vehicle, ResourceLocation profile) {
        ItemStack s = new ItemStack(ModContent.CROWBAR.get());
        s.set(ModContent.CROWBAR_OF.get(), new Owner(vehicle, profile));
        return s;
    }

    // --- going home ---------------------------------------------------------

    /**
     * requires: {@code stack} is an own crowbar, taken out of wherever it was
     * effects: puts it in its vehicle's slot if that vehicle is loaded, else
     * keeps it for the vehicle; a second copy for a vehicle that already has one
     * (a creative clone) is discarded
     */
    public static void sendHome(MinecraftServer server, ItemStack stack) {
        Owner o = stack.get(ModContent.CROWBAR_OF.get());
        if (o == null || stack.isEmpty()) {
            return;
        }
        OwnCrowbars data = get(server);
        Vehicle v = data.loaded.get(o.vehicle());
        if (v != null && !v.isRemoved()) {
            if (v.ownCrowbar().isEmpty()) {
                v.setOwnCrowbar(stack.copyWithCount(1));
            }
            return;
        }
        if (!data.pending.containsKey(o.vehicle())) {
            data.pending.put(o.vehicle(), stack.copyWithCount(1));
            data.setDirty();
        }
    }

    /** requires: server thread, {@code v} has its id; effects: indexes {@code v} and hands it any crowbar waiting for it */
    void arrived(Vehicle v) {
        loaded.put(v.vehicleId(), v);
        ItemStack waiting = pending.remove(v.vehicleId());
        if (waiting != null) {
            setDirty();
            if (v.ownCrowbar().isEmpty()) {
                v.setOwnCrowbar(waiting);
            }
        }
    }

    /** effects: forgets {@code v} as loaded, if it is the one indexed */
    void left(Vehicle v) {
        if (v.vehicleId() != null) {
            loaded.remove(v.vehicleId(), v);
        }
    }

    // --- where it is caught -------------------------------------------------

    /** effects: an own crowbar about to lie on the ground -- tossed, dropped at a death, spilled -- goes home instead */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ItemEntity drop && isOwn(drop.getItem())) {
            event.setCanceled(true);
            sendHome(level.getServer(), drop.getItem());
        }
    }

    /** effects: watches every menu a player opens, so an own crowbar put anywhere in it but their own inventory or a bag they carry goes home */
    public static void onOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.getContainer().addSlotListener(new Guard(player));
        }
    }

    /**
     * effects: a last look as a menu closes, after the game has handed back what its passing slots
     * held (a crafting grid, an anvil): what is left in a keeping container -- an ender chest, a
     * horse's pack -- goes home
     */
    public static void onClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbstractContainerMenu menu = event.getContainer();
            for (int i = 0; i < menu.slots.size(); i++) {
                check(player, menu, i, menu.getSlot(i).getItem(), true);
            }
        }
    }

    /** One player's menu, watched for an own crowbar landing where it may not stay. */
    private record Guard(ServerPlayer player) implements ContainerListener {
        @Override
        public void slotChanged(AbstractContainerMenu menu, int index, ItemStack stack) {
            check(player, menu, index, stack, false);
        }

        @Override
        public void dataChanged(AbstractContainerMenu menu, int index, int value) {}
    }

    /**
     * effects: if slot {@code index} of {@code menu} holds an own crowbar where it may not stay, takes
     * it out and sends it home. While the menu is open only a container that keeps things in the
     * world is acted on at once -- a block's, an entity's, a double chest -- before a hopper can
     * pull from it; a passing slot (a crafting grid, an anvil) is left for the game to hand back,
     * and the rest are looked at on {@code closing}.
     */
    private static void check(ServerPlayer player, AbstractContainerMenu menu, int index, ItemStack stack, boolean closing) {
        if (!isOwn(stack) || index < 0 || index >= menu.slots.size()) {
            return;
        }
        Slot slot = menu.getSlot(index);
        if (slot.container instanceof Inventory inventory && inventory.player == player) {
            return;   // on the person
        }
        if (!closing && !(slot.container instanceof net.minecraft.world.level.block.entity.BlockEntity
                || slot.container instanceof net.minecraft.world.entity.Entity
                || slot.container instanceof net.minecraft.world.CompoundContainer)) {
            return;   // looked at when the menu closes
        }
        if (menu instanceof ToolboxMenu toolbox && index == ToolboxMenu.SLOT && belongsTo(stack, toolbox.vehicleId())) {
            return;   // home
        }
        Owner o = stack.get(ModContent.CROWBAR_OF.get());
        if (Carried.has(player, s -> belongsTo(s, o.vehicle()))) {
            return;   // in a bag the player carries (Carried writes through: Backpacks+ stores each edit at once)
        }
        ItemStack taken = stack.copy();
        slot.set(ItemStack.EMPTY);
        slot.setChanged();
        sendHome(player.server, taken);
    }

    /** effects: an own crowbar is not handed to anything but a vehicle: an item frame, an armour stand, an allay, a villager's gift */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        refuseHandOver(event, event.getTarget());
    }

    /** effects: as {@link #onEntityInteract}, for the click at a point (an armour stand's) */
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        refuseHandOver(event, event.getTarget());
    }

    private static void refuseHandOver(PlayerInteractEvent event, net.minecraft.world.entity.Entity target) {
        if (!isOwn(event.getItemStack()) || target instanceof Vehicle || target instanceof Vehicle.Part) {
            return;
        }
        if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
    }

    /**
     * effects: a block right-clicked with an own crowbar in hand is looked into once the click is
     * over: a block that took it (a decorated pot, a display shelf) gives it up, and it goes home
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            Owner o = event.getItemStack().get(ModContent.CROWBAR_OF.get());
            if (o != null) {
                get(level.getServer()).watch.add(new Watch(player, o.vehicle(), level, event.getPos().immutable()));
            }
        }
    }

    /** effects: after the tick's clicks, pulls an own crowbar out of any block that took it and sends it home */
    public static void onServerTick(ServerTickEvent.Post event) {
        OwnCrowbars data = get(event.getServer());
        if (data.watch.isEmpty()) {
            return;
        }
        List<Watch> clicked = List.copyOf(data.watch);
        data.watch.clear();
        for (Watch w : clicked) {
            if (Carried.has(w.player(), s -> belongsTo(s, w.vehicle()))) {
                continue;   // still on the person
            }
            ItemStack found = takeFrom(w.level(), w.pos(), w.vehicle());
            if (!found.isEmpty()) {
                sendHome(event.getServer(), found);
            }
        }
    }

    /** effects: removes and returns vehicle {@code vehicle}'s crowbar from the block at {@code pos}, by its container or its item handler; empty if it holds none */
    private static ItemStack takeFrom(ServerLevel level, BlockPos pos, UUID vehicle) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (belongsTo(container.getItem(i), vehicle)) {
                    ItemStack found = container.removeItemNoUpdate(i);
                    container.setChanged();
                    return found;
                }
            }
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                if (belongsTo(handler.getStackInSlot(i), vehicle)) {
                    return handler.extractItem(i, 1, false);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // --- saving -------------------------------------------------------------

    /** requires: valid saved NBT; effects: restores the crowbars waiting for their vehicles; unreadable rows are skipped */
    public static OwnCrowbars load(CompoundTag tag, HolderLookup.Provider registries) {
        OwnCrowbars result = new OwnCrowbars();
        for (Tag row : tag.getList("Pending", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) row;
            if (!value.hasUUID("Vehicle")) {
                continue;
            }
            ItemStack.parse(registries, value.getCompound("Crowbar")).filter(OwnCrowbars::isOwn)
                    .ifPresent(s -> result.pending.put(value.getUUID("Vehicle"), s));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, ItemStack> e : pending.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Vehicle", e.getKey());
            row.put("Crowbar", e.getValue().save(registries));
            list.add(row);
        }
        tag.put("Pending", list);
        return tag;
    }

    /** effects: returns whether a crowbar waits for vehicle {@code vehicle} (for the gametests) */
    public boolean waitingFor(UUID vehicle) {
        return pending.containsKey(vehicle);
    }
}
