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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

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
 * <p>When the use key goes down with a gun in the main hand, the click is
 * first offered to whatever is under the crosshair, through vanilla's own
 * methods with vanilla's own reach: an entity is interacted with, a block
 * is used. If either takes the click, that was the click. If nothing does,
 * the event is cancelled -- which returns from vanilla's whole use path,
 * so no item use, no swing, no off-hand attempt -- and the server is told
 * the trigger is held, once. The one thing vanilla's own item-use path
 * must never do here is run: a used item drops the hand out of view and
 * raises it again, the re-equip animation, and at a machine gun's cadence
 * that is a hand that never stops dropping.
 *
 * <p>Vanilla has no packet for the key coming up, and repeats a held use
 * at most every four ticks, so the release is this mod's: when the key
 * comes up, or the gun leaves the hand, or a screen opens, the server is
 * told the trigger is released, once. The server's clock decides every
 * shot in between.
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
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null || !GunItem.isGun(player.getMainHandItem())) {
            return;
        }
        if (offerClickToTarget(mc, player)) {
            // Something in reach took it. Vanilla would go on to the item;
            // it must not (see the class comment), so the event ends here.
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!held) {
            held = true;
            PacketDistributor.sendToServer(new TriggerPayload(true));
        }
    }

    /**
     * effects: gives the click to the entity or block under the crosshair
     * the way vanilla's use path does, with the same calls and so the same
     * reach and packets; returns whether either consumed it
     */
    private static boolean offerClickToTarget(Minecraft mc, LocalPlayer player) {
        HitResult hit = mc.hitResult;
        if (hit == null) {
            return false;
        }
        if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            Entity entity = entityHit.getEntity();
            if (!mc.level.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                return true;   // vanilla returns without doing anything here
            }
            InteractionResult result = mc.gameMode.interactAt(player, entity, entityHit, InteractionHand.MAIN_HAND);
            if (!result.consumesAction()) {
                result = mc.gameMode.interact(player, entity, InteractionHand.MAIN_HAND);
            }
            if (result.consumesAction()) {
                if (result.shouldSwing()) {
                    player.swing(InteractionHand.MAIN_HAND);
                }
                return true;
            }
            return false;
        }
        if (hit.getType() == HitResult.Type.BLOCK) {
            InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, (BlockHitResult) hit);
            if (result.consumesAction()) {
                if (result.shouldSwing()) {
                    player.swing(InteractionHand.MAIN_HAND);
                }
                return true;
            }
            return result == InteractionResult.FAIL;   // vanilla stops on a failed block use too
        }
        return false;
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
