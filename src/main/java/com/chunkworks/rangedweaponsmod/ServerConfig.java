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

import com.chunkworks.rangedweaponsmod.domain.FeedMode;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * What the server decides for everyone, in the world's
 * {@code serverconfig/rangedweaponsmod-server.toml}. NeoForge sends a
 * server config to every client at login, so the counter and the tooltips
 * show the rule in force rather than a guess.
 */
public final class ServerConfig {
    private ServerConfig() {}

    public static final ModConfigSpec SPEC;
    /** How guns take their rounds; see {@link FeedMode}. */
    public static final ModConfigSpec.EnumValue<FeedMode> FEED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("How guns take their rounds, for every player on this server.").push("ammunition");

        FEED = builder
                .comment("MAGAZINES: the pistol, rifles and machine gun take detachable magazines and hold nothing without one;",
                         "           the shotgun and revolver load loose rounds from the inventory.",
                         "LOOSE: every gun loads loose rounds, drawn from the whole inventory and then from carried Backpacks+ bags;",
                         "       magazines are not needed, and a gun holding one hands it back.",
                         "Every client reads this at login: change it, then restart the server.")
                .defineEnum("feed", FeedMode.MAGAZINES);

        builder.pop();
        SPEC = builder.build();
    }

    /**
     * effects: returns the mode in force: the server's, which every client
     * received at login; {@link FeedMode#MAGAZINES} before any is loaded
     * (no world yet, or a client not yet told)
     */
    public static FeedMode feed() {
        return SPEC.isLoaded() ? FEED.get() : FeedMode.MAGAZINES;
    }
}
