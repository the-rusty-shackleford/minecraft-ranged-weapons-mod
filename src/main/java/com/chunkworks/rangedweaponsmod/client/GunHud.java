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

import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.chunkworks.rangedweaponsmod.GunItem;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import java.util.Optional;
import com.chunkworks.rangedweaponsmod.Magazines;
import com.chunkworks.rangedweaponsmod.ModData;
import com.chunkworks.rangedweaponsmod.Reload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The ammo panel above the lower-right quick slots: the round that fires next as
 * its item's icon, {@code rounds / capacity} beside it, the magazine's label
 * under it in the magazine's colour (or the round's name, for a gun loaded
 * directly), and a bar that fills while a reload runs. Drawn only while a
 * gun is held; everything it shows is on the stack (the rounds, the reload
 * and the inserted magazine, all synced) or in the profile (the capacity,
 * synced with the data map).
 */
public final class GunHud {
    private GunHud() {}

    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_LOW_COLOR = 0xFFFF6E6E;
    private static final int BAR_BACK = 0xA0000000;
    private static final int BAR_FILL = 0xFFE0C060;
    private static final int MAX_WIDTH = 144;
    private static final int RIGHT_INSET = 8;
    private static final int BAR_HEIGHT = 3;
    /** Rounds at or below which the counter turns red: a fifth of the magazine. */
    private static final int LOW_FRACTION = 5;
    /** An item icon's size. */
    private static final int ICON = 16;

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientConfig.HUD_ENABLED.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!GunItem.isGun(stack)) {
            return;
        }
        RangedWeapon weapon = RangedWeapons.resolve(stack);
        if (weapon == null) {
            return;
        }
        int rounds = weapon.rounds(stack);
        int capacity = weapon.capacity(stack);
        Font font = mc.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        boolean unlimited = mc.player.hasInfiniteMaterials();
        boolean magazineFed = GunItem.usesMagazines(stack);   // the server's mode, received at login
        Optional<ItemStack> magazine = Magazines.inserted(stack);
        if (magazineFed && !unlimited && magazine.isPresent()) {
            capacity = Magazines.contents(magazine.get()).capacity();
        }
        String text = unlimited ? "\u221e / " + capacity : rounds + " / " + capacity;
        int color = !unlimited && rounds * LOW_FRACTION <= capacity ? TEXT_LOW_COLOR : TEXT_COLOR;

        // The round that fires next, as its own icon: a slug looks like a
        // slug. Beside it the count; under both, what is loaded.
        Optional<Item> next = weapon.loadedAmmo(stack)
                .or(() -> weapon.profile().ammoItem().flatMap(BuiltInRegistries.ITEM::getOptional));
        Component label;
        int labelColor = TEXT_COLOR;
        if (magazineFed && !unlimited) {
            if (magazine.isPresent()) {
                label = magazine.get().getHoverName();
                labelColor = 0xFF000000 | DyedItemColor.getOrDefault(magazine.get(), 0xFFFFFF);
            } else {
                label = Component.translatable("hud.rangedweaponsmod.no_magazine");
                labelColor = TEXT_LOW_COLOR;
            }
        } else {
            label = next.map(Item::getDescription).orElse(Component.empty());
        }
        // Minecraft's minimum GUI is 320 wide. Stay outside its 182-wide
        // hotbar, above Quick Slot, and below Backpacks+' compact mount row.
        int available = Math.max(ICON + 8, screenWidth - screenWidth / 2 - 105);
        int width = Math.min(Math.min(MAX_WIDTH, available),
                Math.max(ICON + 4 + font.width(text), font.width(label)));
        int x = screenWidth - RIGHT_INSET - width;
        int iconY = screenHeight - 51;
        if (net.neoforged.fml.ModList.get().isLoaded("backpacksplus")
                && BackpackHudCompat.browsingBottomRow(mc, screenWidth)) {
            iconY = screenHeight - 114;
        }
        int labelY = iconY + ICON + 2;
        int barY = iconY - BAR_HEIGHT - 2;
        graphics.fill(x - 3, barY - 2, x + width + 3, labelY + font.lineHeight + 1, 0x90000000);
        if (next.isPresent() && (rounds > 0 || unlimited || !magazineFed)) {
            graphics.renderItem(new ItemStack(next.get()), x, iconY);
        }
        int countWidth = width - ICON - 4;
        if (font.width(text) > countWidth) text = text.replace(" / ", "/");
        float scale = Math.min(1F, (float) countWidth / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x + ICON + 4, iconY + 4, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, true);
        graphics.pose().popPose();
        graphics.drawString(font, fitted(font, label, width), x, labelY, labelColor, true);

        Reload reload = stack.get(ModData.RELOAD.get());
        if (reload != null && mc.level != null) {
            float progress = reload.progress(mc.level.getGameTime());
            graphics.fill(x, barY, x + width, barY + BAR_HEIGHT, BAR_BACK);
            graphics.fill(x, barY, x + Math.round(width * progress), barY + BAR_HEIGHT, BAR_FILL);
        }
    }

    /** requires: width fits the ellipsis. effects: preserves styling while fitting the panel. */
    private static FormattedCharSequence fitted(Font font, Component label, int width) {
        if (font.width(label) <= width) return label.getVisualOrderText();
        return Language.getInstance().getVisualOrder(FormattedText.composite(
                font.substrByWidth(label, width - font.width("…")), Component.literal("…")));
    }
}
