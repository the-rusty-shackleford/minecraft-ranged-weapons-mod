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
 * When the next shot may be fired, in game ticks.
 *
 * <p>A trigger finger keeps one number: the tick at which it is next allowed
 * to fire. That is not the vanilla item cooldown -- which is per item type,
 * shared by every gun of that kind, and strobes the hotbar -- and it is not
 * a countdown that would need rewriting every tick. It is compared against
 * the game time, which only ever moves forward inside one world.
 *
 * <p>Across worlds it does not: a scheduled time carried into a world whose
 * clock is younger would sit in the future for hours. So a next-shot time
 * further ahead than any fire rate could have put it is treated as the clock
 * having run backwards, and the gun is ready.
 */
public final class FireClock {
    private FireClock() {}

    /** The slowest fire rate the clock accepts: one shot a minute. */
    public static final int MAX_RATE_TICKS = 20 * 60;

    /**
     * effects: returns whether a shot may be fired at {@code now} when the
     * next is scheduled for {@code nextShotAt}: true once {@code now} has
     * reached it, and true if it lies further ahead than
     * {@link #MAX_RATE_TICKS}, which only a backwards clock produces
     *
     * @param now        the game time
     * @param nextShotAt the tick the last shot scheduled the next for
     * @return whether the trigger may fire
     */
    public static boolean ready(long now, long nextShotAt) {
        return now >= nextShotAt || nextShotAt - now > MAX_RATE_TICKS;
    }

    /**
     * effects: returns the tick a shot fired at {@code now} schedules the
     * next for<br>
     * throws: {@link IllegalArgumentException} if {@code fireRateTicks} is
     * outside {@code [1, MAX_RATE_TICKS]}
     *
     * @param now           the game time of the shot
     * @param fireRateTicks ticks between shots
     * @return the next allowed shot's tick
     */
    public static long next(long now, int fireRateTicks) {
        if (fireRateTicks < 1 || fireRateTicks > MAX_RATE_TICKS) {
            throw new IllegalArgumentException("fireRateTicks must be in [1, " + MAX_RATE_TICKS + "], was " + fireRateTicks);
        }
        return now + fireRateTicks;
    }
}
