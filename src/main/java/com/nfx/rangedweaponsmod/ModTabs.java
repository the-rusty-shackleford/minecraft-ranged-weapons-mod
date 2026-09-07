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

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * The creative tab: everything this mod makes in one place, in the order
 * of the tree -- the guns, their ammunition, then the parts a gun is
 * assembled from. The items stay in the vanilla tabs too (Combat,
 * Ingredients), so a search finds them either way.
 */
public final class ModTabs {
    private ModTabs() {}

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RangedWeaponsMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RANGED_WEAPONS = TABS.register("ranged_weapons", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + RangedWeaponsMod.MOD_ID))
            .icon(() -> new ItemStack(ModItems.MACHINE_GUN.get()))
            .displayItems((parameters, output) -> contents().forEach(output::accept))
            .build());

    /** effects: returns what the tab shows, in order: guns, ammunition, parts */
    public static List<Item> contents() {
        List<Item> items = new ArrayList<>();
        items.add(ModItems.PISTOL.get());
        items.add(ModItems.SHOTGUN.get());
        items.add(ModItems.RIFLE.get());
        items.add(ModItems.SCOPED_RIFLE.get());
        items.add(ModItems.MACHINE_GUN.get());
        items.add(ModItems.SMALL_ROUND.get());
        items.add(ModItems.ROUND.get());
        items.add(ModItems.SHELL.get());
        items.addAll(ModItems.parts());
        return items;
    }

    static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
