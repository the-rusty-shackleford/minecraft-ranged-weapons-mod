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
package com.nfx.rangedweaponsmod.gametest;

import com.nfx.rangedweaponsmod.ModTabs;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.domain.Blueprint;
import com.nfx.rangedweaponsmod.domain.Blueprints;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The recipes on a real server, held to the blueprints they were written
 * from: every blueprint's grid finds exactly its recipe and assembles its
 * result; nothing else of ours is on the server; every unlock exists,
 * rewards its recipe, and accepts the ingredient it names; every ingredient
 * exists and every tag has something in it; a player who picks up iron
 * finds the barrel in the recipe book; and the creative tab, built the way
 * the creative screen builds it, shows every gun, round and part of ours
 * in the order of the tree and nothing else.
 *
 * <p>A grid is filled from a tag by the <em>last</em> item in it, so a tag
 * is proven honoured rather than matched by its usual item (charcoal for
 * coals, not coal). A blueprint edited without {@code ./gradlew runData}
 * fails the first test with a message that says so.
 */
@GameTestHolder(RangedWeaponsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftingGameTests {

    public CraftingGameTests() {}

    @GameTest(template = "arena")
    public void everyBlueprintIsTheRecipeTheServerFinds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Blueprint blueprint : Blueprints.all()) {
            CraftingInput input = input(blueprint);
            List<RecipeHolder<CraftingRecipe>> found = level.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, level);
            helper.assertValueEqual(found.size(), 1,
                    blueprint.id() + ": recipes matching its grid " + found.stream().map(r -> r.id().toString()).toList()
                            + " (a blueprint edited without ./gradlew runData?)");
            RecipeHolder<CraftingRecipe> recipe = found.get(0);
            helper.assertValueEqual(recipe.id().toString(), blueprint.id(), "the recipe the grid finds");
            ItemStack result = recipe.value().assemble(input, level.registryAccess());
            helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), blueprint.result(), blueprint.id() + " makes");
            helper.assertValueEqual(result.getCount(), blueprint.count(), blueprint.id() + " makes this many");
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public void noRecipeOfOursIsOutsideTheBlueprints(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<String> onServer = new HashSet<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (holder.id().getNamespace().equals(RangedWeaponsMod.MOD_ID)) {
                onServer.add(holder.id().toString());
            }
        }
        Set<String> blueprints = new HashSet<>();
        Blueprints.all().forEach(b -> blueprints.add(b.id()));
        helper.assertValueEqual(onServer, blueprints, "this mod's crafting recipes on the server (stale generated files?)");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public void everyBlueprintHasItsUnlock(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (Blueprint blueprint : Blueprints.all()) {
            AdvancementHolder unlock = server.getAdvancements().get(ResourceLocation.parse(blueprint.advancementId()));
            if (unlock == null) {
                helper.fail(blueprint.id() + " has no unlock at " + blueprint.advancementId());
                return;
            }
            helper.assertTrue(unlock.value().rewards().recipes().contains(ResourceLocation.parse(blueprint.id())),
                    blueprint.advancementId() + " rewards " + blueprint.id());
            Set<String> expected = new HashSet<>();
            expected.add("has_the_recipe");
            blueprint.unlockedBy().forEach(i -> expected.add(Blueprint.criterionName(i)));
            helper.assertValueEqual(unlock.value().criteria().keySet(), expected, blueprint.advancementId() + " criteria");
            for (String ingredient : blueprint.unlockedBy()) {
                Criterion<?> criterion = unlock.value().criteria().get(Blueprint.criterionName(ingredient));
                helper.assertTrue(criterion.trigger() == CriteriaTriggers.INVENTORY_CHANGED, Blueprint.criterionName(ingredient) + " watches the inventory");
                InventoryChangeTrigger.TriggerInstance instance = (InventoryChangeTrigger.TriggerInstance) criterion.triggerInstance();
                helper.assertTrue(instance.items().get(0).test(stack(ingredient)), Blueprint.criterionName(ingredient) + " accepts " + ingredient);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public void everyIngredientExistsAndNoTagIsEmpty(GameTestHelper helper) {
        for (Blueprint blueprint : Blueprints.all()) {
            for (String ingredient : blueprint.ingredients().keySet()) {
                if (Blueprint.isTag(ingredient)) {
                    Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.getTag(ItemTags.create(ResourceLocation.parse(Blueprint.tagName(ingredient))));
                    helper.assertTrue(tag.isPresent() && tag.get().size() > 0, blueprint.id() + " takes " + ingredient + ", which nothing is in");
                } else {
                    helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(ingredient)), blueprint.id() + " takes " + ingredient + ", which does not exist");
                }
            }
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(blueprint.result())), blueprint.id() + " makes " + blueprint.result() + ", which does not exist");
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "arena")
    public void pickingUpIronPutsTheBarrelInTheRecipeBook(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ResourceLocation barrel = ResourceLocation.parse(Blueprints.BARREL);
            ResourceLocation stock = ResourceLocation.parse(Blueprints.STOCK);
            helper.assertFalse(player.getRecipeBook().contains(barrel), "the barrel is unknown at first");
            ItemStack iron = new ItemStack(Items.IRON_INGOT);
            // Adding drains the stack handed in; the trigger gets what sits in
            // the slot, as the container listener reports on a real tick -- a
            // placed player is never ticked, so it is reported here.
            player.getInventory().add(iron.copy());
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), iron);
            helper.assertTrue(player.getRecipeBook().contains(barrel), "iron in hand reveals the barrel");
            helper.assertFalse(player.getRecipeBook().contains(stock), "iron in hand does not reveal the stock");
            AdvancementHolder unlock = server.getAdvancements().get(ResourceLocation.parse(Blueprints.BARREL_RECIPE.advancementId()));
            helper.assertTrue(unlock != null && player.getAdvancements().getOrStartProgress(unlock).isDone(), "the barrel's unlock is done");
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public void theCreativeTabShowsEverythingOfOursInTreeOrder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CreativeModeTab tab = ModTabs.RANGED_WEAPONS.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, true, level.registryAccess()));
        List<String> shown = tab.getDisplayItems().stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).toList();
        List<String> expected = new ArrayList<>();
        expected.addAll(Blueprints.guns());
        expected.addAll(Blueprints.ammunition());
        expected.addAll(Blueprints.parts());
        helper.assertValueEqual(shown, expected, "the tab's contents");
        Set<String> ours = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key.getNamespace().equals(RangedWeaponsMod.MOD_ID)) {
                ours.add(key.toString());
            }
        }
        helper.assertValueEqual(new HashSet<>(shown), ours, "every item of ours is in the tab");
        helper.assertTrue(CreativeModeTabs.allTabs().contains(tab), "the tab is registered with the game");
        helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(tab.getIconItem().getItem()).toString(), Blueprints.MACHINE_GUN, "the tab's icon");
        helper.succeed();
    }

    /** The blueprint's grid, as the crafting table would hand it to the server. */
    private static CraftingInput input(Blueprint blueprint) {
        List<ItemStack> cells = new ArrayList<>();
        switch (blueprint) {
            case Blueprint.Shaped shaped -> {
                for (String row : shaped.pattern()) {
                    for (char c : row.toCharArray()) {
                        cells.add(c == ' ' ? ItemStack.EMPTY : stack(shaped.key().get(c)));
                    }
                }
                return CraftingInput.of(shaped.width(), shaped.height(), cells);
            }
            case Blueprint.Shapeless shapeless -> {
                shapeless.ingredientList().forEach(i -> cells.add(stack(i)));
                while (cells.size() < 9) {
                    cells.add(ItemStack.EMPTY);
                }
                return CraftingInput.of(3, 3, cells);
            }
        }
    }

    /** One of {@code ingredient}: the item itself, or the last item in the tag. */
    private static ItemStack stack(String ingredient) {
        if (Blueprint.isTag(ingredient)) {
            HolderSet.Named<Item> tag = BuiltInRegistries.ITEM.getTag(ItemTags.create(ResourceLocation.parse(Blueprint.tagName(ingredient))))
                    .orElseThrow(() -> new IllegalStateException("no such tag: " + ingredient));
            Holder<Item> last = tag.get(tag.size() - 1);
            return new ItemStack(last.value());
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredient)));
    }
}
