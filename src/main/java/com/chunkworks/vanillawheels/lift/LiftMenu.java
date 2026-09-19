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
 * The lift's menu: chassis, wheels, engine and dye, plus a repair slot
 * visible only for a damaged mounted vehicle. The server decides what
 * each button may do every tick and sends the answer down seven data
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
    public static final int REPAIR = 4;
    private static final int PARTS = 5;
    public static final int BUILD_BUTTON = 0;
    public static final int PAINT_BUTTON = 1;
    public static final int REPAIR_BUTTON = 2;

    private final SimpleContainer parts = new SimpleContainer(PARTS);
    private final ContainerLevelAccess access;
    private final ContainerData data = new SimpleContainerData(7);
    private final Player owner;

    public LiftMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModContent.LIFT_MENU.get(), id);
        this.access = access;
        this.owner = inventory.player;
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
        addSlot(new Slot(parts, REPAIR, 192, 47) {
            @Override public boolean isActive() { return repairVisible(); }
            @Override public boolean mayPlace(ItemStack stack) {
                LiftBlockEntity lift = lift();
                Vehicle vehicle = lift == null ? null : lift.vehicleOnDeck();
                // The client receives a representative icon, not the complete ingredient tag.
                // Let the server validate all tag alternatives rather than rejecting valid substitutes here.
                return repairVisible() && (vehicle == null || vehicle.profile().repair().ingredient().test(stack));
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

    /** effects: reports a damaged mounted vehicle, as synced by the server */
    public boolean repairVisible() { return data.get(3) > 0; }
    /** effects: reports remaining condition in hundredths of a percent */
    public int repairCondition() { return repairVisible() ? data.get(3) - 1 : 10000; }
    /** effects: returns the server's proportional material count */
    public int repairCost() { return data.get(4); }
    /** effects: reports that repair is currently affordable and the lift is idle */
    public boolean repairReady() { return data.get(5) == 1; }
    /** effects: returns a display copy of the required repair material */
    public ItemStack repairMaterial() { return data.get(6) > 0 ? new ItemStack(net.minecraft.world.item.Item.byId(data.get(6))) : ItemStack.EMPTY; }

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
        boolean matches = p != null && (owner.hasInfiniteMaterials() || new Assembly(p.wheels().positions().size(), p.engine().isPresent())
                .accepts(true, parts.getItem(WHEELS).getCount(), !parts.getItem(ENGINE).isEmpty()));
        // A chassis selects the vehicle; creative needs no wheels or engine.
        return LiftStatus.build(lift.busy(), matches, p != null && lift.occupied(p));
    }

    private LiftStatus.Paint paint(LiftBlockEntity lift) {
        return LiftStatus.paint(lift.busy(), lift.vehicleOnDeck() != null, parts.getItem(DYE).getItem() instanceof DyeItem);
    }

    private boolean repair(LiftBlockEntity lift, Vehicle vehicle) {
        if (lift.busy() || vehicle == null || vehicle.condition() == 10000 || vehicle.profile() == null) return false;
        var policy = vehicle.profile().repair();
        int cost = new com.chunkworks.vanillawheels.domain.Condition(vehicle.condition()).repairCost(policy.fullCost());
        return owner.hasInfiniteMaterials() || policy.ingredient().test(parts.getItem(REPAIR)) && parts.getItem(REPAIR).getCount() >= cost;
    }

    @Override
    public void broadcastChanges() {
        LiftBlockEntity lift = lift();
        if (lift != null) {
            data.set(0, build(lift).ordinal());
            data.set(1, paint(lift).ordinal());
            data.set(2, lift.jobTicks());
            Vehicle vehicle = lift.vehicleOnDeck();
            boolean damaged = vehicle != null && vehicle.profile() != null && vehicle.condition() < 10000;
            data.set(3, damaged ? vehicle.condition() + 1 : 0);
            data.set(4, damaged ? new com.chunkworks.vanillawheels.domain.Condition(vehicle.condition()).repairCost(vehicle.profile().repair().fullCost()) : 0);
            data.set(5, damaged && repair(lift, vehicle) ? 1 : 0);
            if (damaged) {
                ItemStack[] materials = vehicle.profile().repair().ingredient().getItems();
                data.set(6, materials.length == 0 ? 0 : net.minecraft.world.item.Item.getId(materials[0].getItem()));
            } else {
                data.set(6, 0);
                // Once the section disappears, no ingredients may be stranded in an invisible slot.
                if (!parts.getItem(REPAIR).isEmpty()) owner.getInventory().placeItemBackInInventory(parts.removeItemNoUpdate(REPAIR));
            }
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
            if (!player.hasInfiniteMaterials()) {
                parts.removeItem(CHASSIS, 1);
                parts.removeItem(WHEELS, p.wheels().positions().size());
                if (p.engine().isPresent()) parts.removeItem(ENGINE, 1);
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
            if (!player.hasInfiniteMaterials()) parts.removeItem(DYE, 1);
            lift.paint(v, dye.getDyeColor());
            return true;
        }
        if (id == REPAIR_BUTTON) {
            Vehicle vehicle = lift.vehicleOnDeck();
            if (!repair(lift, vehicle)) return false;
            int cost = new com.chunkworks.vanillawheels.domain.Condition(vehicle.condition()).repairCost(vehicle.profile().repair().fullCost());
            if (!player.hasInfiniteMaterials()) parts.removeItem(REPAIR, cost);
            lift.repair(vehicle);
            broadcastChanges();
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
