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
package com.nfx.rangedweaponsmod.domain;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One crafting recipe, described in plain strings: what goes in, in what
 * shape, what comes out, and which ingredient in hand should reveal it in
 * the recipe book.
 *
 * <p>Ingredients are item ids ({@code minecraft:paper}) or tags with a
 * leading hash ({@code #c:ingots/iron}); results are item ids. The recipe's
 * own id is a resource id too ({@code rangedweaponsmod:pistol}), and by the
 * catalogue's convention names the file the recipe is written to.
 *
 * <p>The rules a shape must obey are the game's own for a shaped recipe,
 * so nothing that passes here can be refused when the recipes are written
 * out: one to three rows of equal width, one to three columns, at least
 * one filled cell, every symbol in the pattern defined, every defined
 * symbol used, and space never a symbol.
 */
public sealed interface Blueprint permits Blueprint.Shaped, Blueprint.Shapeless {

    /** Where the recipe book files a recipe; the folder its unlock is written under. */
    enum Category {
        COMBAT("combat"),
        MISC("misc");

        public final String folder;

        Category(String folder) {
            this.folder = folder;
        }
    }

    Pattern ID = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_/.-]+");
    int MAX_SIZE = 3;
    int MAX_SHAPELESS = 9;

    /** The recipe's id: a resource id in this mod's namespace, naming its file. */
    String id();

    Category category();

    /** The item made. */
    String result();

    /** How many, at least one. */
    int count();

    /**
     * The ingredients any one of which, once held, reveals the recipe in
     * the recipe book: a non-empty subset of the ingredients, each with a
     * distinct criterion name.
     */
    List<String> unlockedBy();

    /**
     * effects: returns the ingredients as a multiset: id to how many cells
     * (or entries) of the recipe take it
     */
    Map<String, Integer> ingredients();

    /** effects: returns the id of the recipe book unlock the game writes for this recipe */
    default String advancementId() {
        return namespace(id()) + ":recipes/" + category().folder + "/" + path(id());
    }

    /** effects: returns whether {@code ingredient} names a tag rather than an item */
    static boolean isTag(String ingredient) {
        return ingredient.startsWith("#");
    }

    /**
     * effects: returns the tag's id without the hash<br>
     * throws: {@link IllegalArgumentException} if {@code ingredient} is not a tag
     */
    static String tagName(String ingredient) {
        if (!isTag(ingredient)) {
            throw new IllegalArgumentException("not a tag: " + ingredient);
        }
        return ingredient.substring(1);
    }

    /** effects: returns the namespace of {@code id}, tag or not */
    static String namespace(String id) {
        String bare = isTag(id) ? tagName(id) : id;
        return bare.substring(0, bare.indexOf(':'));
    }

    /** effects: returns the path of {@code id}, tag or not */
    static String path(String id) {
        String bare = isTag(id) ? tagName(id) : id;
        return bare.substring(bare.indexOf(':') + 1);
    }

    /**
     * effects: returns the name of the unlock criterion for holding
     * {@code ingredient}: {@code has_} and the path with slashes as
     * underscores -- {@code #c:ingots/iron} gives {@code has_ingots_iron}
     */
    static String criterionName(String ingredient) {
        return "has_" + path(ingredient).replace('/', '_');
    }

    private static void checkId(String id, String what, boolean tagAllowed) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException(what + " must be a resource id, was " + id);
        }
        if (!tagAllowed && isTag(id)) {
            throw new IllegalArgumentException(what + " must be an item, not a tag: " + id);
        }
    }

    private static void checkCommon(String id, Category category, String result, int count, List<String> unlockedBy, Map<String, Integer> ingredients) {
        checkId(id, "id", false);
        if (category == null) {
            throw new IllegalArgumentException("category must be given for " + id);
        }
        checkId(result, "result", false);
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1 for " + id + ", was " + count);
        }
        if (unlockedBy == null || unlockedBy.isEmpty()) {
            throw new IllegalArgumentException(id + " must be unlocked by at least one ingredient");
        }
        Set<String> names = new HashSet<>();
        for (String ingredient : unlockedBy) {
            if (!ingredients.containsKey(ingredient)) {
                throw new IllegalArgumentException(id + " is unlocked by " + ingredient + ", which it does not take");
            }
            if (!names.add(criterionName(ingredient))) {
                throw new IllegalArgumentException(id + " has two unlocks named " + criterionName(ingredient));
            }
        }
    }

    /**
     * A recipe with a shape.
     *
     * @param pattern    the rows, top first; a space is an empty cell
     * @param key        what each symbol in the pattern is
     * @param unlockedBy see {@link Blueprint#unlockedBy()}
     */
    record Shaped(String id, Category category, String result, int count, List<String> pattern, Map<Character, String> key, List<String> unlockedBy) implements Blueprint {

        /** @throws IllegalArgumentException if any rule in the class comment is broken */
        public Shaped {
            pattern = List.copyOf(pattern);
            key = Map.copyOf(key);
            unlockedBy = List.copyOf(unlockedBy);
            if (pattern.isEmpty() || pattern.size() > MAX_SIZE) {
                throw new IllegalArgumentException(id + ": a pattern has 1 to " + MAX_SIZE + " rows, had " + pattern.size());
            }
            int width = pattern.get(0).length();
            if (width < 1 || width > MAX_SIZE) {
                throw new IllegalArgumentException(id + ": a pattern has 1 to " + MAX_SIZE + " columns, had " + width);
            }
            Set<Character> used = new HashSet<>();
            for (String row : pattern) {
                if (row.length() != width) {
                    throw new IllegalArgumentException(id + ": every row must be " + width + " wide, one was '" + row + "'");
                }
                for (char c : row.toCharArray()) {
                    if (c != ' ') {
                        used.add(c);
                    }
                }
            }
            if (used.isEmpty()) {
                throw new IllegalArgumentException(id + ": a pattern must fill at least one cell");
            }
            for (char c : used) {
                if (!key.containsKey(c)) {
                    throw new IllegalArgumentException(id + ": the pattern uses '" + c + "', which the key does not define");
                }
            }
            for (Map.Entry<Character, String> entry : key.entrySet()) {
                if (entry.getKey() == ' ') {
                    throw new IllegalArgumentException(id + ": space is always the empty cell, never a key");
                }
                if (!used.contains(entry.getKey())) {
                    throw new IllegalArgumentException(id + ": the key defines '" + entry.getKey() + "', which the pattern does not use");
                }
                checkId(entry.getValue(), id + " key '" + entry.getKey() + "'", true);
            }
            checkCommon(id, category, result, count, unlockedBy, ingredientsOf(pattern, key));
        }

        private static Map<String, Integer> ingredientsOf(List<String> pattern, Map<Character, String> key) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String row : pattern) {
                for (char c : row.toCharArray()) {
                    if (c != ' ') {
                        counts.merge(key.get(c), 1, Integer::sum);
                    }
                }
            }
            return counts;
        }

        @Override
        public Map<String, Integer> ingredients() {
            return ingredientsOf(pattern, key);
        }

        /** effects: returns the pattern's width */
        public int width() {
            return pattern.get(0).length();
        }

        /** effects: returns the pattern's height */
        public int height() {
            return pattern.size();
        }
    }

    /**
     * A recipe with no shape: the ingredients anywhere in the grid.
     *
     * @param ingredientList the ingredients, one entry per item, one to nine
     * @param unlockedBy     see {@link Blueprint#unlockedBy()}
     */
    record Shapeless(String id, Category category, String result, int count, List<String> ingredientList, List<String> unlockedBy) implements Blueprint {

        /** @throws IllegalArgumentException if any rule in the class comment is broken */
        public Shapeless {
            ingredientList = List.copyOf(ingredientList);
            unlockedBy = List.copyOf(unlockedBy);
            if (ingredientList.isEmpty() || ingredientList.size() > MAX_SHAPELESS) {
                throw new IllegalArgumentException(id + ": a shapeless recipe takes 1 to " + MAX_SHAPELESS + " ingredients, had " + ingredientList.size());
            }
            for (String ingredient : ingredientList) {
                checkId(ingredient, id + " ingredient", true);
            }
            checkCommon(id, category, result, count, unlockedBy, ingredientsOf(ingredientList));
        }

        private static Map<String, Integer> ingredientsOf(List<String> list) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String ingredient : list) {
                counts.merge(ingredient, 1, Integer::sum);
            }
            return counts;
        }

        @Override
        public Map<String, Integer> ingredients() {
            return ingredientsOf(ingredientList);
        }
    }
}
