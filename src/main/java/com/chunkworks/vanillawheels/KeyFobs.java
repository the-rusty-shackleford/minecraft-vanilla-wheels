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
import net.minecraft.world.entity.player.Player;
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
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Keys that are never lost (Rusty, 2026-09-29; D-0026), and the keyring they live on (D-0041).
 * A key paired as its vehicle's current key is <em>live</em>; so is a ring carrying one. A live
 * stack lives only on its owner: their inventory, or a bag Carried says they carry. Tossed,
 * spilled at a death, dropped inside a bag or a box, put in a chest, handed to a frame or a pot,
 * or found on anyone else, it goes home: onto its owner's ring at once when they are online and
 * alive (a loose key joins the ring; a ring's keys join their ring), else into this record until
 * they log in, respawn or make room. A blank key, one whose pairing has moved on, and a ring with
 * no live key are ordinary items. Every key wears its vehicle's mark: named for it, and banded
 * in its paint.
 *
 * <p>AF: {@code pending} is the stacks -- keys and rings -- waiting for their owners, in the order
 * they went home; {@code watch} holds the blocks right-clicked this tick with a live stack in
 * hand; {@code tossed} the live stacks a player tried to drop this tick. RI: every pending stack is
 * one key or one ring. Server thread only.
 */
public final class KeyFobs extends SavedData {
    private static final Factory<KeyFobs> FACTORY = new Factory<>(KeyFobs::new, KeyFobs::load);
    /** How deep a key is looked for inside bags, boxes and bundles within one another. */
    private static final int NESTING = 4;
    private final List<ItemStack> pending = new ArrayList<>();
    private final List<Watch> watch = new ArrayList<>();
    private final List<Tossed> tossed = new ArrayList<>();

    /** A block right-clicked by a player holding a live key: checked once the click is over. */
    private record Watch(ServerPlayer player, UUID token, ServerLevel level, BlockPos pos) {}

    /** A live stack a player tried to toss: the toss refused, the stack is looked for on them once the tick is over. */
    private record Tossed(ServerPlayer player, ItemStack stack) {}

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

    /**
     * effects: the token that makes {@code stack} live -- a key paired as its vehicle's current key,
     * or the first such key on a ring -- else null
     */
    @Nullable
    static UUID liveToken(MinecraftServer server, ItemStack stack) {
        RecoveryData data = RecoveryData.get(server);
        if (stack.is(ModContent.KEY_FOB.get())) {
            UUID token = stack.get(ModContent.KEY_TOKEN.get());
            return data.isCurrent(token) ? token : null;
        }
        for (ItemStack key : Keyring.keys(stack)) {
            UUID token = key.get(ModContent.KEY_TOKEN.get());
            if (data.isCurrent(token)) {
                return token;
            }
        }
        return null;
    }

    /** effects: whether {@code stack} is live: a current key, or a ring carrying one */
    static boolean isLive(MinecraftServer server, ItemStack stack) {
        return liveToken(server, stack) != null;
    }

    /** effects: whether {@code stack} is the key with {@code token}, or a ring carrying it */
    static boolean holdsToken(ItemStack stack, UUID token) {
        return stack.is(ModContent.KEY_FOB.get()) ? token.equals(stack.get(ModContent.KEY_TOKEN.get())) : Keyring.holds(stack, token);
    }

    /** effects: whose {@code stack} is: a key's owner, or a ring's; null for neither */
    @Nullable
    static UUID ownerOf(ItemStack stack) {
        return Keyring.isRing(stack) ? Keyring.ownerOf(stack) : stack.get(ModContent.KEY_OWNER.get());
    }

    /**
     * effects: whether {@code player} keeps {@code stack} against the drop key (D-0041): on the
     * server a live key or ring; on a client, which cannot tell a live key from one whose pairing
     * moved on, any paired key and any ring with keys on it -- the server's word settles it
     */
    public static boolean keeps(Player player, ItemStack stack) {
        if (player.level() instanceof ServerLevel level) {
            return isLive(level.getServer(), stack);
        }
        return stack.is(ModContent.KEY_FOB.get()) ? stack.has(ModContent.KEY_TOKEN.get()) : !Keyring.keys(stack).isEmpty();
    }

    /** effects: whether the key with {@code token} is on {@code player}: loose or on a ring, in their inventory or a bag they carry */
    public static boolean onPerson(ServerPlayer player, UUID token) {
        return Carried.has(player, s -> holdsToken(s, token));
    }

    // --- going home ---------------------------------------------------------

    /**
     * requires: {@code stack} is a key or a ring, taken out of wherever it was
     * effects: gives it to its owner -- onto their ring -- when they are online and alive, telling
     * them; what cannot be placed, or waits for an absent or dead owner, is kept here
     */
    public static void sendHome(MinecraftServer server, ItemStack stack) {
        UUID owner = ownerOf(stack);
        if (owner == null || stack.isEmpty() || (!stack.is(ModContent.KEY_FOB.get()) && !Keyring.isRing(stack))) {
            return;
        }
        ItemStack home = stack.copyWithCount(1);
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null && give(p, home)) {
            return;
        }
        KeyFobs data = get(server);
        data.pending.add(home);
        data.setDirty();
    }

    /**
     * effects: puts {@code stack} on {@code player} if they are alive and it can be placed -- a key
     * onto their ring, a ring's keys onto their ring -- telling them; returns whether it was
     */
    private static boolean give(ServerPlayer player, ItemStack stack) {
        if (!player.isAlive() || player.hasDisconnected()) {
            return false;
        }
        Component name = stack.getHoverName();
        boolean placed = Keyring.isRing(stack) ? Keyring.absorb(player, stack) : Keyring.put(player, stack);
        if (!placed) {
            return false;
        }
        player.displayClientMessage(Component.translatable("vanillawheels.key.home", name), true);
        return true;
    }

    /** effects: hands {@code player} every key and ring waiting for them that can be placed, and gathers any loose key onto their ring */
    void deliver(ServerPlayer player) {
        Keyring.gather(player);
        if (pending.isEmpty()) {
            return;
        }
        for (Iterator<ItemStack> it = pending.iterator(); it.hasNext(); ) {
            ItemStack stack = it.next();
            if (player.getUUID().equals(ownerOf(stack)) && give(player, stack)) {
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

    /** effects: {@link #pullNested} over {@code cells}, emptying each key or ring taken and rewriting each container changed; returns whether any was */
    private static boolean pullFrom(MinecraftServer server, List<ItemStack> cells, Predicate<ItemStack> away, List<ItemStack> out, int depth) {
        boolean changed = false;
        for (int i = 0; i < cells.size(); i++) {
            ItemStack cell = cells.get(i);
            if (isLive(server, cell) && away.test(cell)) {
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

    /** effects: whether {@code stack} could hold a key: it is one, or a ring, or it has contents to look in */
    private static boolean mayHoldKey(ItemStack stack) {
        return stack.is(ModContent.KEY_FOB.get()) || Keyring.isRing(stack) || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS);
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
        if (isLive(server, drop.getItem())) {
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
     * effects: a player's toss of a live key or ring (the drop key, a throw out of a screen) is
     * refused; the stack is looked for on them once the tick is over and sent home if the game
     * took it out all the same
     */
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isLive(player.server, event.getEntity().getItem())) {
            event.setCanceled(true);
            get(player.server).tossed.add(new Tossed(player, event.getEntity().getItem().copy()));
        }
    }

    /**
     * effects: at a player's death, before anything drops -- to the ground or into another mod's
     * grave -- every live key and ring on them, bags included, goes home: theirs wait for their
     * respawn. With keepInventory, nothing moves.
     */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        MinecraftServer server = player.server;
        Predicate<ItemStack> live = s -> isLive(server, s);
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
        MinecraftServer server = player.server;
        Predicate<ItemStack> away = key -> {
            if (!player.getUUID().equals(ownerOf(key))) {
                return true;   // not this player's: home to its owner
            }
            // Its owner's: on their person, or in a bag they carry (Carried writes through: Backpacks+ stores each edit at once).
            UUID token = liveToken(server, key);
            return !ownInventory && (token == null || !onPerson(player, token));
        };
        if (isLive(server, stack)) {
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
        if (target instanceof Vehicle || target instanceof Vehicle.Part || (!held.is(ModContent.KEY_FOB.get()) && !Keyring.isRing(held))) {
            return;
        }
        if (keeps(event.getEntity(), held) && event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
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

    /**
     * effects: after the tick's clicks, pulls a key out of any block that took it and sends it home,
     * and sends home a live stack the game took out of a player who tried to toss it; once a
     * second, hands waiting keys and rings to owners who are here
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        KeyFobs data = get(server);
        if (!data.tossed.isEmpty()) {
            List<Tossed> dropped = List.copyOf(data.tossed);
            data.tossed.clear();
            for (Tossed t : dropped) {
                UUID token = liveToken(server, t.stack());
                if (token != null && !onPerson(t.player(), token)) {
                    sendHome(server, t.stack());
                }
            }
        }
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

    /** effects: removes and returns the key with {@code token}, or the ring carrying it, from the block at {@code pos}, by its container or its item handler; empty if it holds none */
    private static ItemStack takeFrom(ServerLevel level, BlockPos pos, UUID token) {
        Predicate<ItemStack> it = s -> holdsToken(s, token);
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

    /** requires: valid saved NBT; effects: restores the keys and rings waiting for their owners; unreadable rows are skipped */
    public static KeyFobs load(CompoundTag tag, HolderLookup.Provider registries) {
        KeyFobs result = new KeyFobs();
        for (Tag row : tag.getList("Pending", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) row;
            ItemStack.parse(registries, value.getCompound("Key"))
                    .filter(s -> s.is(ModContent.KEY_FOB.get()) && s.has(ModContent.KEY_TOKEN.get()) || Keyring.isRing(s))
                    .ifPresent(result.pending::add);
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack stack : pending) {
            CompoundTag row = new CompoundTag();
            row.put("Key", stack.save(registries));
            list.add(row);
        }
        tag.put("Pending", list);
        return tag;
    }

    /** effects: returns whether the key with {@code token} waits for its owner, loose or on a ring */
    public boolean waitingFor(UUID token) {
        for (ItemStack stack : pending) {
            if (holdsToken(stack, token)) {
                return true;
            }
        }
        return false;
    }
}
