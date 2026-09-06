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
package com.nfx.rangedweaponsmod.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * What a player tunes for themselves, in
 * {@code config/rangedweaponsmod-client.toml}. What a gun does is data
 * shared by everyone; how much of its kick reaches your eyes is yours.
 */
public final class ClientConfig {
    private ClientConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue RECOIL_SCALE;
    public static final ModConfigSpec.DoubleValue MODEL_KICK_SCALE;
    public static final ModConfigSpec.BooleanValue HUD_ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("How a shot feels. The kick itself comes from the gun's handling data;",
                        "these scale what reaches your screen. Read live.")
               .push("feel");

        RECOIL_SCALE = builder
                .comment("Scales the camera kick of every shot. 0 turns it off; 1 is as the gun's data says.")
                .defineInRange("recoilScale", 1.0D, 0.0D, 3.0D);

        MODEL_KICK_SCALE = builder
                .comment("Scales how much the gun in your hand jumps with each shot, separately from the camera.")
                .defineInRange("modelKickScale", 1.0D, 0.0D, 3.0D);

        builder.pop();

        builder.comment("The ammo counter beside the hotbar.").push("hud");

        HUD_ENABLED = builder
                .comment("Show rounds and reload progress while holding a gun.")
                .define("enabled", true);

        builder.pop();
        SPEC = builder.build();
    }
}
