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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * The profile-backed revolver with one accepted-shot animation record.
 * AF: the current data-driven revolver, for players and any protocol consumer.
 * RI: the delegate is the revolver's profiled weapon; all mutable state belongs
 * to individual stacks. Ammo, ballistics and cadence keep their existing owners.
 */
public final class RevolverWeapon implements RangedWeapon {
    private final ProfiledWeapon delegate;
    private RevolverWeapon(Item item) { delegate=ProfiledWeapon.of(item); }

    /** effects: installs the shared native provider on the revolver only. */
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        Item item=ModItems.REVOLVER.get();
        RevolverWeapon weapon=new RevolverWeapon(item);
        event.registerItem(RangedWeapons.WEAPON,(stack,context)->weapon,item);
    }

    @Override public WeaponProfile profile() { return delegate.profile(); }
    @Override public WeaponStats stats(ItemStack stack) { return delegate.stats(stack); }
    @Override public int capacity(ItemStack stack) { return delegate.capacity(stack); }
    @Override public int rounds(ItemStack stack) { return delegate.rounds(stack); }
    @Override public void load(ItemStack stack,int count) { delegate.load(stack,count); }
    @Override public void load(ItemStack stack,int count,Item ammo) { delegate.load(stack,count,ammo); }
    @Override public Optional<Item> loadedAmmo(ItemStack stack) { return delegate.loadedAmmo(stack); }
    @Override public void consumeRound(ItemStack stack) { delegate.consumeRound(stack); }

    /** effects: fires through the protocol and advances this stack's visible action; consumes no ammunition. */
    @Override public void fire(ServerLevel level,LivingEntity shooter,ItemStack stack,Shot shot) {
        delegate.fire(level,shooter,stack,shot);
        var previous=stack.getOrDefault(ModData.REVOLVER_CYCLE,new RevolverCycle(0,level.getGameTime()));
        stack.set(ModData.REVOLVER_CYCLE,previous.fired(level.getGameTime()));
    }
}
