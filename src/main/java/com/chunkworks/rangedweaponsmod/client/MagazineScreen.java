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

import com.chunkworks.rangedweaponsmod.MagazineMenu;
import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.domain.Magazine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

/**
 * The magazine screen: the run slots with "fires first" at their left, the
 * count, what fires next, and the fill button; the player's inventory below.
 */
public final class MagazineScreen extends AbstractContainerScreen<MagazineMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "textures/gui/magazine.png");
    private static final int TEXT = 0xFF404040;

    public MagazineScreen(MagazineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // Right of the "Next:" line, above the inventory label, short enough
        // to fit: the long form is its tooltip.
        addRenderableWidget(Button.builder(Component.translatable("screen.rangedweaponsmod.magazine.fill"), button -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MagazineMenu.FILL_BUTTON);
            }
        }).bounds(leftPos + 104, topPos + 56, 64, 14)
                .tooltip(Tooltip.create(Component.translatable("screen.rangedweaponsmod.magazine.fill.tooltip")))
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x >= 8 && x < 168 && y >= titleLabelY && y < titleLabelY + font.lineHeight && font.width(title) > 160) {
            graphics.renderTooltip(font, font.split(title, Math.min(240, width - 16)), mouseX, mouseY);
        } else if (x >= 8 && x < 100 && y >= 59 && y < 59 + font.lineHeight && font.width(nextRound()) > 92) {
            graphics.renderTooltip(font, font.split(nextRound(), Math.min(240, width - 16)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // The overload that takes the texture's own size: the short one assumes
        // 256 by 256 and stretched this 176 by 166 panel over the slots.
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, fitted(title, 160), titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        Magazine<Item> magazine = menu.contents();
        graphics.drawString(font, Component.translatable("screen.rangedweaponsmod.magazine.first"), 8, 26, TEXT, false);
        String count = magazine.rounds() + " / " + magazine.capacity();
        graphics.drawString(font, count, imageWidth - 8 - font.width(count), 26, TEXT, false);
        graphics.drawString(font, fitted(nextRound(), 92), 8, 59, TEXT, false);
    }

    /** effects: returns the complete next-round description from the current server-synced contents. */
    private Component nextRound() {
        return menu.contents().next()
                .map(round -> Component.translatable("screen.rangedweaponsmod.magazine.next", round.getDescription()))
                .orElseGet(() -> Component.translatable("screen.rangedweaponsmod.magazine.empty"));
    }

    /** requires: width fits the ellipsis. effects: fits styled text without drawing over adjacent controls. */
    private FormattedCharSequence fitted(Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        return Language.getInstance().getVisualOrder(FormattedText.composite(
                font.substrByWidth(text, width - font.width("…")), Component.literal("…")));
    }
}
