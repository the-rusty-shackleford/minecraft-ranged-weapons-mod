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
package com.nfx.rangedweaponsmod.domain;

/**
 * How a player's stance scales a weapon's spread: crouching tightens it,
 * moving loosens it, sprinting and being airborne loosen it more.
 *
 * <p>The factors multiply. Sprinting supersedes moving rather than stacking
 * with it -- a sprinting player is moving, and counting that twice would make
 * sprint the only factor that mattered. The product is floored so no
 * combination reduces spread to nothing.
 */
public final class StanceSpread {
    private StanceSpread() {}

    /** The multiplier never falls below this, whatever the factors. */
    public static final float MIN_MULTIPLIER = 0.1f;

    /**
     * The player's stance at the moment of a shot. Immutable.
     *
     * @param sprinting whether the player is sprinting
     * @param moving    whether the player is moving at all
     * @param crouching whether the player is crouching
     * @param airborne  whether the player is off the ground
     */
    public record Stance(boolean sprinting, boolean moving, boolean crouching, boolean airborne) {}

    /**
     * The factor each stance applies. Immutable.
     *
     * <p>RI: every factor finite and {@code > 0}.
     *
     * @param crouching applied while crouching
     * @param moving    applied while moving and not sprinting
     * @param sprinting applied while sprinting
     * @param airborne  applied while airborne
     */
    public record Factors(float crouching, float moving, float sprinting, float airborne) {
        /** The defaults: steadier crouched, looser on the move, loosest sprinting or in the air. */
        public static final Factors DEFAULT = new Factors(0.7f, 1.4f, 2.0f, 1.8f);

        /**
         * @throws IllegalArgumentException if any factor is not finite or not positive
         */
        public Factors {
            requirePositive("crouching", crouching);
            requirePositive("moving", moving);
            requirePositive("sprinting", sprinting);
            requirePositive("airborne", airborne);
        }

        private static void requirePositive(String name, float value) {
            if (!(value > 0.0f) || Float.isInfinite(value)) {
                throw new IllegalArgumentException(name + " must be finite and > 0, was " + value);
            }
        }
    }

    /**
     * effects: returns the product of the factors that apply to
     * {@code stance} -- sprinting instead of moving when both hold -- and
     * never less than {@link #MIN_MULTIPLIER}
     *
     * @param stance  the player's stance
     * @param factors the weapon's factors
     * @return what to multiply the weapon's spread by
     */
    public static float multiplier(Stance stance, Factors factors) {
        float multiplier = 1.0f;
        if (stance.sprinting()) {
            multiplier *= factors.sprinting();
        } else if (stance.moving()) {
            multiplier *= factors.moving();
        }
        if (stance.crouching()) {
            multiplier *= factors.crouching();
        }
        if (stance.airborne()) {
            multiplier *= factors.airborne();
        }
        return Math.max(MIN_MULTIPLIER, multiplier);
    }
}
