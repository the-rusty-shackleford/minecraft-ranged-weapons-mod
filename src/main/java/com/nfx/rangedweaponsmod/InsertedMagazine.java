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
package com.nfx.rangedweaponsmod;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The magazine in a gun, whole, as a data component: a stack wrapped so the
 * component compares by what the stack is -- item, count, components --
 * rather than by identity, which a component must and a stack does not.
 *
 * <p>RI: {@code stack} is never empty; treat the value as immutable -- the
 * stack is copied on the way in and must be copied on the way out before it
 * is changed.
 *
 * @param stack the magazine
 */
public record InsertedMagazine(ItemStack stack) {

    public static final Codec<InsertedMagazine> CODEC = ItemStack.CODEC.xmap(InsertedMagazine::new, InsertedMagazine::stack);

    public static final StreamCodec<RegistryFriendlyByteBuf, InsertedMagazine> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(InsertedMagazine::new, InsertedMagazine::stack);

    public InsertedMagazine {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("an inserted magazine is never an empty stack");
        }
        stack = stack.copy();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof InsertedMagazine other && ItemStack.matches(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount();
    }
}
