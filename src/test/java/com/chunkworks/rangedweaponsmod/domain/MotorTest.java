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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Age: 0 / within the ejection / the tick the motor lights / mid-burn / at full burn /
 * after it / negative. Flown: 0 / just short of the arming distance / at it / beyond / negative or
 * not finite.
 */
final class MotorTest {

    @Test
    void theRocketLeavesTheTubeAtTheEjectionSpeed() {
        assertEquals(Motor.EJECT_SPEED, Motor.speed(0));
        assertEquals(Motor.EJECT_SPEED, Motor.speed(Motor.EJECT_TICKS - 1));
        assertEquals(Motor.EJECT_SPEED, Motor.speed(Motor.EJECT_TICKS));
    }

    @Test
    void theMotorRampsToTopSpeedAndHoldsIt() {
        int mid = (Motor.EJECT_TICKS + Motor.FULL_BURN_TICKS) / 2;
        assertTrue(Motor.speed(mid) > Motor.EJECT_SPEED && Motor.speed(mid) < Motor.TOP_SPEED);
        assertEquals(Motor.TOP_SPEED, Motor.speed(Motor.FULL_BURN_TICKS));
        assertEquals(Motor.TOP_SPEED, Motor.speed(Motor.LIFETIME_TICKS));
        for (int age = 1; age <= Motor.LIFETIME_TICKS; age++) {
            assertTrue(Motor.speed(age) >= Motor.speed(age - 1), "never slower at " + age);
        }
    }

    @Test
    void theRocketSteersOnlyOnceItsMotorHasLit() {
        assertFalse(Motor.steers(0));
        assertFalse(Motor.steers(Motor.EJECT_TICKS - 1));
        assertTrue(Motor.steers(Motor.EJECT_TICKS));
    }

    @Test
    void theWarheadArmsAtTheArmingDistance() {
        assertFalse(Motor.armed(0.0));
        assertFalse(Motor.armed(Motor.ARMING_DISTANCE - 1e-9));
        assertTrue(Motor.armed(Motor.ARMING_DISTANCE));
        assertTrue(Motor.armed(50.0));
    }

    @Test
    void badInputsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Motor.speed(-1));
        assertThrows(IllegalArgumentException.class, () -> Motor.steers(-1));
        assertThrows(IllegalArgumentException.class, () -> Motor.armed(-0.1));
        assertThrows(IllegalArgumentException.class, () -> Motor.armed(Double.NaN));
    }
}
