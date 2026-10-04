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

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The lock-on chip fitted to a launcher at the weapons workbench (D-0029), whole: its wear, and a
 * name if it has one, so it comes off as it went on.
 *
 * <p>Immutable. A component value is shared between copies of the stack that carries it, so the
 * chip inside is copied going in and coming out, and compared by value: two launchers carrying
 * equal chips are equal, which is what lets a menu tell a changed slot from an unchanged one
 * (an {@link ItemStack} itself compares by identity).<br>
 * RI: the chip is one, never empty.
 */
public final class FittedChip {
    public static final Codec<FittedChip> CODEC = ItemStack.CODEC.xmap(FittedChip::new, FittedChip::chip);
    public static final StreamCodec<RegistryFriendlyByteBuf, FittedChip> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(FittedChip::new, FittedChip::chip);

    private final ItemStack chip;

    /**
     * @param chip the chip; one is kept, a copy
     * @throws IllegalArgumentException if {@code chip} is empty
     */
    public FittedChip(ItemStack chip) {
        if (chip.isEmpty()) {
            throw new IllegalArgumentException("a fitted chip is a chip, not nothing");
        }
        this.chip = chip.copyWithCount(1);
    }

    /** effects: returns the chip, a copy of one */
    public ItemStack chip() {
        return chip.copy();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FittedChip that && ItemStack.matches(chip, that.chip);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(chip);
    }

    @Override
    public String toString() {
        return "FittedChip[" + chip + "]";
    }
}
