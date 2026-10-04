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

import com.chunkworks.rangedweaponsmod.AssemblyRecipe;
import com.chunkworks.rangedweaponsmod.ModTabs;
import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.domain.Blueprint;
import com.chunkworks.rangedweaponsmod.domain.Blueprint.Station;
import com.chunkworks.rangedweaponsmod.domain.Blueprints;
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
 * from: every blueprint's grid finds exactly its recipe at its station and
 * none at the other -- a gun, part, magazine or fitting at the weapons
 * workbench and never at a crafting table, a round, a rocket or the bench
 * at the table and never at the bench (D-0029) -- and assembles its result;
 * nothing else of ours is on the server; every table recipe's unlock exists,
 * rewards its recipe, and accepts the ingredient it names, and no bench
 * recipe has one; every ingredient exists and every tag has something in
 * it; a player who picks up steel finds the bench in the recipe book; the
 * creative tab, built the way the creative screen builds it, shows every
 * item of ours in the order of the tree and nothing else; and steel, which
 * was ours before 2.3.0, is Metals and Materials': the tag has their ingot
 * in it, the old id is not an item, and a stack saved under the old id
 * loads as theirs.
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
    public void everyBlueprintIsTheRecipeTheServerFindsAtItsStationAndNoneAtTheOther(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Blueprint blueprint : Blueprints.all()) {
            CraftingInput input = input(blueprint);
            List<String> atTable = level.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, level).stream()
                    .map(r -> r.id().toString()).toList();
            List<RecipeHolder<AssemblyRecipe>> bench = level.getRecipeManager().getRecipesFor(AssemblyRecipe.TYPE.get(), input, level);
            List<String> atBench = bench.stream().map(r -> r.id().toString()).toList();
            boolean benchMade = blueprint.station() == Station.BENCH;
            List<String> found = benchMade ? atBench : atTable;
            List<String> elsewhere = benchMade ? atTable : atBench;
            helper.assertValueEqual(found, List.of(blueprint.id()),
                    blueprint.id() + ": recipes matching its grid at the " + blueprint.station() + " (a blueprint edited without ./gradlew runData?)");
            helper.assertValueEqual(elsewhere, List.of(), blueprint.id() + ": recipes matching its grid at the other station");
            ItemStack result = benchMade
                    ? bench.get(0).value().assemble(input, level.registryAccess())
                    : level.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, level).get(0).value().assemble(input, level.registryAccess());
            helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), blueprint.result(), blueprint.id() + " makes");
            helper.assertValueEqual(result.getCount(), blueprint.count(), blueprint.id() + " makes this many");
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public void noRecipeOfOursIsOutsideTheBlueprints(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<String> atTable = new HashSet<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (holder.id().getNamespace().equals(RangedWeaponsMod.MOD_ID)) {
                atTable.add(holder.id().toString());
            }
        }
        Set<String> atBench = new HashSet<>();
        for (RecipeHolder<AssemblyRecipe> holder : level.getRecipeManager().getAllRecipesFor(AssemblyRecipe.TYPE.get())) {
            atBench.add(holder.id().toString());
        }
        Set<String> tableBlueprints = new HashSet<>();
        Set<String> benchBlueprints = new HashSet<>();
        Blueprints.all().forEach(b -> (b.station() == Station.BENCH ? benchBlueprints : tableBlueprints).add(b.id()));
        helper.assertValueEqual(atTable, tableBlueprints, "this mod's crafting recipes on the server (stale generated files?)");
        helper.assertValueEqual(atBench, benchBlueprints, "the bench's recipes on the server");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public void everyTableBlueprintHasItsUnlockAndNoBenchBlueprintHasOne(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (Blueprint blueprint : Blueprints.all()) {
            if (blueprint.station() == Station.BENCH) {
                String would = Blueprint.namespace(blueprint.id()) + ":recipes/" + blueprint.category().folder + "/" + Blueprint.path(blueprint.id());
                helper.assertTrue(server.getAdvancements().get(ResourceLocation.parse(would)) == null,
                        blueprint.id() + " is made at the bench, yet " + would + " unlocks it (stale generated files?)");
                continue;
            }
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
    public void pickingUpSteelPutsTheWorkbenchInTheRecipeBook(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ResourceLocation bench = ResourceLocation.parse(Blueprints.WEAPONS_WORKBENCH);
            ResourceLocation round = ResourceLocation.parse(Blueprints.ROUND);
            helper.assertFalse(player.getRecipeBook().contains(bench), "the bench is unknown at first");
            ItemStack steel = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(Blueprints.STEEL_INGOT)));
            // Adding drains the stack handed in; the trigger gets what sits in
            // the slot, as the container listener reports on a real tick -- a
            // placed player is never ticked, so it is reported here.
            player.getInventory().add(steel.copy());
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), steel);
            helper.assertTrue(player.getRecipeBook().contains(bench), "steel in hand reveals the bench");
            helper.assertFalse(player.getRecipeBook().contains(round), "steel in hand does not reveal the round");
            helper.assertFalse(player.getRecipeBook().contains(ResourceLocation.parse(Blueprints.BARREL)),
                    "a bench recipe is never in the recipe book");
            AdvancementHolder unlock = server.getAdvancements().get(ResourceLocation.parse(Blueprints.WEAPONS_WORKBENCH_RECIPE.advancementId()));
            helper.assertTrue(unlock != null && player.getAdvancements().getOrStartProgress(unlock).isDone(), "the bench's unlock is done");
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
        expected.addAll(Blueprints.launchers());
        expected.addAll(Blueprints.ammunition());
        expected.addAll(Blueprints.magazines());
        expected.addAll(Blueprints.fittings());
        expected.addAll(Blueprints.parts());
        expected.addAll(Blueprints.stations());
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

    @GameTest(template = "arena")
    public void steelIsMetalsAndMaterialsAndOurOldIdLoadsAsTheirs(GameTestHelper helper) {
        ResourceLocation theirs = ResourceLocation.parse(Blueprints.STEEL_INGOT);
        ResourceLocation ours = ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "steel_ingot");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(theirs), "Metals and Materials' ingot is registered");
        Item ingot = BuiltInRegistries.ITEM.get(theirs);
        helper.assertTrue(new ItemStack(ingot).is(ItemTags.create(ResourceLocation.parse("c:ingots/steel"))), "it fills c:ingots/steel");
        helper.assertFalse(BuiltInRegistries.ITEM.getOptional(ours).map(i -> BuiltInRegistries.ITEM.getKey(i).equals(ours)).orElse(false),
                "no item of ours is registered under the old id");
        // A stack a player saved before the move: the id is ours, the item must be theirs.
        net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
        saved.putString("id", ours.toString());
        saved.putInt("count", 5);
        Optional<ItemStack> loaded = ItemStack.parse(helper.getLevel().registryAccess(), saved);
        helper.assertTrue(loaded.isPresent() && loaded.get().is(ingot) && loaded.get().getCount() == 5,
                "the old stack loads as five of their ingots, loaded " + loaded);
        helper.assertTrue(BuiltInRegistries.ITEM.get(ours) == ingot, "the registry answers the old id with their ingot");
        helper.succeed();
    }
}
