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
import com.nfx.rangedweaponsmod.domain.Magazine;
import com.nfx.rangedweaponsmod.domain.Magazine.Segment;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/**
 * What a magazine holds, as the game stores it on the magazine's stack: the
 * domain's {@link Magazine} segments, rounds named by item. Immutable.
 * Persistent and synced, so a magazine keeps its load through a save and
 * the HUD can name what fires next.
 *
 * <p>RI: every run's count is at least one. The capacity is the item's, not
 * the component's, so a magazine whose item shrinks in a later version is
 * clamped when read, never rejected.
 *
 * @param runs the runs of rounds, first to fire first
 */
public record MagazineContents(List<Run> runs) {

    /** A run of one kind of round. */
    public record Run(Item round, int count) {
        public static final Codec<Run> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("round").forGetter(Run::round),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(Run::count)
        ).apply(i, Run::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Run> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM), Run::round,
                ByteBufCodecs.VAR_INT, Run::count,
                Run::new);
    }

    public static final MagazineContents EMPTY = new MagazineContents(List.of());

    public static final Codec<MagazineContents> CODEC = Run.CODEC.listOf().xmap(MagazineContents::new, MagazineContents::runs);

    public static final StreamCodec<RegistryFriendlyByteBuf, MagazineContents> STREAM_CODEC =
            Run.STREAM_CODEC.apply(ByteBufCodecs.list()).map(MagazineContents::new, MagazineContents::runs);

    public MagazineContents {
        runs = List.copyOf(runs);
    }

    /**
     * effects: returns these contents as a magazine of {@code capacity},
     * runs that no longer fit dropped from the end
     */
    public Magazine<Item> toMagazine(int capacity) {
        Magazine<Item> magazine = Magazine.empty(capacity);
        for (Run run : runs) {
            int count = Math.min(run.count(), magazine.space());
            if (count <= 0) {
                break;
            }
            magazine = magazine.push(run.round(), count);
        }
        return magazine;
    }

    /** effects: returns the contents of {@code magazine} */
    public static MagazineContents of(Magazine<Item> magazine) {
        return new MagazineContents(magazine.segments().stream()
                .map(segment -> new Run(segment.round(), segment.count())).toList());
    }

    /** effects: returns the segments as the domain sees them */
    public List<Segment<Item>> segments() {
        return runs.stream().map(run -> new Segment<>(run.round(), run.count())).toList();
    }
}
