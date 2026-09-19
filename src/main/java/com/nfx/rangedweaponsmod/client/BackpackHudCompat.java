/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.nfx.rangedweaponsmod.client;

import com.chunkworks.backpacksplus.BagContents;
import com.chunkworks.backpacksplus.client.GearClient;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;

/** Optional adapter for Backpacks+' server-synced gear preview; reads without changing equipment. */
final class BackpackHudCompat {
    private BackpackHudCompat() {}

    /**
     * requires: Backpacks+ is loaded and the player holds a gun.
     * effects: returns whether right-side gear browsing occupies the ammo panel's usual row.
     * Backpacks+ 0.2.1 gives a held item one extra stow column, even when stowing is refused.
     */
    static boolean browsingBottomRow(Minecraft mc, int width) {
        if (mc.player.getMainArm() != HumanoidArm.RIGHT || !GearClient.browsing()) return false;
        var state = GearClient.snapshot(mc.player.getUUID());
        if (state == null || state.bag().isEmpty()) return false;
        int mounts = BagContents.tier(state.bag()).mounts().size();
        if (mounts == 0) return false;
        int distance = mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR ? 121 : 97;
        int edge = width / 2 + distance + 24 + (mounts + 1) * 24;
        return edge <= width - 3;
    }
}
