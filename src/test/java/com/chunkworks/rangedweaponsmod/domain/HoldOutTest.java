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

import com.chunkworks.rangedweaponsmod.domain.HoldOut.Arm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link HoldOut}.
 *
 * <p>Partitions. Head pitch: level / down / up / the limits (+-90 degrees).
 * Head yaw: straight / left / right / at the body's limit (50 degrees).
 * Arm: right / left. Recognition: the trigger arm itself; the arm after the
 * game's idle bob (pitch off by up to 0.05); the pose this mod used to
 * carry (yaw off by 0.2); a pitch just past the slack; a level arm without
 * the lift.
 */
final class HoldOutTest {

    private static final float DEG = (float) (Math.PI / 180);
    private static final float[] PITCHES = {0.0f, 10 * DEG, -10 * DEG, 90 * DEG, -90 * DEG};
    private static final float[] YAWS = {0.0f, 30 * DEG, -30 * DEG, 50 * DEG, -50 * DEG};

    /** The game's crossbow hold, transcribed from {@code AnimationUtils.animateCrossbowHold} for the trigger arm. */
    private static Arm vanilla(float headPitch, float headYaw, boolean right) {
        float yaw = right ? -0.3F + headYaw : 0.3F + headYaw;
        float pitch = (float) (-Math.PI / 2) + headPitch + 0.1F;
        return new Arm(pitch, yaw, 0.0f);
    }

    @Test
    void theTriggerArmIsTheGamesCrossbowHoldToTheLastBit() {
        for (float pitch : PITCHES) {
            for (float yaw : YAWS) {
                for (boolean right : new boolean[] {true, false}) {
                    Arm expected = vanilla(pitch, yaw, right);
                    Arm arm = HoldOut.triggerArm(pitch, yaw, right);
                    assertEquals(Float.floatToIntBits(expected.pitch()), Float.floatToIntBits(arm.pitch()), "pitch at " + pitch + "," + yaw);
                    assertEquals(Float.floatToIntBits(expected.yaw()), Float.floatToIntBits(arm.yaw()), "yaw at " + pitch + "," + yaw);
                    assertEquals(0.0f, arm.roll());
                }
            }
        }
    }

    @Test
    void theLeftArmMirrorsTheYawOnly() {
        Arm right = HoldOut.triggerArm(0.2f, 0.4f, true);
        Arm left = HoldOut.triggerArm(0.2f, 0.4f, false);
        assertEquals(right.pitch(), left.pitch());
        assertEquals(-0.3f + 0.4f, right.yaw());
        assertEquals(0.3f + 0.4f, left.yaw());
    }

    @Test
    void theTriggerArmIsRecognisedAtEveryHeadAngle() {
        for (float pitch : PITCHES) {
            for (float yaw : YAWS) {
                assertTrue(HoldOut.recognised(HoldOut.triggerArm(pitch, yaw, true), pitch, yaw, true));
                assertTrue(HoldOut.recognised(HoldOut.triggerArm(pitch, yaw, false), pitch, yaw, false));
            }
        }
    }

    @Test
    void theGamesIdleBobDoesNotBreakRecognition() {
        Arm arm = HoldOut.triggerArm(0.1f, 0.2f, true);
        assertTrue(HoldOut.recognised(new Arm(arm.pitch() + 0.05f, arm.yaw(), 0.05f), 0.1f, 0.2f, true));
        assertTrue(HoldOut.recognised(new Arm(arm.pitch() - 0.05f, arm.yaw(), -0.05f), 0.1f, 0.2f, true));
    }

    @Test
    void aPitchPastTheSlackIsNotRecognised() {
        Arm arm = HoldOut.triggerArm(0.1f, 0.2f, true);
        assertFalse(HoldOut.recognised(new Arm(arm.pitch() + 0.06f, arm.yaw(), 0.0f), 0.1f, 0.2f, true));
    }

    @Test
    void theOldOneHandedPoseIsNotRecognised() {
        // Before 2.3.1: level with the eyes, yaw a tenth beside the head's.
        float pitch = 10 * DEG;
        float yaw = 0.0f;
        Arm old = new Arm((float) (-Math.PI / 2) + pitch, -0.1f + yaw, 0.0f);
        assertFalse(HoldOut.recognised(old, pitch, yaw, true));
    }

    @Test
    void aLevelArmWithoutTheLiftIsNotRecognised() {
        float pitch = 0.0f;
        Arm level = new Arm((float) (-Math.PI / 2) + pitch, -0.3f, 0.0f);
        assertFalse(HoldOut.recognised(level, pitch, 0.0f, true));
    }
}
