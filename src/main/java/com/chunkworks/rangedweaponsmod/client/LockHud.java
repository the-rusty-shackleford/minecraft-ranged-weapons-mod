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
package com.chunkworks.rangedweaponsmod.client;

import com.chunkworks.rangedweaponsmod.LauncherItem;
import com.chunkworks.rangedweaponsmod.ModData;
import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.domain.Seeker;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

/**
 * The seeker on the holder's screen (D-0028), read off the synced {@link ModData#LOCK}: with the
 * sight up, a ring at the centre; while acquiring, amber brackets on the candidate that close in
 * as the lock nears; once locked, a red diamond on the target wherever it is, and an edge marker
 * pointing to it when it is off the screen or behind.
 *
 * <p>Targets are placed by projecting their centre through the camera's own axes and the world's
 * field of view as last computed this frame, not through the world's render matrices, so a shader
 * pack that replaces the renderer does not take the marks away. View bobbing is not modelled; the
 * marks sway a pixel or two from the target with it.
 *
 * <p>Drawn with plain fills, so it needs no textures; Astra may restyle it (the brief).
 */
@EventBusSubscriber(modid = RangedWeaponsMod.MOD_ID, value = Dist.CLIENT)
public final class LockHud {
    private LockHud() {}

    private static final int RING = 0x99FFFFFF;
    private static final int ACQUIRING = 0xFFFFB000;
    private static final int LOCKED = 0xFFFF4040;
    /** Pixels the edge marker keeps from the screen's edge. */
    private static final int EDGE = 24;

    /** The world's field of view, degrees, as last computed this frame. */
    private static double worldFov = 70.0;

    /** effects: remembers the world's field of view once every other mod has had its say */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) {
            worldFov = event.getFOV();
        }
    }

    /** effects: draws the seeker's marks for the launcher in the main hand; nothing otherwise */
    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.level == null || !LauncherItem.isLauncher(player.getMainHandItem())) {
            return;
        }
        Seeker seeker = player.getData(ModData.LOCK);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Camera camera = mc.gameRenderer.getMainCamera();
        if (player.getData(ModData.AIMING)) {
            corners(graphics, width / 2, height / 2, 12, 4, RING);
        }
        if (seeker.isAcquiring()) {
            Entity candidate = mc.level.getEntity(seeker.candidate());
            if (candidate != null) {
                double[] at = project(centre(candidate, partial), camera, width, height);
                if (at[2] > 0.0) {
                    int half = (int) Math.round(40 - 26 * seeker.progress());
                    corners(graphics, (int) at[0], (int) at[1], half, Math.max(3, half / 3), ACQUIRING);
                }
            }
        }
        if (seeker.isLocked()) {
            Entity target = mc.level.getEntity(seeker.locked());
            Component label = Component.translatable("hud.rangedweaponsmod.lock");
            if (target == null) {
                graphics.drawCenteredString(mc.font, label, width / 2, height / 2 + 18, LOCKED);
                return;
            }
            double[] at = project(centre(target, partial), camera, width, height);
            boolean onScreen = at[2] > 0.0 && at[0] >= 0 && at[0] < width && at[1] >= 0 && at[1] < height;
            if (onScreen) {
                diamond(graphics, (int) at[0], (int) at[1], 10, LOCKED);
                graphics.drawCenteredString(mc.font, label, (int) at[0], (int) at[1] + 14, LOCKED);
            } else {
                int[] edge = edgeToward(at, width, height);
                diamond(graphics, edge[0], edge[1], 5, LOCKED);
                graphics.drawCenteredString(mc.font, label, edge[0], edge[1] + 9, LOCKED);
            }
        }
    }

    private static Vec3 centre(Entity entity, float partial) {
        return entity.getPosition(partial).add(0.0, entity.getBbHeight() / 2.0, 0.0);
    }

    /**
     * effects: returns {@code {x, y, depth}}: where {@code world} falls on a screen of the given
     * size through {@code camera} at the world's field of view, and its depth along the look
     * (non-positive behind the camera, where x and y are mirrored and off any screen)
     */
    static double[] project(Vec3 world, Camera camera, int width, int height) {
        Vec3 rel = world.subtract(camera.getPosition());
        Vector3f forward = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        double depth = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
        double right = -(rel.x * left.x() + rel.y * left.y() + rel.z * left.z());
        double upward = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();
        double t = Math.tan(Math.toRadians(worldFov) / 2.0);
        double aspect = width / (double) height;
        double z = Math.abs(depth) < 1e-6 ? 1e-6 : depth;
        double ndcX = right / (z * t * aspect);
        double ndcY = upward / (z * t);
        return new double[] {(ndcX + 1.0) / 2.0 * width, (1.0 - ndcY) / 2.0 * height, depth};
    }

    /** effects: returns the point on a rectangle {@link #EDGE} inside the screen in the direction of {@code at} from the centre */
    private static int[] edgeToward(double[] at, int width, int height) {
        double cx = width / 2.0;
        double cy = height / 2.0;
        double dx = at[0] - cx;
        double dy = at[1] - cy;
        if (at[2] <= 0.0) {
            dx = -dx;
            dy = -dy;
        }
        if (Math.abs(dx) < 1e-6 && Math.abs(dy) < 1e-6) {
            dy = 1.0;
        }
        double sx = (cx - EDGE) / Math.max(1e-6, Math.abs(dx));
        double sy = (cy - EDGE) / Math.max(1e-6, Math.abs(dy));
        double s = Math.min(sx, sy);
        return new int[] {(int) Math.round(cx + dx * s), (int) Math.round(cy + dy * s)};
    }

    /** effects: four L-shaped corners of a square of half-side {@code half} centred at (x, y) */
    private static void corners(GuiGraphics g, int x, int y, int half, int length, int color) {
        int l = x - half, r = x + half, t = y - half, b = y + half;
        g.fill(l, t, l + length, t + 1, color);
        g.fill(l, t, l + 1, t + length, color);
        g.fill(r - length + 1, t, r + 1, t + 1, color);
        g.fill(r, t, r + 1, t + length, color);
        g.fill(l, b, l + length, b + 1, color);
        g.fill(l, b - length + 1, l + 1, b + 1, color);
        g.fill(r - length + 1, b, r + 1, b + 1, color);
        g.fill(r, b - length + 1, r + 1, b + 1, color);
    }

    /** effects: the outline of a diamond of radius {@code radius} centred at (x, y) */
    private static void diamond(GuiGraphics g, int x, int y, int radius, int color) {
        for (int i = 0; i <= radius; i++) {
            int j = radius - i;
            g.fill(x + i, y + j, x + i + 1, y + j + 1, color);
            g.fill(x - i, y + j, x - i + 1, y + j + 1, color);
            g.fill(x + i, y - j, x + i + 1, y - j + 1, color);
            g.fill(x - i, y - j, x - i + 1, y - j + 1, color);
        }
    }
}
