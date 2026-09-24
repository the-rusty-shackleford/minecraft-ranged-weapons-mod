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

import com.chunkworks.rangedweaponsmod.client.ClientConfig;
import com.chunkworks.rangedweaponsmod.domain.FeedMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Each player's own choice of how their guns take rounds (D-0024). The
 * choice lives in the player's client config; the client tells the server
 * at login and whenever it changes, and the server keeps it here for as
 * long as the player is connected. A player the server has not heard from
 * (an older client, or the first ticks before the word arrives) is in
 * {@link FeedMode#MAGAZINES}, the mode every gun is designed for.
 *
 * <p>AF: the mode each connected player asked for. RI: only connected
 * players have an entry; the map is safe to read from any thread, though
 * it is written on the server thread alone.
 */
public final class FeedModes {
    private FeedModes() {}

    private static final Map<UUID, FeedMode> CHOSEN = new ConcurrentHashMap<>();

    /**
     * effects: returns the mode {@code player}'s guns follow: on a client,
     * that client's own setting; on the server, what the player's client
     * told it, or {@link FeedMode#MAGAZINES} if it never did
     */
    public static FeedMode of(Player player) {
        if (player.level().isClientSide()) {
            return local();
        }
        return CHOSEN.getOrDefault(player.getUUID(), FeedMode.MAGAZINES);
    }

    /**
     * effects: returns this client's own setting, {@link FeedMode#MAGAZINES}
     * where there is no client config (a dedicated server, or before it
     * loads). The tooltip and the counter read this, so what a player sees
     * is what they chose, the moment they choose it.
     */
    public static FeedMode local() {
        return ClientConfig.feed();
    }

    /** effects: records what {@code player}'s client asked for; the player's next look at a gun follows it */
    public static void set(Player player, FeedMode mode) {
        CHOSEN.put(player.getUUID(), mode);
    }

    /** effects: forgets a player who left; their client says again at the next login */
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHOSEN.remove(event.getEntity().getUUID());
    }
}
