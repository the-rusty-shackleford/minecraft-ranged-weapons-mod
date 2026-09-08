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
import net.minecraft.client.gui.screens.Screen;
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
 * Turns the attack key -- left click -- into trigger state.
 *
 * <p>When the attack key goes down with a gun in the main hand, the event
 * is cancelled, which returns from vanilla's whole attack path: no swing,
 * no melee hit on whatever is under the crosshair, no block breaking --
 * a gun is not a pickaxe, and while one is in hand nothing is mined. The
 * server is told the trigger is held, once. The use key is left entirely
 * to vanilla, so right-clicking a chest, a door or a villager with a gun
 * in hand does what it does with anything else in hand; the gun's own use
 * passes, so the hand is never dropped by the item-use path either.
 *
 * <p>Vanilla has no packet for the key coming up, so the release is this
 * mod's: when the key comes up, or the gun leaves the hand, or a screen
 * opens, the server is told the trigger is released, once. The server's
 * clock decides every shot in between. Vanilla posts the attack event
 * again every tick the key is held on a block; each is cancelled the same
 * way and tells the server nothing new.
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
        if (!event.isAttack() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null || !GunItem.isGun(player.getMainHandItem())) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!held) {
            held = true;
            PacketDistributor.sendToServer(new TriggerPayload(true));
        }
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
                    && mc.options.keyAttack.isDown() && GunItem.isGun(player.getMainHandItem());
            if (!stillHolding) {
                held = false;
                if (player != null) {
                    PacketDistributor.sendToServer(new TriggerPayload(false));
                }
            }
        }
        while (Keys.RELOAD.consumeClick()) {
            if (player != null && GunItem.isGun(player.getMainHandItem())) {
                // Shift+R is the swap: the same key, so it is rebound with it.
                PacketDistributor.sendToServer(new ReloadPayload(Screen.hasShiftDown()));
            }
        }
        Aiming.tick(mc, player);
        RecoilCamera.tick();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        held = false;
        Aiming.reset();
        RecoilCamera.reset();
    }
}
