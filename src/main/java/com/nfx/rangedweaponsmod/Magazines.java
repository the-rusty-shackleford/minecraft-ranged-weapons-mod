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
import com.nfx.rangedweaponsmod.domain.Magazine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Magazines in guns and in hands: the one place that knows how a magazine's
 * contents are stored, how a gun carries its inserted magazine, and how the
 * protocol's round store is kept telling the truth about it.
 *
 * <p>The protocol's weapon keeps a round count and one loaded round on the
 * gun's stack; a magazine keeps an ordered list. This class keeps the two
 * agreeing: after every change to the inserted magazine, the store is
 * written with the magazine's count and its <em>next</em> round, so the
 * profile's stats, the bullet's falloff and the HUD -- which all ask the
 * store -- see the round that will actually leave the barrel, and a mixed
 * magazine changes what the gun does from one shot to the next. Mobs' guns
 * and creative's never carry a magazine; for them the store is all there
 * is, as before.
 */
public final class Magazines {
    private Magazines() {}

    /** effects: returns what {@code magazine} holds, clamped to its item's capacity; empty for a non-magazine */
    public static Magazine<Item> contents(ItemStack magazine) {
        if (!(magazine.getItem() instanceof MagazineItem item)) {
            return Magazine.empty(1);
        }
        return magazine.getOrDefault(ModData.MAGAZINE_CONTENTS.get(), MagazineContents.EMPTY).toMagazine(item.capacity());
    }

    /**
     * requires: {@code magazine} is a magazine item<br>
     * effects: writes {@code contents} onto it, in place
     */
    public static void setContents(ItemStack magazine, Magazine<Item> contents) {
        if (contents.isEmpty()) {
            magazine.remove(ModData.MAGAZINE_CONTENTS.get());
        } else {
            magazine.set(ModData.MAGAZINE_CONTENTS.get(), MagazineContents.of(contents));
        }
    }

    /** effects: returns a copy of the magazine in {@code gun}, if one is inserted */
    public static Optional<ItemStack> inserted(ItemStack gun) {
        InsertedMagazine inserted = gun.get(ModData.INSERTED_MAGAZINE.get());
        return inserted == null ? Optional.empty() : Optional.of(inserted.stack().copy());
    }

    /** effects: returns whether {@code magazine} is a magazine that {@code weapon} takes: one of its family */
    public static boolean accepts(RangedWeapon weapon, ItemStack magazine) {
        Optional<TagKey<Item>> family = weapon.profile().ammoFamily();
        return family.isPresent() && magazine.getItem() instanceof MagazineItem item && item.family().equals(family.get());
    }

    /**
     * requires: {@code weapon} operates {@code gun}; {@code magazine} is accepted<br>
     * effects: puts a copy of {@code magazine} into {@code gun}, in place, and
     * brings the store up to date with it
     */
    public static void insert(ItemStack gun, RangedWeapon weapon, ItemStack magazine) {
        gun.set(ModData.INSERTED_MAGAZINE.get(), new InsertedMagazine(magazine.copyWithCount(1)));
        sync(gun, weapon);
    }

    /**
     * requires: {@code weapon} operates {@code gun}<br>
     * effects: takes the inserted magazine out of {@code gun}, in place,
     * leaving the store empty; returns it, or an empty stack if none was in
     */
    public static ItemStack eject(ItemStack gun, RangedWeapon weapon) {
        ItemStack magazine = inserted(gun).orElse(ItemStack.EMPTY);
        gun.remove(ModData.INSERTED_MAGAZINE.get());
        weapon.load(gun, 0);
        gun.remove(com.nfx.rangedweapons.fallback.Fallback.LOADED_AMMO.get());
        return magazine;
    }

    /**
     * requires: {@code weapon} operates {@code gun} and a magazine is inserted with rounds in it<br>
     * effects: takes the next round out of the inserted magazine: the round
     * the store just spent
     */
    public static void pop(ItemStack gun, RangedWeapon weapon) {
        ItemStack magazine = inserted(gun).orElseThrow(() -> new IllegalStateException("no magazine in the gun"));
        setContents(magazine, contents(magazine).pop());
        gun.set(ModData.INSERTED_MAGAZINE.get(), new InsertedMagazine(magazine));
        sync(gun, weapon);
    }

    /**
     * effects: writes the inserted magazine's count and next round to the
     * store; with no magazine, or an empty one, the store is empty
     */
    public static void sync(ItemStack gun, RangedWeapon weapon) {
        Optional<ItemStack> magazine = inserted(gun);
        if (magazine.isEmpty()) {
            weapon.load(gun, 0);
            return;
        }
        Magazine<Item> contents = contents(magazine.get());
        int rounds = Math.min(contents.rounds(), weapon.capacity(gun));
        Optional<Item> next = contents.next();
        if (next.isPresent()) {
            weapon.load(gun, rounds, next.get());
        } else {
            weapon.load(gun, rounds);
        }
    }

    /**
     * Rounds loaded loose into a gun before magazines existed -- or by
     * creative, which needs none -- become a magazine on first sight.
     *
     * <p>effects: if {@code gun} has rounds in its store and no magazine
     * inserted, inserts a fresh default magazine of its family holding those
     * rounds (of the loaded round, else the gun's native one); otherwise
     * nothing. Returns whether it did.
     */
    public static boolean adopt(ItemStack gun, RangedWeapon weapon) {
        if (inserted(gun).isPresent() || weapon.rounds(gun) == 0) {
            return false;
        }
        Optional<MagazineItem> item = ModItems.defaultMagazineFor(weapon);
        Optional<Item> round = weapon.loadedAmmo(gun)
                .or(() -> weapon.profile().ammoItem().flatMap(BuiltInRegistries.ITEM::getOptional));
        if (item.isEmpty() || round.isEmpty()) {
            return false;
        }
        ItemStack magazine = new ItemStack(item.get());
        int rounds = Math.min(weapon.rounds(gun), item.get().capacity());
        setContents(magazine, Magazine.<Item>empty(item.get().capacity()).push(round.get(), rounds));
        insert(gun, weapon, magazine);
        return true;
    }

    /**
     * effects: returns the inventory slots holding a magazine {@code weapon}
     * takes with rounds in it, in inventory order -- hotbar first, as the
     * container numbers them
     */
    public static List<Integer> loadedMagazineSlots(Player player, RangedWeapon weapon) {
        List<Integer> slots = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (accepts(weapon, candidate) && contents(candidate).rounds() > 0) {
                slots.add(slot);
            }
        }
        return slots;
    }

    /** effects: returns the weapon for {@code gun}, or empty if the protocol has none for it */
    public static Optional<RangedWeapon> weaponOf(ItemStack gun) {
        return Optional.ofNullable(RangedWeapons.resolve(gun));
    }
}
