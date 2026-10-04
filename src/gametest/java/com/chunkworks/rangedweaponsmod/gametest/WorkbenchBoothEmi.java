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
package com.chunkworks.rangedweaponsmod.gametest;

import com.chunkworks.rangedweaponsmod.ModItems;
import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.WorkbenchMenu;
import com.chunkworks.rangedweaponsmod.client.WorkbenchScreen;
import com.chunkworks.rangedweaponsmod.domain.Blueprint;
import com.chunkworks.rangedweaponsmod.domain.Blueprints;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipeFiller;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The workbench booth's EMI half (D-0029), kept apart so that no other booth, and no run without
 * EMI, ever loads a class that names EMI's. EMI's own "+" is its recipe filler, called as the
 * button calls it.
 */
final class WorkbenchBoothEmi {
    private WorkbenchBoothEmi() {}

    private static final ResourceLocation CATEGORY = ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "assembly");

    /** effects: opens EMI on the rifle's bench recipe, on the bench's page */
    static void showRiflePage(Minecraft mc) {
        EmiRecipe rifle = EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse(Blueprints.RIFLE));
        if (rifle != null) {
            EmiApi.displayRecipe(rifle);
        } else {
            EmiApi.displayRecipes(EmiStack.of(ModItems.RIFLE.get()));
        }
    }

    /** effects: returns whether EMI has the bench's category with exactly the bench blueprints in it */
    static boolean everyBenchRecipeListed() {
        EmiRecipeCategory category = EmiApi.getRecipeManager().getCategories().stream()
                .filter(c -> c.getId().equals(CATEGORY)).findFirst().orElse(null);
        if (category == null) {
            return false;
        }
        Set<String> listed = new HashSet<>();
        for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipes(category)) {
            listed.add(String.valueOf(recipe.getId()));
        }
        Set<String> bench = new HashSet<>();
        for (Blueprint blueprint : Blueprints.all()) {
            if (blueprint.station() == Blueprint.Station.BENCH) {
                bench.add(blueprint.id());
            }
        }
        RangedWeaponsMod.LOGGER.info("booth: EMI lists {} bench recipes; the blueprints have {}", listed.size(), bench.size());
        return listed.equals(bench);
    }

    /** effects: presses EMI's "+" for the rifle's recipe on the open bench; returns whether EMI took it */
    static boolean fillRifle(WorkbenchScreen screen) {
        EmiRecipe rifle = EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse(Blueprints.RIFLE));
        if (rifle == null) {
            return false;
        }
        boolean filled = EmiRecipeFiller.performFill(rifle, screen, EmiCraftContext.Type.FILL_BUTTON, EmiCraftContext.Destination.NONE, 1);
        RangedWeaponsMod.LOGGER.info("booth: EMI's fill for {} on slot {}: {}", rifle.getId(), WorkbenchMenu.GRID_START, filled);
        return filled;
    }
}
