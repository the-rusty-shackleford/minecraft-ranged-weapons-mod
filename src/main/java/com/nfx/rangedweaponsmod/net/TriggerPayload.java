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
package com.nfx.rangedweaponsmod.net;

import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the trigger was pressed or released. One per change,
 * never one per shot -- the server's clock decides the shots.
 *
 * @param held whether the trigger is now held
 */
public record TriggerPayload(boolean held) implements CustomPacketPayload {

    public static final Type<TriggerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "trigger"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TriggerPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, TriggerPayload::held, TriggerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
