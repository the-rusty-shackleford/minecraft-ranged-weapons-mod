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

import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A gun a player can hold. Deliberately almost nothing: what the gun
 * <em>is</em> lives in its {@code rangedweapons:weapons} profile, how it is
 * <em>operated</em> lives in the protocol's weapon for it, and how it is
 * <em>fired by a player</em> lives in {@link PlayerGunnery}. This class
 * marks an item as one the trigger works on and shows the load on hover.
 *
 * <p>None of vanilla's use-item machinery is involved: right-click is taken
 * over on the client and turned into trigger state, because vanilla repeats
 * a use at most every four ticks and slows a player using an item to a
 * fifth of their speed, neither of which is a gun.
 */
public final class GunItem extends Item {

    public GunItem(Properties properties) {
        super(properties);
    }

    /** effects: returns whether {@code stack} is a gun this mod's trigger works on */
    public static boolean isGun(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof GunItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RangedWeapon weapon = RangedWeapons.resolve(stack);
        if (weapon == null) {
            tooltip.add(Component.translatable("item.rangedweaponsmod.gun.no_profile").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Component.translatable("item.rangedweaponsmod.gun.rounds", weapon.rounds(stack), weapon.capacity(stack))
                .withStyle(ChatFormatting.GRAY));
    }
}
