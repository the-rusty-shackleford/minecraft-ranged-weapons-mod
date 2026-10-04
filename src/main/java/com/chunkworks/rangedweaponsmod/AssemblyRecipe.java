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
package com.chunkworks.rangedweaponsmod;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.RecipeMatcher;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A recipe made at the weapons workbench (D-0029): every gun, part, magazine and fitting. The
 * bench's grid is the crafting table's, three by three, and so is the matching: a shaped recipe
 * is vanilla's {@link ShapedRecipePattern} (mirror included), a shapeless one vanilla's rule, as
 * NeoForge has it. What differs is the type, {@link #TYPE}: no crafting table finds these, and the
 * warehouse manager, which crafts only the table's recipes, does not either.
 *
 * <p>Special, in the game's word: the recipe book never shows them and no unlock gates them, so
 * the limited-crafting rule does not apply. EMI shows them, under the bench.
 */
public sealed interface AssemblyRecipe extends Recipe<CraftingInput> permits AssemblyRecipe.Shaped, AssemblyRecipe.Shapeless {

    DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, RangedWeaponsMod.MOD_ID);
    DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, RangedWeaponsMod.MOD_ID);

    /** Every bench recipe's type: {@code rangedweaponsmod:assembly}. */
    DeferredHolder<RecipeType<?>, RecipeType<AssemblyRecipe>> TYPE =
            TYPES.register("assembly", () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "assembly")));

    DeferredHolder<RecipeSerializer<?>, RecipeSerializer<Shaped>> SHAPED =
            SERIALIZERS.register("assembly_shaped", () -> new Serializer<>(Shaped.CODEC, Shaped.STREAM_CODEC));
    DeferredHolder<RecipeSerializer<?>, RecipeSerializer<Shapeless>> SHAPELESS =
            SERIALIZERS.register("assembly_shapeless", () -> new Serializer<>(Shapeless.CODEC, Shapeless.STREAM_CODEC));

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
    }

    /** What is made, one craft's worth; never mutated, so every taker gets a copy. */
    ItemStack result();

    @Override
    default ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result().copy();
    }

    @Override
    default ItemStack getResultItem(HolderLookup.Provider registries) {
        return result();
    }

    @Override
    default RecipeType<?> getType() {
        return TYPE.get();
    }

    /** Never in the recipe book, never gated by an unlock. */
    @Override
    default boolean isSpecial() {
        return true;
    }

    @Override
    default ItemStack getToastSymbol() {
        return new ItemStack(ModBlocks.WEAPONS_WORKBENCH.get());
    }

    /**
     * A recipe with a shape: vanilla's pattern, matched as vanilla matches it.
     *
     * @param pattern the shape and its key
     * @param result  what one craft makes
     */
    record Shaped(ShapedRecipePattern pattern, ItemStack result) implements AssemblyRecipe {
        static final MapCodec<Shaped> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ShapedRecipePattern.MAP_CODEC.forGetter(Shaped::pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(Shaped::result)
        ).apply(i, Shaped::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Shaped> STREAM_CODEC = StreamCodec.composite(
                ShapedRecipePattern.STREAM_CODEC, Shaped::pattern,
                ItemStack.STREAM_CODEC, Shaped::result,
                Shaped::new);

        @Override
        public boolean matches(CraftingInput input, Level level) {
            return pattern.matches(input);
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width >= pattern.width() && height >= pattern.height();
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return pattern.ingredients();
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return SHAPED.get();
        }
    }

    /**
     * A recipe with no shape: its ingredients anywhere in the grid, one item to each.
     *
     * @param ingredients one to nine, none empty
     * @param result      what one craft makes
     */
    record Shapeless(NonNullList<Ingredient> ingredients, ItemStack result) implements AssemblyRecipe {
        private static final int MAX = ShapedRecipePattern.getMaxWidth() * ShapedRecipePattern.getMaxHeight();

        static final MapCodec<Shapeless> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap(
                        list -> list.isEmpty() || list.size() > MAX
                                ? DataResult.<NonNullList<Ingredient>>error(() -> "a shapeless assembly takes 1 to " + MAX + " ingredients, had " + list.size())
                                : DataResult.success(NonNullList.of(Ingredient.EMPTY, list.toArray(Ingredient[]::new))),
                        list -> DataResult.success((List<Ingredient>) list)
                ).forGetter(Shapeless::ingredients),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(Shapeless::result)
        ).apply(i, Shapeless::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Shapeless> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()).map(
                        list -> NonNullList.of(Ingredient.EMPTY, list.toArray(Ingredient[]::new)),
                        ArrayList::new),
                Shapeless::ingredients,
                ItemStack.STREAM_CODEC, Shapeless::result,
                Shapeless::new);

        /** The game's rule, as NeoForge has it: the same count of items, each taken by one ingredient. */
        @Override
        public boolean matches(CraftingInput input, Level level) {
            if (input.ingredientCount() != ingredients.size()) {
                return false;
            }
            if (ingredients.stream().allMatch(Ingredient::isSimple)) {
                return input.size() == 1 && ingredients.size() == 1
                        ? ingredients.getFirst().test(input.getItem(0))
                        : input.stackedContents().canCraft(this, null);
            }
            List<ItemStack> present = new ArrayList<>(input.ingredientCount());
            for (ItemStack item : input.items()) {
                if (!item.isEmpty()) {
                    present.add(item);
                }
            }
            return RecipeMatcher.findMatches(present, ingredients) != null;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width * height >= ingredients.size();
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return ingredients;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return SHAPELESS.get();
        }
    }

    /** A serializer from its two codecs. */
    record Serializer<R extends AssemblyRecipe>(MapCodec<R> codec, StreamCodec<RegistryFriendlyByteBuf, R> streamCodec)
            implements RecipeSerializer<R> {
    }
}
