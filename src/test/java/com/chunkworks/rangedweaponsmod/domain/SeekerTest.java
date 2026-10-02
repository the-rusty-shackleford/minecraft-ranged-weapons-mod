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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Sight: down / up. In the reticle: nothing / the candidate / another target / the
 * lock. Lock: none / holds / lost. Contact: short of ACQUIRE_TICKS / reaching it. Lapse: within
 * GRACE_TICKS / beyond. Geometry: on the axis / off it within the cone / beyond the cone / a
 * large target's edge in the cone / beyond RANGE / the eye inside the target.
 */
final class SeekerTest {

    private static final int A = 7, B = 9;

    private static Seeker hold(Seeker s, int target, int ticks) {
        for (int i = 0; i < ticks; i++) {
            s = s.step(true, target, true);
        }
        return s;
    }

    @Test
    void aTargetHeldInTheReticleLocksAtExactlyAcquireTicks() {
        Seeker s = hold(Seeker.IDLE, A, Seeker.ACQUIRE_TICKS - 1);
        assertFalse(s.isLocked());
        assertEquals(A, s.candidate());
        assertEquals((Seeker.ACQUIRE_TICKS - 1) / (double) Seeker.ACQUIRE_TICKS, s.progress(), 1e-12);
        s = s.step(true, A, true);
        assertEquals(A, s.locked());
        assertFalse(s.isAcquiring());
    }

    @Test
    void aLapseWithinTheGraceKeepsTheProgressAndOneBeyondItDropsIt() {
        Seeker s = hold(Seeker.IDLE, A, 10);
        for (int i = 0; i < Seeker.GRACE_TICKS; i++) {
            s = s.step(true, Seeker.NONE, true);
        }
        assertEquals(A, s.candidate());
        assertEquals(10, s.contactTicks());
        assertEquals(11, s.step(true, A, true).contactTicks(), "contact resumes where it was");
        assertEquals(Seeker.IDLE, s.step(true, Seeker.NONE, true), "one tick past the grace drops it");
    }

    @Test
    void anotherTargetInTheReticleStartsOver() {
        Seeker s = hold(Seeker.IDLE, A, 20).step(true, B, true);
        assertEquals(B, s.candidate());
        assertEquals(1, s.contactTicks());
    }

    @Test
    void loweringTheSightDropsTheAcquisitionButNotTheLock() {
        Seeker locked = hold(Seeker.IDLE, A, Seeker.ACQUIRE_TICKS);
        Seeker s = hold(locked, B, 10).step(false, B, true);
        assertEquals(A, s.locked());
        assertFalse(s.isAcquiring());
    }

    @Test
    void aLockHoldsWithoutTheReticleOrTheSight() {
        Seeker s = hold(Seeker.IDLE, A, Seeker.ACQUIRE_TICKS);
        for (int i = 0; i < 200; i++) {
            s = s.step(i % 2 == 0, Seeker.NONE, true);
        }
        assertEquals(A, s.locked());
    }

    @Test
    void aLockWhoseTargetIsLostIsDropped() {
        Seeker s = hold(Seeker.IDLE, A, Seeker.ACQUIRE_TICKS).step(false, Seeker.NONE, false);
        assertEquals(Seeker.IDLE, s);
    }

    @Test
    void aNewLockReplacesTheOldOnlyWhenItCompletes() {
        Seeker s = hold(hold(Seeker.IDLE, A, Seeker.ACQUIRE_TICKS), B, Seeker.ACQUIRE_TICKS - 1);
        assertEquals(A, s.locked(), "still on the first while the second is acquired");
        assertEquals(B, s.candidate());
        s = s.step(true, B, true);
        assertEquals(B, s.locked());
        assertFalse(s.isAcquiring());
    }

    @Test
    void lookingAtTheLockedTargetNeedsNothing() {
        Seeker s = hold(Seeker.IDLE, A, Seeker.ACQUIRE_TICKS);
        assertEquals(new Seeker(A, Seeker.NONE, 0, 0), s.step(true, A, true));
    }

    @Test
    void firingSpendsTheLockAndKeepsTheAcquisition() {
        Seeker s = hold(hold(Seeker.IDLE, A, Seeker.ACQUIRE_TICKS), B, 5).fired();
        assertFalse(s.isLocked());
        assertEquals(B, s.candidate());
        assertEquals(5, s.contactTicks());
    }

    @Test
    void theRepInvariantIsEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new Seeker(-2, Seeker.NONE, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Seeker(A, A, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Seeker(Seeker.NONE, Seeker.NONE, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Seeker(Seeker.NONE, A, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Seeker(Seeker.NONE, A, Seeker.ACQUIRE_TICKS, 0));
        assertThrows(IllegalArgumentException.class, () -> new Seeker(Seeker.NONE, A, 1, Seeker.GRACE_TICKS + 1));
        assertThrows(IllegalArgumentException.class, () -> Seeker.IDLE.step(true, -5, true));
    }

    @Test
    void theReticleTakesWhatIsOnTheAxisOrWithinTheCone() {
        Vector3 look = new Vector3(0, 0, 1);
        assertTrue(Seeker.inReticle(look, new Vector3(0, 0, 50), 0.0));
        double inside = Math.toRadians(2.4);
        assertTrue(Seeker.inReticle(look, new Vector3(50 * Math.sin(inside), 0, 50 * Math.cos(inside)), 0.0));
        double outside = Math.toRadians(3.0);
        assertFalse(Seeker.inReticle(look, new Vector3(50 * Math.sin(outside), 0, 50 * Math.cos(outside)), 0.0));
    }

    @Test
    void aLargeTargetIsInTheReticleWhenItsEdgeIs() {
        Vector3 look = new Vector3(0, 0, 1);
        // Ten degrees to its centre at 20 blocks; a radius of 3 subtends about 8.6 degrees.
        double off = Math.toRadians(10);
        Vector3 center = new Vector3(20 * Math.sin(off), 0, 20 * Math.cos(off));
        assertTrue(Seeker.inReticle(look, center, 3.0));
        assertFalse(Seeker.inReticle(look, center, 0.3));
    }

    @Test
    void nothingBeyondRangeIsInTheReticle() {
        assertFalse(Seeker.inReticle(new Vector3(0, 0, 1), new Vector3(0, 0, Seeker.RANGE + 1), 0.5));
    }

    @Test
    void anEyeInsideTheTargetIsOnIt() {
        assertEquals(0.0, Seeker.offAxis(new Vector3(0, 0, 1), new Vector3(0, 0, -0.5), 1.0));
    }
}
