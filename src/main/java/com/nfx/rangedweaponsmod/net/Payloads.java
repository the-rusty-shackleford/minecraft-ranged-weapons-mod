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
package com.nfx.rangedweaponsmod.net;

import com.nfx.rangedweaponsmod.PlayerGunnery;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.client.RecoilCamera;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The three payloads, registered under one protocol version. Handlers run
 * on the main thread of their side, which the registrar arranges by
 * default.
 */
public final class Payloads {
    private Payloads() {}

    /** Bumped when a payload's shape changes; a mismatch refuses the connection early. */
    private static final String VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(TriggerPayload.TYPE, TriggerPayload.STREAM_CODEC,
                (payload, context) -> PlayerGunnery.onTrigger(context.player(), payload.held()));
        registrar.playToServer(ReloadPayload.TYPE, ReloadPayload.STREAM_CODEC,
                (payload, context) -> PlayerGunnery.onReloadKey(context.player()));
        registrar.playToServer(AimPayload.TYPE, AimPayload.STREAM_CODEC,
                (payload, context) -> PlayerGunnery.onAim(context.player(), payload.aiming()));
        registrar.playToClient(ShotFiredPayload.TYPE, ShotFiredPayload.STREAM_CODEC,
                (payload, context) -> {
                    // Only ever executed on a client; the guard keeps the
                    // client class from being touched on a dedicated server.
                    if (FMLEnvironment.dist.isClient()) {
                        RecoilCamera.onShotFired(payload);
                    }
                });
    }
}
