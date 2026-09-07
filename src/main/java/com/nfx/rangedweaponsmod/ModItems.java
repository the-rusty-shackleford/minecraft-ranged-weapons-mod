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
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** The items: the guns, their ammunition, and the parts a gun is assembled from. */
public final class ModItems {
    private ModItems() {}

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RangedWeaponsMod.MOD_ID);

    // Durability is the shot count before the gun breaks; everything else
    // about a gun -- rate, damage, spread, magazine, ammunition -- is its
    // profile in data. Every gun repairs like anything with durability.

    /** A sidearm: one hand, one shot per pull, small rounds. */
    public static final DeferredItem<GunItem> PISTOL =
            ITEMS.registerItem("pistol", p -> new GunItem(p, GunItem.Grip.ONE_HANDED, false), new Item.Properties().durability(800));

    /** A pump shotgun: shells, a spread of pellets, brutal close and a sting at range. */
    public static final DeferredItem<GunItem> SHOTGUN =
            ITEMS.registerItem("shotgun", p -> new GunItem(p, GunItem.Grip.TWO_HANDED, false), new Item.Properties().durability(500));

    /** A semi-automatic rifle: medium rounds, accurate, one shot per pull. */
    public static final DeferredItem<GunItem> RIFLE =
            ITEMS.registerItem("rifle", p -> new GunItem(p, GunItem.Grip.TWO_HANDED, false), new Item.Properties().durability(700));

    /** A bolt-action rifle with a scope: medium rounds, the hardest hit and the longest reach. */
    public static final DeferredItem<GunItem> SCOPED_RIFLE =
            ITEMS.registerItem("scoped_rifle", p -> new GunItem(p, GunItem.Grip.TWO_HANDED, true), new Item.Properties().durability(600));

    /** A light machine gun: medium rounds, the one gun that fires for as long as the trigger is held. */
    public static final DeferredItem<GunItem> MACHINE_GUN =
            ITEMS.registerItem("machine_gun", p -> new GunItem(p, GunItem.Grip.TWO_HANDED, false), new Item.Properties().durability(1200));

    /** A small round: the pistol's. */
    public static final DeferredItem<Item> SMALL_ROUND =
            ITEMS.registerItem("small_round", Item::new, new Item.Properties().stacksTo(64));

    /** A medium round: the rifles' and the machine gun's. */
    public static final DeferredItem<Item> ROUND =
            ITEMS.registerItem("round", Item::new, new Item.Properties().stacksTo(64));

    /** A shotgun shell. */
    public static final DeferredItem<Item> SHELL =
            ITEMS.registerItem("shell", Item::new, new Item.Properties().stacksTo(64));

    // The parts. A gun is assembled from these (see the domain's Blueprints);
    // they are plain items, never GunItems, so nothing that treats a gun as
    // a gun -- the carry pose, the Hold My Items exclusion -- sees them.

    /** Iron with a little coal: what receivers are made of. */
    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerItem("steel_ingot", Item::new, new Item.Properties());
    /** The body over the trigger group. */
    public static final DeferredItem<Item> LOWER_RECEIVER = ITEMS.registerItem("lower_receiver", Item::new, new Item.Properties());
    /** The block that houses the action. */
    public static final DeferredItem<Item> UPPER_RECEIVER = ITEMS.registerItem("upper_receiver", Item::new, new Item.Properties());
    /** A tube of iron. */
    public static final DeferredItem<Item> BARREL = ITEMS.registerItem("barrel", Item::new, new Item.Properties());
    /** A barrel wrapped in steel, for fire that does not stop. */
    public static final DeferredItem<Item> HEAVY_BARREL = ITEMS.registerItem("heavy_barrel", Item::new, new Item.Properties());
    /** A wooden butt with a raked wrist. */
    public static final DeferredItem<Item> STOCK = ITEMS.registerItem("stock", Item::new, new Item.Properties());
    /** The shotgun's forend. */
    public static final DeferredItem<Item> PUMP = ITEMS.registerItem("pump", Item::new, new Item.Properties());
    /** A lens, a tube, a lens. */
    public static final DeferredItem<Item> SCOPE = ITEMS.registerItem("scope", Item::new, new Item.Properties());

    private static final List<DeferredItem<Item>> PARTS = List.of(
            STEEL_INGOT, LOWER_RECEIVER, UPPER_RECEIVER, BARREL, HEAVY_BARREL, STOCK, PUMP, SCOPE);

    /** effects: returns every part a gun is assembled from, in the order the tab shows them */
    public static List<Item> parts() {
        return PARTS.stream().<Item>map(DeferredHolder::get).toList();
    }

    /** effects: returns every gun this mod registers, for whoever needs the list */
    public static List<Item> guns() {
        return ITEMS.getEntries().stream().<Item>map(DeferredHolder::get).filter(GunItem.class::isInstance).toList();
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::buildCreativeTabs);
    }

    private static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(PISTOL.get());
            event.accept(SHOTGUN.get());
            event.accept(RIFLE.get());
            event.accept(SCOPED_RIFLE.get());
            event.accept(MACHINE_GUN.get());
            event.accept(SMALL_ROUND.get());
            event.accept(ROUND.get());
            event.accept(SHELL.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            parts().forEach(event::accept);
        }
    }
}
