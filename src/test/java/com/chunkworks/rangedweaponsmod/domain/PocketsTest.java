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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.chunkworks.rangedweaponsmod.domain.Pockets.Stack;
import com.chunkworks.rangedweaponsmod.domain.Pockets.Take;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Pockets: none, one, several; a pocket empty or holding one
 * or several stacks. Kinds: none; repeats within a pocket and across
 * pockets (once, first met first); a kind first met in a later pocket
 * comes after every kind of the earlier; kinds not accepted are left out.
 * Count: absent; within one pocket; across pockets. Plan: zero; within
 * the first stack; across stacks of one pocket; across pockets with the
 * first emptied before the next is touched; exactly everything; more
 * than in reach and negative refused; stacks of other kinds skipped.
 * Values: a stack or a take of no rounds refused; the input rows are
 * copied; equality by contents.
 */
final class PocketsTest {

    private static final String ROUND = "round";
    private static final String NUGGET = "nugget";
    private static final String SHELL = "shell";

    private static Stack<String> s(String kind, int count) {
        return new Stack<>(kind, count);
    }

    /** The inventory holding rounds and a shell, then a bag holding nuggets and more rounds. */
    private static Pockets<String> carried() {
        return Pockets.of(List.of(
                List.of(s(ROUND, 5), s(SHELL, 2), s(ROUND, 3)),
                List.of(s(NUGGET, 4), s(ROUND, 60))));
    }

    @Test
    void kindsAreMetPocketByPocketEachOnceAndOnlyTheAcceptedOnes() {
        assertEquals(List.of(), Pockets.<String>none().kinds(k -> true));
        assertEquals(List.of(), Pockets.of(List.of(List.<Stack<String>>of())).kinds(k -> true));
        assertEquals(List.of(ROUND, SHELL, NUGGET), carried().kinds(k -> true));
        assertEquals(List.of(ROUND, NUGGET), carried().kinds(k -> !k.equals(SHELL)));
        assertEquals(List.of(), carried().kinds(k -> false));
        // A kind kept only in the bag comes after every kind of the pockets, however many there are of it.
        Pockets<String> bagFirstByCount = Pockets.of(List.of(List.of(s(SHELL, 1)), List.of(s(ROUND, 99))));
        assertEquals(List.of(SHELL, ROUND), bagFirstByCount.kinds(k -> true));
    }

    @Test
    void countSumsEveryStackOfTheKindOverEveryPocket() {
        assertEquals(0, Pockets.<String>none().count(ROUND));
        assertEquals(68, carried().count(ROUND));
        assertEquals(2, carried().count(SHELL));
        assertEquals(4, carried().count(NUGGET));
        assertEquals(0, carried().count("slug"));
    }

    @Test
    void aPlanDrawsFirstPocketFirstAndFirstStackFirstEmptyingEachBeforeTheNext() {
        Pockets<String> pockets = carried();
        assertEquals(List.of(), pockets.plan(ROUND, 0));
        assertEquals(List.of(new Take(0, 0, 4)), pockets.plan(ROUND, 4));
        assertEquals(List.of(new Take(0, 0, 5), new Take(0, 2, 1)), pockets.plan(ROUND, 6));
        assertEquals(List.of(new Take(0, 0, 5), new Take(0, 2, 3), new Take(1, 1, 2)), pockets.plan(ROUND, 10));
        assertEquals(List.of(new Take(0, 0, 5), new Take(0, 2, 3), new Take(1, 1, 60)), pockets.plan(ROUND, 68));
        assertEquals(List.of(new Take(1, 0, 4)), pockets.plan(NUGGET, 4));
        assertEquals(List.of(), pockets.plan("slug", 0));
    }

    @Test
    void aPlanForMoreThanIsInReachOrForANegativeCountIsRefused() {
        Pockets<String> pockets = carried();
        assertThrows(IllegalArgumentException.class, () -> pockets.plan(ROUND, 69));
        assertThrows(IllegalArgumentException.class, () -> pockets.plan("slug", 1));
        assertThrows(IllegalArgumentException.class, () -> pockets.plan(ROUND, -1));
        assertThrows(IllegalArgumentException.class, () -> Pockets.<String>none().plan(ROUND, 1));
    }

    @Test
    void aPlanTakesWhatItSaysNoMoreAndNoLess() {
        Pockets<String> pockets = carried();
        for (int n = 0; n <= pockets.count(ROUND); n++) {
            int drawn = 0;
            for (Take take : pockets.plan(ROUND, n)) {
                Stack<String> stack = pockets.stacks(take.pocket()).get(take.stack());
                assertEquals(ROUND, stack.kind());
                assertEquals(true, take.count() <= stack.count());
                drawn += take.count();
            }
            assertEquals(n, drawn);
        }
    }

    @Test
    void stacksAndTakesHoldAtLeastOneRoundAndTheRowsAreCopied() {
        assertThrows(IllegalArgumentException.class, () -> s(ROUND, 0));
        assertThrows(NullPointerException.class, () -> new Stack<String>(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new Take(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Take(-1, 0, 1));
        List<List<Stack<String>>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(List.of(s(ROUND, 5))));
        Pockets<String> pockets = Pockets.of(rows);
        rows.get(0).add(s(ROUND, 9));
        rows.add(List.of(s(SHELL, 1)));
        assertEquals(5, pockets.count(ROUND));
        assertEquals(1, pockets.size());
        assertThrows(UnsupportedOperationException.class, () -> pockets.stacks(0).add(s(ROUND, 1)));
        assertThrows(IndexOutOfBoundsException.class, () -> pockets.stacks(1));
    }

    @Test
    void pocketsAreEqualByContents() {
        assertEquals(carried(), carried());
        assertEquals(carried().hashCode(), carried().hashCode());
        assertNotEquals(carried(), Pockets.none());
        assertNotEquals(Pockets.of(List.of(List.of(s(ROUND, 1)), List.of(s(SHELL, 1)))),
                Pockets.of(List.of(List.of(s(SHELL, 1)), List.of(s(ROUND, 1)))));
    }
}
