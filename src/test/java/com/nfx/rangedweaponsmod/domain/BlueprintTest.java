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

import com.nfx.rangedweaponsmod.domain.Blueprint.Category;
import com.nfx.rangedweaponsmod.domain.Blueprint.Shaped;
import com.nfx.rangedweaponsmod.domain.Blueprint.Shapeless;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Blueprint}.
 *
 * <p>Partitions. Shaped patterns: one row / three rows / four; one column /
 * three / four; ragged rows; all spaces; a symbol the key lacks; a key the
 * pattern lacks; space as a key. Ids: item / tag / malformed / a tag where
 * an item is required. Count: one / zero. Unlocks: one / several / none /
 * not an ingredient / two with one criterion name. Shapeless: one
 * ingredient / nine / none / ten. Helpers: namespace, path and criterion
 * name of an item and of a tag; the advancement id per category; the
 * ingredient multiset with repeats and spaces.
 */
final class BlueprintTest {

    private static final String IRON = "#c:ingots/iron";
    private static final String STICK = "minecraft:stick";

    private static Shaped shaped(List<String> pattern, Map<Character, String> key, List<String> unlockedBy) {
        return new Shaped("rangedweaponsmod:thing", Category.MISC, "rangedweaponsmod:thing", 1, pattern, key, unlockedBy);
    }

    @Test
    void aShapedRecipeTalliesItsCells() {
        Shaped s = shaped(List.of("II ", " S "), Map.of('I', IRON, 'S', STICK), List.of(IRON));
        assertEquals(Map.of(IRON, 2, STICK, 1), s.ingredients());
        assertEquals(3, s.width());
        assertEquals(2, s.height());
    }

    @Test
    void patternsAreOneToThreeRowsAndColumns() {
        shaped(List.of("I"), Map.of('I', IRON), List.of(IRON));
        shaped(List.of("III", "III", "III"), Map.of('I', IRON), List.of(IRON));
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of(), Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of("I", "I", "I", "I"), Map.of('I', IRON), List.of(IRON)));
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of("IIII"), Map.of('I', IRON), List.of(IRON)));
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of(""), Map.of(), List.of()));
    }

    @Test
    void rowsMustBeEqualAndSomethingMustBeInThem() {
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of("II", "I"), Map.of('I', IRON), List.of(IRON)));
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of("  ", "  "), Map.of(), List.of()));
    }

    @Test
    void everySymbolIsDefinedAndEveryKeyIsUsed() {
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of("IS"), Map.of('I', IRON), List.of(IRON)));
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of("I"), Map.of('I', IRON, 'S', STICK), List.of(IRON)));
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of("I "), Map.of('I', IRON, ' ', STICK), List.of(IRON)));
    }

    @Test
    void idsAreResourceIdsAndResultsAreItems() {
        assertThrows(IllegalArgumentException.class, () -> new Shaped("thing", Category.MISC, "rangedweaponsmod:thing", 1, List.of("I"), Map.of('I', IRON), List.of(IRON)));
        assertThrows(IllegalArgumentException.class, () -> new Shaped("rangedweaponsmod:thing", Category.MISC, "#c:ingots/iron", 1, List.of("I"), Map.of('I', IRON), List.of(IRON)));
        assertThrows(IllegalArgumentException.class, () -> new Shaped("rangedweaponsmod:thing", Category.MISC, "Rangedweaponsmod:Thing", 1, List.of("I"), Map.of('I', IRON), List.of(IRON)));
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of("I"), Map.of('I', "iron"), List.of("iron")));
    }

    @Test
    void countIsAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> new Shaped("rangedweaponsmod:thing", Category.MISC, "rangedweaponsmod:thing", 0, List.of("I"), Map.of('I', IRON), List.of(IRON)));
    }

    @Test
    void unlocksAreIngredientsWithDistinctNames() {
        shaped(List.of("IS"), Map.of('I', IRON, 'S', STICK), List.of(IRON, STICK));
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of("I"), Map.of('I', IRON), List.of()));
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of("I"), Map.of('I', IRON), List.of(STICK)));
        // minecraft:barrel and rangedweaponsmod:barrel would both be "has_barrel".
        assertThrows(IllegalArgumentException.class, () -> shaped(List.of("AB"), Map.of('A', "minecraft:barrel", 'B', "rangedweaponsmod:barrel"),
                List.of("minecraft:barrel", "rangedweaponsmod:barrel")));
    }

    @Test
    void aShapelessRecipeTakesOneToNineIngredients() {
        Shapeless one = new Shapeless("rangedweaponsmod:thing", Category.MISC, "rangedweaponsmod:thing", 2, List.of(IRON), List.of(IRON));
        assertEquals(Map.of(IRON, 1), one.ingredients());
        List<String> nine = List.of(IRON, IRON, IRON, IRON, IRON, IRON, IRON, IRON, STICK);
        assertEquals(Map.of(IRON, 8, STICK, 1), new Shapeless("rangedweaponsmod:thing", Category.MISC, "rangedweaponsmod:thing", 1, nine, List.of(STICK)).ingredients());
        assertThrows(IllegalArgumentException.class, () -> new Shapeless("rangedweaponsmod:thing", Category.MISC, "rangedweaponsmod:thing", 1, List.of(), List.of()));
        List<String> ten = List.of(IRON, IRON, IRON, IRON, IRON, IRON, IRON, IRON, IRON, IRON);
        assertThrows(IllegalArgumentException.class, () -> new Shapeless("rangedweaponsmod:thing", Category.MISC, "rangedweaponsmod:thing", 1, ten, List.of(IRON)));
    }

    @Test
    void helpersReadItemsAndTagsAlike() {
        assertTrue(Blueprint.isTag(IRON));
        assertFalse(Blueprint.isTag(STICK));
        assertEquals("c:ingots/iron", Blueprint.tagName(IRON));
        assertThrows(IllegalArgumentException.class, () -> Blueprint.tagName(STICK));
        assertEquals("c", Blueprint.namespace(IRON));
        assertEquals("minecraft", Blueprint.namespace(STICK));
        assertEquals("ingots/iron", Blueprint.path(IRON));
        assertEquals("stick", Blueprint.path(STICK));
        assertEquals("has_ingots_iron", Blueprint.criterionName(IRON));
        assertEquals("has_stick", Blueprint.criterionName(STICK));
    }

    @Test
    void theAdvancementIdFollowsTheCategory() {
        Shaped misc = shaped(List.of("I"), Map.of('I', IRON), List.of(IRON));
        assertEquals("rangedweaponsmod:recipes/misc/thing", misc.advancementId());
        Shaped combat = new Shaped("rangedweaponsmod:pistol", Category.COMBAT, "rangedweaponsmod:pistol", 1, List.of("I"), Map.of('I', IRON), List.of(IRON));
        assertEquals("rangedweaponsmod:recipes/combat/pistol", combat.advancementId());
    }

    @Test
    void aBlueprintIsImmutable() {
        List<String> pattern = new java.util.ArrayList<>(List.of("I"));
        Map<Character, String> key = new java.util.HashMap<>(Map.of('I', IRON));
        Shaped s = shaped(pattern, key, List.of(IRON));
        pattern.add("I");
        key.put('S', STICK);
        assertEquals(List.of("I"), s.pattern());
        assertEquals(Map.of('I', IRON), s.key());
    }
}
