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
package com.chunkworks.rangedweaponsmod.domain;

import com.chunkworks.rangedweaponsmod.domain.Blueprint.Category;
import com.chunkworks.rangedweaponsmod.domain.Blueprint.Shaped;
import com.chunkworks.rangedweaponsmod.domain.Blueprint.Shapeless;
import com.chunkworks.rangedweaponsmod.domain.Blueprint.Station;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.chunkworks.rangedweaponsmod.domain.Blueprints.BARREL;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.COAL;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.HEAVY_BARREL;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.IRON;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.LOWER_RECEIVER;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.MACHINE_GUN;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.PISTOL;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.RIFLE;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.SCOPE;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.SCOPED_RIFLE;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.SHOTGUN;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.STEEL_INGOT;
import static com.chunkworks.rangedweaponsmod.domain.Blueprints.STOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Blueprints}: that the catalogue is the tree it claims
 * to be, and that the tree is intuitive.
 *
 * <p>Partitions. Uniqueness: ids, results. Structure: guns made of parts
 * only; stock in every long gun and not the pistol; one lower receiver and
 * one barrel per gun, the machine gun's inside the heavy barrel; scoped
 * rifle = rifle + scope exactly; steel is iron and a little coal and is
 * another mod's, known for the tally and not generated. Cost:
 * the guns ordered pistol, shotgun, rifle, scoped rifle, machine gun by
 * iron. Tally: raw materials expand parts and whole crafts; parts count
 * transitively; a raw item is its own tally; a cycle is caught. Ammunition
 * yields. Every material constant used. The launcher (D-0028): parts only, a
 * seeker and a tube, dearer than any gun in what it takes beyond iron.
 * Stations (D-0029): every gun, launcher, part, magazine and fitting at the
 * bench, every round, the rocket and the bench itself at the table; no two
 * recipes at one station accept one grid (shaped against shaped, mirrored
 * too, and anything against a shapeless one with the same ingredients); the
 * bench a steel top over a crafting table, revealed by steel; the chip a
 * comparator on a quartz board, an ingredient of nothing.
 */
final class BlueprintsTest {

    @Test
    void idsAndResultsAreUnique() {
        Set<String> ids = new HashSet<>();
        Set<String> results = new HashSet<>();
        for (Blueprint b : Blueprints.all()) {
            assertTrue(ids.add(b.id()), "two recipes with id " + b.id());
            assertTrue(results.add(b.result()), "two recipes make " + b.result());
            assertEquals(b.id(), b.result(), "a recipe is named for what it makes");
        }
    }

    @Test
    void gunsAreAssembledFromPartsOnly() {
        for (String gun : Blueprints.guns()) {
            Blueprint recipe = Blueprints.byResult(gun).orElseThrow();
            assertEquals(Category.COMBAT, recipe.category());
            for (String ingredient : recipe.ingredients().keySet()) {
                assertTrue(Blueprints.maker(ingredient).isPresent(), gun + " takes raw " + ingredient);
            }
        }
    }

    @Test
    void everyLongGunHasOneStockAndTheSidearmsNone() {
        assertFalse(Blueprints.partsOf(PISTOL).containsKey(STOCK));
        assertFalse(Blueprints.partsOf(Blueprints.REVOLVER).containsKey(STOCK));
        for (String gun : List.of(SHOTGUN, RIFLE, SCOPED_RIFLE, MACHINE_GUN)) {
            assertEquals(1, Blueprints.partsOf(gun).get(STOCK), gun);
        }
    }

    @Test
    void everyGunHasOneLowerReceiverAndOneBarrel() {
        for (String gun : Blueprints.guns()) {
            Map<String, Integer> parts = Blueprints.partsOf(gun);
            assertEquals(1, parts.get(LOWER_RECEIVER), gun);
            assertEquals(1, parts.get(BARREL), gun + " (the machine gun's is inside its heavy barrel)");
        }
        assertEquals(1, Blueprints.partsOf(MACHINE_GUN).get(HEAVY_BARREL));
        assertFalse(Blueprints.partsOf(RIFLE).containsKey(HEAVY_BARREL));
    }

    @Test
    void aScopedRifleIsARifleWithAScope() {
        Blueprint recipe = Blueprints.byResult(SCOPED_RIFLE).orElseThrow();
        assertEquals(Map.of(SCOPE, 1, RIFLE, 1), recipe.ingredients());
        Map<String, Integer> expected = new java.util.HashMap<>(Blueprints.rawMaterials(RIFLE));
        Blueprints.rawMaterials(SCOPE).forEach((k, v) -> expected.merge(k, v, Integer::sum));
        assertEquals(expected, Blueprints.rawMaterials(SCOPED_RIFLE));
    }

