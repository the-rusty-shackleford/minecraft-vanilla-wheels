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

import net.neoforged.neoforge.common.ModConfigSpec;

/** The server's switches: {@code config/vanillawheels-common.toml}. */
public final class WheelsConfig {
    private WheelsConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RUN_OVER = BUILDER
            .comment("Whether a moving vehicle hurts and shoves what it runs into.")
            .define("runOver", true);
    public static final ModConfigSpec.DoubleValue DAMAGE_SCALE = BUILDER
            .comment("Multiplies the damage of running something over.")
            .defineInRange("damageScale", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.BooleanValue FUEL_REQUIRED = BUILDER
            .comment("Whether an engine needs fuel. Off, every tank reads full and nothing burns.")
            .define("fuelRequired", true);
    public static final ModConfigSpec.IntValue AUTO_LIGHTS_BELOW = BUILDER
            .comment("Headlights set to auto come on when the light at the vehicle is below this (0-15).")
            .defineInRange("autoLightsBelow", 8, 0, 15);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
