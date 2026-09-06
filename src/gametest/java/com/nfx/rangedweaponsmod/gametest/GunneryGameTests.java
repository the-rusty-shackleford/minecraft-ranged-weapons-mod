/*
 * Ranged Weapons Mod - guns for players, on the Ranged Weapons protocol.
 * Copyright (C) 2026 Rusty Shackleford and contributors
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
package com.nfx.rangedweaponsmod.gametest;

import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.api.WeaponClass;
import com.nfx.rangedweaponsmod.Handling;
import com.nfx.rangedweaponsmod.ModItems;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The mod on a real headless server. The class has a public no-argument
 * constructor and instance test methods because the gametest registry
 * instantiates the holder class reflectively before invoking each test.
 */
@GameTestHolder(RangedWeaponsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GunneryGameTests {

    public GunneryGameTests() {}

    /** The loader hosts this mod and the protocol it nests. */
    @GameTest(template = "arena")
    public void modAndProtocolAreLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(RangedWeaponsMod.MOD_ID), "this mod is loaded");
        helper.assertTrue(ModList.get().isLoaded("rangedweapons"), "the nested protocol is loaded");
        helper.succeed();
    }

    /** The shipped profile makes the machine gun a protocol weapon with the numbers the data says. */
    @GameTest(template = "arena")
    public void theMachineGunIsAProtocolWeaponFromItsProfile(GameTestHelper helper) {
        ItemStack gun = new ItemStack(ModItems.MACHINE_GUN.get());
        RangedWeapon weapon = RangedWeapons.resolve(gun);
        helper.assertTrue(weapon != null, "the machine gun resolves to a weapon");
        helper.assertValueEqual(weapon.capacity(gun), 50, "capacity from the profile");
        helper.assertValueEqual(weapon.stats(gun).fireRateTicks(), 3, "fire rate from the profile");
        helper.assertValueEqual(weapon.stats(gun).fullReloadTicks(), 50, "full reload from the profile");
        helper.assertTrue(weapon.profile().weaponClass() == WeaponClass.AUTOMATIC, "class from the profile");
        helper.assertTrue(weapon.profile().ammoItem().map(id -> id.toString()).orElse("").equals("rangedweaponsmod:round"),
                "ammunition from the profile");
        helper.assertValueEqual(weapon.rounds(gun), 0, "a fresh gun is empty");
        helper.succeed();
    }

    /** The shipped handling entry is read; a gun without one falls back to its class. */
    @GameTest(template = "arena")
    public void handlingComesFromTheDataMapWithClassDefaults(GameTestHelper helper) {
        Handling declared = Handling.of(new ItemStack(ModItems.MACHINE_GUN.get()));
        helper.assertValueEqual(declared.recoilPitch(), 0.55f, "recoil pitch from the data map");
        helper.assertValueEqual(declared.recovery(), 0.35f, "recovery from the data map");
        Handling fallback = Handling.of(new ItemStack(ModItems.ROUND.get()));
        helper.assertTrue(fallback.equals(Handling.defaultFor(WeaponClass.UNCLASSIFIED)),
                "an item with neither handling nor profile gets the unclassified default");
        helper.succeed();
    }
}
