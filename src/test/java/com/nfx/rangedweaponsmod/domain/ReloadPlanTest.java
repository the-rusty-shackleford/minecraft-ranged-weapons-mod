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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ReloadPlan}.
 *
 * <p>Partitions. roundsToLoad: magazine empty / partly full / full;
 * ammunition none / fewer than the space / exactly the space / more;
 * invalid capacity, rounds, ammunition. durationTicks: 0 (floored to 1) /
 * 1 / larger / negative. done: before / exactly at / after the end; a clock
 * that ran backwards.
 */
final class ReloadPlanTest {

    @Test
    void anEmptyMagazineLoadsAsMuchAsThereIsUpToCapacity() {
        assertEquals(30, ReloadPlan.roundsToLoad(0, 30, 64));
        assertEquals(12, ReloadPlan.roundsToLoad(0, 30, 12));
        assertEquals(30, ReloadPlan.roundsToLoad(0, 30, 30));
    }

    @Test
    void aPartMagazineLoadsOnlyTheSpace() {
        assertEquals(20, ReloadPlan.roundsToLoad(10, 30, 64));
        assertEquals(5, ReloadPlan.roundsToLoad(10, 30, 5));
        assertEquals(20, ReloadPlan.roundsToLoad(10, 30, 20));
    }

    @Test
    void aFullMagazineLoadsNothing() {
        assertEquals(0, ReloadPlan.roundsToLoad(30, 30, 64));
    }

    @Test
    void noAmmunitionLoadsNothing() {
        assertEquals(0, ReloadPlan.roundsToLoad(0, 30, 0));
    }

    @Test
    void impossibleArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ReloadPlan.roundsToLoad(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ReloadPlan.roundsToLoad(31, 30, 1));
        assertThrows(IllegalArgumentException.class, () -> ReloadPlan.roundsToLoad(-1, 30, 1));
        assertThrows(IllegalArgumentException.class, () -> ReloadPlan.roundsToLoad(0, 30, -1));
    }

    @Test
    void durationIsTheFullReloadTimeButNeverZero() {
        assertEquals(1, ReloadPlan.durationTicks(0));
        assertEquals(1, ReloadPlan.durationTicks(1));
        assertEquals(50, ReloadPlan.durationTicks(50));
        assertThrows(IllegalArgumentException.class, () -> ReloadPlan.durationTicks(-1));
    }

    @Test
    void doneAtTheEndAndAfterNotBefore() {
        assertFalse(ReloadPlan.done(149, 100, 50));
        assertTrue(ReloadPlan.done(150, 100, 50));
        assertTrue(ReloadPlan.done(151, 100, 50));
    }

    @Test
    void aClockBeforeTheStartMeansItRanBackwardsAndTheReloadIsOver() {
        assertTrue(ReloadPlan.done(99, 100, 50));
        assertTrue(ReloadPlan.done(0, 1_000_000, 50));
    }
}
