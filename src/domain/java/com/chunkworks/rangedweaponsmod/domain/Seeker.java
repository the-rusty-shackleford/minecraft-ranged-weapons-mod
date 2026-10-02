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
 * The rocket launcher's seeker (D-0028): what it is locked onto, and what it is locking onto.
 *
 * <p>Rusty's rules. While the sight is up, a valid target held in the reticle for
 * {@link #ACQUIRE_TICKS} becomes the lock; a lapse of up to {@link #GRACE_TICKS} is forgiven,
 * a different target starts over, and lowering the sight drops the acquisition. A lock, once
 * made, needs neither the reticle nor the sight: it holds until it is fired, the launcher is
 * put away, or the target is lost (the adapter's {@code lockHolds}). A new acquisition while
 * locked replaces the lock only when it completes.
 *
 * <p>Immutable. AF: locked onto entity {@code locked} ({@link #NONE}: no lock), and acquiring
 * entity {@code candidate} ({@link #NONE}: none) with {@code contactTicks} ticks of contact so
 * far and {@code gapTicks} ticks since the last.<br>
 * RI: {@code locked} and {@code candidate} are {@link #NONE} or {@code >= 0}; a candidate is
 * never the locked target; with no candidate, both counts are 0; with one,
 * {@code 1 <= contactTicks < ACQUIRE_TICKS} and {@code 0 <= gapTicks <= GRACE_TICKS}.
 *
 * @param locked       the entity locked onto, or {@link #NONE}
 * @param candidate    the entity being acquired, or {@link #NONE}
 * @param contactTicks ticks it has been in the reticle
 * @param gapTicks     ticks since it was last in the reticle
 */
public record Seeker(int locked, int candidate, int contactTicks, int gapTicks) {

    /** No entity. Entity ids are never negative. */
    public static final int NONE = -1;
    /** Ticks of reticle contact a lock takes (1.5 seconds). */
    public static final int ACQUIRE_TICKS = 30;
    /** Ticks out of the reticle an acquisition survives. */
    public static final int GRACE_TICKS = 4;
    /** The reticle's half-angle, radians (2.5 degrees). */
    public static final double HALF_CONE = Math.toRadians(2.5);
    /** Blocks the seeker sees, and a lock holds, to. */
    public static final double RANGE = 128.0;
    /** The seeker at rest. */
    public static final Seeker IDLE = new Seeker(NONE, NONE, 0, 0);

    /**
     * @throws IllegalArgumentException if the RI does not hold
     */
    public Seeker {
        if (locked < NONE || candidate < NONE) {
            throw new IllegalArgumentException("ids must be NONE or >= 0, were " + locked + ", " + candidate);
        }
        if (candidate != NONE && candidate == locked) {
            throw new IllegalArgumentException("the candidate is already the lock: " + candidate);
        }
        if (candidate == NONE ? contactTicks != 0 || gapTicks != 0
                : contactTicks < 1 || contactTicks >= ACQUIRE_TICKS || gapTicks < 0 || gapTicks > GRACE_TICKS) {
            throw new IllegalArgumentException("bad counts for candidate " + candidate + ": " + contactTicks + ", " + gapTicks);
        }
    }

    /**
     * requires: {@code inReticle} is {@link #NONE} or {@code >= 0}<br>
     * effects: returns the seeker one tick later. The lock is kept if {@code lockHolds} and
     * dropped otherwise. With the sight down, the acquisition ends. With it up: a target in the
     * reticle that is already the lock needs nothing; the candidate gains a tick of contact and
     * becomes the lock at {@link #ACQUIRE_TICKS}, replacing any other; another target starts a
     * new acquisition; no target lets the candidate lapse for up to {@link #GRACE_TICKS}.
     *
     * @param seeking   whether the sight is up this tick
     * @param inReticle the valid target in the reticle this tick, or {@link #NONE}
     * @param lockHolds whether the lock's target is still valid and in range
     * @return the seeker after this tick
     */
    public Seeker step(boolean seeking, int inReticle, boolean lockHolds) {
        if (inReticle < NONE) {
            throw new IllegalArgumentException("inReticle must be NONE or >= 0, was " + inReticle);
        }
        int lock = locked != NONE && lockHolds ? locked : NONE;
        if (!seeking) {
            return new Seeker(lock, NONE, 0, 0);
        }
        if (inReticle == NONE) {
            return candidate != NONE && gapTicks < GRACE_TICKS
                    ? new Seeker(lock, candidate, contactTicks, gapTicks + 1)
                    : new Seeker(lock, NONE, 0, 0);
        }
        if (inReticle == lock) {
            return new Seeker(lock, NONE, 0, 0);
        }
        int contact = inReticle == candidate ? contactTicks + 1 : 1;
        return contact >= ACQUIRE_TICKS ? new Seeker(inReticle, NONE, 0, 0) : new Seeker(lock, inReticle, contact, 0);
    }

    /** effects: returns this seeker with its lock spent by a launch; the acquisition goes on */
    public Seeker fired() {
        return new Seeker(NONE, candidate, contactTicks, gapTicks);
    }

    /** effects: returns whether there is a lock */
    public boolean isLocked() {
        return locked != NONE;
    }

    /** effects: returns whether a target is being acquired */
    public boolean isAcquiring() {
        return candidate != NONE;
    }

    /** effects: returns the acquisition's progress, {@code contactTicks / ACQUIRE_TICKS}, in {@code [0, 1)} */
    public double progress() {
        return (double) contactTicks / ACQUIRE_TICKS;
    }

    /**
     * requires: {@code look} non-zero; {@code radius >= 0}<br>
     * effects: returns the angle, radians, between the look and the nearest edge of a target of
     * {@code radius} whose centre is at {@code toCenter}: the angle to its centre less the angle its
     * radius subtends there; 0 when the look passes within the radius or the eye is inside it
     *
     * @param look     the direction looked in
     * @param toCenter the target's centre less the eye
     * @param radius   the target's radius, blocks
     * @return the angle off the target, radians, {@code >= 0}
     */
    public static double offAxis(Vector3 look, Vector3 toCenter, double radius) {
        if (!(radius >= 0.0) || !Double.isFinite(radius)) {
            throw new IllegalArgumentException("radius must be finite and >= 0, was " + radius);
        }
        double distance = toCenter.length();
        if (distance <= radius) {
            return 0.0;
        }
        double off = look.angleTo(toCenter) - Math.asin(radius / distance);
        return Math.max(0.0, off);
    }

    /**
     * effects: returns whether a target of {@code radius} centred at {@code toCenter} is in the
     * reticle: within {@link #RANGE} and no more than {@link #HALF_CONE} off the look
     *
     * @param look     the direction looked in, non-zero
     * @param toCenter the target's centre less the eye
     * @param radius   the target's radius, blocks, {@code >= 0}
     * @return whether it is in the reticle
     */
    public static boolean inReticle(Vector3 look, Vector3 toCenter, double radius) {
        return toCenter.length() <= RANGE && offAxis(look, toCenter, radius) <= HALF_CONE;
    }
}
