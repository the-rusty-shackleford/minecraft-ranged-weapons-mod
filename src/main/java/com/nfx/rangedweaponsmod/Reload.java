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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nfx.rangedweaponsmod.domain.ReloadPlan;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A reload in progress, on the gun's stack: a property of the gun, so a gun
 * dropped mid-reload is still mid-reload when picked up. Immutable.
 *
 * <p>Synced to the client with the stack, so the HUD can show progress.
 *
 * <p>RI: {@code durationTicks >= 1}.
 *
 * @param startedAt     the game time the reload began
 * @param durationTicks how long it takes
 */
public record Reload(long startedAt, int durationTicks) {

    public static final Codec<Reload> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("started_at").forGetter(Reload::startedAt),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("duration_ticks").forGetter(Reload::durationTicks)
    ).apply(i, Reload::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Reload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Reload::startedAt,
            ByteBufCodecs.VAR_INT, Reload::durationTicks,
            Reload::new);

    /**
     * @throws IllegalArgumentException if {@code durationTicks < 1}
     */
    public Reload {
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks must be >= 1, was " + durationTicks);
        }
    }

    /** effects: returns whether this reload is over at {@code now}, per {@link ReloadPlan#done} */
    public boolean done(long now) {
        return ReloadPlan.done(now, startedAt, durationTicks);
    }

    /** effects: returns how far along this reload is at {@code now}, in {@code [0, 1]} */
    public float progress(long now) {
        if (now < startedAt) {
            return 1.0f;
        }
        return Math.min(1.0f, (float) (now - startedAt) / durationTicks);
    }
}
