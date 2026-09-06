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
 * Recoil as a camera offset that recovers: what the player sees, never
 * where the player is looking.
 *
 * <p>Each shot adds a kick to the offset. Each tick the offset shrinks by a
 * fixed fraction toward zero. Between ticks the view shows the offset
 * interpolated from the previous tick's, so the kick eases in over the
 * frames of one tick and the recovery is smooth at any frame rate. Under
 * rapid fire kicks simply add up and settle; nothing is restarted, so there
 * is nothing to snap. The offset is applied to the camera only: the player's
 * own rotation, and so the aim, is never written, and nothing fights the
 * mouse.
 *
 * <p>Immutable. AF: the camera is displaced by {@code (pitch, yaw)} degrees
 * now and was by {@code (prevPitch, prevYaw)} at the previous tick; a tick
 * multiplies both by {@code 1 - recovery}.<br>
 * RI: {@code recovery} in {@code (0, 1]}; every angle finite and within
 * {@code [-MAX_OFFSET, MAX_OFFSET]}.
 *
 * @param pitch     the current upward displacement, degrees
 * @param yaw       the current sideways displacement, degrees
 * @param prevPitch the displacement at the previous tick
 * @param prevYaw   the displacement at the previous tick
 * @param recovery  the fraction recovered per tick
 */
public record Recoil(float pitch, float yaw, float prevPitch, float prevYaw, float recovery) {

    /** No kick may displace the camera further than this, whatever adds up. */
    public static final float MAX_OFFSET = 45.0f;
    /** Below this the offset is treated as zero, so recovery ends instead of tailing off forever. */
    public static final float REST_EPSILON = 1e-3f;

    /**
     * @throws IllegalArgumentException if the RI does not hold
     */
    public Recoil {
        if (!(recovery > 0.0f && recovery <= 1.0f)) {
            throw new IllegalArgumentException("recovery must be in (0, 1], was " + recovery);
        }
        requireOffset("pitch", pitch);
        requireOffset("yaw", yaw);
        requireOffset("prevPitch", prevPitch);
        requireOffset("prevYaw", prevYaw);
    }

    /** The offset seen at one moment. */
    public record Sample(float pitch, float yaw) {}

    /**
     * effects: returns a recoil at rest that recovers {@code recovery} of
     * itself per tick
     *
     * @param recovery the fraction recovered per tick, in {@code (0, 1]}
     * @return the resting recoil
     */
    public static Recoil atRest(float recovery) {
        return new Recoil(0.0f, 0.0f, 0.0f, 0.0f, recovery);
    }

    /**
     * effects: returns this recoil with a kick added to the current offset,
     * clamped to {@code MAX_OFFSET}; the previous tick's offset is unchanged,
     * so the kick eases in over the current tick's frames<br>
     * throws: {@link IllegalArgumentException} if either kick is not finite
     *
     * @param pitchKick degrees to add upward
     * @param yawKick   degrees to add sideways, signed
     * @return the kicked recoil
     */
    public Recoil kicked(float pitchKick, float yawKick) {
        if (!Float.isFinite(pitchKick) || !Float.isFinite(yawKick)) {
            throw new IllegalArgumentException("kicks must be finite, were " + pitchKick + ", " + yawKick);
        }
        return new Recoil(clamp(pitch + pitchKick), clamp(yaw + yawKick), prevPitch, prevYaw, recovery);
    }

    /**
     * effects: returns this recoil one tick later: the current offset becomes
     * the previous, and the current shrinks by {@code recovery}; an offset
     * that shrinks below {@link #REST_EPSILON} becomes exactly zero
     *
     * @return the recoil after one tick
     */
    public Recoil stepped() {
        return new Recoil(settle(pitch * (1.0f - recovery)), settle(yaw * (1.0f - recovery)), pitch, yaw, recovery);
    }

    /**
     * effects: returns the offset seen {@code partial} of the way from the
     * previous tick to the current one<br>
     * throws: {@link IllegalArgumentException} if {@code partial} is outside
     * {@code [0, 1]}
     *
     * @param partial how far into the tick the frame is, in {@code [0, 1]}
     * @return the interpolated offset
     */
    public Sample sample(float partial) {
        if (!(partial >= 0.0f && partial <= 1.0f)) {
            throw new IllegalArgumentException("partial must be in [0, 1], was " + partial);
        }
        return new Sample(prevPitch + (pitch - prevPitch) * partial, prevYaw + (yaw - prevYaw) * partial);
    }

    /**
     * effects: returns this recoil recovering {@code recovery} of itself per
     * tick from now on, offsets unchanged<br>
     * throws: {@link IllegalArgumentException} if {@code recovery} is outside {@code (0, 1]}
     *
     * @param recovery the fraction recovered per tick
     * @return the recoil with that recovery
     */
    public Recoil withRecovery(float recovery) {
        return new Recoil(pitch, yaw, prevPitch, prevYaw, recovery);
    }

    /** effects: returns whether both the current and previous offsets are zero */
    public boolean atRest() {
        return pitch == 0.0f && yaw == 0.0f && prevPitch == 0.0f && prevYaw == 0.0f;
    }

    private static float settle(float value) {
        return Math.abs(value) < REST_EPSILON ? 0.0f : value;
    }

    private static float clamp(float value) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, value));
    }

    private static void requireOffset(String name, float value) {
        if (!Float.isFinite(value) || Math.abs(value) > MAX_OFFSET) {
            throw new IllegalArgumentException(name + " must be finite and within +-" + MAX_OFFSET + ", was " + value);
        }
    }
}
