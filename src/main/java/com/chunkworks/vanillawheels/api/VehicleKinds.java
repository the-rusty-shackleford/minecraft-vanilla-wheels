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
package com.chunkworks.vanillawheels.api;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * Which entity makes a vehicle (D-0030). Every vehicle is a {@link Vehicle} of a profile in this
 * protocol's registry, and this protocol's own entity type makes every one nobody else claims. A
 * protocol built on this one -- Rotorcraft's aircraft and slung loads -- registers a kind for the
 * profiles it claims, with an entity type whose class extends {@code Vehicle} and takes over what
 * that protocol owns (how it moves, what of a fall it hands on, how it is drawn). Everything that
 * makes a vehicle goes through {@link Vehicle#create}, which asks here: the lift's Build, a vehicle
 * item set down, a trailer put on a hitch, a key's recall. Vehicle mods themselves stay data: a
 * profile names no entity type, the kind that claims it does.
 *
 * <p>A mod registers its kinds from its constructor, before any level loads. Kinds are asked in the
 * order registered; the first that claims a profile makes it.
 */
public final class VehicleKinds {
    private VehicleKinds() {}

    /**
     * A protocol's vehicles: which profiles it claims, by id in a level's registries -- both sides
     * ask, so the claim must be decided by what both sides see, such as the protocol's own synced
     * datapack registry -- and the entity type that makes them. The registries are a lookup, not
     * the level's whole access, so a creative tab may ask too (until 1.14.0 they were a
     * {@code RegistryAccess}; the erased signature is the same).
     */
    public record Kind(BiPredicate<HolderLookup.Provider, ResourceLocation> claims, Supplier<? extends EntityType<? extends Vehicle>> type) {}

    private static final List<Kind> KINDS = new CopyOnWriteArrayList<>();

    /** effects: adds {@code kind}, asked after every kind registered before it */
    public static void register(Kind kind) {
        KINDS.add(kind);
    }

    /** effects: returns the entity type that makes the vehicle of profile {@code id}: the first kind's that claims it, else this protocol's own */
    public static EntityType<? extends Vehicle> typeOf(RegistryAccess registries, ResourceLocation id) {
        for (Kind kind : KINDS) {
            if (kind.claims().test(registries, id)) {
                return kind.type().get();
            }
        }
        return ModContent.VEHICLE_ENTITY.get();
    }

    /**
     * effects: returns whether a protocol built on this one claims profile {@code id}: not one of
     * this protocol's own ground vehicles, so it is listed in that protocol's creative tab, not here
     */
    public static boolean claimed(HolderLookup.Provider registries, ResourceLocation id) {
        for (Kind kind : KINDS) {
            if (kind.claims().test(registries, id)) {
                return true;
            }
        }
        return false;
    }

    /** effects: returns every entity type a vehicle can be: this protocol's own first, then each kind's, in order */
    public static List<EntityType<? extends Vehicle>> types() {
        List<EntityType<? extends Vehicle>> all = new ArrayList<>();
        all.add(ModContent.VEHICLE_ENTITY.get());
        for (Kind kind : KINDS) {
            all.add(kind.type().get());
        }
        return all;
    }
}
