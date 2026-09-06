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

import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
}
