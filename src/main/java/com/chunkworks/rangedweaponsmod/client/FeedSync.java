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

import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.domain.FeedMode;
import com.chunkworks.rangedweaponsmod.net.FeedPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tells the server this player's feed mode: at login, and again whenever
 * the client config changes (the file edited, or the Mods screen's config
 * page saved). The server keeps it for the connection; nothing here touches
 * a gun.
 */
@EventBusSubscriber(modid = RangedWeaponsMod.MOD_ID, value = Dist.CLIENT)
public final class FeedSync {
    private FeedSync() {}

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        send();
    }

    @SubscribeEvent
    public static void onConfigChanged(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            send();
        }
    }

    /**
     * effects: makes {@code mode} this player's setting, saved to their
     * client config, and tells the server if connected; what the booth and
     * any future key or command go through
     */
    public static void choose(FeedMode mode) {
        ClientConfig.FEED.set(mode);
        ClientConfig.SPEC.save();
        send();
    }

    /** effects: sends the current setting to the server if this client is connected to one */
    static void send() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && mc.getConnection().hasChannel(FeedPayload.TYPE)) {
            PacketDistributor.sendToServer(new FeedPayload(ClientConfig.feed()));
        }
    }
}
