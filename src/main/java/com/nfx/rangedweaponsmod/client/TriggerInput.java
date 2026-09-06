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

import com.nfx.rangedweaponsmod.GunItem;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.net.ReloadPayload;
import com.nfx.rangedweaponsmod.net.TriggerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns the use key into trigger state.
 *
 * <p>Vanilla's right-click cannot drive a gun: it repeats a use at most
 * every four ticks, stops repeating once an item is in use, and slows a
 * player using an item to a fifth of their speed. So when the use key goes
 * down with a gun in the main hand, the click is cancelled -- no use packet,
 * no block or entity interaction, no arm swing -- and the server is told
 * the trigger is held, once. When the key comes up, or the gun leaves the
 * hand, or a screen opens, the server is told it is released, once. The
 * server's clock decides every shot in between.
 *
 * <p>Cost, stated: one boolean per client tick while a gun is held.
 */
@EventBusSubscriber(modid = RangedWeaponsMod.MOD_ID, value = Dist.CLIENT)
public final class TriggerInput {
    private TriggerInput() {}

    // What the server was last told. RI: true only while the server has been
    // sent a press that has not been followed by a release.
    private static boolean held = false;

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !GunItem.isGun(player.getMainHandItem())) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!held) {
            held = true;
            PacketDistributor.sendToServer(new TriggerPayload(true));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (held) {
            boolean stillHolding = player != null && mc.screen == null
                    && mc.options.keyUse.isDown() && GunItem.isGun(player.getMainHandItem());
            if (!stillHolding) {
                held = false;
                if (player != null) {
                    PacketDistributor.sendToServer(new TriggerPayload(false));
                }
            }
        }
        while (Keys.RELOAD.consumeClick()) {
            if (player != null && GunItem.isGun(player.getMainHandItem())) {
                PacketDistributor.sendToServer(ReloadPayload.INSTANCE);
            }
        }
        RecoilCamera.tick();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        held = false;
        RecoilCamera.reset();
    }
}
