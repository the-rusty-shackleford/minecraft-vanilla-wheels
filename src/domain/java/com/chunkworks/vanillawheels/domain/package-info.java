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
/**
 * The pure layer of Vanilla Wheels: how a vehicle moves under its driver's
 * input, how its body rides the ground, what running into something costs,
 * what a tank holds, what a mesh is and how it is drawn, what a lift is.
 * Compiled against the JDK only; a {@code net.minecraft} import here is a
 * compile error, which is the layer rule enforced by the build. Everything
 * here is immutable, specified with {@code requires/effects/throws}, carries
 * its rep invariant, and is tested with partitions written at the top of
 * each test class. Units are blocks and ticks; angles are radians.
 */
package com.chunkworks.vanillawheels.domain;
