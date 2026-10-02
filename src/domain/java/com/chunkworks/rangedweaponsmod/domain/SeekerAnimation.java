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

/**
 * Continuous visual acquisition progress, following confirmed seeker updates without granting
 * a lock. A new progress value starts from the value already on screen and closes the remaining
 * distance over two ticks. Overlapping updates keep it moving between server packets.
 *
 * <p>Immutable. AF: candidate's displayed progress travels from {@code from} to {@code target}
 * starting at {@code startedAt}, on a caller-supplied clock measured in ticks.
 * RI: candidate is NONE or nonnegative; 0 <= from <= target < 1; startedAt is finite and
 * nonnegative; NONE has zero progress. A target change or restarted acquisition starts at zero.
 *
 * @param candidate entity being acquired, or Seeker.NONE
 * @param from progress displayed when the latest update arrived
 * @param target latest confirmed progress
 * @param startedAt time of that update, in ticks
 */
public record SeekerAnimation(int candidate, double from, double target, double startedAt) {
    public static final SeekerAnimation IDLE = new SeekerAnimation(Seeker.NONE, 0.0, 0.0, 0.0);
    private static final double DURATION = 2.0;

    /** throws: IllegalArgumentException if the representation invariant does not hold */
    public SeekerAnimation {
        if (candidate < Seeker.NONE || !(from >= 0.0 && from <= target && target < 1.0)
                || !Double.isFinite(startedAt) || startedAt < 0.0
                || (candidate == Seeker.NONE && (from != 0.0 || target != 0.0))) {
            throw new IllegalArgumentException("Invalid seeker animation");
        }
    }

    /**
     * requires: seeker is non-null; time uses the same clock as preceding updates<br>
     * effects: follows the latest confirmed progress continuously; resets on loss, lock,
     * a different candidate or restarted acquisition. An unchanged update preserves this
     * animation, so rendering repeatedly cannot restart it.<br>
     * throws: IllegalArgumentException if time is negative, non-finite or precedes startedAt
     */
    public SeekerAnimation updated(Seeker seeker, double time) {
        requireTime(time);
        if (time < startedAt) {
            throw new IllegalArgumentException("Animation clock moved backward");
        }
        if (!seeker.isAcquiring()) {
            return IDLE;
        }
        double next = seeker.progress();
        if (candidate != seeker.candidate() || next < target) {
            return new SeekerAnimation(seeker.candidate(), 0.0, next, time);
        }
        return next == target ? this : new SeekerAnimation(candidate, sample(time), next, time);
    }

    /**
     * effects: returns displayed progress at time, clamped to the animation's endpoints;
     * never predicts progress beyond the server's confirmed target<br>
     * throws: IllegalArgumentException if time is negative or non-finite
     */
    public double sample(double time) {
        requireTime(time);
        double fraction = Math.clamp((time - startedAt) / DURATION, 0.0, 1.0);
        return from + (target - from) * fraction;
    }

    private static void requireTime(double time) {
        if (!Double.isFinite(time) || time < 0.0) {
            throw new IllegalArgumentException("Animation time must be finite and nonnegative");
        }
    }
}
