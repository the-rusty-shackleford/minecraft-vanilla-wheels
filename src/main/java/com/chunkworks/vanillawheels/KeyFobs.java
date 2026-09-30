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
import com.chunkworks.vanillawheels.api.VehicleProfile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Keys that are never lost (Rusty, 2026-09-29; D-0026). A key paired as its vehicle's current key
 * lives only on its owner: their inventory, or a bag Carried says they carry. Tossed, spilled at a
 * death, dropped inside a bag or a box, put in a chest, handed to a frame or a pot, or found on
 * anyone else, it goes home: into its owner's inventory or bag at once when they are online,
 * alive and have room, else into this record until they log in, respawn or make room. A blank
 * key, or one whose pairing has moved on, is an ordinary item. Every key wears its vehicle's
 * mark: named for it, and banded in its paint.
 *
 * <p>AF: {@code pending.get(t)} is the key with token {@code t} waiting for its owner, in the
 * order they went home; {@code watch} holds the blocks right-clicked this tick with a live key in
 * hand. RI: every pending stack is one key, one per token. Server thread only.
 */
public final class KeyFobs extends SavedData {
    private static final Factory<KeyFobs> FACTORY = new Factory<>(KeyFobs::new, KeyFobs::load);
    /** How deep a key is looked for inside bags, boxes and bundles within one another. */
    private static final int NESTING = 4;
    private final Map<UUID, ItemStack> pending = new LinkedHashMap<>();
    private final List<Watch> watch = new ArrayList<>();

    /** A block right-clicked by a player holding a live key: checked once the click is over. */
    private record Watch(ServerPlayer player, UUID token, ServerLevel level, BlockPos pos) {}

