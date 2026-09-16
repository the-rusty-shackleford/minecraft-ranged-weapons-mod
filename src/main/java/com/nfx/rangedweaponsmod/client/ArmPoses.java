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
package com.nfx.rangedweaponsmod.client;

import com.nfx.rangedweaponsmod.domain.HoldOut;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * How a body holds a gun, seen by others.
 *
 * <p>The game has no pose for a pistol: an item in hand hangs at the side.
 * A pistol is held out and follows the look, as in every shooter -- one
 * arm, the crossbow's trigger arm number for number ({@link HoldOut}), so
 * that animation packs, which recognise the game's poses by their numbers
 * and know nothing of a mod's, show it held out too. The pose is a value
 * added to the game's own enum, declared in
 * {@code META-INF/enumextensions.json}, and this class is where it is
 * read from; the two-handed guns use the game's crossbow hold itself.
 */
public final class ArmPoses {
    private ArmPoses() {}

    /** A single arm out along the look: the crossbow's trigger arm, alone. */
    public static final EnumProxy<HumanoidModel.ArmPose> PISTOL =
            new EnumProxy<>(HumanoidModel.ArmPose.class, false, (IArmPoseTransformer) ArmPoses::aim);

    private static void aim(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart limb = right ? model.rightArm : model.leftArm;
        HoldOut.Arm hold = HoldOut.triggerArm(model.head.xRot, model.head.yRot, right);
        limb.xRot = hold.pitch();
        limb.yRot = hold.yaw();
        limb.zRot = hold.roll();
    }
}
