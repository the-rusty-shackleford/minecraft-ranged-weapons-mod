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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Partitions: idle/acquiring/locked; fresh/same/different/restarted candidate; updates on and
 * between ticks, overlapping and late updates; unchanged progress during a grace gap;
 * sample before/during/after a transition; stopped clock; invalid state and time.
 * Real immutable seeker values and numeric time exercise the animation without game mocks.
 */
class SeekerAnimationTest {
    private static final double EPS = 1e-12;

    private static Seeker acquiring(int candidate, int ticks) {
        return new Seeker(Seeker.NONE, candidate, ticks, 0);
    }

    @Test void freshAcquisitionMovesBetweenFramesAndStopsAtConfirmedProgress() {
        var a = SeekerAnimation.IDLE.updated(acquiring(7, 10), 5.25);
        assertEquals(0, a.sample(5.25), EPS);
        assertTrue(a.sample(5.75) > 0);
        assertTrue(a.sample(6.0) > a.sample(5.75));
        assertEquals(10.0 / 30, a.sample(7.25), EPS);
        assertEquals(10.0 / 30, a.sample(100), EPS);
        assertEquals(0, a.sample(5), EPS);
    }

    @Test void packetBetweenTicksNeverSnapsForwardOrBackward() {
        var a = SeekerAnimation.IDLE.updated(acquiring(7, 9), 10);
        double before = a.sample(10.7);
        var b = a.updated(acquiring(7, 10), 10.7);
        assertEquals(before, b.sample(10.7), EPS);
        assertTrue(b.sample(11) >= before, "crossing the next tick cannot reverse the brackets");
        assertTrue(b.sample(11.2) > b.sample(11));
    }

    @Test void jitteredUpdatesRemainContinuousMonotonicAndBounded() {
        var a = SeekerAnimation.IDLE;
        double time = 1;
        double last = 0;
        for (int tick = 1; tick < 30; tick++) {
            time += tick % 2 == 0 ? 0.7 : 1.3;
            double atUpdate = a.sample(time);
            a = a.updated(acquiring(7, tick), time);
            assertEquals(atUpdate, a.sample(time), EPS);
            for (double offset : new double[] {0, 0.15, 0.3, 0.6}) {
                double shown = a.sample(time + offset);
                assertTrue(shown >= last);
                assertTrue(shown <= tick / 30.0);
                last = shown;
            }
        }
    }

    @Test void repeatedFramesAndGraceGapsDoNotRestartTheAnimation() {
        var a = SeekerAnimation.IDLE.updated(acquiring(7, 10), 3);
        assertSame(a, a.updated(acquiring(7, 10), 3.5));
        assertSame(a, a.updated(new Seeker(Seeker.NONE, 7, 10, 3), 4));
        assertEquals(a.sample(4), a.sample(4), EPS, "paused clock is stable");
        assertEquals(10.0 / 30, a.sample(10), EPS);
        var b = a.updated(acquiring(7, 11), 10);
        assertEquals(a.sample(10), b.sample(10), EPS, "late update continues from displayed value");
    }

    @Test void targetChangeRestartLossAndLockDiscardOldProgress() {
        var a = SeekerAnimation.IDLE.updated(acquiring(7, 20), 1);
        assertEquals(0, a.updated(acquiring(8, 1), 4).sample(4), EPS);
        assertEquals(0, a.updated(acquiring(7, 1), 4).sample(4), EPS);
        assertSame(SeekerAnimation.IDLE, a.updated(Seeker.IDLE, 4));
        assertSame(SeekerAnimation.IDLE, a.updated(new Seeker(7, Seeker.NONE, 0, 0), 4));
    }

    @Test void invalidRepresentationsAndClocksAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SeekerAnimation(-2, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SeekerAnimation(7, 0.5, 0.2, 0));
        assertThrows(IllegalArgumentException.class, () -> new SeekerAnimation(7, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SeekerAnimation(Seeker.NONE, 0, 0.2, 0));
        for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> SeekerAnimation.IDLE.sample(invalid));
            assertThrows(IllegalArgumentException.class, () -> SeekerAnimation.IDLE.updated(acquiring(7, 1), invalid));
        }
        var a = SeekerAnimation.IDLE.updated(acquiring(7, 1), 5);
        assertThrows(IllegalArgumentException.class, () -> a.updated(acquiring(7, 2), 4));
    }
}
