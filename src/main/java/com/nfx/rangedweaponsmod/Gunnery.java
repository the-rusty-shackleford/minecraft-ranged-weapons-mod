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
package com.nfx.rangedweaponsmod;

import net.minecraft.resources.ResourceLocation;

/**
 * A player's trigger finger, server side: whether the trigger is held, when
 * the next shot is allowed, whether the dry click already sounded for this
 * press, and whether the reload key is waiting to be honoured. Immutable;
 * held as a transient attachment on the player and replaced whole.
 *
 * <p>Per player rather than per gun so that swapping between two guns
 * cannot double a fire rate: the finger has one clock.
 *
 * @param held             whether the trigger is held
 * @param nextShotAt       the tick the next shot is allowed at
 * @param clickedThisPress whether the empty click sounded since the trigger was pressed
 * @param reloadRequested  whether the reload key was pressed and not yet acted on
 * @param firedThisPress   whether the gun has fired since the trigger was pressed; a semi-automatic fires once per pull
 * @param aiming           whether the player is aiming down the sights
 * @param swapRequested    whether the swap key (Shift+R) was pressed and not yet acted on
 * @param lastSwapSlot     the inventory slot the last swap took its magazine from, or -1: swaps walk the inventory from there
 * @param cycleAt          the tick the action's sound (a pump, a bolt) is due after the last shot, or 0 for none
 * @param cycleSound       that sound's id, or null
 */
public record Gunnery(boolean held, long nextShotAt, boolean clickedThisPress, boolean reloadRequested,
                      boolean firedThisPress, boolean aiming, boolean swapRequested, int lastSwapSlot,
                      long cycleAt, ResourceLocation cycleSound) {

    /** Trigger released, nothing scheduled, sights down, no swap yet. */
    public static final Gunnery RELEASED = new Gunnery(false, 0L, false, false, false, false, false, -1, 0L, null);

    /** effects: returns this with the action's sound {@code sound} due at {@code tick} */
    public Gunnery cycleDue(long tick, ResourceLocation sound) {
        return new Gunnery(held, nextShotAt, clickedThisPress, reloadRequested, firedThisPress, aiming, swapRequested, lastSwapSlot, tick, sound);
    }

    /** effects: returns this with the action's sound played */
    public Gunnery cycled() {
        return new Gunnery(held, nextShotAt, clickedThisPress, reloadRequested, firedThisPress, aiming, swapRequested, lastSwapSlot, 0L, null);
    }

    /** effects: returns this with the trigger pressed, the click and the one-shot latch armed again */
    public Gunnery pressed() {
        return new Gunnery(true, nextShotAt, false, reloadRequested, false, aiming, swapRequested, lastSwapSlot, cycleAt, cycleSound);
    }

    /** effects: returns this with the trigger released */
    public Gunnery released() {
        return new Gunnery(false, nextShotAt, clickedThisPress, reloadRequested, firedThisPress, aiming, swapRequested, lastSwapSlot, cycleAt, cycleSound);
    }

    /** effects: returns this having just fired, with the next shot allowed at {@code tick} */
    public Gunnery firedUntil(long tick) {
        return new Gunnery(held, tick, clickedThisPress, reloadRequested, true, aiming, swapRequested, lastSwapSlot, cycleAt, cycleSound);
    }

    /** effects: returns this with the empty click spent for this press */
    public Gunnery clicked() {
        return new Gunnery(held, nextShotAt, true, reloadRequested, firedThisPress, aiming, swapRequested, lastSwapSlot, cycleAt, cycleSound);
    }

    /** effects: returns this with a reload asked for */
    public Gunnery reloadAsked() {
        return new Gunnery(held, nextShotAt, clickedThisPress, true, firedThisPress, aiming, swapRequested, lastSwapSlot, cycleAt, cycleSound);
    }

    /** effects: returns this with a swap asked for */
    public Gunnery swapAsked() {
        return new Gunnery(held, nextShotAt, clickedThisPress, reloadRequested, firedThisPress, aiming, true, lastSwapSlot, cycleAt, cycleSound);
    }

    /** effects: returns this with the reload and swap requests consumed */
    public Gunnery requestsHandled() {
        return new Gunnery(held, nextShotAt, clickedThisPress, false, firedThisPress, aiming, false, lastSwapSlot, cycleAt, cycleSound);
    }

    /** effects: returns this remembering that a swap took its magazine from {@code slot} */
    public Gunnery swappedFrom(int slot) {
        return new Gunnery(held, nextShotAt, clickedThisPress, reloadRequested, firedThisPress, aiming, swapRequested, slot, cycleAt, cycleSound);
    }

    /** effects: returns this with the sights up or down */
    public Gunnery aiming(boolean aiming) {
        return new Gunnery(held, nextShotAt, clickedThisPress, reloadRequested, firedThisPress, aiming, swapRequested, lastSwapSlot, cycleAt, cycleSound);
    }
}