    @Test
    void steelIsIronWithALittleCoalAndIsNotOurs() {
        // Metals and Materials' recipe, known here for the tally: not one of ours.
        assertTrue(Blueprints.byResult(STEEL_INGOT).isEmpty(), "steel is not generated by this mod");
        Blueprint steel = Blueprints.maker(Blueprints.STEEL).orElseThrow();
        assertEquals(3, steel.count());
        assertEquals(Map.of(IRON, 3, COAL, 1), steel.ingredients());
        assertEquals(List.of(steel), Blueprints.external());
        assertEquals("metalsandmaterials:steel_ingot", STEEL_INGOT, "the ingot that fills c:ingots/steel in the pack");
        assertEquals(Map.of(IRON, 3, COAL, 1), Blueprints.rawMaterials(STEEL_INGOT), "one ingot still takes a whole craft");
    }

    @Test
    void theGunsCostMoreIronInTheIntendedOrder() {
        int pistol = Blueprints.ironEquivalent(PISTOL);
        int shotgun = Blueprints.ironEquivalent(SHOTGUN);
        int rifle = Blueprints.ironEquivalent(RIFLE);
        int scoped = Blueprints.ironEquivalent(SCOPED_RIFLE);
        int machineGun = Blueprints.ironEquivalent(MACHINE_GUN);
        assertTrue(pistol <= shotgun, "pistol " + pistol + " > shotgun " + shotgun);
        assertTrue(shotgun < rifle, "shotgun " + shotgun + " >= rifle " + rifle);
        assertTrue(rifle < scoped, "rifle " + rifle + " >= scoped rifle " + scoped);
        assertTrue(scoped < machineGun, "scoped rifle " + scoped + " >= machine gun " + machineGun);
        // A pistol should be within reach early: a stack of iron, not a chest of it.
        assertTrue(pistol <= 8, "pistol costs " + pistol + " iron");
    }

    @Test
    void rawMaterialsExpandPartsInWholeCrafts() {
        // A barrel is three iron; a heavy barrel is a barrel and two steel, and
        // two steel take one three-ingot craft: three iron and a coal.
        assertEquals(Map.of(IRON, 3), Blueprints.rawMaterials(BARREL));
        assertEquals(Map.of(IRON, 6, COAL, 1), Blueprints.rawMaterials(HEAVY_BARREL));
        assertEquals(Map.of("minecraft:paper", 1), Blueprints.rawMaterials("minecraft:paper"));
        assertEquals(Map.of(), Blueprints.partsOf("minecraft:paper"));
        assertEquals(Map.of(BARREL, 1, STEEL_INGOT, 2), Blueprints.partsOf(HEAVY_BARREL));
    }

    @Test
    void theCatalogueHasNoCycleAndACycleWouldBeCaught() {
        assertTrue(Blueprints.cycle().isEmpty(), () -> "cycle: " + Blueprints.cycle().get());
        // The tally on a hand-built loop: a thing made of itself.
        Shaped loop = new Shaped("rangedweaponsmod:loop", Category.MISC, "rangedweaponsmod:loop", 1, List.of("L"), Map.of('L', "rangedweaponsmod:loop"), List.of("rangedweaponsmod:loop"));
        assertEquals(Map.of("rangedweaponsmod:loop", 1), loop.ingredients());
        assertThrows(IllegalStateException.class, () -> Blueprints.rawMaterialsOf(List.of(loop), "rangedweaponsmod:loop"));
    }

    @Test
    void ammunitionYieldsAsBefore() {
        assertEquals(10, Blueprints.byResult(Blueprints.SMALL_ROUND).orElseThrow().count());
        assertEquals(8, Blueprints.byResult(Blueprints.ROUND).orElseThrow().count());
        assertEquals(4, Blueprints.byResult(Blueprints.SHELL).orElseThrow().count());
        assertEquals(4, Blueprints.byResult(Blueprints.SLUG).orElseThrow().count());
        assertTrue(Blueprints.rawMaterials(Blueprints.SLUG).containsKey(IRON), "a slug takes a whole ingot");
        // D-0025: a round's case is a copper nugget, not an ingot.
        assertEquals(Map.of(Blueprints.IRON_NUGGET, 1, Blueprints.GUNPOWDER, 1, Blueprints.COPPER_NUGGET, 1),
                Blueprints.byResult(Blueprints.ROUND).orElseThrow().ingredients());
        assertEquals(1, Blueprints.byResult(Blueprints.ROCKET).orElseThrow().count(), "a rocket is one a craft");
        assertEquals(List.of(Blueprints.SMALL_ROUND, Blueprints.ROUND, Blueprints.SHELL, Blueprints.SLUG, Blueprints.ROCKET),
                Blueprints.ammunition());
    }

