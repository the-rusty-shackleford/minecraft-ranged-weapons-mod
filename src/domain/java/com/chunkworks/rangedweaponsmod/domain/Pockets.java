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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The loose rounds a player can reach, as a reload sees them: a row of
 * pockets -- the inventory first, hotbar first within it, then each
 * carried bag -- each an ordered row of stacks. What kinds there are, how
 * many of one, and which stacks a draw takes from: first pocket first,
 * first stack first, so a bag is the reserve behind the pockets.
 *
 * <p>Immutable; the rows are copied on the way in. Rounds are named by
 * {@code T}, whatever the caller uses for an item, so this stays free of
 * the game.
 *
 * <p>RI: no row is null; every stack's count is {@code >= 1}.<br>
 * AF: AF(pockets) = "the reachable rounds in draw order: pocket 0's stacks
 * in order, then pocket 1's, and so on", pocket {@code i}'s stack
 * {@code j} addressed as {@code (i, j)}.
 *
 * @param <T> however a round is named
 */
public final class Pockets<T> {

    /**
     * A stack of one kind of round.
     *
     * @param kind  the round
     * @param count how many, at least one
     * @param <T>   however a round is named
     */
    public record Stack<T>(T kind, int count) {
        public Stack {
            Objects.requireNonNull(kind, "a stack needs a kind");
            if (count < 1) {
                throw new IllegalArgumentException("a stack holds at least one round, was " + count);
            }
        }
    }

    /**
     * One step of a draw: this many from one stack.
     *
     * @param pocket the pocket's index
     * @param stack  the stack's index within the pocket
     * @param count  how many from it, at least one
     */
    public record Take(int pocket, int stack, int count) {
        public Take {
            if (pocket < 0 || stack < 0) {
                throw new IllegalArgumentException("a take addresses a pocket and a stack, was (" + pocket + ", " + stack + ")");
            }
            if (count < 1) {
                throw new IllegalArgumentException("a take takes at least one round, was " + count);
            }
        }
    }

    private final List<List<Stack<T>>> pockets;

    private Pockets(List<List<Stack<T>>> pockets) {
        this.pockets = pockets;
    }

    /**
     * effects: returns the pockets {@code rows} describe, in order, copied
     * so that later changes to {@code rows} change nothing here
     */
    public static <T> Pockets<T> of(List<? extends List<Stack<T>>> rows) {
        List<List<Stack<T>>> copy = new ArrayList<>(rows.size());
        for (List<Stack<T>> row : rows) {
            copy.add(List.copyOf(row));
        }
        return new Pockets<>(List.copyOf(copy));
    }

    /** effects: returns no pockets at all */
    public static <T> Pockets<T> none() {
        return new Pockets<>(List.of());
    }

    /** effects: returns how many pockets there are */
    public int size() {
        return pockets.size();
    }

    /**
     * effects: returns pocket {@code pocket}'s stacks in order<br>
     * throws: {@link IndexOutOfBoundsException} if there is no such pocket
     */
    public List<Stack<T>> stacks(int pocket) {
        return pockets.get(pocket);
    }

    /**
     * effects: returns the kinds of round in reach that {@code accepts},
     * each once, in the order they are first met: pocket by pocket, stack
     * by stack -- so a kind kept in the pockets comes before one kept only
     * in a bag
     */
    public List<T> kinds(Predicate<? super T> accepts) {
        List<T> kinds = new ArrayList<>();
        for (List<Stack<T>> row : pockets) {
            for (Stack<T> stack : row) {
                if (!kinds.contains(stack.kind()) && accepts.test(stack.kind())) {
                    kinds.add(stack.kind());
                }
            }
        }
        return List.copyOf(kinds);
    }

    /** effects: returns how many rounds of {@code kind} are in reach, over every pocket */
    public int count(T kind) {
        int total = 0;
        for (List<Stack<T>> row : pockets) {
            for (Stack<T> stack : row) {
                if (stack.kind().equals(kind)) {
                    total += stack.count();
                }
            }
        }
        return total;
    }

    /**
     * effects: returns the steps that draw {@code n} rounds of {@code kind}:
     * first pocket first, first stack first, each stack emptied before the
     * next is touched; the counts sum to {@code n}; empty for zero<br>
     * throws: {@link IllegalArgumentException} if {@code n < 0} or more than
     * {@link #count} of {@code kind} are asked for
     */
    public List<Take> plan(T kind, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0, was " + n);
        }
        int reach = count(kind);
        if (n > reach) {
            throw new IllegalArgumentException(n + " of " + kind + " asked for, " + reach + " in reach");
        }
        List<Take> plan = new ArrayList<>();
        int left = n;
        for (int p = 0; p < pockets.size() && left > 0; p++) {
            List<Stack<T>> row = pockets.get(p);
            for (int s = 0; s < row.size() && left > 0; s++) {
                Stack<T> stack = row.get(s);
                if (stack.kind().equals(kind)) {
                    int take = Math.min(left, stack.count());
                    plan.add(new Take(p, s, take));
                    left -= take;
                }
            }
        }
        return List.copyOf(plan);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Pockets<?> other && other.pockets.equals(pockets);
    }

    @Override
    public int hashCode() {
        return pockets.hashCode();
    }

    @Override
    public String toString() {
        return "Pockets" + pockets;
    }
}
