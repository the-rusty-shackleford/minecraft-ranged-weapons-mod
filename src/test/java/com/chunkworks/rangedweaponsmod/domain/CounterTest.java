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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Partitions: unlimited (whatever the counts, negative included); loaded 0 / &gt; 0; reserve 0 /
 * &gt; 0; a negative loaded or reserve count.
 */
final class CounterTest {

    @Test
    void aFullRifleWithTwoFullMagazinesReadsThirtyOverNinety() {
        assertEquals("30 / 90", Counter.text(false, 30, 60));
    }

    @Test
    void withNoReserveBothNumbersAreTheLoadedRounds() {
        assertEquals("30 / 30", Counter.text(false, 30, 0));
        assertEquals("0 / 0", Counter.text(false, 0, 0));
    }

    @Test
    void anEmptyGunShowsWhatAReloadWouldBring() {
        assertEquals("0 / 45", Counter.text(false, 0, 45));
    }

    @Test
    void unlimitedIsOneInfinitySignWhateverTheCounts() {
        assertEquals("∞", Counter.text(true, 30, 60));
        assertEquals(Counter.UNLIMITED, Counter.text(true, -1, -1));
    }

    @Test
    void aNegativeCountIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Counter.text(false, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> Counter.text(false, 0, -1));
    }
}
