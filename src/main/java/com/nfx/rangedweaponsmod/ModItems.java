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

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The items: the guns, and the round they all take. */
public final class ModItems {
    private ModItems() {}

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RangedWeaponsMod.MOD_ID);

    /** Wears out over about twelve hundred shots; repairs like anything with durability. */
    public static final DeferredItem<GunItem> MACHINE_GUN =
            ITEMS.registerItem("machine_gun", GunItem::new, new Item.Properties().durability(1200));

    /** One round of ammunition: one round in a magazine. */
    public static final DeferredItem<Item> ROUND =
            ITEMS.registerItem("round", Item::new, new Item.Properties().stacksTo(64));

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::buildCreativeTabs);
    }

    private static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(MACHINE_GUN.get());
            event.accept(ROUND.get());
        }
    }
}
