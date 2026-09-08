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
package com.nfx.rangedweaponsmod.net;

import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the reload key was pressed -- with Shift, a swap (out
 * with what is loaded, in with the next magazine or kind) rather than a
 * top-up.
 *
 * @param swap whether Shift was held
 */
public record ReloadPayload(boolean swap) implements CustomPacketPayload {

    public static final Type<ReloadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "reload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.<RegistryFriendlyByteBuf>cast().map(ReloadPayload::new, ReloadPayload::swap);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
