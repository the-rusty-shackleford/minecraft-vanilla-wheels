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
package com.chunkworks.vanillawheels.lift;

import com.chunkworks.vanillawheels.ChassisItem;
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Assembly;
import com.chunkworks.vanillawheels.domain.LiftStatus;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;

/**
 * The lift's menu: four slots -- a chassis, wheels, an engine, a dye --
 * over the player's inventory, and two buttons. The server decides what
 * each button may do every tick and sends the answer down three data
 * slots, so the screen greys a button with the server's truth and no
 * round trip; a click is refused server-side by the same rule. The client's
 * copy holds no position at all. The slots are transient: closing the menu
 * hands the parts back.
 *
 * <p>Build wants exactly the vehicle's own parts ({@link Assembly}); Paint
 * wants a vehicle on the deck and a dye, and only ever paints an entity
 * found there, so a chassis in the slot cannot be painted by construction.
 */
public final class LiftMenu extends AbstractContainerMenu {
    public static final int CHASSIS = 0;
    public static final int WHEELS = 1;
    public static final int ENGINE = 2;
    public static final int DYE = 3;
    private static final int PARTS = 4;
    public static final int BUILD_BUTTON = 0;
    public static final int PAINT_BUTTON = 1;

    private final SimpleContainer parts = new SimpleContainer(PARTS);
    private final ContainerLevelAccess access;
    private final ContainerData data = new SimpleContainerData(3);

    public LiftMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModContent.LIFT_MENU.get(), id);
        this.access = access;
        addSlot(new Slot(parts, CHASSIS, 26, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ChassisItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(parts, WHEELS, 62, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModContent.WHEEL.get());
            }
        });
        addSlot(new Slot(parts, ENGINE, 98, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModContent.ENGINE.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(parts, DYE, 134, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DyeItem;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 160));
        }
        addDataSlots(data);
    }

    // --- what the client reads --------------------------------------------

    public LiftStatus.Build buildStatus() {
        return LiftStatus.Build.values()[Math.min(3, Math.max(0, data.get(0)))];
    }

    public LiftStatus.Paint paintStatus() {
        return LiftStatus.Paint.values()[Math.min(3, Math.max(0, data.get(1)))];
    }

    public int jobTicks() {
        return data.get(2);
    }

    // --- the server's truth -----------------------------------------------

    @Nullable
    private LiftBlockEntity lift() {
        return access.evaluate((level, pos) -> level.getBlockEntity(pos) instanceof LiftBlockEntity l ? l : null).orElse(null);
    }

    /** effects: returns the vehicle the chassis in the slot names, resolved in this level, or null */
    @Nullable
    private ResourceLocation chassisVehicle() {
        ItemStack chassis = parts.getItem(CHASSIS);
        return chassis.getItem() instanceof ChassisItem ? VanillaWheels.vehicleOf(chassis).orElse(null) : null;
    }

    private LiftStatus.Build build(LiftBlockEntity lift) {
        ResourceLocation id = chassisVehicle();
        VehicleProfile p = id == null ? null : lift.profile(id);
        boolean matches = p != null && new Assembly(p.wheels().positions().size(), p.engine().isPresent())
                .accepts(true, parts.getItem(WHEELS).getCount(), !parts.getItem(ENGINE).isEmpty());
        return LiftStatus.build(lift.busy(), matches, p != null && lift.occupied(p));
    }

    private LiftStatus.Paint paint(LiftBlockEntity lift) {
        return LiftStatus.paint(lift.busy(), lift.vehicleOnDeck() != null, parts.getItem(DYE).getItem() instanceof DyeItem);
    }

    @Override
    public void broadcastChanges() {
        LiftBlockEntity lift = lift();
        if (lift != null) {
            data.set(0, build(lift).ordinal());
            data.set(1, paint(lift).ordinal());
            data.set(2, lift.jobTicks());
        }
        super.broadcastChanges();
    }

    /** effects: Build spawns the vehicle and takes the parts; Paint paints the vehicle on the deck and takes one dye; either only when READY */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        LiftBlockEntity lift = lift();
        if (lift == null || player.level().isClientSide()) {
            return false;
        }
        if (id == BUILD_BUTTON) {
            if (build(lift) != LiftStatus.Build.READY) {
                return false;
            }
            ResourceLocation vehicle = chassisVehicle();
            VehicleProfile p = vehicle == null ? null : lift.profile(vehicle);
            if (p == null) {
                return false;
            }
            parts.removeItem(CHASSIS, 1);
            parts.removeItem(WHEELS, p.wheels().positions().size());
            if (p.engine().isPresent()) {
                parts.removeItem(ENGINE, 1);
            }
            lift.build(vehicle);
            return true;
        }
        if (id == PAINT_BUTTON) {
            if (paint(lift) != LiftStatus.Paint.READY) {
                return false;
            }
            Vehicle v = lift.vehicleOnDeck();
            if (v == null || !(parts.getItem(DYE).getItem() instanceof DyeItem dye)) {
                return false;
            }
            parts.removeItem(DYE, 1);
            lift.paint(v, dye.getDyeColor());
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < PARTS) {
                if (!moveItemStackTo(stack, PARTS, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, PARTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModContent.LIFT_CONTROLLER.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, parts));
    }
}
