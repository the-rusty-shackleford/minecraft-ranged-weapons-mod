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
import com.nfx.rangedweapons.api.Shot;
import com.nfx.rangedweapons.api.WeaponProfile;
import com.nfx.rangedweapons.api.WeaponStats;
import com.nfx.rangedweapons.fallback.Fallback;
import com.nfx.rangedweapons.fallback.ProfiledWeapon;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * A magazine-fed gun on the protocol's native tier: the profile's weapon
 * with one difference -- its capacity is the inserted magazine's.
 *
 * <p>The profile's capacity is what the gun holds with no magazine in it
 * (rounds loaded loose, before magazines existed or by creative, until they
 * are adopted) and what the fallback would clamp to; with a magazine in,
 * the magazine says how many rounds fit, so an extended or a double-stack
 * magazine is a matter of registering it, and the stats keep the invariant
 * {@code stats(stack).capacity() == capacity(stack)}. The round store is
 * the fallback's own components, read and written here with this capacity
 * instead of the profile's; firing, the stats and the loaded round's
 * effect on them are the profile weapon's, untouched.
 */
public final class MagazineFedWeapon implements RangedWeapon {

    private static final Map<Item, MagazineFedWeapon> INSTANCES = new ConcurrentHashMap<>();

    private final ProfiledWeapon profiled;

    private MagazineFedWeapon(Item item) {
        this.profiled = ProfiledWeapon.of(item);
    }

    /** effects: returns the one weapon for {@code item}, creating it on the first call */
    public static MagazineFedWeapon of(Item item) {
        return INSTANCES.computeIfAbsent(item, MagazineFedWeapon::new);
    }

    /** Registers this weapon on every magazine-fed gun, ahead of the profile tier. */
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        Item[] guns = ModItems.guns().stream().filter(gun -> ((GunItem) gun).feed() == GunItem.Feed.MAGAZINE).toArray(Item[]::new);
        event.registerItem(RangedWeapons.WEAPON, (stack, context) -> of(stack.getItem()), guns);
    }

    @Override
    public WeaponProfile profile() {
        return profiled.profile();
    }

    /** effects: returns the inserted magazine's capacity, or the profile's with none inserted */
    @Override
    public int capacity(ItemStack stack) {
        return Magazines.inserted(stack).map(magazine -> Magazines.contents(magazine).capacity())
                .orElse(profiled.capacity(stack));
    }

    @Override
    public WeaponStats stats(ItemStack stack) {
        return profiled.stats(stack).withCapacity(capacity(stack));
    }

    @Override
    public int rounds(ItemStack stack) {
        return Math.min(stack.getOrDefault(Fallback.ROUNDS.get(), 0), capacity(stack));
    }

    @Override
    public void load(ItemStack stack, int count) {
        int capacity = capacity(stack);
        if (count < 0 || count > capacity) {
            throw new IllegalArgumentException("count must be in [0, " + capacity + "], was " + count);
        }
        stack.set(Fallback.ROUNDS.get(), count);
    }

    @Override
    public void load(ItemStack stack, int count, Item ammo) {
        load(stack, count);
        stack.set(Fallback.LOADED_AMMO.get(), ammo);
    }

    @Override
    public Optional<Item> loadedAmmo(ItemStack stack) {
        return Optional.ofNullable(stack.get(Fallback.LOADED_AMMO.get()));
    }

    @Override
    public void consumeRound(ItemStack stack) {
        int rounds = rounds(stack);
        if (rounds == 0) {
            throw new IllegalStateException("cannot consume a round from an empty " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        stack.set(Fallback.ROUNDS.get(), rounds - 1);
    }

    @Override
    public void fire(ServerLevel level, LivingEntity shooter, ItemStack stack, Shot shot) {
        profiled.fire(level, shooter, stack, shot);
    }

    @Override
    public String toString() {
        return "MagazineFedWeapon[" + profiled + "]";
    }
}
