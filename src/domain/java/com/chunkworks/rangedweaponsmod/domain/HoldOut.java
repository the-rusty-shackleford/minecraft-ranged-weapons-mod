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
 * The arm that holds a gun out along the look: the game's own crossbow
 * trigger arm, number for number.
 *
 * <p>Animation packs cannot see a mod's arm pose. They see the vanilla
 * model's arm angles after the game has posed it, and recognise the game's
 * poses by matching those angles numerically. Fresh Animations: Player
 * Extension (v1.1, {@code a_player_variables.jpm}) takes an arm for the
 * crossbow's trigger arm when its yaw is within {@value #YAW_SLACK} of the
 * head's yaw {@value #YAW} and its pitch within {@value #PITCH_SLACK} of the
 * head's pitch less a right angle plus {@value #LIFT}; the pitch slack is the
 * idle bob the game adds to every arm after posing it, up to 0.05. A pose
 * that matches no fingerprint is a plain held item to such a pack, and the
 * arm hangs. So every gun's trigger arm speaks the crossbow's numbers
 * exactly, in the game's own order of float operations, and every such pack
 * shows it held out.
 */
public final class HoldOut {
    private HoldOut() {}

    /** The trigger arm's yaw beside the head's, radians, for the right arm; negated for the left. The game's crossbow hold. */
    public static final float YAW = -0.3f;
    /** The trigger arm's pitch above the head's less a right angle, radians. The game's crossbow hold. */
    public static final float LIFT = 0.1f;
    /** How far a yaw may stray and still be recognised. */
    public static final float YAW_SLACK = 1e-4f;
    /** How far a pitch may stray and still be recognised: the game's idle bob, and a hair. */
    public static final float PITCH_SLACK = 0.0501f;

    private static final float RIGHT_ANGLE = (float) (Math.PI / 2);

    /**
     * An arm's rotation, radians, in the model's order. Immutable.
     *
     * @param pitch about x: negative raises the arm forward
     * @param yaw   about y
     * @param roll  about z
     */
    public record Arm(float pitch, float yaw, float roll) {}

    /**
     * The trigger arm for a head at the given angles.
     *
     * @param headPitch the head's pitch, radians, positive looking down
     * @param headYaw   the head's yaw relative to the body, radians
     * @param right     whether the arm is the right one
     * @return the arm's rotation, computed as the game computes its crossbow hold so the floats match to the last bit
     */
    public static Arm triggerArm(float headPitch, float headYaw, boolean right) {
        return new Arm(-RIGHT_ANGLE + headPitch + LIFT, (right ? YAW : -YAW) + headYaw, 0.0f);
    }

    /**
     * Whether an arm is recognised as the crossbow's trigger arm by the
     * fingerprint above.
     *
     * @param arm       the arm's rotation as the model holds it after posing and bobbing
     * @param headPitch the head's pitch, radians
     * @param headYaw   the head's yaw relative to the body, radians
     * @param right     whether the arm is the right one
     * @return true if both yaw and pitch are within their slack of the trigger arm's
     */
    public static boolean recognised(Arm arm, float headPitch, float headYaw, boolean right) {
        Arm expected = triggerArm(headPitch, headYaw, right);
        return Math.abs(arm.yaw() - expected.yaw()) <= YAW_SLACK
                && Math.abs(arm.pitch() - expected.pitch()) <= PITCH_SLACK;
    }
}
