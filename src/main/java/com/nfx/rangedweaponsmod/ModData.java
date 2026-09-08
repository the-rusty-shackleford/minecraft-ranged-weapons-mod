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

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What this mod keeps in the game's own containers: the reload and the
 * inserted magazine on a gun's stack, the contents on a magazine's, the
 * trigger finger on a player, and the handling data map.
 */
public final class ModData {
    private ModData() {}

    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, RangedWeaponsMod.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, RangedWeaponsMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, RangedWeaponsMod.MOD_ID);

    /** The reload in progress on a gun, if any. Persistent and synced. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Reload>> RELOAD =
            COMPONENTS.registerComponentType("reload", builder -> builder
                    .persistent(Reload.CODEC)
                    .networkSynchronized(Reload.STREAM_CODEC));

    /**
     * The magazine inserted in a gun, whole -- its contents, its label, its
     * colour -- so it comes back out as it went in, less what was fired.
     * Persistent and synced: the HUD names and colours it.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<InsertedMagazine>> INSERTED_MAGAZINE =
            COMPONENTS.registerComponentType("inserted_magazine", builder -> builder
                    .persistent(InsertedMagazine.CODEC)
                    .networkSynchronized(InsertedMagazine.STREAM_CODEC));

    /** What a magazine holds, in firing order. Persistent and synced. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MagazineContents>> MAGAZINE_CONTENTS =
            COMPONENTS.registerComponentType("magazine_contents", builder -> builder
                    .persistent(MagazineContents.CODEC)
                    .networkSynchronized(MagazineContents.STREAM_CODEC));

    /** The screen a magazine is filled in. */
    public static final DeferredHolder<MenuType<?>, MenuType<MagazineMenu>> MAGAZINE_MENU =
            MENUS.register("magazine", () -> IMenuTypeExtension.create(MagazineMenu::fromNetwork));

    /**
     * A player's trigger finger. Transient: it is not saved and not synced,
     * because a held trigger does not survive a logout and the client keeps
     * its own copy of what it is pressing.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Gunnery>> GUNNERY =
            ATTACHMENTS.register("gunnery", () -> AttachmentType.builder(() -> Gunnery.RELEASED).build());

    /**
     * Item to {@link Handling}: how a gun feels in a player's hands. Any
     * datapack contributes at {@code data/rangedweaponsmod/data_maps/item/handling.json}.
     * Not synced: the server sends each shot's kick in the shot payload.
     */
    public static final DataMapType<Item, Handling> HANDLING = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "handling"),
            Registries.ITEM, Handling.CODEC).build();

    static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(ModData::registerDataMaps);
    }

    private static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(HANDLING);
    }
}
