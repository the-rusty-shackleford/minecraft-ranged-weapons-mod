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

import com.nfx.rangedweapons.client.compat.HoldMyItems;
import com.nfx.rangedweaponsmod.GunItem;
import com.nfx.rangedweaponsmod.ModItems;
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
 * <p>The press is vanilla's: the use key runs its ordinary path, so a door
 * or a villager under the crosshair gets the click, and only when nothing
 * in reach wants it does the gun's own {@code use} tell the server the
 * trigger is held. Vanilla has no packet for the key coming up, and it
 * repeats a held use at most every four ticks and never once an item is
 * "in use", so the release is this mod's: when the key comes up, or the
 * gun leaves the hand, or a screen opens, the server is told the trigger is
 * released, once. The server's clock decides every shot in between.
 *
 * <p>Cost, stated: one boolean per client tick while a gun is held.
 */
@EventBusSubscriber(modid = RangedWeaponsMod.MOD_ID, value = Dist.CLIENT)
public final class TriggerInput {
    private TriggerInput() {}

    // Whether the server may believe the trigger is held: a use key press
    // with a gun in hand went to vanilla and has not been followed by our
    // release. RI: a release is sent exactly once per true-to-false edge.
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
        // Not cancelled: vanilla decides between an interaction and the gun's use.
        held = true;
    }

    // Done on the first client tick, when every mod's config is loaded for
    // certain, rather than at a setup event whose order against config
    // loading would have to be known.
    private static boolean handsSettled = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!handsSettled) {
            handsSettled = true;
            HoldMyItems.excludeItems(ModItems.guns());
        }
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
