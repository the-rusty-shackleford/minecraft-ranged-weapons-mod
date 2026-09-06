/*
 * Ranged Weapons Mod - guns for players, on the Ranged Weapons protocol.
 * Copyright (C) 2026 Rusty Shackleford and contributors
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

import com.nfx.rangedweaponsmod.domain.Trigger.Action;
import com.nfx.rangedweaponsmod.domain.Trigger.Inputs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Trigger}.
 *
 * <p>Partitions, one per rule and its priority over the ones below it.
 * Reloading: in progress and not done / done -- with the trigger held or
 * not, the magazine empty or not, ammunition or not (reloading wins over
 * everything). Reload requested: magazine not full with ammunition / full /
 * no ammunition -- trigger held or not (works without the trigger; loses to
 * reloading). Trigger: not held (nothing, whatever else is true). Empty
 * magazine: ammunition available / none and not yet clicked / none and
 * already clicked. Loaded: clock ready / not ready. Inputs RI: capacity 0,
 * rounds above capacity or negative, fire rate 0 or above the maximum,
 * reloadDone without reloading.
 */
final class TriggerTest {

    private static final int CAPACITY = 30;
    private static final int RATE = 3;

    /** A loaded gun, trigger held, clock ready, nothing else going on. */
    private static Inputs base() {
        return new Inputs(true, false, 100, 100, 10, CAPACITY, false, false, true, false, RATE);
    }

    private static Inputs with(Inputs b, boolean held, boolean reloadRequested, long now, long nextShotAt, int rounds,
                               boolean reloading, boolean reloadDone, boolean ammo, boolean clicked) {
        return new Inputs(held, reloadRequested, now, nextShotAt, rounds, b.capacity(), reloading, reloadDone, ammo, clicked, b.fireRateTicks());
    }

    // --- rule 1: reloading ---------------------------------------------------

    @Test
    void aReloadInProgressDoesNothingEvenWithTheTriggerHeldAndTheClockReady() {
        assertEquals(Action.IDLE, Trigger.tick(with(base(), true, false, 100, 100, 10, true, false, true, false)));
    }

    @Test
    void aReloadInProgressIsNotRestartedByTheReloadKey() {
        assertEquals(Action.IDLE, Trigger.tick(with(base(), false, true, 100, 100, 0, true, false, true, false)));
    }

    @Test
    void aFinishedReloadIsFinishedWhateverElseIsTrue() {
        assertEquals(Action.FINISH_RELOAD, Trigger.tick(with(base(), true, true, 100, 100, 0, true, true, false, true)));
        assertEquals(Action.FINISH_RELOAD, Trigger.tick(with(base(), false, false, 100, 100, 10, true, true, true, false)));
    }

    // --- rule 2: the reload key -----------------------------------------------

    @Test
    void theReloadKeyStartsAReloadWithoutTheTrigger() {
        assertEquals(Action.START_RELOAD, Trigger.tick(with(base(), false, true, 100, 100, 10, false, false, true, false)));
    }

    @Test
    void theReloadKeyStartsAReloadWithTheTriggerHeldToo() {
        assertEquals(Action.START_RELOAD, Trigger.tick(with(base(), true, true, 100, 100, 10, false, false, true, false)));
    }

    @Test
    void theReloadKeyDoesNothingWhenTheMagazineIsFull() {
        assertEquals(Action.IDLE, Trigger.tick(with(base(), false, true, 100, 100, CAPACITY, false, false, true, false)));
        // and a held trigger with a full magazine then just fires
        assertEquals(Action.FIRE, Trigger.tick(with(base(), true, true, 100, 100, CAPACITY, false, false, true, false)));
    }

    @Test
    void theReloadKeyDoesNothingWithoutAmmunition() {
        assertEquals(Action.IDLE, Trigger.tick(with(base(), false, true, 100, 100, 10, false, false, false, false)));
    }

    // --- rule 3: the trigger --------------------------------------------------

    @Test
    void aReleasedTriggerDoesNothingWhateverTheClockOrMagazineSay() {
        assertEquals(Action.IDLE, Trigger.tick(with(base(), false, false, 100, 100, 10, false, false, true, false)));
        assertEquals(Action.IDLE, Trigger.tick(with(base(), false, false, 100, 100, 0, false, false, true, false)));
        assertEquals(Action.IDLE, Trigger.tick(with(base(), false, false, 100, 100, 0, false, false, false, false)));
    }

    // --- rule 4: an empty magazine --------------------------------------------

    @Test
    void anEmptyMagazineWithAmmunitionStartsAReload() {
        assertEquals(Action.START_RELOAD, Trigger.tick(with(base(), true, false, 100, 100, 0, false, false, true, false)));
    }

    @Test
    void anEmptyMagazineWithoutAmmunitionClicksOnce() {
        assertEquals(Action.CLICK_EMPTY, Trigger.tick(with(base(), true, false, 100, 100, 0, false, false, false, false)));
    }

    @Test
    void anEmptyMagazineThatAlreadyClickedThisPressStaysQuiet() {
        assertEquals(Action.IDLE, Trigger.tick(with(base(), true, false, 100, 100, 0, false, false, false, true)));
    }

    @Test
    void anEmptyMagazineNeverFiresEvenWithTheClockReady() {
        Action a = Trigger.tick(with(base(), true, false, 200, 100, 0, false, false, false, true));
        assertEquals(Action.IDLE, a);
    }

    // --- rule 5: the clock ----------------------------------------------------

    @Test
    void aLoadedGunFiresWhenTheClockIsReady() {
        assertEquals(Action.FIRE, Trigger.tick(with(base(), true, false, 100, 100, 10, false, false, true, false)));
        assertEquals(Action.FIRE, Trigger.tick(with(base(), true, false, 105, 100, 1, false, false, false, false)));
    }

    @Test
    void aLoadedGunWaitsWhenTheClockIsNot() {
        assertEquals(Action.IDLE, Trigger.tick(with(base(), true, false, 100, 102, 10, false, false, true, false)));
    }

    @Test
    void aClockThatRanBackwardsCountsAsReady() {
        assertEquals(Action.FIRE, Trigger.tick(with(base(), true, false, 100, 100 + FireClock.MAX_RATE_TICKS + 1, 10, false, false, true, false)));
    }

    @Test
    void theLastRoundFiresAndOnlyTheNextPressReloads() {
        assertEquals(Action.FIRE, Trigger.tick(with(base(), true, false, 100, 100, 1, false, false, true, false)));
        assertEquals(Action.START_RELOAD, Trigger.tick(with(base(), true, false, 103, 103, 0, false, false, true, false)));
    }

    // --- the rep invariant ------------------------------------------------------

    @Test
    void inputsRefuseAnImpossibleMagazine() {
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, 0, 0, false, false, true, false, RATE));
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, CAPACITY + 1, CAPACITY, false, false, true, false, RATE));
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, -1, CAPACITY, false, false, true, false, RATE));
    }

    @Test
    void inputsRefuseAnImpossibleFireRate() {
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, 1, CAPACITY, false, false, true, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, 1, CAPACITY, false, false, true, false, FireClock.MAX_RATE_TICKS + 1));
    }

    @Test
    void inputsRefuseAReloadDoneWithoutAReloadInProgress() {
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, 1, CAPACITY, false, true, true, false, RATE));
    }
}
