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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FireClock}.
 *
 * <p>Partitions. ready: now before / exactly at / after nextShotAt; nextShotAt
 * ahead by exactly the maximum rate (not ready) / by more (a backwards clock,
 * ready); a fresh clock (nextShotAt 0). next: rate 1 / the maximum / below 1
 * / above the maximum.
 */
final class FireClockTest {

    @Test
    void beforeTheScheduledTickIsNotReady() {
        assertFalse(FireClock.ready(99, 100));
    }

    @Test
    void exactlyAtTheScheduledTickIsReady() {
        assertTrue(FireClock.ready(100, 100));
    }

    @Test
    void afterTheScheduledTickIsReady() {
        assertTrue(FireClock.ready(101, 100));
    }

    @Test
    void aFreshClockIsReady() {
        assertTrue(FireClock.ready(0, 0));
        assertTrue(FireClock.ready(12345, 0));
    }

    @Test
    void scheduledAtTheMaximumRateAheadIsStillWaiting() {
        assertFalse(FireClock.ready(100, 100 + FireClock.MAX_RATE_TICKS));
    }

    @Test
    void scheduledFurtherAheadThanAnyRateMeansTheClockRanBackwards() {
        assertTrue(FireClock.ready(100, 100 + FireClock.MAX_RATE_TICKS + 1));
        assertTrue(FireClock.ready(0, 10_000_000L));
    }

    @Test
    void nextIsNowPlusTheRate() {
        assertEquals(103, FireClock.next(100, 3));
        assertEquals(101, FireClock.next(100, 1));
        assertEquals(100 + FireClock.MAX_RATE_TICKS, FireClock.next(100, FireClock.MAX_RATE_TICKS));
    }

    @Test
    void ratesOutsideTheClockAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> FireClock.next(100, 0));
        assertThrows(IllegalArgumentException.class, () -> FireClock.next(100, -3));
        assertThrows(IllegalArgumentException.class, () -> FireClock.next(100, FireClock.MAX_RATE_TICKS + 1));
    }

    @Test
    void aShotScheduledByNextIsNotReadyUntilItsTick() {
        long next = FireClock.next(100, 3);
        assertFalse(FireClock.ready(102, next));
        assertTrue(FireClock.ready(103, next));
    }
}
