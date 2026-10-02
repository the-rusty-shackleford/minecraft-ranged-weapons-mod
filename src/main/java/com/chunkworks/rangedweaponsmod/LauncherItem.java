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

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The rocket launcher (D-0028): a gun in every way the trigger, the reload and the counter care
 * about -- two hands, one rocket in its tube, fired on the attack key -- whose sight, raised by
 * the use key or the aim key, is a seeker ({@link Seeking}). Rockets are {@link Rocket}s, launched
 * by {@link LauncherWeapon}.
 *
 * <p>Immutable, as items are.
 */
public final class LauncherItem extends GunItem {

    /**
     * @param properties the item's
     */
    public LauncherItem(Properties properties) {
        super(properties, Grip.TWO_HANDED, false, Feed.INTERNAL);
    }

    /** effects: returns whether {@code stack} is a launcher */
    public static boolean isLauncher(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof LauncherItem;
    }

    /** effects: the gun's lines, then how the seeker is used */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.rangedweaponsmod.rocket_launcher.seeker").withStyle(ChatFormatting.GRAY));
    }
}
