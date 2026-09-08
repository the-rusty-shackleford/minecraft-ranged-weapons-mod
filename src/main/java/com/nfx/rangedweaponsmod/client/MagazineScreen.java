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

import com.nfx.rangedweaponsmod.MagazineMenu;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.domain.Magazine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Magazine<Item> magazine = menu.contents();
        graphics.drawString(font, Component.translatable("screen.rangedweaponsmod.magazine.first"), 8, 26, TEXT, false);
        String count = magazine.rounds() + " / " + magazine.capacity();
        graphics.drawString(font, count, imageWidth - 8 - font.width(count), 26, TEXT, false);
        Component next = magazine.next()
                .map(round -> Component.translatable("screen.rangedweaponsmod.magazine.next", round.getDescription()))
                .orElseGet(() -> Component.translatable("screen.rangedweaponsmod.magazine.empty"));
        graphics.drawString(font, next, 8, 59, TEXT, false);
    }
}
