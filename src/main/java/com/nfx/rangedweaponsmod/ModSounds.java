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

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The sounds. A gun's own shot and far report are named in its profile and
 * played by the protocol's {@code ShotReport}; the handling sounds -- the dry
 * click, the reload -- are this mod's and played by {@link PlayerGunnery}.
 * Variable range so a loud report carries.
 */
public final class ModSounds {
    private ModSounds() {}

    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, RangedWeaponsMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_GUN_SHOT = sound("machine_gun_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> FAR_SHOT = sound("far_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMPTY_CLICK = sound("empty_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD_START = sound("reload_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD_END = sound("reload_end");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, name)));
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
