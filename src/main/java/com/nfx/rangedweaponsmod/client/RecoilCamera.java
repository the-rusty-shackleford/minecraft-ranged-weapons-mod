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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nfx.rangedweaponsmod.GunItem;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.domain.Recoil;
import com.nfx.rangedweaponsmod.net.ShotFiredPayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The kick, on screen: a camera offset that recovers, and the same offset
 * jolting the gun in hand. The player's own rotation is never touched --
 * the offset is added to the camera's angles as they are computed for the
 * frame, so it cannot fight the mouse and leaves no drift behind.
 *
 * <p>State is one immutable {@link Recoil}, replaced on each kick and each
 * tick; frames read it with the tick's partial. That is the whole reason
 * rapid fire looks smooth here: nothing is restarted, kicks add and settle.
 */
@EventBusSubscriber(modid = RangedWeaponsMod.MOD_ID, value = Dist.CLIENT)
public final class RecoilCamera {
    private RecoilCamera() {}

    // The camera's kick is under a degree a shot; the gun in hand exaggerates
    // it, or the eye reads nothing. The three terms are client config, read
    // live: a rise that lifts the whole gun on screen, a pitch about the
    // camera that makes the muzzle climb faster than the grip, a sideways
    // swing. Their signs were settled in the photo booth, not derived: the
    // hand's frame is not the frame one would guess.

    private static Recoil recoil = Recoil.atRest(0.35f);

    /** effects: adds the shot's kick, scaled by the player's recoil setting, at the gun's recovery */
    public static void onShotFired(ShotFiredPayload payload) {
        float scale = ClientConfig.RECOIL_SCALE.get().floatValue();
        recoil = recoil.withRecovery(payload.recovery())
                .kicked(payload.pitchKick() * scale, payload.yawKick() * scale);
    }

    /** effects: one tick of recovery; nothing if already at rest */
    static void tick() {
        if (!recoil.atRest()) {
            recoil = recoil.stepped();
        }
    }

    /** effects: drops any offset, for a new world (or a fresh photograph) */
    public static void reset() {
        recoil = Recoil.atRest(recoil.recovery());
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (recoil.atRest()) {
            return;
        }
        Recoil.Sample sample = recoil.sample((float) event.getPartialTick());
        event.setPitch(event.getPitch() - sample.pitch());
        event.setYaw(event.getYaw() + sample.yaw());
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || recoil.atRest() || !GunItem.isGun(event.getItemStack())) {
            return;
        }
        float scale = ClientConfig.MODEL_KICK_SCALE.get().floatValue();
        if (scale == 0.0f) {
            return;
        }
        Recoil.Sample sample = recoil.sample(event.getPartialTick());
        // Not cancelled: these transforms are on the stack vanilla goes on
        // to render the arm and item with.
        PoseStack pose = event.getPoseStack();
        pose.translate(0.0f, sample.pitch() * ClientConfig.MODEL_RISE.get().floatValue() * scale, 0.0f);
        pose.mulPose(Axis.XP.rotationDegrees(sample.pitch() * ClientConfig.MODEL_PITCH.get().floatValue() * scale));
        pose.mulPose(Axis.YP.rotationDegrees(sample.yaw() * ClientConfig.MODEL_YAW.get().floatValue() * scale));
    }
}
