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
package com.nfx.rangedweaponsmod;

import com.nfx.rangedweaponsmod.domain.Blueprint;
import com.nfx.rangedweaponsmod.domain.Blueprints;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The recipes, written from the domain's blueprints by the loader's data
 * generation ({@code ./gradlew runData}): one recipe file and one
 * recipe-book unlock each, into {@code src/generated/resources}, which
 * ships. The blueprints are the source; nothing here decides a shape.
 */
public final class ModRecipes {
    private ModRecipes() {}

    static void register(IEventBus modBus) {
        modBus.addListener(ModRecipes::gatherData);
    }

    private static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
                new Provider(event.getGenerator().getPackOutput(), event.getLookupProvider()));
    }

    /** Every blueprint as a recipe and its unlock. */
    static final class Provider extends RecipeProvider {
        Provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void buildRecipes(RecipeOutput out) {
            for (Blueprint blueprint : Blueprints.all()) {
                ResourceLocation id = ResourceLocation.parse(blueprint.id());
                switch (blueprint) {
                    case Blueprint.Shaped shaped -> {
                        ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(category(shaped), item(shaped.result()), shaped.count());
                        shaped.pattern().forEach(builder::pattern);
                        shaped.key().forEach((symbol, ingredient) -> {
                            if (Blueprint.isTag(ingredient)) {
                                builder.define(symbol, tag(ingredient));
                            } else {
                                builder.define(symbol, item(ingredient));
                            }
                        });
                        unlocks(shaped).forEach(builder::unlockedBy);
                        builder.save(out, id);
                    }
                    case Blueprint.Shapeless shapeless -> {
                        ShapelessRecipeBuilder builder = ShapelessRecipeBuilder.shapeless(category(shapeless), item(shapeless.result()), shapeless.count());
                        for (String ingredient : shapeless.ingredientList()) {
                            if (Blueprint.isTag(ingredient)) {
                                builder.requires(tag(ingredient));
                            } else {
                                builder.requires(item(ingredient));
                            }
                        }
                        unlocks(shapeless).forEach(builder::unlockedBy);
                        builder.save(out, id);
                    }
                }
            }
        }

        /** The unlock criteria: holding any listed ingredient, named as the blueprint names them. */
        private static Map<String, Criterion<?>> unlocks(Blueprint blueprint) {
            Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
            for (String ingredient : blueprint.unlockedBy()) {
                criteria.put(Blueprint.criterionName(ingredient),
                        Blueprint.isTag(ingredient) ? has(tag(ingredient)) : has(item(ingredient)));
            }
            return criteria;
        }

        private static Item item(String id) {
            ResourceLocation location = ResourceLocation.parse(id);
            // The item registry answers air for anything it does not have.
            if (!BuiltInRegistries.ITEM.containsKey(location)) {
                throw new IllegalStateException("a blueprint names an item that does not exist: " + id);
            }
            return BuiltInRegistries.ITEM.get(location);
        }

        private static TagKey<Item> tag(String id) {
            return ItemTags.create(ResourceLocation.parse(Blueprint.tagName(id)));
        }

        private static RecipeCategory category(Blueprint blueprint) {
            return switch (blueprint.category()) {
                case COMBAT -> RecipeCategory.COMBAT;
                case MISC -> RecipeCategory.MISC;
            };
        }
    }
}
