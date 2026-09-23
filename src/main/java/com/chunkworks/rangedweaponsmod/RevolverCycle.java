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
package com.chunkworks.rangedweaponsmod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.chunkworks.rangedweaponsmod.domain.RevolverAction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable record of the last accepted shot, saved and synchronized with its gun.
 * AF: the cylinder's next chamber and the world tick of its last firing cycle.
 * RI: chamber in [0,5]. A new stack has no cycle component and renders at rest.
 */
public record RevolverCycle(int chamber, long firedAt) {
    public static final Codec<RevolverCycle> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0,5).fieldOf("chamber").forGetter(RevolverCycle::chamber),
            Codec.LONG.fieldOf("fired_at").forGetter(RevolverCycle::firedAt)
    ).apply(i,RevolverCycle::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,RevolverCycle> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,RevolverCycle::chamber,
            ByteBufCodecs.VAR_LONG,RevolverCycle::firedAt,RevolverCycle::new);

    /** effects: records an accepted cycle; throws: IllegalArgumentException for a chamber outside [0,5]. */
    public RevolverCycle {
        if (chamber < 0 || chamber >= RevolverAction.CHAMBERS)
            throw new IllegalArgumentException("invalid revolver chamber");
    }

    /** effects: returns the next chamber, wrapping once per six shots, at the given world tick. */
    public RevolverCycle fired(long time) { return new RevolverCycle((chamber+1)%RevolverAction.CHAMBERS,time); }
}
