/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.garage;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.domain.Sensing;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/**
 * The Vehicle Sensor's watch (D-0035): every {@link Sensing#SCAN_PERIOD} ticks it looks for a vehicle
 * within its range and turns its block on or counts it down. A vehicle is any of ours, any part of
 * one's body, or anything that is not a creature carrying a player (a boat, a minecart, another mod's
 * aircraft); a player on foot, a mob, a ridden horse and an item are not.
 *
 * <p>AF: {@code hold} is the countdown of scans the sensor stays on. RI: 0 <= hold <= Sensing.HOLD;
 * {@code reach} is the sensor's block grown by {@code reachRange} blocks, or null before the first scan.
 * The countdown is not saved: a sensor loaded on scans afresh and lets go if nothing is there.
 */
public final class VehicleSensorBlockEntity extends BlockEntity {
    private static final EntityTypeTest<Entity, Entity> ANY = EntityTypeTest.forClass(Entity.class);
    private static final Predicate<Entity> VEHICLE = e -> !e.isSpectator()
            && (e instanceof Vehicle || e instanceof Vehicle.Part
                || !(e instanceof LivingEntity) && e.hasPassenger(p -> p instanceof Player));
    private final List<Entity> found = new ArrayList<>(1);
    private AABB reach;
    private int reachRange = -1;
    private int hold;

    /** requires: a sensor's position and state; effects: creates its watch, counting down from nothing */
    public VehicleSensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.VEHICLE_SENSOR_BE.get(), pos, state);
    }

    /** requires: server side; effects: every SCAN_PERIOD ticks (staggered by position), looks within
     * the range for a vehicle: seeing one, the sensor is on for the full hold; seeing none, it counts
     * down, and turns off at the end of it */
    public static void serverTick(Level level, BlockPos pos, BlockState state, VehicleSensorBlockEntity sensor) {
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), Sensing.SCAN_PERIOD) != 0) {
            return;
        }
        int range = state.getValue(VehicleSensorBlock.RANGE);
        if (range != sensor.reachRange) {
            sensor.reach = new AABB(pos).inflate(range);
            sensor.reachRange = range;
        }
        level.getEntities(ANY, sensor.reach, VEHICLE, sensor.found, 1);
        boolean seen = !sensor.found.isEmpty();
        sensor.found.clear();
        sensor.hold = seen ? Sensing.HOLD : Sensing.decay(sensor.hold);
        boolean on = Sensing.on(sensor.hold);
        if (on != state.getValue(VehicleSensorBlock.POWERED)) {
            VehicleSensorBlock.power(level, pos, state, on);
        }
    }
}
