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
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.WeaponProfile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The rounds a reload can reach, which the counter adds to the loaded ones (D-0027): everything
 * the player carries through Carried, as the reload itself reaches it (D-0026). A magazine-fed
 * gun reaches the rounds in every carried magazine it takes, a stack counting each magazine in it,
 * and never loose rounds, which must go into a magazine first. A gun loaded directly (the shotgun,
 * the revolver, every gun in loose mode) reaches every loose round it accepts, of every kind, since
 * {@code Shift+R} changes kind.
 *
 * <p>Read live whenever asked, on either side: Carried's reads allocate nothing and a store's
 * contents reach the owning client with its item.
 */
public final class Stock {
    private Stock() {}

    /**
     * requires: {@code weapon} operates {@code gun}<br>
     * effects: returns the rounds a reload of {@code gun} can reach in what {@code player} carries,
     * under the feed {@code magazineFed} names; the rounds in the gun are not counted
     */
    public static int reserve(Player player, ItemStack gun, RangedWeapon weapon, boolean magazineFed) {
        int[] reserve = {0};
        if (magazineFed) {
            Carried.forEach(player, (store, cell, candidate) -> {
                if (candidate.getItem() instanceof MagazineItem item && Magazines.accepts(gun, weapon, candidate)) {
                    reserve[0] += candidate.getOrDefault(ModData.MAGAZINE_CONTENTS.get(), MagazineContents.EMPTY)
                            .rounds(item.capacity()) * candidate.getCount();
                }
            });
        } else {
            WeaponProfile profile = weapon.profile();
            Carried.forEach(player, (store, cell, candidate) -> {
                if (profile.acceptsAmmo(candidate)) {
                    reserve[0] += candidate.getCount();
                }
            });
        }
        return reserve[0];
    }
}
