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

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** The keys this mod adds. Firing is the vanilla use key; reload and aim are new. */
public final class Keys {
    private Keys() {}

    public static final String CATEGORY = "key.categories.rangedweaponsmod";

    /** Reload the held gun. R by default; rebindable in Controls. */
    public static final KeyMapping RELOAD =
            new KeyMapping("key.rangedweaponsmod.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    /** Aim down the sights while held. Left Alt by default, which vanilla leaves unbound; rebindable. */
    public static final KeyMapping AIM =
            new KeyMapping("key.rangedweaponsmod.aim", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
}