    @Test
    void everyMaterialIsUsedAndEveryPartIsMadeAndUsed() {
        Set<String> used = new HashSet<>();
        Blueprints.all().forEach(b -> used.addAll(b.ingredients().keySet()));
        // Coal is not here: only steel's recipe took it, and that is Metals and Materials' now.
        for (String material : List.of(IRON, Blueprints.REDSTONE, Blueprints.PLANKS, Blueprints.STICK, Blueprints.GLASS_PANE,
                Blueprints.COPPER_NUGGET, Blueprints.IRON_NUGGET, Blueprints.GUNPOWDER, Blueprints.PAPER, Blueprints.STEEL,
                Blueprints.GOLD, Blueprints.DIAMOND, Blueprints.REDSTONE_BLOCK, Blueprints.TNT, Blueprints.BLAZE_POWDER,
                Blueprints.CRAFTING_TABLE, Blueprints.GOLD_NUGGET, Blueprints.QUARTZ, Blueprints.COMPARATOR)) {
            assertTrue(used.contains(material), material + " is never used");
        }
        assertTrue(Blueprints.external().stream().anyMatch(b -> b.ingredients().containsKey(COAL)), "coal is used by the steel we count through");
        for (String part : Blueprints.parts()) {
            assertTrue(Blueprints.byResult(part).isPresent(), part + " has no recipe");
            boolean consumed = Blueprints.all().stream().anyMatch(b -> b.ingredients().keySet().stream()
                    .anyMatch(i -> Blueprints.maker(i).map(m -> m.result().equals(part)).orElse(false)));
            assertTrue(consumed, part + " is made but nothing takes it");
        }
    }

    @Test
    void theLauncherIsASeekerOverAStockALowerReceiverAndATube() {
        assertEquals(List.of(Blueprints.ROCKET_LAUNCHER), Blueprints.launchers());
        Blueprint recipe = Blueprints.byResult(Blueprints.ROCKET_LAUNCHER).orElseThrow();
        assertEquals(Category.COMBAT, recipe.category());
        assertEquals(Map.of(Blueprints.SEEKER, 1, STOCK, 1, LOWER_RECEIVER, 1, Blueprints.LAUNCH_TUBE, 1), recipe.ingredients());
        for (String ingredient : recipe.ingredients().keySet()) {
            assertTrue(Blueprints.maker(ingredient).isPresent(), "the launcher takes raw " + ingredient);
        }
        assertFalse(Blueprints.guns().contains(Blueprints.ROCKET_LAUNCHER), "it has no barrel: not a gun");
    }

    @Test
    void theLauncherAndItsRocketsAreDear() {
        Map<String, Integer> launcher = Blueprints.rawMaterials(Blueprints.ROCKET_LAUNCHER);
        assertEquals(1, launcher.get(Blueprints.DIAMOND), "a diamond for the seeker's lens");
        assertEquals(2, launcher.get(Blueprints.GOLD));
        assertEquals(1, launcher.get(Blueprints.REDSTONE_BLOCK));
        assertTrue(Blueprints.ironEquivalent(Blueprints.ROCKET_LAUNCHER) >= Blueprints.ironEquivalent(RIFLE),
                "at least a rifle's iron besides");
        Map<String, Integer> rocket = Blueprints.rawMaterials(Blueprints.ROCKET);
        assertEquals(1, rocket.get(Blueprints.TNT));
        assertEquals(1, rocket.get(Blueprints.BLAZE_POWDER));
        assertTrue(Blueprints.ironEquivalent(Blueprints.ROCKET) >= 2, "two steel in every rocket");
    }

    @Test
    void gunsPartsMagazinesAndFittingsAreMadeAtTheBenchAndTheRestAtTheTable() {
        List<String> bench = new ArrayList<>();
        bench.addAll(Blueprints.guns());
        bench.addAll(Blueprints.launchers());
        bench.addAll(Blueprints.parts());
        bench.addAll(Blueprints.magazines());
        bench.addAll(Blueprints.fittings());
        List<String> table = new ArrayList<>(Blueprints.ammunition());
        table.addAll(Blueprints.stations());
        for (String item : bench) {
            assertEquals(Station.BENCH, Blueprints.byResult(item).orElseThrow().station(), item);
        }
        for (String item : table) {
            assertEquals(Station.TABLE, Blueprints.byResult(item).orElseThrow().station(), item);
        }
        Set<String> classified = new HashSet<>(bench);
        classified.addAll(table);
        for (Blueprint b : Blueprints.all()) {
            assertTrue(classified.contains(b.result()), b.id() + " is in no list");
        }
        assertEquals(Blueprints.all().size(), classified.size(), "every recipe in exactly one list");
    }

