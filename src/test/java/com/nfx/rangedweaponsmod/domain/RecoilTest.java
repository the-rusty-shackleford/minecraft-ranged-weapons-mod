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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Recoil}.
 *
 * <p>Partitions. At rest: sample is zero at every partial. Kick: adds to the
 * current offset, leaves the previous alone; two kicks in one tick add;
 * a kick beyond the cap is clamped; a non-finite kick is refused. Step:
 * current becomes previous; current shrinks by exactly the recovery
 * fraction; the offset never changes sign; repeated steps reach exactly
 * zero (the rest epsilon); recovery 1 settles in one step. Sample: partial
 * 0 is the previous, 1 the current, 0.5 the midpoint; partial outside
 * [0, 1] is refused. Rapid fire: kicks every third tick reach a bounded
 * steady state. RI: recovery 0 / above 1 / NaN; an angle beyond the cap.
 */
final class RecoilTest {

    private static final float EPS = 1e-6f;

    @Test
    void atRestSamplesZeroEverywhere() {
        Recoil r = Recoil.atRest(0.35f);
        assertTrue(r.atRest());
        for (float p : new float[]{0.0f, 0.25f, 0.5f, 1.0f}) {
            assertEquals(0.0f, r.sample(p).pitch(), 0.0f);
            assertEquals(0.0f, r.sample(p).yaw(), 0.0f);
        }
    }

    @Test
    void aKickAddsToTheCurrentOffsetAndLeavesThePreviousAlone() {
        Recoil r = Recoil.atRest(0.35f).kicked(0.5f, -0.2f);
        assertEquals(0.5f, r.pitch(), EPS);
        assertEquals(-0.2f, r.yaw(), EPS);
        assertEquals(0.0f, r.prevPitch(), 0.0f);
        assertEquals(0.0f, r.prevYaw(), 0.0f);
        assertFalse(r.atRest());
    }

    @Test
    void twoKicksInOneTickAdd() {
        Recoil r = Recoil.atRest(0.35f).kicked(0.5f, 0.1f).kicked(0.5f, -0.3f);
        assertEquals(1.0f, r.pitch(), EPS);
        assertEquals(-0.2f, r.yaw(), EPS);
    }

    @Test
    void aKickBeyondTheCapIsClamped() {
        Recoil r = Recoil.atRest(0.5f).kicked(1000.0f, -1000.0f);
        assertEquals(Recoil.MAX_OFFSET, r.pitch(), 0.0f);
        assertEquals(-Recoil.MAX_OFFSET, r.yaw(), 0.0f);
    }

    @Test
    void aNonFiniteKickIsRefused() {
        Recoil r = Recoil.atRest(0.5f);
        assertThrows(IllegalArgumentException.class, () -> r.kicked(Float.NaN, 0.0f));
        assertThrows(IllegalArgumentException.class, () -> r.kicked(0.0f, Float.POSITIVE_INFINITY));
    }

    @Test
    void aStepMovesCurrentToPreviousAndShrinksCurrentByTheRecovery() {
        Recoil r = Recoil.atRest(0.25f).kicked(2.0f, -1.0f).stepped();
        assertEquals(2.0f, r.prevPitch(), EPS);
        assertEquals(-1.0f, r.prevYaw(), EPS);
        assertEquals(1.5f, r.pitch(), EPS);
        assertEquals(-0.75f, r.yaw(), EPS);
    }

    @Test
    void recoveryNeverChangesTheSignAndNeverOvershoots() {
        Recoil r = Recoil.atRest(0.6f).kicked(3.0f, -2.0f);
        float lastPitch = r.pitch();
        float lastYaw = r.yaw();
        for (int i = 0; i < 20; i++) {
            r = r.stepped();
            assertTrue(r.pitch() >= 0.0f && r.pitch() <= lastPitch, "pitch at step " + i + ": " + r.pitch());
            assertTrue(r.yaw() <= 0.0f && r.yaw() >= lastYaw, "yaw at step " + i + ": " + r.yaw());
            lastPitch = r.pitch();
            lastYaw = r.yaw();
        }
    }

    @Test
    void repeatedStepsReachExactlyZero() {
        Recoil r = Recoil.atRest(0.35f).kicked(5.0f, 5.0f);
        int steps = 0;
        while (!r.atRest() && steps < 200) {
            r = r.stepped();
            steps++;
        }
        assertTrue(r.atRest(), "not at rest after " + steps + " steps");
        assertEquals(0.0f, r.pitch(), 0.0f);
        assertTrue(steps < 60, "took " + steps + " steps to settle");
    }

    @Test
    void recoveryOneSettlesInASingleStep() {
        Recoil r = Recoil.atRest(1.0f).kicked(4.0f, 4.0f).stepped();
        assertEquals(0.0f, r.pitch(), 0.0f);
        assertEquals(4.0f, r.prevPitch(), 0.0f);
        assertTrue(r.stepped().atRest());
    }

    @Test
    void samplesInterpolateFromPreviousToCurrent() {
        Recoil r = Recoil.atRest(0.5f).kicked(2.0f, -2.0f).stepped();   // prev 2, current 1
        assertEquals(2.0f, r.sample(0.0f).pitch(), EPS);
        assertEquals(1.5f, r.sample(0.5f).pitch(), EPS);
        assertEquals(1.0f, r.sample(1.0f).pitch(), EPS);
        assertEquals(-1.5f, r.sample(0.5f).yaw(), EPS);
    }

    @Test
    void aKickEasesInOverTheCurrentTick() {
        Recoil r = Recoil.atRest(0.5f).kicked(2.0f, 0.0f);   // prev 0, current 2
        assertEquals(0.0f, r.sample(0.0f).pitch(), EPS);
        assertEquals(1.0f, r.sample(0.5f).pitch(), EPS);
        assertEquals(2.0f, r.sample(1.0f).pitch(), EPS);
    }

    @Test
    void aPartialOutsideTheTickIsRefused() {
        Recoil r = Recoil.atRest(0.5f);
        assertThrows(IllegalArgumentException.class, () -> r.sample(-0.01f));
        assertThrows(IllegalArgumentException.class, () -> r.sample(1.01f));
        assertThrows(IllegalArgumentException.class, () -> r.sample(Float.NaN));
    }

    @Test
    void rapidFireAccumulatesToABoundedSteadyStateInsteadOfSnapping() {
        // A kick of 0.55 every third tick at recovery 0.35: the geometric
        // steady state is 0.55 / (1 - 0.65^3) ~= 0.759 just after a kick.
        Recoil r = Recoil.atRest(0.35f);
        float peak = 0.0f;
        for (int tick = 0; tick < 300; tick++) {
            if (tick % 3 == 0) {
                r = r.kicked(0.55f, 0.0f);
                peak = r.pitch();
            }
            r = r.stepped();
        }
        assertEquals(0.759f, peak, 0.01f);
        // and never anywhere near the cap
        assertTrue(peak < 2.0f);
    }

    @Test
    void theRepInvariantIsEnforced() {
        assertThrows(IllegalArgumentException.class, () -> Recoil.atRest(0.0f));
        assertThrows(IllegalArgumentException.class, () -> Recoil.atRest(1.01f));
        assertThrows(IllegalArgumentException.class, () -> Recoil.atRest(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Recoil(Recoil.MAX_OFFSET + 1, 0, 0, 0, 0.5f));
        assertThrows(IllegalArgumentException.class, () -> new Recoil(0, 0, Float.NaN, 0, 0.5f));
    }
}
