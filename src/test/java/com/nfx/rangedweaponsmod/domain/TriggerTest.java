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

import com.nfx.rangedweaponsmod.domain.Trigger.Action;
import com.nfx.rangedweaponsmod.domain.Trigger.Inputs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * already clicked. Loaded: clock ready / not ready. Swap requested: with
 * something to swap to (a full magazine too; loses to reloading; beats a
 * reload asked in the same tick) / nothing. Inputs RI: capacity 0,
 * rounds above capacity or negative, fire rate 0 or above the maximum,
 * reloadDone without reloading.
 */
final class TriggerTest {

    private static final int CAPACITY = 30;
    private static final int RATE = 3;

    /** A loaded gun, trigger held, clock ready, nothing else going on. */
    private static Inputs base() {
        return new Inputs(true, false, 100, 100, 10, CAPACITY, false, false, true, false, RATE, true, false);
    }

    private static Inputs with(Inputs b, boolean held, boolean reloadRequested, long now, long nextShotAt, int rounds,
                               boolean reloading, boolean reloadDone, boolean ammo, boolean clicked) {
        return new Inputs(held, reloadRequested, now, nextShotAt, rounds, b.capacity(), reloading, reloadDone, ammo, clicked, b.fireRateTicks(), b.automatic(), b.firedThisPress());
    }

    private static Inputs swap(Inputs b, boolean reloading, boolean reloadRequested, int rounds, boolean available) {
        return new Inputs(b.held(), reloadRequested, b.now(), b.nextShotAt(), rounds, b.capacity(), reloading, false,
                b.ammoAvailable(), b.clickedThisPress(), b.fireRateTicks(), b.automatic(), b.firedThisPress(), true, available);
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
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, 0, 0, false, false, true, false, RATE, true, false));
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, CAPACITY + 1, CAPACITY, false, false, true, false, RATE, true, false));
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, -1, CAPACITY, false, false, true, false, RATE, true, false));
    }

    @Test
    void inputsRefuseAnImpossibleFireRate() {
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, 1, CAPACITY, false, false, true, false, 0, true, false));
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, 1, CAPACITY, false, false, true, false, FireClock.MAX_RATE_TICKS + 1, true, false));
    }

    @Test
    void inputsRefuseAReloadDoneWithoutAReloadInProgress() {
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, false, 0, 0, 1, CAPACITY, false, true, true, false, RATE, true, false));
    }

    // --- rule 5: one shot per pull unless automatic ------------------------

    private static Inputs semi(boolean firedThisPress) {
        Inputs b = base();
        return new Inputs(b.held(), b.reloadRequested(), b.now(), b.nextShotAt(), b.rounds(), b.capacity(),
                b.reloading(), b.reloadDone(), b.ammoAvailable(), b.clickedThisPress(), b.fireRateTicks(), false, firedThisPress);
    }

    @Test
    void aSemiAutomaticFiresOnceThenIdlesWhileHeld() {
        assertEquals(Action.FIRE, Trigger.tick(semi(false)), "the pull's first shot");
        assertEquals(Action.IDLE, Trigger.tick(semi(true)), "held on: nothing, however ready the clock");
    }

    @Test
    void anAutomaticKeepsFiringWhileHeld() {
        Inputs b = base();
        Inputs firedAndHeld = new Inputs(b.held(), b.reloadRequested(), b.now(), b.nextShotAt(), b.rounds(), b.capacity(),
                b.reloading(), b.reloadDone(), b.ammoAvailable(), b.clickedThisPress(), b.fireRateTicks(), true, true);
        assertEquals(Action.FIRE, Trigger.tick(firedAndHeld));
    }

    @Test
    void theSemiAutomaticLatchYieldsToEveryEarlierRule() {
        Inputs b = semi(true);
        assertEquals(Action.START_RELOAD, Trigger.tick(new Inputs(true, true, b.now(), b.nextShotAt(), 3, b.capacity(),
                false, false, true, false, b.fireRateTicks(), false, true)), "the reload key still reloads");
        assertEquals(Action.START_RELOAD, Trigger.tick(new Inputs(true, false, b.now(), b.nextShotAt(), 0, b.capacity(),
                false, false, true, false, b.fireRateTicks(), false, true)), "an empty gun still reloads");
        assertEquals(Action.IDLE, Trigger.tick(new Inputs(false, false, b.now(), b.nextShotAt(), 3, b.capacity(),
                false, false, true, false, b.fireRateTicks(), false, true)), "released is released");
    }

    // --- rule 2: a swap asked for --------------------------------------------

    @Test
    void aSwapWithSomethingToSwapToStartsEvenWithAFullMagazine() {
        assertEquals(Action.START_SWAP, Trigger.tick(swap(base(), false, false, 10, true)));
        assertEquals(Action.START_SWAP, Trigger.tick(swap(base(), false, false, CAPACITY, true)));
        assertEquals(Action.START_SWAP, Trigger.tick(swap(base(), false, false, 0, true)));
    }

    @Test
    void aSwapWithNothingToSwapToFallsThroughToTheOtherRules() {
        assertEquals(Action.FIRE, Trigger.tick(swap(base(), false, false, 10, false)));
        assertEquals(Action.START_RELOAD, Trigger.tick(swap(base(), false, true, 10, false)));
    }

    @Test
    void aSwapLosesToAReloadInProgressAndBeatsAReloadAskedTheSameTick() {
        assertEquals(Action.IDLE, Trigger.tick(swap(base(), true, false, 10, true)));
        assertEquals(Action.START_SWAP, Trigger.tick(swap(base(), false, true, 10, true)));
    }

    @Test
    void theThirteenArgumentInputsAskForNoSwap() {
        assertFalse(base().swapRequested());
        assertFalse(base().swapAvailable());
    }
}
