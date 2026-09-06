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

import com.nfx.rangedweaponsmod.GunItem;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.net.AimPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aiming down the sights: the aim key held with a gun in the main hand.
 *
 * <p>The server is told on each change, for spread; everything visible is
 * the client's. Any gun leans in a little (a small zoom). A scoped gun
 * looks through its scope: a real magnification, the scope's mask over
 * the view with the crosshair left showing, and the gun itself out of the
 * way, as vanilla does for a spyglass. The lean in and out is eased over a
 * few ticks so the view does not snap.
 *
 * <p>Cost, stated: a few comparisons per client tick while a gun is held.
 */
@EventBusSubscriber(modid = RangedWeaponsMod.MOD_ID, value = Dist.CLIENT)
public final class Aiming {
    private Aiming() {}

    private static final ResourceLocation SCOPE_MASK =
            ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "textures/gui/scope.png");
    /** Ticks from sights down to fully up, and back. */
    private static final float EASE_TICKS = 4.0f;

    // What the server was last told. RI: mirrors the last AimPayload sent.
    private static boolean aiming = false;
    // 0 sights down .. 1 sights up, eased; and its value last tick, for the frame lerp.
    private static float progress = 0.0f;
    private static float previousProgress = 0.0f;

    /**
     * effects: reads the aim key against the held gun, tells the server of
     * a change, and advances the ease; called once per client tick
     */
    static void tick(Minecraft mc, LocalPlayer player) {
        boolean wants = player != null && mc.screen == null && Keys.AIM.isDown() && GunItem.isGun(player.getMainHandItem());
        if (wants != aiming) {
            aiming = wants;
            if (player != null) {
                PacketDistributor.sendToServer(new AimPayload(aiming));
            }
        }
        previousProgress = progress;
        progress = Mth.clamp(progress + (aiming ? 1.0f : -1.0f) / EASE_TICKS, 0.0f, 1.0f);
    }

    /** effects: drops the sights, for a new world */
    static void reset() {
        aiming = false;
        progress = 0.0f;
        previousProgress = 0.0f;
    }

    /** effects: returns how far up the sights are this frame, 0 to 1 */
    static float progress(float partialTick) {
        return Mth.lerp(partialTick, previousProgress, progress);
    }

    private static boolean throughAScope(Minecraft mc) {
        return mc.player != null && GunItem.isScoped(mc.player.getMainHandItem());
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (progress == 0.0f && previousProgress == 0.0f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float zoom = throughAScope(mc) ? ClientConfig.SCOPE_ZOOM.get().floatValue() : ClientConfig.AIM_ZOOM.get().floatValue();
        float p = progress((float) event.getPartialTick());
        // The world's field of view narrows; the hand's is left alone, so a
        // gun that stays visible does not balloon.
        if (event.usedConfiguredFov()) {
            event.setFOV(event.getFOV() / Mth.lerp(p, 1.0f, zoom));
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        // Looking through the scope, the gun is out of the way, as a spyglass is.
        if (event.getHand() == InteractionHand.MAIN_HAND && progress >= 1.0f && throughAScope(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    /** The scope's mask: black to the edges, the view through a circle; the crosshair is drawn above it. */
    static void renderScope(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (progress <= 0.0f || !throughAScope(mc) || mc.options.hideGui) {
            return;
        }
        float p = progress(delta.getGameTimeDeltaPartialTick(false));
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // The mask is square and is drawn as large as the shorter screen
        // dimension, centred, so the circle is a circle at any aspect; the
        // bands the square leaves on the wide sides are filled black.
        int size = Math.min(width, height);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        int alpha = Math.round(255 * Mth.clamp(p, 0.0f, 1.0f));
        int black = alpha << 24;
        if (x > 0) {
            graphics.fill(0, 0, x, height, black);
            graphics.fill(x + size, 0, width, height, black);
        }
        if (y > 0) {
            graphics.fill(0, 0, width, y, black);
            graphics.fill(0, y + size, width, height, black);
        }
        graphics.setColor(1.0f, 1.0f, 1.0f, alpha / 255.0f);
        graphics.blit(SCOPE_MASK, x, y, 0, 0, size, size, size, size);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
