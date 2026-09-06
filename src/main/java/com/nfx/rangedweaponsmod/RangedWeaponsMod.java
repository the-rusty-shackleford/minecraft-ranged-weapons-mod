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
package com.nfx.rangedweaponsmod;

import com.mojang.logging.LogUtils;
import com.nfx.rangedweaponsmod.net.Payloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Ranged Weapons Mod: guns for players, on the Ranged Weapons protocol.
 *
 * <p>The protocol already makes any item with a profile a working weapon for
 * a mob: rounds on the stack, a projectile that flies and hits. This mod is
 * the player half of the same contract -- the trigger, the reload, the
 * recoil, the ammo counter -- over whatever weapon the protocol resolves
 * from the player's hand. Its own guns are plain items with profiles; how
 * they fire is data.
 */
@Mod(RangedWeaponsMod.MOD_ID)
public final class RangedWeaponsMod {
    public static final String MOD_ID = "rangedweaponsmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RangedWeaponsMod(IEventBus modBus, ModContainer container) {
        ModItems.register(modBus);
        ModSounds.register(modBus);
        ModData.register(modBus);
        modBus.addListener(Payloads::register);
        // A game-bus event, not a mod-bus one.
        NeoForge.EVENT_BUS.addListener(PlayerGunnery::onPlayerTick);
    }
}
