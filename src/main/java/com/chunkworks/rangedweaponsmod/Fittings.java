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

import net.minecraft.world.item.ItemStack;

/**
 * What the weapons workbench fits to a weapon (D-0029), and to which: today, one kind, the lock-on
 * chip, to the rocket launcher. A later fitting -- a scope, a suppressor -- is another kind here
 * and another slot beside the chip's on the bench, not another screen.
 */
public final class Fittings {
    private Fittings() {}

    /** effects: returns whether {@code weapon} takes any fitting, and so may stand in the bench's weapon slot */
    public static boolean fittable(ItemStack weapon) {
        return takesChip(weapon);
    }

    /** effects: returns whether {@code weapon} takes a lock-on chip: a rocket launcher does */
    public static boolean takesChip(ItemStack weapon) {
        return LauncherItem.isLauncher(weapon);
    }
}