    @Test
    void noTwoRecipesAtOneStationAcceptTheSameGrid() {
        List<Blueprint> all = Blueprints.all();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Blueprint a = all.get(i), b = all.get(j);
                if (a.station() == b.station()) {
                    assertFalse(collide(a, b), a.id() + " and " + b.id() + " accept the same grid at the " + a.station());
                }
            }
        }
        // The check itself bites: a mirrored copy of the rifle magazine's diagonal, and a
        // shapeless pair of steel, both collide with it.
        Shaped mirrored = Shaped.bench("rangedweaponsmod:x", Category.MISC, "rangedweaponsmod:x", 1, List.of(" T", "T "), Map.of('T', Blueprints.STEEL));
        assertTrue(collide(Blueprints.RIFLE_MAGAZINE_RECIPE, mirrored));
        Shapeless pair = Shapeless.bench("rangedweaponsmod:y", Category.MISC, "rangedweaponsmod:y", 1, List.of(Blueprints.STEEL, Blueprints.STEEL));
        assertTrue(collide(Blueprints.RIFLE_MAGAZINE_RECIPE, pair));
    }

    @Test
    void theWorkbenchIsASteelTopOverACraftingTableAndSteelRevealsIt() {
        Blueprint bench = Blueprints.byResult(Blueprints.WEAPONS_WORKBENCH).orElseThrow();
        assertEquals(Station.TABLE, bench.station(), "the one station made at the table");
        assertEquals(Map.of(Blueprints.STEEL, 3, Blueprints.PLANKS, 5, Blueprints.CRAFTING_TABLE, 1), bench.ingredients());
        assertEquals(List.of(Blueprints.STEEL), bench.unlockedBy());
        assertEquals(List.of(Blueprints.WEAPONS_WORKBENCH), Blueprints.stations());
    }

    @Test
    void theLockOnChipIsAComparatorOnAQuartzBoardAndAnIngredientOfNothing() {
        Blueprint chip = Blueprints.byResult(Blueprints.LOCK_ON_CHIP).orElseThrow();
        assertEquals(Station.BENCH, chip.station());
        assertEquals(Map.of(Blueprints.GOLD_NUGGET, 4, Blueprints.REDSTONE, 2, Blueprints.QUARTZ, 2, Blueprints.COMPARATOR, 1), chip.ingredients());
        assertEquals(List.of(Blueprints.LOCK_ON_CHIP), Blueprints.fittings());
        for (Blueprint b : Blueprints.all()) {
            assertFalse(b.ingredients().containsKey(Blueprints.LOCK_ON_CHIP), "a chip is fitted, never crafted into " + b.id());
        }
    }

    /** effects: returns whether one grid at the bench or table could match both recipes */
    private static boolean collide(Blueprint a, Blueprint b) {
        if (a instanceof Shaped sa && b instanceof Shaped sb) {
            List<List<String>> ga = grid(sa), gb = grid(sb);
            return ga.equals(gb) || ga.equals(mirror(gb));
        }
        return a.ingredients().equals(b.ingredients());
    }

    /** effects: returns the pattern's cells as ingredient ids ("" for empty), empty rows and columns at the edges trimmed, as the game trims them */
    private static List<List<String>> grid(Shaped s) {
        List<List<String>> rows = new ArrayList<>();
        for (String row : s.pattern()) {
            List<String> cells = new ArrayList<>();
            for (char c : row.toCharArray()) {
                cells.add(c == ' ' ? "" : s.key().get(c));
            }
            rows.add(cells);
        }
        while (rows.stream().allMatch(r -> r.get(0).isEmpty())) {
            rows.forEach(r -> r.remove(0));
        }
        while (rows.stream().allMatch(r -> r.get(r.size() - 1).isEmpty())) {
            rows.forEach(r -> r.remove(r.size() - 1));
        }
        while (rows.get(0).stream().allMatch(String::isEmpty)) {
            rows.remove(0);
        }
        while (rows.get(rows.size() - 1).stream().allMatch(String::isEmpty)) {
            rows.remove(rows.size() - 1);
        }
        return rows;
    }

    private static List<List<String>> mirror(List<List<String>> grid) {
        List<List<String>> mirrored = new ArrayList<>();
        for (List<String> row : grid) {
            List<String> copy = new ArrayList<>(row);
            java.util.Collections.reverse(copy);
            mirrored.add(copy);
        }
        return mirrored;
    }
}
