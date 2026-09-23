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

import com.chunkworks.backpacksplus.BagContents;
import com.chunkworks.backpacksplus.BagLocations;
import com.chunkworks.rangedweaponsmod.domain.Pockets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Backpacks+ bags as pockets. Only ever touched when Backpacks+ is loaded
 * (see {@link RoundSources}); the bridge reads a bag's storage cells
 * straight from its contents component, and writes a withdrawal back
 * through {@code BagContents.store}, which refuses anything but a
 * withdrawal and advances the bag's revision, then marks the bag's
 * inventory changed. The mounts are gear, not ammunition, and are never
 * read.
 */
final class BackpackPockets {
    private BackpackPockets() {}

    /**
     * effects: appends a pocket for the worn bag, if one is worn, then one
     * for each bag carried in an inventory slot, in slot order, each
     * addressed by its Backpacks+ source
     */
    static void collect(Player player, List<List<Pockets.Stack<Item>>> rows, List<RoundSources.Where> where) {
        int worn = BagLocations.worn(player);
        if (worn != BagLocations.NONE) {
            pocket(player, worn, rows, where);
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot != worn && BagLocations.isBag(inventory.getItem(slot))) {
                pocket(player, slot, rows, where);
            }
        }
    }

    private static void pocket(Player player, int source, List<List<Pockets.Stack<Item>>> rows, List<RoundSources.Where> where) {
        ItemStack bag = BagLocations.stack(player, source);
        ItemContainerContents contents = bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        int storage = Math.min(BagContents.tier(bag).storageSlots(), contents.getSlots());
        List<Pockets.Stack<Item>> row = new ArrayList<>();
        List<Integer> cells = new ArrayList<>();
        for (int cell = 0; cell < storage; cell++) {
            ItemStack stack = contents.getStackInSlot(cell);
            if (!stack.isEmpty()) {
                row.add(new Pockets.Stack<>(stack.getItem(), stack.getCount()));
                cells.add(cell);
            }
        }
        rows.add(row);
        where.add(new RoundSources.BagCells(source, cells.stream().mapToInt(Integer::intValue).toArray()));
    }

    /**
     * requires: {@code takes} address stacks of the bag at {@code bag.source()}
     * as {@link #collect} numbered them, and the bag is unchanged since<br>
     * effects: takes those rounds out of the bag's storage cells, rewriting
     * the bag once and advancing its revision once
     */
    static void take(Player player, RoundSources.BagCells bag, List<Pockets.Take> takes) {
        ItemStack stack = BagLocations.stack(player, bag.source());
        NonNullList<ItemStack> cells = BagContents.copy(stack);
        for (Pockets.Take take : takes) {
            cells.get(bag.cells()[take.stack()]).shrink(take.count());
        }
        BagContents.store(stack, cells);
        BagLocations.changed(player, bag.source());
    }
}
