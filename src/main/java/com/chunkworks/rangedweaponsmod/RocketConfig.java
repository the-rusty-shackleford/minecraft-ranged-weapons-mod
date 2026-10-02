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
package com.chunkworks.rangedweaponsmod;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The world's rules for rockets (D-0028), in the server config
 * ({@code serverconfig/rangedweaponsmod-server.toml} in the world): how hard a rocket's blast is
 * and whether it breaks blocks, as Dynamite's own switch does. A world rule, not a player's
 * preference, so it is the server's; the feed mode stays each player's (D-0024).
 *
 * <p>Read on the server only, when a rocket detonates; the defaults stand in until the config
 * has loaded.
 */
public final class RocketConfig {
    private RocketConfig() {}

    public static final ModConfigSpec SPEC;
    static final ModConfigSpec.DoubleValue POWER;
    static final ModConfigSpec.BooleanValue BREAK_BLOCKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("rocket");
        POWER = b.comment("The blast's power. A creeper's is 3, a block of TNT's 4.")
                .defineInRange("power", 3.0, 0.5, 8.0);
        BREAK_BLOCKS = b.comment("Whether the blast breaks blocks. Off, it hurts what stands near it and leaves the ground alone.")
                .define("breakBlocks", true);
        b.pop();
        SPEC = b.build();
    }

    /** effects: returns the blast's power: the config's once loaded, 3 before */
    public static float power() {
        return SPEC.isLoaded() ? POWER.get().floatValue() : 3.0f;
    }

    /** effects: returns whether the blast breaks blocks: the config's once loaded, true before */
    public static boolean breakBlocks() {
        return !SPEC.isLoaded() || BREAK_BLOCKS.get();
    }

    /** requires: the config is loaded. effects: sets whether blasts break blocks, for tests */
    public static void setBreakBlocks(boolean breaks) {
        BREAK_BLOCKS.set(breaks);
    }
}
