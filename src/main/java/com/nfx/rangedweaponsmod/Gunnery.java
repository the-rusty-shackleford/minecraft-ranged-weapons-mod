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
 */
public record Gunnery(boolean held, long nextShotAt, boolean clickedThisPress, boolean reloadRequested,
                      boolean firedThisPress, boolean aiming) {

    /** Trigger released, nothing scheduled, sights down. */
    public static final Gunnery RELEASED = new Gunnery(false, 0L, false, false, false, false);

    /** effects: returns this with the trigger pressed, the click and the one-shot latch armed again */
    public Gunnery pressed() {
        return new Gunnery(true, nextShotAt, false, reloadRequested, false, aiming);
    }

    /** effects: returns this with the trigger released */
    public Gunnery released() {
        return new Gunnery(false, nextShotAt, clickedThisPress, reloadRequested, firedThisPress, aiming);
    }

    /** effects: returns this having just fired, with the next shot allowed at {@code tick} */
    public Gunnery firedUntil(long tick) {
        return new Gunnery(held, tick, clickedThisPress, reloadRequested, true, aiming);
    }

    /** effects: returns this with the empty click spent for this press */
    public Gunnery clicked() {
        return new Gunnery(held, nextShotAt, true, reloadRequested, firedThisPress, aiming);
    }

    /** effects: returns this with a reload asked for */
    public Gunnery reloadAsked() {
        return new Gunnery(held, nextShotAt, clickedThisPress, true, firedThisPress, aiming);
    }

    /** effects: returns this with the reload request consumed */
    public Gunnery reloadHandled() {
        return new Gunnery(held, nextShotAt, clickedThisPress, false, firedThisPress, aiming);
    }

    /** effects: returns this with the sights up or down */
    public Gunnery aiming(boolean aiming) {
        return new Gunnery(held, nextShotAt, clickedThisPress, reloadRequested, firedThisPress, aiming);
    }
}
