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
package com.chunkworks.rangedweaponsmod.domain;

/**
 * What the ammo counter reads (D-0027): the rounds loaded over every round the player has for the
 * gun, the loaded ones included, so the second number falls only as the gun fires. A full rifle
 * with two full magazines carried reads {@code 30 / 90}. Creative's unlimited supply is one
 * infinity sign.
 */
public final class Counter {
    /** The whole text under unlimited ammunition. */
    public static final String UNLIMITED = "∞";

    private Counter() {}

    /**
     * requires: {@code loaded} and {@code reserve} at least zero, unless {@code unlimited}<br>
     * effects: returns {@link #UNLIMITED} when unlimited, else {@code "loaded / total"}, the total
     * being the loaded rounds and the reserve a reload can reach<br>
     * throws: IllegalArgumentException for a negative count when not unlimited
     */
    public static String text(boolean unlimited, int loaded, int reserve) {
        if (unlimited) {
            return UNLIMITED;
        }
        if (loaded < 0 || reserve < 0) {
            throw new IllegalArgumentException("negative rounds: " + loaded + ", " + reserve);
        }
        return loaded + " / " + (loaded + reserve);
    }
}
