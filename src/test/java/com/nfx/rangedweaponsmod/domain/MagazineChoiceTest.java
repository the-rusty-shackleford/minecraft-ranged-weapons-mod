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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * Partitions: no magazines carried; one; several; a swap with no previous
 * swap, one before some, one after all (wrap), one that is no longer loaded.
 */
final class MagazineChoiceTest {

    @Test
    void aReloadTakesTheFirstLoadedMagazineCarried() {
        assertEquals(OptionalInt.empty(), MagazineChoice.forReload(List.of()));
        assertEquals(OptionalInt.of(5), MagazineChoice.forReload(List.of(5)));
        assertEquals(OptionalInt.of(3), MagazineChoice.forReload(List.of(3, 7, 20)));
    }

    @Test
    void aFirstSwapTakesTheFirstLoadedMagazine() {
        assertEquals(OptionalInt.of(3), MagazineChoice.forSwap(List.of(3, 7, 20), OptionalInt.empty()));
        assertEquals(OptionalInt.empty(), MagazineChoice.forSwap(List.of(), OptionalInt.of(3)));
    }

    @Test
    void repeatedSwapsWalkEveryMagazineInInventoryOrderAndWrap() {
        List<Integer> carried = List.of(3, 7, 20);
        assertEquals(OptionalInt.of(7), MagazineChoice.forSwap(carried, OptionalInt.of(3)));
        assertEquals(OptionalInt.of(20), MagazineChoice.forSwap(carried, OptionalInt.of(7)));
        assertEquals(OptionalInt.of(3), MagazineChoice.forSwap(carried, OptionalInt.of(20)));
    }

    @Test
    void aSwapAfterASlotThatIsNoLongerLoadedContinuesFromThere() {
        assertEquals(OptionalInt.of(20), MagazineChoice.forSwap(List.of(3, 20), OptionalInt.of(7)));
        assertEquals(OptionalInt.of(3), MagazineChoice.forSwap(List.of(3), OptionalInt.of(3)));
    }
}
