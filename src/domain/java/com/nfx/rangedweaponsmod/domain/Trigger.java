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
 * What a held trigger does this tick.
 *
 * <p>One pure function from what is true of the player and the gun to one
 * of five actions. The adapter reads the game into {@link Inputs} and
 * performs the {@link Action}; every rule about when a gun fires, reloads
 * or clicks lives here, in priority order, and is tested here.
 *
 * <p>The rules, first match wins:
 * <ol>
 *   <li>Reloading: finish when the reload is done, otherwise nothing -- a
 *       gun mid-reload neither fires nor restarts its reload.</li>
 *   <li>A swap was asked for (Shift+R) and there is something to swap to
 *       -- another loaded magazine, or another kind of round for a gun
 *       loaded directly: start one. A swap is a reload that changes what
 *       is loaded rather than topping it up, so it goes ahead with a full
 *       magazine too.</li>
 *   <li>A reload was asked for, the magazine is not full and there is
 *       ammunition: start one. The reload key works without the trigger.</li>
 *   <li>Trigger not held: nothing.</li>
 *   <li>Magazine empty: start a reload if there is ammunition, else click
 *       once per press -- an empty gun does not click every tick.</li>
 *   <li>The fire clock says ready: fire. Otherwise wait.</li>
 * </ol>
 */
public final class Trigger {
    private Trigger() {}

    /** What the trigger decides to do this tick. */
    public enum Action {
        /** Nothing. */
        IDLE,
        /** Launch a shot, spend a round, schedule the next. */
        FIRE,
        /** Begin a reload of the held gun. */
        START_RELOAD,
        /** Begin a swap: out with what is loaded, in with the next magazine or kind. */
        START_SWAP,
        /** The reload's time is up: load the rounds. */
        FINISH_RELOAD,
        /** The magazine is empty and nothing can be loaded: the dry click. */
        CLICK_EMPTY
    }

    /**
     * Everything the decision depends on. Immutable.
     *
     * <p>RI: {@code capacity >= 1}; {@code 0 <= rounds <= capacity};
     * {@code fireRateTicks} in {@code [1, FireClock.MAX_RATE_TICKS]};
     * {@code reloadDone} implies {@code reloading}.
     *
     * @param held             whether the trigger is held this tick
     * @param reloadRequested  whether the reload key was pressed since the last tick
     * @param now              the game time
     * @param nextShotAt       the tick the fire clock allows the next shot
     * @param rounds           rounds in the magazine
     * @param capacity         the magazine's capacity
     * @param reloading        whether a reload is in progress
     * @param reloadDone       whether that reload's time is up
     * @param ammoAvailable    whether the inventory holds at least one round to load
     * @param clickedThisPress whether the dry click already sounded for this press
     * @param automatic        whether the weapon fires for as long as the trigger is held; if not, once per pull
     * @param firedThisPress   whether it has already fired since the trigger was pressed
     * @param fireRateTicks    ticks between shots
     * @param swapRequested    whether the swap key was pressed since the last tick
     * @param swapAvailable    whether there is another magazine, or another kind of round, to swap to
     */
    public record Inputs(boolean held, boolean reloadRequested, long now, long nextShotAt, int rounds, int capacity,
                         boolean reloading, boolean reloadDone, boolean ammoAvailable, boolean clickedThisPress,
                         int fireRateTicks, boolean automatic, boolean firedThisPress,
                         boolean swapRequested, boolean swapAvailable) {
        /** The inputs with no swap asked for. */
        public Inputs(boolean held, boolean reloadRequested, long now, long nextShotAt, int rounds, int capacity,
                      boolean reloading, boolean reloadDone, boolean ammoAvailable, boolean clickedThisPress,
                      int fireRateTicks, boolean automatic, boolean firedThisPress) {
            this(held, reloadRequested, now, nextShotAt, rounds, capacity, reloading, reloadDone, ammoAvailable,
                    clickedThisPress, fireRateTicks, automatic, firedThisPress, false, false);
        }

        /**
         * @throws IllegalArgumentException if the RI does not hold
         */
        public Inputs {
            if (capacity < 1) {
                throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
            }
            if (rounds < 0 || rounds > capacity) {
                throw new IllegalArgumentException("rounds must be in [0, " + capacity + "], was " + rounds);
            }
            if (fireRateTicks < 1 || fireRateTicks > FireClock.MAX_RATE_TICKS) {
                throw new IllegalArgumentException("fireRateTicks must be in [1, " + FireClock.MAX_RATE_TICKS
                        + "], was " + fireRateTicks);
            }
            if (reloadDone && !reloading) {
                throw new IllegalArgumentException("a reload cannot be done without being in progress");
            }
        }
    }

    /**
     * effects: returns the action for {@code in}, by the rules above
     *
     * @param in what is true this tick
     * @return what to do
     */
    public static Action tick(Inputs in) {
        if (in.reloading()) {
            return in.reloadDone() ? Action.FINISH_RELOAD : Action.IDLE;
        }
        if (in.swapRequested() && in.swapAvailable()) {
            return Action.START_SWAP;
        }
        if (in.reloadRequested() && in.rounds() < in.capacity() && in.ammoAvailable()) {
            return Action.START_RELOAD;
        }
        if (!in.held()) {
            return Action.IDLE;
        }
        if (in.rounds() == 0) {
            if (in.ammoAvailable()) {
                return Action.START_RELOAD;
            }
            return in.clickedThisPress() ? Action.IDLE : Action.CLICK_EMPTY;
        }
        if (!in.automatic() && in.firedThisPress()) {
            return Action.IDLE;
        }
        return FireClock.ready(in.now(), in.nextShotAt()) ? Action.FIRE : Action.IDLE;
    }
}
