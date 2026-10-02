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
import com.chunkworks.rangedweaponsmod.Seeking;
import com.chunkworks.rangedweaponsmod.domain.Seeker;
import com.chunkworks.rangedweaponsmod.net.SeekPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The launcher's reticle as this client shows it (D-0028): with the sight up, the valid target the
 * reticle is on, by the same search the server once ran, over what this client draws; reported to
 * the server on each change, and "nothing" when the sight comes down. Contact is the player's: a
 * moving target is where the client shows it, not where the server has it by the time the aim
 * arrives.
 *
 * <p>Cost, stated: with the launcher's sight up, {@link Seeking#inReticle}'s eight entity queries and
 * a block ray or two per client tick; otherwise one comparison.
 */
public final class SeekReport {
    private SeekReport() {}

    private static int sent = Seeker.NONE;

    /** effects: reports a change of what the reticle is on */
    static void tick(Minecraft mc, LocalPlayer player) {
        int seen = Seeker.NONE;
        if (player != null && mc.level != null && Aiming.isUp() && LauncherItem.isLauncher(player.getMainHandItem())) {
            Entity target = Seeking.inReticle(mc.level, player);
            seen = target == null ? Seeker.NONE : target.getId();
        }
        if (seen != sent && player != null) {
            sent = seen;
            PacketDistributor.sendToServer(new SeekPayload(seen));
        }
    }

    /** effects: forgets what was sent, for a new world */
    static void reset() {
        sent = Seeker.NONE;
    }
}
