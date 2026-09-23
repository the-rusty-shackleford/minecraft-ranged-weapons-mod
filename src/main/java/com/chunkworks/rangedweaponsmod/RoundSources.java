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

import com.chunkworks.rangedweaponsmod.domain.FeedMode;
import com.chunkworks.rangedweaponsmod.domain.Pockets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Where a player's loose rounds are, on the server: the inventory, hotbar
 * first, as pocket 0 -- and, in loose mode with Backpacks+ present, the
 * worn bag and then bags carried in the inventory in slot order, their
 * storage cells only (mounts are gear, not ammunition). A read builds a
 * {@link Pockets} value; a draw applies its plan to the real stacks, and
 * to a bag through Backpacks+'s own contents API, which validates the
 * withdrawal and advances the bag's revision. Without Backpacks+ the
 * inventory is the only pocket, in either mode.
 *
 * <p>Built from the live inventory when asked for and never cached: the
 * addressing behind the value is good only until the inventory changes,
 * which is why a draw happens on the value that was just read.
 */
final class RoundSources {

    private static final boolean BACKPACKS = ModList.get().isLoaded("backpacksplus");

    /** Where a pocket's stacks really are. */
    sealed interface Where permits InventorySlots, BagCells {}

    /** Stack {@code j} of the pocket is inventory slot {@code slots[j]}. */
    record InventorySlots(int[] slots) implements Where {}

    /** Stack {@code j} of the pocket is storage cell {@code cells[j]} of the bag at Backpacks+ source {@code source}. */
    record BagCells(int source, int[] cells) implements Where {}

    private final Player player;
    private final Pockets<Item> pockets;
    private final List<Where> where;

    private RoundSources(Player player, Pockets<Item> pockets, List<Where> where) {
        this.player = player;
        this.pockets = pockets;
        this.where = where;
    }

    /**
     * effects: returns what {@code player} can reach under {@code mode}: the
     * inventory alone in magazines mode; the inventory, the worn bag and
     * the carried bags in loose mode
     */
    static RoundSources of(Player player, FeedMode mode) {
        List<List<Pockets.Stack<Item>>> rows = new ArrayList<>();
        List<Where> where = new ArrayList<>();
        Inventory inventory = player.getInventory();
        List<Pockets.Stack<Item>> row = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                row.add(new Pockets.Stack<>(stack.getItem(), stack.getCount()));
                slots.add(slot);
            }
        }
        rows.add(row);
        where.add(new InventorySlots(slots.stream().mapToInt(Integer::intValue).toArray()));
        if (!mode.requiresMagazines() && BACKPACKS) {
            BackpackPockets.collect(player, rows, where);
        }
        return new RoundSources(player, Pockets.of(rows), where);
    }

    /** effects: returns the pockets as the domain sees them */
    Pockets<Item> pockets() {
        return pockets;
    }

    /** effects: returns the kinds of round in reach that {@code accepts}, each once, pockets before bags */
    List<Item> kinds(Predicate<? super Item> accepts) {
        return pockets.kinds(accepts);
    }

    /** effects: returns how many of {@code kind} are in reach */
    int count(Item kind) {
        return pockets.count(kind);
    }

    /**
     * requires: {@code n <= count(kind)}, and the inventory unchanged since
     * this was built<br>
     * effects: removes {@code n} of {@code kind}: the pockets first, then
     * the worn bag, then the carried bags; a bag is rewritten once
     */
    void take(Item kind, int n) {
        List<Pockets.Take> plan = pockets.plan(kind, n);
        for (int p = 0; p < where.size(); p++) {
            final int pocket = p;
            List<Pockets.Take> takes = plan.stream().filter(take -> take.pocket() == pocket).toList();
            if (takes.isEmpty()) {
                continue;
            }
            switch (where.get(p)) {
                case InventorySlots slots -> {
                    for (Pockets.Take take : takes) {
                        player.getInventory().getItem(slots.slots()[take.stack()]).shrink(take.count());
                    }
                }
                case BagCells bag -> BackpackPockets.take(player, bag, takes);
            }
        }
    }
}
