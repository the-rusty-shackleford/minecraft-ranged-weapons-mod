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

import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.WorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The weapons workbench's screen (D-0029): the grid, the arrow and the result on the left, the
 * fittings on the right -- the weapon slot, and under a launcher the chip slot -- and the inventory
 * below. Empty fitting slots show a faint launcher and a faint chip, and say what they take on
 * hover. The background and its slot frame are {@code textures/gui/workbench.png}, drawn by
 * {@code devtools/art/workbench_art.py}: the panel at its top left, the chip slot's frame beside it,
 * drawn only while the slot is open.
 */
public final class WorkbenchScreen extends AbstractContainerScreen<WorkbenchMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "textures/gui/workbench.png");
    private static final int TEXTURE = 256;
    /** Where the chip slot's frame is kept in the texture, beside the panel. */
    private static final int FRAME_U = 176, FRAME_V = 0;
    private static final int TEXT = 0xFF404040;

    public WorkbenchScreen(WorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        Slot hovered = hoveredSlot;
        if (hovered != null && !hovered.hasItem() && menu.getCarried().isEmpty()) {
            if (hovered.index == WorkbenchMenu.WEAPON) {
                graphics.renderTooltip(font, Component.translatable("screen.rangedweaponsmod.workbench.weapon"), mouseX, mouseY);
            } else if (hovered.index == WorkbenchMenu.CHIP) {
                graphics.renderTooltip(font, Component.translatable("screen.rangedweaponsmod.workbench.chip"), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEXTURE, TEXTURE);
        if (menu.chipSlotOpen()) {
            graphics.blit(BACKGROUND, leftPos + WorkbenchMenu.CHIP_X - 1, topPos + WorkbenchMenu.CHIP_Y - 1,
                    FRAME_U, FRAME_V, 18, 18, TEXTURE, TEXTURE);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        // Over the fittings, right-aligned in the title's row, while the two do not meet.
        Component fittings = Component.translatable("screen.rangedweaponsmod.workbench.fittings");
        int x = imageWidth - 8 - font.width(fittings);
        if (titleLabelX + font.width(title) + 8 <= x) {
            graphics.drawString(font, fittings, x, titleLabelY, TEXT, false);
        }
    }
}
