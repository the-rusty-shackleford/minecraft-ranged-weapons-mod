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

import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.api.Shot;
import com.nfx.rangedweapons.api.WeaponProfile;
import com.nfx.rangedweapons.api.WeaponStats;
import com.nfx.rangedweapons.fallback.ProfiledWeapon;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * The rocket launcher as a protocol weapon (D-0028): the revolver's shape. Its tube, its
 * rockets and its numbers are the profile's, through the protocol's own {@link ProfiledWeapon};
 * only the launch is its own. A launch is one {@link Rocket} from the shot's origin along its
 * direction, homing on the shooter's lock if a player fired it with one, flying straight
 * otherwise -- a mob issued a launcher by some other mod fires straight. The profile's spread is
 * not applied: a rocket leaves the tube true. Its speed and lifetime are the rocket's
 * {@link com.chunkworks.rangedweaponsmod.domain.Motor}'s; the profile's describe it to a mob's AI.
 *
 * <p>RI: the delegate is the launcher's profiled weapon; all mutable state belongs to stacks.
 */
public final class LauncherWeapon implements RangedWeapon {
    private final ProfiledWeapon delegate;

    private LauncherWeapon(Item item) {
        delegate = ProfiledWeapon.of(item);
    }

    /** effects: installs the native provider on the launcher only */
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        Item item = ModItems.ROCKET_LAUNCHER.get();
        LauncherWeapon weapon = new LauncherWeapon(item);
        event.registerItem(RangedWeapons.WEAPON, (stack, context) -> weapon, item);
    }

    @Override public WeaponProfile profile() { return delegate.profile(); }
    @Override public WeaponStats stats(ItemStack stack) { return delegate.stats(stack); }
    @Override public int capacity(ItemStack stack) { return delegate.capacity(stack); }
    @Override public int rounds(ItemStack stack) { return delegate.rounds(stack); }
    @Override public void load(ItemStack stack, int count) { delegate.load(stack, count); }
    @Override public void load(ItemStack stack, int count, Item ammo) { delegate.load(stack, count, ammo); }
    @Override public Optional<Item> loadedAmmo(ItemStack stack) { return delegate.loadedAmmo(stack); }
    @Override public void consumeRound(ItemStack stack) { delegate.consumeRound(stack); }

    /**
     * effects: launches one rocket from the shot's origin along its direction, owned by
     * {@code shooter}; a player's lock, if it still holds, is its target and is spent. Consumes no
     * ammunition and plays no sound, as the protocol requires.
     */
    @Override
    public void fire(ServerLevel level, LivingEntity shooter, ItemStack stack, Shot shot) {
        Entity target = shooter instanceof Player player ? Seeking.spend(player, level) : null;
        Rocket.launch(level, shooter, shot.origin(), shot.direction(), target);
    }
}
