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

import com.chunkworks.rangedweaponsmod.domain.Chip;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The lock-on chip (D-0029): fitted to a rocket launcher at the weapons workbench, it is what lets
 * the seeker lock. Each guided launch spends one of its {@link Chip#CHARGES} charges, which its
 * durability bar shows; the last burns it out ({@link LauncherItem#wearChip}). A chip carries its
 * wear, so chips do not stack.
 *
 * <p>Immutable, as items are.
 */
public final class ChipItem extends Item {

    /**
     * @param properties the item's; the durability is forced to {@link Chip#CHARGES}
     */
    public ChipItem(Properties properties) {
        super(properties.durability(Chip.CHARGES));
    }

    /** effects: returns whether {@code stack} is a lock-on chip */
    public static boolean isChip(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ChipItem;
    }

    /** effects: returns how many guided launches {@code chip} has left: its durability, at least none */
    public static int left(ItemStack chip) {
        return Math.max(0, chip.getMaxDamage() - chip.getDamageValue());
    }

    /** effects: how many locks it has left, and where it is fitted */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rangedweaponsmod.lock_on_chip.left", left(stack), stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rangedweaponsmod.lock_on_chip.where").withStyle(ChatFormatting.DARK_GRAY));
    }
}
