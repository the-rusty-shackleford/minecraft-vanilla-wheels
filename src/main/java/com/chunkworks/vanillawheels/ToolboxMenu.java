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

import java.util.UUID;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A vehicle's toolbox (D-0023): one slot for the vehicle's own crowbar, over
 * the player's inventory. The slot takes that vehicle's own crowbar and
 * nothing else. On the server the slot is the vehicle's; the client's copy
 * learns the vehicle's id from four data slots (its 128 bits) and shows what
 * the server syncs -- a plain menu, opened by the game's own packet, as the
 * lift's is. An open toolbox holds the vehicle as an open chest does: it
 * cannot be pried loose meanwhile.
 */
public final class ToolboxMenu extends AbstractContainerMenu {
    /** The crowbar's slot; the player's inventory follows. */
    public static final int SLOT = 0;
    private static final int INVENTORY_TOP = 51;
    private static final int HOTBAR_TOP = 109;

    @Nullable private final Vehicle vehicle;
    /** The vehicle's id in four words, most significant first: set from the vehicle on the server, synced to the client. */
    private final int[] idWords = new int[4];

    /** requires: server side; effects: opens {@code vehicle}'s toolbox and counts the player in */
    public ToolboxMenu(int id, Inventory inventory, Vehicle vehicle) {
        this(id, inventory, vehicle, new Box(vehicle));
        UUID vid = vehicle.vehicleId();
        if (vid != null) {
            long hi = vid.getMostSignificantBits(), lo = vid.getLeastSignificantBits();
            idWords[0] = (int) (hi >>> 32); idWords[1] = (int) hi; idWords[2] = (int) (lo >>> 32); idWords[3] = (int) lo;
        }
        vehicle.toolboxOpened(true);
    }

    /** effects: the client's copy: an empty slot and a zero id until the server syncs them */
    public static ToolboxMenu client(int id, Inventory inventory) {
        return new ToolboxMenu(id, inventory, null, new SimpleContainer(1));
    }

    private ToolboxMenu(int id, Inventory inventory, @Nullable Vehicle vehicle, Container box) {
        super(ModContent.TOOLBOX_MENU.get(), id);
        this.vehicle = vehicle;
        for (int w = 0; w < 4; w++) {
            final int word = w;
            addDataSlot(new DataSlot() {
                @Override public int get() { return idWords[word]; }
                @Override public void set(int value) { idWords[word] = value; }
            });
        }
        addSlot(new Slot(box, 0, 80, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return OwnCrowbars.belongsTo(stack, vehicleId());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_TOP));
        }
    }

    /** effects: returns the id of the vehicle this toolbox belongs to; null on a client the server has not yet told */
    @Nullable
    public UUID vehicleId() {
        if (idWords[0] == 0 && idWords[1] == 0 && idWords[2] == 0 && idWords[3] == 0) {
            return null;
        }
        return new UUID((long) idWords[0] << 32 | (idWords[1] & 0xFFFFFFFFL), (long) idWords[2] << 32 | (idWords[3] & 0xFFFFFFFFL));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        if (index == SLOT) {
            if (!moveItemStackTo(stack, SLOT + 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!slots.get(SLOT).mayPlace(stack) || !moveItemStackTo(stack, SLOT, SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }

    /** effects: open while the vehicle stands within eight blocks; the client's copy defers to the server */
    @Override
    public boolean stillValid(Player player) {
        return vehicle == null ? player.level().isClientSide() : vehicle.isAlive() && player.distanceToSqr(vehicle) < 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (vehicle != null && !player.level().isClientSide()) {
            vehicle.toolboxOpened(false);
        }
    }

    /** The vehicle's one slot, as a container: what it holds is the vehicle's own crowbar field. */
    private record Box(Vehicle vehicle) implements Container {
        @Override public int getContainerSize() { return 1; }
        @Override public boolean isEmpty() { return vehicle.ownCrowbar().isEmpty(); }
        @Override public ItemStack getItem(int slot) { return slot == 0 ? vehicle.ownCrowbar() : ItemStack.EMPTY; }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack held = vehicle.ownCrowbar();
            if (slot != 0 || count <= 0 || held.isEmpty()) {
                return ItemStack.EMPTY;
            }
            vehicle.setOwnCrowbar(ItemStack.EMPTY);
            return held;
        }

        @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, 1); }
        @Override public void setItem(int slot, ItemStack stack) { if (slot == 0) vehicle.setOwnCrowbar(stack); }
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return vehicle.isAlive(); }
        @Override public void clearContent() { vehicle.setOwnCrowbar(ItemStack.EMPTY); }
    }
}
