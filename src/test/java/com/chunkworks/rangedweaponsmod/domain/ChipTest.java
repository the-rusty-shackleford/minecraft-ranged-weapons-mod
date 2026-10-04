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

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Chip}.
 *
 * <p>Partitions. Wear: fresh (used 0), middle (0 < used < charges - 1), last charge
 * (used = charges - 1); a single-charge chip, whose fresh is its last. Construction: charges 0
 * and negative; used negative, equal to the charges, above them.
 */
final class ChipTest {

    @Test
    void aFreshChipHasEveryChargeAndAGuidedLaunchSpendsOne() {
        Chip fresh = Chip.fresh();
        assertEquals(Chip.CHARGES, fresh.left());
        assertEquals(8, Chip.CHARGES, "eight guided launches to a chip (Rusty's call: worn per guided shot)");
        assertEquals(Optional.of(new Chip(1, Chip.CHARGES)), fresh.afterGuidedLaunch());
    }

    @Test
    void aChipInTheMiddleWearsByOne() {
        Chip chip = new Chip(3, 8);
        assertEquals(5, chip.left());
        assertEquals(Optional.of(new Chip(4, 8)), chip.afterGuidedLaunch());
    }

    @Test
    void theLastChargeBurnsTheChipOut() {
        Chip last = new Chip(7, 8);
        assertEquals(1, last.left());
        assertEquals(Optional.empty(), last.afterGuidedLaunch());
    }

    @Test
    void aSingleChargeChipIsFreshAndLastAtOnce() {
        Chip one = new Chip(0, 1);
        assertEquals(1, one.left());
        assertEquals(Optional.empty(), one.afterGuidedLaunch());
    }

    @Test
    void aChipHoldsAtLeastOneChargeAndHasGivenFewerThanItHeld() {
        assertThrows(IllegalArgumentException.class, () -> new Chip(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Chip(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Chip(-1, 8));
        assertThrows(IllegalArgumentException.class, () -> new Chip(8, 8), "a burnt chip does not exist");
        assertThrows(IllegalArgumentException.class, () -> new Chip(9, 8));
    }
}
