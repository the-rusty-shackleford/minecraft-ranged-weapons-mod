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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link AmmoChoice}.
 *
 * <p>Partitions. Magazine: empty / part full. Loaded round: known / not.
 * Carried: none / one / two in either order / the loaded round carried or
 * not. Negative rounds.
 */
final class AmmoChoiceTest {

    @Test
    void anEmptyMagazineTakesTheFirstRoundCarried() {
        assertEquals(Optional.of("slug"), AmmoChoice.choose(Optional.of("shell"), 0, List.of("slug", "shell")));
        assertEquals(Optional.of("shell"), AmmoChoice.choose(Optional.empty(), 0, List.of("shell", "slug")));
        assertEquals(Optional.empty(), AmmoChoice.choose(Optional.of("shell"), 0, List.of()));
    }

    @Test
    void aPartMagazineTopsUpWithWhatItHoldsAndNothingElse() {
        assertEquals(Optional.of("shell"), AmmoChoice.choose(Optional.of("shell"), 3, List.of("slug", "shell")));
        assertEquals(Optional.of("shell"), AmmoChoice.choose(Optional.of("shell"), 3, List.of("slug")), "the slug stays in the pack");
        assertEquals(Optional.of("shell"), AmmoChoice.choose(Optional.of("shell"), 3, List.of()));
    }

    @Test
    void aPartMagazineOfUnknownRoundTakesTheFirstCarried() {
        // A gun filled before rounds were remembered.
        assertEquals(Optional.of("slug"), AmmoChoice.choose(Optional.empty(), 3, List.of("slug", "shell")));
        assertEquals(Optional.empty(), AmmoChoice.choose(Optional.empty(), 3, List.of()));
    }

    @Test
    void roundsMayNotBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> AmmoChoice.choose(Optional.empty(), -1, List.of()));
    }

    @Test
    void aSwapChangesToTheKindAfterTheLoadedOneAndWraps() {
        assertEquals(Optional.of("slug"), AmmoChoice.next(Optional.of("shell"), List.of("shell", "slug")));
        assertEquals(Optional.of("shell"), AmmoChoice.next(Optional.of("slug"), List.of("shell", "slug")));
        assertEquals(Optional.of("slug"), AmmoChoice.next(Optional.of("shell"), List.of("shell", "slug", "flare")));
    }

    @Test
    void aSwapWithOnlyTheLoadedKindCarriedHasNothingToChangeTo() {
        assertEquals(Optional.empty(), AmmoChoice.next(Optional.of("shell"), List.of("shell")));
        assertEquals(Optional.empty(), AmmoChoice.next(Optional.of("shell"), List.of()));
        assertEquals(Optional.empty(), AmmoChoice.next(Optional.empty(), List.of()));
    }

    @Test
    void aSwapFromAnUnknownOrUncarriedKindTakesTheFirstCarried() {
        assertEquals(Optional.of("slug"), AmmoChoice.next(Optional.empty(), List.of("slug", "shell")));
        assertEquals(Optional.of("slug"), AmmoChoice.next(Optional.of("flare"), List.of("slug", "shell")));
    }
}
