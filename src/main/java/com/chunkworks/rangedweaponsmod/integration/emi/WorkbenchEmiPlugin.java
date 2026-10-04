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
package com.chunkworks.rangedweaponsmod.integration.emi;

import com.chunkworks.rangedweaponsmod.AssemblyRecipe;
import com.chunkworks.rangedweaponsmod.ModBlocks;
import com.chunkworks.rangedweaponsmod.ModData;
import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.WorkbenchMenu;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * The weapons workbench in EMI (D-0029): its recipes under a category of their own, the bench as
 * that category's workstation, each recipe in the crafting table's three-by-three layout, and the
 * bench's grid filled by EMI's "+". EMI discovers this class by its annotation and loads it only
 * when EMI is installed; nothing else here names it.
 */
@EmiEntrypoint
public final class WorkbenchEmiPlugin implements EmiPlugin {

    /** The bench's category; set when EMI registers its plugins, and read only by EMI after. */
    private static EmiRecipeCategory assembly;

    @Override
    public void register(EmiRegistry registry) {
        EmiStack bench = EmiStack.of(ModBlocks.WEAPONS_WORKBENCH.get());
        assembly = new EmiRecipeCategory(ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "assembly"), bench);
        registry.addCategory(assembly);
        registry.addWorkstation(assembly, bench);
        for (RecipeHolder<AssemblyRecipe> holder : registry.getRecipeManager().getAllRecipesFor(AssemblyRecipe.TYPE.get())) {
            registry.addRecipe(new Assembly(holder));
        }
        registry.addRecipeHandler(ModData.WORKBENCH_MENU.get(), new BenchHandler());
    }

    /** A bench recipe, laid out as the crafting table's are, under the bench's category. */
    private static final class Assembly extends EmiCraftingRecipe {
        Assembly(RecipeHolder<AssemblyRecipe> holder) {
            super(inputs(holder.value()), EmiStack.of(holder.value().result()), holder.id(),
                    holder.value() instanceof AssemblyRecipe.Shapeless);
        }

        @Override
        public EmiRecipeCategory getCategory() {
            return assembly;
        }

        /** effects: a shaped recipe as nine cells, row by row, the empty ones empty; a shapeless one as its ingredients */
        private static List<EmiIngredient> inputs(AssemblyRecipe recipe) {
            if (recipe instanceof AssemblyRecipe.Shaped shaped) {
                ShapedRecipePattern pattern = shaped.pattern();
                List<EmiIngredient> cells = new ArrayList<>(9);
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        boolean inside = row < pattern.height() && col < pattern.width();
                        cells.add(inside ? EmiIngredient.of(pattern.ingredients().get(col + row * pattern.width())) : EmiStack.EMPTY);
                    }
                }
                return cells;
            }
            List<EmiIngredient> handful = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                handful.add(EmiIngredient.of(ingredient));
            }
            return handful;
        }
    }

    /** EMI's "+" on the bench: takes from the inventory and the grid, fills the grid, takes from the result. */
    private static final class BenchHandler implements StandardRecipeHandler<WorkbenchMenu> {
        @Override
        public List<Slot> getInputSources(WorkbenchMenu menu) {
            List<Slot> slots = new ArrayList<>();
            for (int i = WorkbenchMenu.INVENTORY_START; i < WorkbenchMenu.END; i++) {
                slots.add(menu.getSlot(i));
            }
            for (int i = WorkbenchMenu.GRID_START; i < WorkbenchMenu.GRID_END; i++) {
                slots.add(menu.getSlot(i));
            }
            return slots;
        }

        @Override
        public List<Slot> getCraftingSlots(WorkbenchMenu menu) {
            List<Slot> slots = new ArrayList<>();
            for (int i = WorkbenchMenu.GRID_START; i < WorkbenchMenu.GRID_END; i++) {
                slots.add(menu.getSlot(i));
            }
            return slots;
        }

        @Override
        public Slot getOutputSlot(WorkbenchMenu menu) {
            return menu.getSlot(WorkbenchMenu.RESULT);
        }

        @Override
        public boolean supportsRecipe(EmiRecipe recipe) {
            return recipe.getCategory() == assembly && recipe.supportsRecipeTree();
        }
    }
}
