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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nfx.rangedweaponsmod.domain.Magazine.Segment;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Partitions: capacity (invalid, 1, larger); contents (empty, one segment,
 * several, full); push (zero, merging with the last segment, a new
 * segment, over capacity); pop (empty, last of a segment, mid-segment);
 * fill (nothing carried, less than the space, more, several kinds, an
 * already-loaded magazine); equality.
 */
final class MagazineTest {

    @Test
    void aMagazineNeedsRoomForAtLeastOneRound() {
        assertThrows(IllegalArgumentException.class, () -> Magazine.empty(0));
        assertThrows(IllegalArgumentException.class, () -> Magazine.empty(-3));
        assertEquals(1, Magazine.empty(1).capacity());
    }

    @Test
    void emptyIsEmpty() {
        Magazine<String> m = Magazine.empty(15);
        assertTrue(m.isEmpty());
        assertFalse(m.isFull());
        assertEquals(0, m.rounds());
        assertEquals(15, m.space());
        assertEquals(Optional.empty(), m.next());
        assertEquals(List.of(), m.segments());
    }

    @Test
    void pushingLoadsAfterWhatIsThereAndMergesARunOfTheSameRound() {
        Magazine<String> m = Magazine.<String>empty(15).push("shell", 4).push("shell", 2).push("slug", 3);
        assertEquals(List.of(new Segment<>("shell", 6), new Segment<>("slug", 3)), m.segments());
        assertEquals(9, m.rounds());
        assertEquals(Optional.of("shell"), m.next());
    }

    @Test
    void pushingNothingChangesNothingAndTooMuchIsRefused() {
        Magazine<String> m = Magazine.<String>empty(5).push("shell", 3);
        assertEquals(m, m.push("slug", 0));
        assertThrows(IllegalArgumentException.class, () -> m.push("slug", 3));
        assertThrows(IllegalArgumentException.class, () -> m.push("slug", -1));
        assertTrue(m.push("slug", 2).isFull());
    }

    @Test
    void poppingFiresTheFirstRoundFirstAndDropsAnEmptiedSegment() {
        Magazine<String> m = Magazine.<String>empty(15).push("shell", 1).push("slug", 2);
        m = m.pop();
        assertEquals(List.of(new Segment<>("slug", 2)), m.segments());
        assertEquals(Optional.of("slug"), m.next());
        m = m.pop();
        assertEquals(List.of(new Segment<>("slug", 1)), m.segments());
        m = m.pop();
        assertTrue(m.isEmpty());
        assertThrows(IllegalStateException.class, m::pop);
    }

    @Test
    void ofMergesAndDropsNothingButRefusesOverfill() {
        Magazine<String> m = Magazine.of(10, List.of(new Segment<>("a", 2), new Segment<>("a", 3), new Segment<>("b", 1)));
        assertEquals(List.of(new Segment<>("a", 5), new Segment<>("b", 1)), m.segments());
        assertThrows(IllegalArgumentException.class,
                () -> Magazine.of(3, List.of(new Segment<>("a", 2), new Segment<>("b", 2))));
        assertThrows(IllegalArgumentException.class, () -> new Segment<>("a", 0));
    }

    @Test
    void fillTakesTheFirstKindCarriedFirstThenTheNext() {
        Magazine<String> m = Magazine.empty(15);
        Magazine.Filled<String> filled = m.fill(List.of(Map.entry("slug", 4), Map.entry("shell", 64)));
        assertEquals(List.of(new Segment<>("slug", 4), new Segment<>("shell", 11)), filled.magazine().segments());
        assertEquals(List.of(Map.entry("slug", 4), Map.entry("shell", 11)), filled.taken());
        assertTrue(filled.magazine().isFull());
    }

    @Test
    void fillStopsWhenTheRoundsRunOutOrTheMagazineIsFull() {
        Magazine.Filled<String> few = Magazine.<String>empty(15).fill(List.of(Map.entry("shell", 3)));
        assertEquals(3, few.magazine().rounds());
        assertEquals(List.of(Map.entry("shell", 3)), few.taken());

        Magazine.Filled<String> none = Magazine.<String>empty(15).fill(List.of());
        assertTrue(none.magazine().isEmpty());
        assertEquals(List.of(), none.taken());

        Magazine.Filled<String> full = Magazine.<String>empty(2).push("shell", 2).fill(List.of(Map.entry("slug", 9)));
        assertEquals(List.of(), full.taken());
    }

    @Test
    void fillTopsUpAfterWhatIsLoadedSoTheLoadedRoundsStillFireFirst() {
        Magazine<String> m = Magazine.<String>empty(15).push("slug", 2);
        Magazine.Filled<String> filled = m.fill(List.of(Map.entry("shell", 5)));
        assertEquals(List.of(new Segment<>("slug", 2), new Segment<>("shell", 5)), filled.magazine().segments());
        assertEquals(Optional.of("slug"), filled.magazine().next());
    }

    @Test
    void equalMagazinesAreEqual() {
        Magazine<String> a = Magazine.<String>empty(15).push("shell", 2);
        Magazine<String> b = Magazine.of(15, List.of(new Segment<>("shell", 2)));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(Magazine.<String>empty(16).push("shell", 2)));
    }
}
