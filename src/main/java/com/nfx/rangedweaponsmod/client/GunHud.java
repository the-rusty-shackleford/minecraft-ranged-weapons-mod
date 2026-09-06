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

import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweaponsmod.GunItem;
import com.nfx.rangedweaponsmod.ModData;
import com.nfx.rangedweaponsmod.Reload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The ammo counter: {@code rounds / capacity} to the right of the hotbar,
 * and a bar that fills while a reload runs. Drawn only while a gun is held;
 * everything it shows is on the stack (the rounds and the reload, both
 * synced) or in the profile (the capacity, synced with the data map).
 */
public final class GunHud {
    private GunHud() {}

    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_LOW_COLOR = 0xFFFF6E6E;
    private static final int BAR_BACK = 0xA0000000;
    private static final int BAR_FILL = 0xFFE0C060;
    private static final int BAR_WIDTH = 40;
    private static final int BAR_HEIGHT = 3;
    /** Rounds at or below which the counter turns red: a fifth of the magazine. */
    private static final int LOW_FRACTION = 5;

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientConfig.HUD_ENABLED.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!GunItem.isGun(stack)) {
            return;
        }
        RangedWeapon weapon = RangedWeapons.resolve(stack);
        if (weapon == null) {
            return;
        }
        int rounds = weapon.rounds(stack);
        int capacity = weapon.capacity(stack);
        Font font = mc.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        // Just right of the hotbar, which is 182 wide and centred.
        int x = screenWidth / 2 + 91 + 6;
        int y = screenHeight - 19;

        boolean unlimited = mc.player.hasInfiniteMaterials();
        String text = unlimited ? "\u221e / " + capacity : rounds + " / " + capacity;
        int color = !unlimited && rounds * LOW_FRACTION <= capacity ? TEXT_LOW_COLOR : TEXT_COLOR;
        graphics.drawString(font, text, x, y, color, true);

        Reload reload = stack.get(ModData.RELOAD.get());
        if (reload != null && mc.level != null) {
            float progress = reload.progress(mc.level.getGameTime());
            int barY = y - BAR_HEIGHT - 2;
            graphics.fill(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, BAR_BACK);
            graphics.fill(x, barY, x + Math.round(BAR_WIDTH * progress), barY + BAR_HEIGHT, BAR_FILL);
        }
    }
}
