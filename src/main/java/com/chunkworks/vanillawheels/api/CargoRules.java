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

import com.chunkworks.carried.api.Carried;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What rides in a vehicle's cargo (D-0029). A vehicle whose profile has
 * cargo takes, through its open doors, whatever a rule here admits:
 * animals on a lead, built in, and whatever another mod registers. A
 * rule says what rides as cargo once aboard, what a player may load now,
 * what item leads it, and what becomes of the tether as it boards.
 *
 * <p>Rules are asked in the order they were registered, the animals'
 * first; an entity is the first rule's that says it {@link Rule#rides}.
 * Register from a mod's constructor; the list is safe to read from any
 * thread.
 */
public final class CargoRules {
    private CargoRules() {}

    /** One kind of cargo. */
    public interface Rule {
        /**
         * effects: returns whether {@code entity}, aboard a vehicle, stands
         * in its cargo rather than a seat. Asked on both sides, so it is
         * decided by what the entity is and by what both sides see of it.
         */
        boolean rides(Entity entity);

        /**
         * requires: rides(entity)
         * effects: returns whether {@code player} may load {@code entity}
         * now: it is theirs to lead and ready to board. Asked on the
         * server to board it, and on the client to tell whether an
         * empty-handed click is a load.
         */
        boolean loads(Entity entity, Player player);

        /**
         * effects: returns whether {@code stack} leads this cargo: a click
         * with it in hand loads, and a crouch with it at the open doors
         * unloads
         */
        boolean leadsWith(ItemStack stack);

        /**
         * requires: server side; loads(entity, player)
         * effects: undoes the tether as {@code entity} boards, loaded by
         * {@code player}: lets go of its lead and hands back what led it,
         * or whatever the rule's cargo keeps aboard
         */
        void boarding(Entity entity, Player player);
    }

    /** Animals on a vanilla lead: the lead comes back to the player as they board. */
    public static final Rule ANIMALS = new Rule() {
        @Override
        public boolean rides(Entity entity) {
            return entity instanceof Animal;
        }

        @Override
        public boolean loads(Entity entity, Player player) {
            return entity instanceof Animal a && a.getLeashHolder() == player;
        }

        @Override
        public boolean leadsWith(ItemStack stack) {
            return stack.is(Items.LEAD);
        }

        @Override
        public void boarding(Entity entity, Player player) {
            ((Animal) entity).dropLeash(true, false);
            Carried.giveOrDrop(player, new ItemStack(Items.LEAD));
        }
    };

    private static final List<Rule> RULES = new CopyOnWriteArrayList<>(List.of(ANIMALS));

    /** effects: adds {@code rule} after every rule registered before it */
    public static void register(Rule rule) {
        RULES.add(Objects.requireNonNull(rule));
    }

    /** effects: returns the rule {@code entity} rides by, the first that says it rides; empty when it is no cargo */
    public static Optional<Rule> of(Entity entity) {
        for (Rule rule : RULES) {
            if (rule.rides(entity)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    /** effects: returns whether {@code entity}, aboard a vehicle, rides in its cargo */
    public static boolean rides(Entity entity) {
        return of(entity).isPresent();
    }

    /** effects: returns whether {@code stack} leads any rule's cargo */
    public static boolean leads(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Rule rule : RULES) {
            if (rule.leadsWith(stack)) {
                return true;
            }
        }
        return false;
    }

    /** effects: returns whether {@code entity} takes a young one's room in the cargo */
    public static boolean young(Entity entity) {
        return entity instanceof LivingEntity living && living.isBaby();
    }
}
