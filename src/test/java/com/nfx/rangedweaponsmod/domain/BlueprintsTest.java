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
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nfx.rangedweaponsmod.domain.Blueprints.BARREL;
import static com.nfx.rangedweaponsmod.domain.Blueprints.COAL;
import static com.nfx.rangedweaponsmod.domain.Blueprints.HEAVY_BARREL;
import static com.nfx.rangedweaponsmod.domain.Blueprints.IRON;
import static com.nfx.rangedweaponsmod.domain.Blueprints.LOWER_RECEIVER;
import static com.nfx.rangedweaponsmod.domain.Blueprints.MACHINE_GUN;
import static com.nfx.rangedweaponsmod.domain.Blueprints.PISTOL;
import static com.nfx.rangedweaponsmod.domain.Blueprints.RIFLE;
import static com.nfx.rangedweaponsmod.domain.Blueprints.SCOPE;
import static com.nfx.rangedweaponsmod.domain.Blueprints.SCOPED_RIFLE;
import static com.nfx.rangedweaponsmod.domain.Blueprints.SHOTGUN;
import static com.nfx.rangedweaponsmod.domain.Blueprints.STEEL_INGOT;
import static com.nfx.rangedweaponsmod.domain.Blueprints.STOCK;
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
 * rifle = rifle + scope exactly; steel is iron and a little coal. Cost:
 * the guns ordered pistol, shotgun, rifle, scoped rifle, machine gun by
 * iron. Tally: raw materials expand parts and whole crafts; parts count
 * transitively; a raw item is its own tally; a cycle is caught. Ammunition
 * yields. Every material constant used.
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
    void everyLongGunHasOneStockAndThePistolNone() {
        assertFalse(Blueprints.partsOf(PISTOL).containsKey(STOCK));
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
    void steelIsIronWithALittleCoal() {
        Blueprint steel = Blueprints.byResult(STEEL_INGOT).orElseThrow();
        assertEquals(3, steel.count());
        assertEquals(Map.of(IRON, 3, COAL, 1), steel.ingredients());
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
        assertEquals(List.of(Blueprints.SMALL_ROUND, Blueprints.ROUND, Blueprints.SHELL, Blueprints.SLUG), Blueprints.ammunition());
    }

    @Test
    void everyMaterialIsUsedAndEveryPartIsMadeAndUsed() {
        Set<String> used = new HashSet<>();
        Blueprints.all().forEach(b -> used.addAll(b.ingredients().keySet()));
        for (String material : List.of(IRON, COAL, Blueprints.REDSTONE, Blueprints.PLANKS, Blueprints.STICK, Blueprints.GLASS_PANE,
                Blueprints.COPPER, Blueprints.IRON_NUGGET, Blueprints.GUNPOWDER, Blueprints.PAPER, Blueprints.STEEL)) {
            assertTrue(used.contains(material), material + " is never used");
        }
        for (String part : Blueprints.parts()) {
            assertTrue(Blueprints.byResult(part).isPresent(), part + " has no recipe");
            boolean consumed = Blueprints.all().stream().anyMatch(b -> b.ingredients().keySet().stream()
                    .anyMatch(i -> Blueprints.maker(i).map(m -> m.result().equals(part)).orElse(false)));
            assertTrue(consumed, part + " is made but nothing takes it");
        }
    }
}
