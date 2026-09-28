/*
 * Ranged Weapons Mod - guns for players, on the Ranged Weapons protocol.
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
package com.chunkworks.rangedweaponsmod;

import com.chunkworks.carried.api.Carried;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Where a player's loose rounds are, on the server: everything they carry, through the Carried
 * protocol (D-0026): the inventory, hotbar first, then the offhand, then every carried bag's
 * storage cells (a bag's mounts are gear, never ammunition), in either feed mode. A draw takes the
 * inventory's rounds before the bags', so a bag is the reserve, and is all or nothing.
 *
 * <p>Read live whenever asked: nothing is kept between calls.
 */
final class RoundSources {
    private final Player player;

    private RoundSources(Player player) {
        this.player = player;
    }

    /** effects: returns what {@code player} can reach: everything they carry */
    static RoundSources of(Player player) {
        return new RoundSources(player);
    }

    /** effects: returns the kinds of round in reach that {@code accepts}, each once, in the order first met: the inventory before the bags */
    List<Item> kinds(Predicate<? super Item> accepts) {
        List<Item> kinds = new ArrayList<>();
        Carried.forEach(player, (store, cell, stack) -> {
            Item item = stack.getItem();
            if (accepts.test(item) && !kinds.contains(item)) {
                kinds.add(item);
            }
        });
        return kinds;
    }

    /** effects: returns how many of {@code kind} are in reach */
    int count(Item kind) {
        return Carried.count(player, kind);
    }

    /**
     * requires: the logical server<br>
     * effects: removes {@code n} of {@code kind}, the inventory's before the bags', and returns
     * true; removes nothing and returns false when fewer are in reach
     */
    boolean take(Item kind, int n) {
        return Carried.take(player, stack -> stack.is(kind), n, taken -> {});
    }
}
