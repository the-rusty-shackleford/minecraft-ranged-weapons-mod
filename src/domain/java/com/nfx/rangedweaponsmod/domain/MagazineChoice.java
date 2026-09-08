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
package com.nfx.rangedweaponsmod.domain;

import java.util.List;
import java.util.OptionalInt;

/**
 * Which carried magazine goes into the gun.
 *
 * <p>Two questions, both answered by inventory order, hotbar first, so the
 * player picks by where they keep things:
 * <ul>
 *   <li>On a reload (the gun ran dry, or R): the first carried magazine
 *       that has rounds in it.</li>
 *   <li>On a swap (Shift+R): the next loaded magazine after the one the
 *       last swap took, wrapping round -- so pressing again walks through
 *       every magazine carried rather than bouncing between two.</li>
 * </ul>
 * Magazines are named by their inventory slot; the gun's own magazine is
 * not in the inventory and so never a candidate.
 */
public final class MagazineChoice {
    private MagazineChoice() {}

    /**
     * effects: returns the slot of the first loaded magazine, or empty
     *
     * @param loadedSlots the slots holding accepted magazines with rounds, in inventory order
     */
    public static OptionalInt forReload(List<Integer> loadedSlots) {
        return loadedSlots.isEmpty() ? OptionalInt.empty() : OptionalInt.of(loadedSlots.get(0));
    }

    /**
     * effects: returns the slot of the first loaded magazine after
     * {@code lastSwapSlot} in inventory order, wrapping to the start; the
     * first of all if {@code lastSwapSlot} is empty; empty if none is
     * carried
     *
     * @param loadedSlots  the slots holding accepted magazines with rounds, in inventory order
     * @param lastSwapSlot the slot the previous swap took its magazine from, if any
     */
    public static OptionalInt forSwap(List<Integer> loadedSlots, OptionalInt lastSwapSlot) {
        if (loadedSlots.isEmpty()) {
            return OptionalInt.empty();
        }
        if (lastSwapSlot.isPresent()) {
            for (int slot : loadedSlots) {
                if (slot > lastSwapSlot.getAsInt()) {
                    return OptionalInt.of(slot);
                }
            }
        }
        return OptionalInt.of(loadedSlots.get(0));
    }
}
