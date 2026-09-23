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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Partitions: each mode; the default a config file names is the first constant. */
final class FeedModeTest {

    @Test
    void magazinesModeRequiresThemAndLooseModeDoesNot() {
        assertTrue(FeedMode.MAGAZINES.requiresMagazines());
        assertFalse(FeedMode.LOOSE.requiresMagazines());
    }

    @Test
    void theModesAreExactlyTwoAndMagazinesComesFirst() {
        assertEquals(2, FeedMode.values().length);
        assertEquals(FeedMode.MAGAZINES, FeedMode.values()[0]);
        assertEquals(FeedMode.LOOSE, FeedMode.valueOf("LOOSE"));
    }
}
