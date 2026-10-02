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
package com.chunkworks.rangedweaponsmod.net;

import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: what the launcher's reticle is on, as the client shows it (D-0028), sent on
 * each change while the sight is up, and {@code -1} when it is on nothing or comes down. The server
 * validates it before it counts ({@code Seeking.reported}).
 *
 * @param entity the entity's network id, or -1
 */
public record SeekPayload(int entity) implements CustomPacketPayload {

    public static final Type<SeekPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "seek"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SeekPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, SeekPayload::entity, SeekPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
