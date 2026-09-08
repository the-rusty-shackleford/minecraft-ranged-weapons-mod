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

/**
 * What a reload loads and how long it takes.
 *
 * <p>One round of ammunition is one round in the magazine; a reload tops the
 * magazine up with as many as the inventory can give, and takes the weapon's
 * full reload time whether it loads one round or fifty -- the time is the
 * magazine change, not the rounds.
 *
 * <p>A detachable magazine's change is the same pair of movements whatever
 * the magazine holds, so it takes the gun's own change time, not a time per
 * round of capacity; only a magazine larger than the gun's standard one
 * takes longer, a quarter more per doubling, for being heavier and
 * clumsier in the hand.
 */
public final class ReloadPlan {
    private ReloadPlan() {}

    /**
     * effects: returns the rounds a reload loads, and therefore the
     * ammunition items it consumes: the lesser of the space in the magazine
     * and the ammunition available<br>
     * throws: {@link IllegalArgumentException} if {@code capacity < 1},
     * {@code rounds} is outside {@code [0, capacity]} or
     * {@code ammoAvailable < 0}
     *
     * @param rounds        rounds in the magazine now
     * @param capacity      the magazine's capacity
     * @param ammoAvailable rounds of ammunition in the inventory
     * @return rounds to load, zero if nothing can be
     */
    public static int roundsToLoad(int rounds, int capacity, int ammoAvailable) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
        }
        if (rounds < 0 || rounds > capacity) {
            throw new IllegalArgumentException("rounds must be in [0, " + capacity + "], was " + rounds);
        }
        if (ammoAvailable < 0) {
            throw new IllegalArgumentException("ammoAvailable must be >= 0, was " + ammoAvailable);
        }
        return Math.min(capacity - rounds, ammoAvailable);
    }

    /**
     * effects: returns how long a reload takes: the weapon's full reload
     * time, and at least one tick so a reload is never over before it began<br>
     * throws: {@link IllegalArgumentException} if {@code fullReloadTicks < 0}
     *
     * @param fullReloadTicks the weapon's reload time for a full magazine
     * @return ticks from start to finish
     */
    public static int durationTicks(int fullReloadTicks) {
        if (fullReloadTicks < 0) {
            throw new IllegalArgumentException("fullReloadTicks must be >= 0, was " + fullReloadTicks);
        }
        return Math.max(1, fullReloadTicks);
    }

    /**
     * effects: returns whether a reload started at {@code startedAt} for
     * {@code durationTicks} is over at {@code now}; also true if {@code now}
     * is before {@code startedAt}, which only a backwards clock produces,
     * so a reload carried into another world does not hang
     *
     * @param now           the game time
     * @param startedAt     when the reload began
     * @param durationTicks how long it takes
     * @return whether it is done
     */
    public static boolean done(long now, long startedAt, int durationTicks) {
        return now < startedAt || now - startedAt >= durationTicks;
    }

    /**
     * effects: returns how long changing a detachable magazine takes:
     * {@code baseTicks} for a magazine of the standard capacity or smaller,
     * and a quarter of that more (rounded up) for every doubling of the
     * standard capacity the magazine reaches -- twice the standard, five
     * quarters; four times, three halves; at least one tick<br>
     * throws: {@link IllegalArgumentException} if {@code baseTicks < 0},
     * {@code standardCapacity < 1} or {@code capacity < 1}
     *
     * @param baseTicks        the gun's magazine change time
     * @param standardCapacity the capacity of the gun's standard magazine
     * @param capacity         the capacity of the magazine going in
     * @return ticks from start to finish
     */
    public static int magazineChangeTicks(int baseTicks, int standardCapacity, int capacity) {
        if (baseTicks < 0) {
            throw new IllegalArgumentException("baseTicks must be >= 0, was " + baseTicks);
        }
        if (standardCapacity < 1) {
            throw new IllegalArgumentException("standardCapacity must be >= 1, was " + standardCapacity);
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
        }
        int doublings = 0;
        long threshold = 2L * standardCapacity;
        while (capacity >= threshold) {
            doublings++;
            threshold *= 2;
        }
        int extra = (int) Math.ceil(baseTicks * 0.25 * doublings);
        return Math.max(1, baseTicks + extra);
    }
}
