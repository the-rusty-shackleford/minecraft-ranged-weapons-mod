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
package com.nfx.rangedweaponsmod.domain;

import java.util.List;
import java.util.Optional;

/**
 * Which round a reload loads when a gun's family has more than one -- a
 * shotgun with shells and slugs both in the pack.
 *
 * <p>A magazine is one kind of round at a time. While rounds remain, a
 * reload tops up with the round that is loaded, and nothing else, even
 * if none of it is carried (then nothing loads). Once the magazine is
 * empty, the first accepted round the player carries, in inventory order,
 * hotbar first -- so the player picks by where they keep the round they
 * want next.
 */
public final class AmmoChoice {
    private AmmoChoice() {}

    /**
     * effects: returns the round to load: {@code loaded} while
     * {@code rounds > 0} and it is known; otherwise the first of
     * {@code carried}; empty if the magazine is empty and nothing accepted
     * is carried<br>
     * throws: {@link IllegalArgumentException} if {@code rounds < 0}
     *
     * @param loaded  the round the magazine holds, if known
     * @param rounds  rounds in the magazine now
     * @param carried the accepted rounds the player carries, in inventory order, each once
     * @param <T>     however a round is named
     * @return the round to load
     */
    public static <T> Optional<T> choose(Optional<T> loaded, int rounds, List<T> carried) {
        if (rounds < 0) {
            throw new IllegalArgumentException("rounds must be >= 0, was " + rounds);
        }
        if (rounds > 0 && loaded.isPresent()) {
            return loaded;
        }
        return carried.stream().findFirst();
    }
}
