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

import com.nfx.rangedweaponsmod.domain.StanceSpread.Factors;
import com.nfx.rangedweaponsmod.domain.StanceSpread.Stance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link StanceSpread}.
 *
 * <p>Partitions. Stance: still / moving / sprinting / crouching / airborne,
 * alone; sprinting and moving together (sprint supersedes); crouching while
 * moving, sprinting, airborne (products); everything at once. Floor: tiny
 * factors clamp to the minimum. Factors RI: zero / negative / NaN /
 * infinite.
 */
final class StanceSpreadTest {

    private static final float EPS = 1e-6f;
    private static final Factors F = new Factors(0.5f, 2.0f, 3.0f, 4.0f);

    private static float mult(boolean sprinting, boolean moving, boolean crouching, boolean airborne) {
        return StanceSpread.multiplier(new Stance(sprinting, moving, crouching, airborne), F);
    }

    @Test
    void standingStillIsTheWeaponsOwnSpread() {
        assertEquals(1.0f, mult(false, false, false, false), EPS);
    }

    @Test
    void eachStanceAloneAppliesItsFactor() {
        assertEquals(2.0f, mult(false, true, false, false), EPS);
        assertEquals(3.0f, mult(true, true, false, false), EPS);
        assertEquals(0.5f, mult(false, false, true, false), EPS);
        assertEquals(4.0f, mult(false, false, false, true), EPS);
    }

    @Test
    void sprintingSupersedesMovingRatherThanStacking() {
        assertEquals(3.0f, mult(true, true, false, false), EPS);
        assertEquals(3.0f, mult(true, false, false, false), EPS);
    }

    @Test
    void factorsMultiply() {
        assertEquals(1.0f, mult(false, true, true, false), EPS);          // moving x crouching
        assertEquals(1.5f, mult(true, true, true, false), EPS);          // sprinting x crouching
        assertEquals(2.0f, mult(false, false, true, true), EPS);         // crouching x airborne
        assertEquals(6.0f, mult(true, true, true, true), EPS);           // sprinting x crouching x airborne
    }

    @Test
    void theProductIsFlooredAtTheMinimum() {
        Factors tiny = new Factors(0.01f, 0.01f, 0.01f, 0.01f);
        assertEquals(StanceSpread.MIN_MULTIPLIER,
                StanceSpread.multiplier(new Stance(true, true, true, true), tiny), EPS);
    }

    @Test
    void theDefaultsTightenCrouchedAndLoosenOnTheMove() {
        Factors d = Factors.DEFAULT;
        assertEquals(0.7f, StanceSpread.multiplier(new Stance(false, false, true, false), d), EPS);
        assertEquals(1.4f, StanceSpread.multiplier(new Stance(false, true, false, false), d), EPS);
        assertEquals(2.0f, StanceSpread.multiplier(new Stance(true, true, false, false), d), EPS);
        assertEquals(1.8f, StanceSpread.multiplier(new Stance(false, false, false, true), d), EPS);
    }

    @Test
    void factorsMustBeFiniteAndPositive() {
        assertThrows(IllegalArgumentException.class, () -> new Factors(0.0f, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Factors(1, -1.0f, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Factors(1, 1, Float.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> new Factors(1, 1, 1, Float.POSITIVE_INFINITY));
    }
}
