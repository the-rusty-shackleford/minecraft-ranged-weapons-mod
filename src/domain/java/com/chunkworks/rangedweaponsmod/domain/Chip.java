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

import java.util.Optional;

/**
 * A lock-on chip's wear (D-0029): the rocket launcher locks only with one fitted, and each guided
 * launch spends one of its charges. Straight launches, and locks never fired, spend nothing; the
 * last charge burns the chip out, and a burnt chip is no chip at all.
 *
 * <p>Immutable. AF: a chip that has given {@code used} guided launches of the {@code charges} it
 * was made with.<br>
 * RI: {@code charges >= 1} and {@code 0 <= used < charges}: a chip with nothing left does not exist.
 *
 * @param used    guided launches given
 * @param charges guided launches it was made with
 */
public record Chip(int used, int charges) {

    /** Guided launches in a new chip. */
    public static final int CHARGES = 8;

    /**
     * @throws IllegalArgumentException if the RI does not hold
     */
    public Chip {
        if (charges < 1) {
            throw new IllegalArgumentException("a chip holds at least one charge, was made with " + charges);
        }
        if (used < 0 || used >= charges) {
            throw new IllegalArgumentException("a chip of " + charges + " has given 0 to " + (charges - 1) + " launches, not " + used);
        }
    }

    /** effects: returns a new chip, with every one of its {@link #CHARGES} to give */
    public static Chip fresh() {
        return new Chip(0, CHARGES);
    }

    /** effects: returns how many guided launches it has left, at least one */
    public int left() {
        return charges - used;
    }

    /**
     * effects: returns the chip after one guided launch: worn by one, or empty when that launch
     * was its last
     */
    public Optional<Chip> afterGuidedLaunch() {
        return used + 1 < charges ? Optional.of(new Chip(used + 1, charges)) : Optional.empty();
    }
}
