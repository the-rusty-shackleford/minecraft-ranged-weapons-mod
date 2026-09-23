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
 * How guns take their rounds on a server: the one rule every player on it
 * shares.
 *
 * <p>A gun is either fed from a detachable magazine or loaded directly
 * into an internal store; which is the gun's own design. The mode decides
 * whether that design is honoured: under {@link #MAGAZINES} it is, and
 * under {@link #LOOSE} every gun is loaded directly, from loose rounds
 * wherever the player carries them.
 */
public enum FeedMode {
    /**
     * The pistol, rifles and machine gun take a detachable magazine and hold
     * nothing without one; the shotgun and revolver load loose rounds from
     * the inventory.
     */
    MAGAZINES,
    /**
     * Every gun loads loose rounds, drawn from the whole inventory and then
     * from carried bags; no magazine is needed, and one in a gun is handed
     * back.
     */
    LOOSE;

    /** effects: returns whether a magazine-fed gun needs its magazine under this mode */
    public boolean requiresMagazines() {
        return this == MAGAZINES;
    }
}
