/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.rangedweaponsmod.client;

import com.chunkworks.backpacksplus.client.GearClient;

/** Optional adapter for Backpacks+' gear bar; reads without changing equipment. */
final class BackpackHudCompat {
    private BackpackHudCompat() {}

    /**
     * requires: Backpacks+ 0.3.0 or later is loaded.
     * effects: returns whether a gear gesture is open with its mount row beside the hotbar on
     * the screen's right, so its deposit cells and text rise into the ammo panel's usual place.
     * Backpacks+ decides from its own layout (D-0025 there); this mod no longer guesses.
     */
    static boolean browsingBottomRight() {
        return GearClient.browsingBottomRight();
    }
}