    /** requires: server thread; effects: returns the world's record, made on first use */
    public static KeyFobs get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "vanillawheels_key_fobs");
    }

    // --- the mark -----------------------------------------------------------

    /** A key's mark: its vehicle's name and paint (an RGB colour), as the key shows them. */
    public record Mark(Component name, int colour) {
        /** No mark yet: a pairing made before 1.10.0, until its key next meets its vehicle. */
        public static final Mark NONE = new Mark(Component.empty(), 0);

        /** effects: the mark of {@code vehicle} as it is now */
        public static Mark of(Vehicle vehicle) {
            VehicleProfile p = vehicle.profile();
            return new Mark(vehicle.getName(), (p == null ? 0xFFFFFF : Vehicle.colourOf(vehicle.paint(), p)) & 0xFFFFFF);
        }

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", Component.Serializer.toJson(name, registries));
            tag.putInt("Colour", colour);
            return tag;
        }

        static Mark load(CompoundTag tag, HolderLookup.Provider registries) {
            Component name = Component.Serializer.fromJson(tag.getString("Name"), registries);
            return name == null ? NONE : new Mark(name, tag.getInt("Colour") & 0xFFFFFF);
        }
    }

    /** effects: names {@code key} for its vehicle and bands it in its paint; NONE takes the mark off */
    public static void mark(ItemStack key, Mark mark) {
        if (!key.is(ModContent.KEY_FOB.get())) {
            return;
        }
        if (Mark.NONE.equals(mark)) {
            key.remove(DataComponents.ITEM_NAME);
            key.remove(ModContent.KEY_COLOUR.get());
            return;
        }
        key.set(DataComponents.ITEM_NAME, Component.translatable("item.vanillawheels.key_fob.of", mark.name()));
        key.set(ModContent.KEY_COLOUR.get(), mark.colour());
    }

    /** effects: the token of {@code stack} if it is a key paired as its vehicle's current key, else null */
    @Nullable
    static UUID liveToken(MinecraftServer server, ItemStack stack) {
        if (!stack.is(ModContent.KEY_FOB.get())) {
            return null;
        }
        UUID token = stack.get(ModContent.KEY_TOKEN.get());
        return RecoveryData.get(server).isCurrent(token) ? token : null;
    }

    /** effects: whether the key with {@code token} is on {@code player}: their inventory or a bag they carry */
    public static boolean onPerson(ServerPlayer player, UUID token) {
        return Carried.has(player, s -> s.is(ModContent.KEY_FOB.get()) && token.equals(s.get(ModContent.KEY_TOKEN.get())));
    }

    // --- going home ---------------------------------------------------------

    /**
     * requires: {@code stack} is a key, taken out of wherever it was
     * effects: gives it to its owner -- inventory, else a bag they carry -- when they are online and
     * alive, telling them; what does not fit, or waits for an absent or dead owner, is kept here
     */
    public static void sendHome(MinecraftServer server, ItemStack stack) {
        UUID token = stack.get(ModContent.KEY_TOKEN.get());
        UUID owner = stack.get(ModContent.KEY_OWNER.get());
        if (token == null || owner == null || stack.isEmpty()) {
            return;
        }
        ItemStack key = stack.copyWithCount(1);
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null && give(p, key)) {
            return;
        }
        KeyFobs data = get(server);
        if (data.pending.putIfAbsent(token, key) == null) {
            data.setDirty();
        }
    }

    /** effects: puts {@code key} on {@code player} if they are alive and it fits, telling them; returns whether it did */
    private static boolean give(ServerPlayer player, ItemStack key) {
        if (!player.isAlive() || player.hasDisconnected()) {
            return false;
        }
        Component name = key.getHoverName();
        ItemStack placing = key.copy();
        Carried.give(player, placing);
        if (!placing.isEmpty()) {
            return false;
        }
        player.displayClientMessage(Component.translatable("vanillawheels.key.home", name), true);
        return true;
    }

    /** effects: hands {@code player} every key waiting for them that fits */
    void deliver(ServerPlayer player) {
        if (pending.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, ItemStack>> it = pending.entrySet().iterator(); it.hasNext(); ) {
            ItemStack key = it.next().getValue();
            if (player.getUUID().equals(key.get(ModContent.KEY_OWNER.get())) && give(player, key)) {
                it.remove();
                setDirty();
            }
        }
    }

    /**
     * effects: takes out of {@code stack}'s contents -- a bag's, a box's, a bundle's, and theirs in
     * turn, {@link #NESTING} deep -- every live key {@code away} says may not stay there, adding
     * them to {@code out}; returns the stack as it is without them, or {@code stack} itself when
     * none was taken
     */
    private static ItemStack pullNested(MinecraftServer server, ItemStack stack, Predicate<ItemStack> away, List<ItemStack> out, int depth) {
        if (depth >= NESTING || stack.isEmpty()) {
            return stack;
        }
        ItemStack result = stack;
        ItemContainerContents box = stack.get(DataComponents.CONTAINER);
        if (box != null) {
            List<ItemStack> cells = new ArrayList<>(box.stream().toList());
            if (pullFrom(server, cells, away, out, depth)) {
                result = result.copy();
                result.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(cells));
            }
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            List<ItemStack> cells = new ArrayList<>();
            bundle.itemsCopy().forEach(cells::add);
            if (pullFrom(server, cells, away, out, depth)) {
                cells.removeIf(ItemStack::isEmpty);
                result = result == stack ? result.copy() : result;
                result.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(cells));
            }
        }
        return result;
    }

    /** effects: {@link #pullNested} over {@code cells}, emptying each key taken and rewriting each container changed; returns whether any was */
    private static boolean pullFrom(MinecraftServer server, List<ItemStack> cells, Predicate<ItemStack> away, List<ItemStack> out, int depth) {
        boolean changed = false;
        for (int i = 0; i < cells.size(); i++) {
            ItemStack cell = cells.get(i);
            if (liveToken(server, cell) != null && away.test(cell)) {
                out.add(cell.copy());
                cells.set(i, ItemStack.EMPTY);
                changed = true;
            } else {
                ItemStack without = pullNested(server, cell, away, out, depth + 1);
                if (without != cell) {
                    cells.set(i, without);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** effects: whether {@code stack} could hold a key: it is one, or it has contents to look in */
    private static boolean mayHoldKey(ItemStack stack) {
        return stack.is(ModContent.KEY_FOB.get()) || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    // --- where they are caught ------------------------------------------------

    /**
     * effects: a key about to lie on the ground -- tossed, dropped at a death, spilled -- goes home
     * instead; one inside a bag, box or bundle on the ground is taken out of it and goes home, and
     * the bag lies there without it
     */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ItemEntity drop) || !mayHoldKey(drop.getItem())) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (liveToken(server, drop.getItem()) != null) {
            event.setCanceled(true);
            sendHome(server, drop.getItem());
            return;
        }
        List<ItemStack> keys = new ArrayList<>();
        ItemStack without = pullNested(server, drop.getItem(), k -> true, keys, 0);
        if (!keys.isEmpty()) {
            drop.setItem(without);
            keys.forEach(k -> sendHome(server, k));
        }
    }

    /**
     * effects: at a player's death, before anything drops -- to the ground or into another mod's
     * grave -- every live key on them, bags included, goes home: theirs wait for their respawn.
     * With keepInventory, nothing moves.
     */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        MinecraftServer server = player.server;
        Predicate<ItemStack> live = s -> liveToken(server, s) != null;
        int n = Carried.count(player, live);
        if (n > 0) {
            List<ItemStack> keys = new ArrayList<>();
            Carried.take(player, live, n, taken -> keys.add(taken.copy()));
            keys.forEach(k -> sendHome(server, k));
        }
    }

    /** effects: hands a player who logs in the keys that waited for them */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            get(player.server).deliver(player);
        }
    }

    /** effects: hands a player who respawns the keys that waited for them since their death */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            get(player.server).deliver(player);
        }
    }

    /** effects: watches every menu a player opens, so a key put anywhere in it but its owner's person goes home */
    public static void onOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.getContainer().addSlotListener(new Guard(player));
        }
    }

    /**
     * effects: a last look as a menu closes, after the game has handed back what its passing slots
     * held (a crafting grid, an anvil): a key left in a keeping container -- an ender chest, a
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

    /** One player's menu, watched for a key landing where it may not stay. */
    private record Guard(ServerPlayer player) implements ContainerListener {
        @Override
        public void slotChanged(AbstractContainerMenu menu, int index, ItemStack stack) {
            check(player, menu, index, stack, false);
        }

        @Override
        public void dataChanged(AbstractContainerMenu menu, int index, int value) {}
    }

    /**
     * effects: takes out of slot {@code index} of {@code menu} every live key -- the stack itself or
     * inside it -- that may not stay there, and sends it home. A key may stay in its owner's own
     * inventory, or in a bag its owner carries; in anyone else's inventory it goes at once. While the
     * menu is open only a container that keeps things in the world is acted on -- a block's, an
     * entity's, a double chest -- before a hopper can pull from it; a passing slot is left for the
     * game to hand back, and the rest are looked at on {@code closing}.
     */
    private static void check(ServerPlayer player, AbstractContainerMenu menu, int index, ItemStack stack, boolean closing) {
        if (index < 0 || index >= menu.slots.size() || !mayHoldKey(stack)) {
            return;
        }
        Slot slot = menu.getSlot(index);
        boolean ownInventory = slot.container instanceof Inventory inventory && inventory.player == player;
        if (!ownInventory && !closing && !(slot.container instanceof net.minecraft.world.level.block.entity.BlockEntity
                || slot.container instanceof net.minecraft.world.entity.Entity
                || slot.container instanceof net.minecraft.world.CompoundContainer)) {
            return;   // looked at when the menu closes
        }
        Predicate<ItemStack> away = key -> {
            if (!player.getUUID().equals(key.get(ModContent.KEY_OWNER.get()))) {
                return true;   // not this player's: home to its owner
            }
            // Its owner's: on their person, or in a bag they carry (Carried writes through: Backpacks+ stores each edit at once).
            return !ownInventory && !onPerson(player, key.get(ModContent.KEY_TOKEN.get()));
        };
        MinecraftServer server = player.server;
        if (liveToken(server, stack) != null) {
            if (away.test(stack)) {
                ItemStack taken = stack.copy();
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
                sendHome(server, taken);
            }
            return;
        }
        List<ItemStack> keys = new ArrayList<>();
        ItemStack without = pullNested(server, stack, away, keys, 0);
        if (!keys.isEmpty()) {
            slot.set(without);
            slot.setChanged();
            keys.forEach(k -> sendHome(server, k));
        }
    }

    /** effects: a key is not handed to anything but a vehicle: an item frame, an armour stand, an allay, a villager's gift */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        refuseHandOver(event, event.getTarget());
    }

    /** effects: as {@link #onEntityInteract}, for the click at a point (an armour stand's) */
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        refuseHandOver(event, event.getTarget());
    }

    private static void refuseHandOver(PlayerInteractEvent event, net.minecraft.world.entity.Entity target) {
        ItemStack held = event.getItemStack();
        if (target instanceof Vehicle || target instanceof Vehicle.Part || !held.is(ModContent.KEY_FOB.get())) {
            return;
        }
        // The client cannot tell a live key from one whose pairing moved on: it holds back any paired one.
        boolean live = event.getLevel() instanceof ServerLevel level ? liveToken(level.getServer(), held) != null : held.has(ModContent.KEY_TOKEN.get());
        if (live && event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
    }

    /**
     * effects: a block right-clicked with a live key in hand is looked into once the click is over:
     * a block that took it (a decorated pot, a display shelf) gives it up, and it goes home
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            UUID token = liveToken(level.getServer(), event.getItemStack());
            if (token != null) {
                get(level.getServer()).watch.add(new Watch(player, token, level, event.getPos().immutable()));
            }
        }
    }

    /** effects: after the tick's clicks, pulls a key out of any block that took it and sends it home; once a second, hands waiting keys to owners who are here */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        KeyFobs data = get(server);
        if (!data.watch.isEmpty()) {
            List<Watch> clicked = List.copyOf(data.watch);
            data.watch.clear();
            for (Watch w : clicked) {
                if (onPerson(w.player(), w.token())) {
                    continue;   // still on the person
                }
                ItemStack found = takeFrom(w.level(), w.pos(), w.token());
                if (!found.isEmpty()) {
                    sendHome(server, found);
                }
            }
        }
        if (!data.pending.isEmpty() && server.getTickCount() % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                data.deliver(player);
            }
        }
    }

    /** effects: removes and returns the key with {@code token} from the block at {@code pos}, by its container or its item handler; empty if it holds none */
    private static ItemStack takeFrom(ServerLevel level, BlockPos pos, UUID token) {
        Predicate<ItemStack> it = s -> s.is(ModContent.KEY_FOB.get()) && token.equals(s.get(ModContent.KEY_TOKEN.get()));
        if (level.getBlockEntity(pos) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (it.test(container.getItem(i))) {
                    ItemStack found = container.removeItemNoUpdate(i);
                    container.setChanged();
                    return found;
                }
            }
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                if (it.test(handler.getStackInSlot(i))) {
                    return handler.extractItem(i, 1, false);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // --- saving -------------------------------------------------------------

    /** requires: valid saved NBT; effects: restores the keys waiting for their owners; unreadable rows are skipped */
    public static KeyFobs load(CompoundTag tag, HolderLookup.Provider registries) {
        KeyFobs result = new KeyFobs();
        for (Tag row : tag.getList("Pending", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) row;
            ItemStack.parse(registries, value.getCompound("Key"))
                    .filter(s -> s.is(ModContent.KEY_FOB.get()) && s.has(ModContent.KEY_TOKEN.get()))
                    .ifPresent(s -> result.pending.put(s.get(ModContent.KEY_TOKEN.get()), s));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack key : pending.values()) {
            CompoundTag row = new CompoundTag();
            row.put("Key", key.save(registries));
            list.add(row);
        }
        tag.put("Pending", list);
        return tag;
    }

    /** effects: returns whether the key with {@code token} waits for its owner */
    public boolean waitingFor(UUID token) {
        return pending.containsKey(token);
    }
}
