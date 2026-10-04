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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every recipe this mod ships, and what they add up to.
 *
 * <p>A gun is assembled from parts made separately: receivers of steel
 * (iron with a little coal), a barrel of iron, a stock of wood, a scope of
 * iron and glass. The shape a gun is assembled in follows its silhouette.
 * The pistol is the simplest and cheapest; the machine gun is the most
 * involved, the only gun whose barrel is itself a two-step part.
 *
 * <p>Every gun, part, magazine and fitting is made at the weapons workbench
 * (D-0029); rounds, rockets and the bench itself at the crafting table.
 *
 * <p>This catalogue is the single source: the recipe files and their
 * recipe-book unlocks are written from it, the tests hold it to the rules
 * above, and the gametests check the running server finds exactly these.
 * Materials are tags where the game or the loader defines one, so another
 * mod's iron, coal, planks or panes serve as well.
 */
public final class Blueprints {
    private Blueprints() {}

    public static final String NS = "rangedweaponsmod";

    // Materials.
    public static final String IRON = "#c:ingots/iron";
    public static final String COAL = "#minecraft:coals";
    public static final String REDSTONE = "#c:dusts/redstone";
    public static final String PLANKS = "#minecraft:planks";
    public static final String STICK = "#c:rods/wooden";
    public static final String GLASS_PANE = "#c:glass_panes/colorless";
    /** A round's case, by the common tag: Create's nugget fills it in the pack (D-0025). */
    public static final String COPPER_NUGGET = "#c:nuggets/copper";
    public static final String IRON_NUGGET = "#c:nuggets/iron";
    public static final String GUNPOWDER = "#c:gunpowders";
    public static final String PAPER = "minecraft:paper";
    /** Steel by its common tag: Metals and Materials' -- or any other mod's. */
    public static final String STEEL = "#c:ingots/steel";
    // The launcher's and the rocket's dearer materials (D-0028).
    public static final String GOLD = "#c:ingots/gold";
    public static final String DIAMOND = "#c:gems/diamond";
    public static final String REDSTONE_BLOCK = "#c:storage_blocks/redstone";
    public static final String TNT = "minecraft:tnt";
    public static final String BLAZE_POWDER = "minecraft:blaze_powder";
    /** The ingot that fills the tag in the pack: Metals and Materials', not ours since 2.3.0. */
    public static final String STEEL_INGOT = "metalsandmaterials:steel_ingot";
    // The bench's and the lock-on chip's (D-0029).
    public static final String CRAFTING_TABLE = "#c:player_workstations/crafting_tables";
    public static final String GOLD_NUGGET = "#c:nuggets/gold";
    public static final String QUARTZ = "#c:gems/quartz";
    public static final String COMPARATOR = "minecraft:comparator";

    // Parts, by item id.
    public static final String LOWER_RECEIVER = NS + ":lower_receiver";
    public static final String UPPER_RECEIVER = NS + ":upper_receiver";
    public static final String BARREL = NS + ":barrel";
    public static final String HEAVY_BARREL = NS + ":heavy_barrel";
    public static final String STOCK = NS + ":stock";
    public static final String PUMP = NS + ":pump";
    public static final String SCOPE = NS + ":scope";
    public static final String LAUNCH_TUBE = NS + ":launch_tube";
    public static final String SEEKER = NS + ":seeker";

    // Guns and ammunition, by item id.
    public static final String PISTOL = NS + ":pistol";
    public static final String REVOLVER = NS + ":revolver";
    public static final String SHOTGUN = NS + ":shotgun";
    public static final String RIFLE = NS + ":rifle";
    public static final String SCOPED_RIFLE = NS + ":scoped_rifle";
    public static final String MACHINE_GUN = NS + ":machine_gun";
    public static final String ROCKET_LAUNCHER = NS + ":rocket_launcher";
    public static final String SMALL_ROUND = NS + ":small_round";
    public static final String ROUND = NS + ":round";
    public static final String SHELL = NS + ":shell";
    public static final String SLUG = NS + ":slug";
    public static final String ROCKET = NS + ":rocket";

    // The workbench and what is fitted at it (D-0029), by item id.
    public static final String WEAPONS_WORKBENCH = NS + ":weapons_workbench";
    public static final String LOCK_ON_CHIP = NS + ":lock_on_chip";

    /**
     * Three iron and a little carbon make three steel -- Metals and Materials'
     * recipe, known here so the tally can count steel as the iron it was made
     * from. Not one of ours: never generated, never expected on the server
     * under our id.
     */
    public static final Blueprint STEEL_INGOT_RECIPE = new Shapeless(STEEL_INGOT, Category.MISC, STEEL_INGOT, 3,
            List.of(IRON, IRON, IRON, COAL), List.of(IRON, COAL));

    /** The body over the trigger group. */
    public static final Blueprint LOWER_RECEIVER_RECIPE = Shaped.bench(LOWER_RECEIVER, Category.MISC, LOWER_RECEIVER, 1,
            List.of("TTT", " R "), Map.of('T', STEEL, 'R', REDSTONE));

    /** A block of steel that houses the action. */
    public static final Blueprint UPPER_RECEIVER_RECIPE = Shaped.bench(UPPER_RECEIVER, Category.MISC, UPPER_RECEIVER, 1,
            List.of("TT", "TT"), Map.of('T', STEEL));

    /** A tube of iron. */
    public static final Blueprint BARREL_RECIPE = Shaped.bench(BARREL, Category.MISC, BARREL, 1,
            List.of("III"), Map.of('I', IRON));

    /** A barrel wrapped in steel, for fire that does not stop. */
    public static final Blueprint HEAVY_BARREL_RECIPE = Shapeless.bench(HEAVY_BARREL, Category.MISC, HEAVY_BARREL, 1,
            List.of(BARREL, STEEL, STEEL));

    /** A wooden butt with a raked wrist. */
    public static final Blueprint STOCK_RECIPE = Shaped.bench(STOCK, Category.MISC, STOCK, 1,
            List.of("PP", "PS"), Map.of('P', PLANKS, 'S', STICK));

    /** The shotgun's forend: wood around a rod. */
    public static final Blueprint PUMP_RECIPE = Shaped.bench(PUMP, Category.MISC, PUMP, 1,
            List.of("P", "S", "P"), Map.of('P', PLANKS, 'S', STICK));

    /** A lens, a tube, a lens. */
    public static final Blueprint SCOPE_RECIPE = Shaped.bench(SCOPE, Category.MISC, SCOPE, 1,
            List.of("GIG"), Map.of('G', GLASS_PANE, 'I', IRON));

    /** A lower receiver with a barrel: the grip and what it fires from. */
    public static final Blueprint PISTOL_RECIPE = Shaped.bench(PISTOL, Category.COMBAT, PISTOL, 1,
            List.of("LB"), Map.of('L', LOWER_RECEIVER, 'B', BARREL));

    /** A receiver for the cylinder over the grip, beside a short barrel. */
    public static final Blueprint REVOLVER_RECIPE = Shaped.bench(REVOLVER, Category.COMBAT, REVOLVER, 1,
            List.of("UB", "L "), Map.of('U', UPPER_RECEIVER, 'B', BARREL, 'L', LOWER_RECEIVER));

    /** Stock, receiver, barrel, and the pump under the barrel. */
    public static final Blueprint SHOTGUN_RECIPE = Shaped.bench(SHOTGUN, Category.COMBAT, SHOTGUN, 1,
            List.of("KLB", "  M"), Map.of('K', STOCK, 'L', LOWER_RECEIVER, 'B', BARREL, 'M', PUMP));

    /** Stock, both receivers, barrel. */
    public static final Blueprint RIFLE_RECIPE = Shaped.bench(RIFLE, Category.COMBAT, RIFLE, 1,
            List.of(" U ", "KLB"), Map.of('U', UPPER_RECEIVER, 'K', STOCK, 'L', LOWER_RECEIVER, 'B', BARREL));

    /** A rifle with a scope on top. */
    public static final Blueprint SCOPED_RIFLE_RECIPE = Shaped.bench(SCOPED_RIFLE, Category.COMBAT, SCOPED_RIFLE, 1,
            List.of("O", "F"), Map.of('O', SCOPE, 'F', RIFLE));

    /** A rifle's build on a heavy barrel. */
    public static final Blueprint MACHINE_GUN_RECIPE = Shaped.bench(MACHINE_GUN, Category.COMBAT, MACHINE_GUN, 1,
            List.of(" U ", "KLH"), Map.of('U', UPPER_RECEIVER, 'K', STOCK, 'L', LOWER_RECEIVER, 'H', HEAVY_BARREL));

    public static final Blueprint SMALL_ROUND_RECIPE = new Shaped(SMALL_ROUND, Category.COMBAT, SMALL_ROUND, 10,
            List.of("N", "G", "N"), Map.of('N', IRON_NUGGET, 'G', GUNPOWDER), List.of(GUNPOWDER));

    public static final Blueprint ROUND_RECIPE = new Shaped(ROUND, Category.COMBAT, ROUND, 8,
            List.of("N", "G", "C"), Map.of('N', IRON_NUGGET, 'G', GUNPOWDER, 'C', COPPER_NUGGET), List.of(GUNPOWDER));

    public static final Blueprint SHELL_RECIPE = new Shaped(SHELL, Category.COMBAT, SHELL, 4,
            List.of("P", "G", "N"), Map.of('P', PAPER, 'G', GUNPOWDER, 'N', IRON_NUGGET), List.of(GUNPOWDER));

    /** A shell with a whole ingot behind the powder: one heavy round, four to the ingot. */
    public static final Blueprint SLUG_RECIPE = new Shaped(SLUG, Category.COMBAT, SLUG, 4,
            List.of("P", "G", "I"), Map.of('P', PAPER, 'G', GUNPOWDER, 'I', IRON), List.of(GUNPOWDER, IRON));

    /** The rocket launcher's tube: eight steel, a pipe seen end-on (D-0028). */
    public static final Blueprint LAUNCH_TUBE_RECIPE = Shaped.bench(LAUNCH_TUBE, Category.MISC, LAUNCH_TUBE, 1,
            List.of("TTT", "T T", "TTT"), Map.of('T', STEEL));

    /** The seeker: a scope behind a diamond lens, gold contacts, a block of redstone to think with. */
    public static final Blueprint SEEKER_RECIPE = Shaped.bench(SEEKER, Category.MISC, SEEKER, 1,
            List.of("GDG", " O ", " R "), Map.of('G', GOLD, 'D', DIAMOND, 'O', SCOPE, 'R', REDSTONE_BLOCK));

    /** The launcher: the seeker over a stock, a lower receiver and the tube, in its silhouette. */
    public static final Blueprint ROCKET_LAUNCHER_RECIPE = Shaped.bench(ROCKET_LAUNCHER, Category.COMBAT, ROCKET_LAUNCHER, 1,
            List.of(" S ", "KLU"), Map.of('S', SEEKER, 'K', STOCK, 'L', LOWER_RECEIVER, 'U', LAUNCH_TUBE));

    /** A rocket, one a craft: a TNT charge in a steel body, blaze powder for the motor, redstone to steer. */
    public static final Blueprint ROCKET_RECIPE = new Shaped(ROCKET, Category.COMBAT, ROCKET, 1,
            List.of(" R ", "STS", " B "), Map.of('R', REDSTONE, 'S', STEEL, 'T', TNT, 'B', BLAZE_POWDER),
            List.of(TNT, BLAZE_POWDER));

    public static final String PISTOL_MAGAZINE = NS + ":pistol_magazine";
    public static final String RIFLE_MAGAZINE = NS + ":rifle_magazine";
    public static final String MACHINE_GUN_BOX = NS + ":machine_gun_box";

    // The magazines: two steel each, told apart by the shape -- a short
    // stack for the pistol's, a staggered pair for the rifle's curve, a
    // pair side by side for the squat box. Steel, so a magazine costs a
    // little more than the rounds it holds and less than any gun.
    public static final Blueprint PISTOL_MAGAZINE_RECIPE = Shaped.bench(PISTOL_MAGAZINE, Category.COMBAT, PISTOL_MAGAZINE, 1,
            List.of("T", "T"), Map.of('T', STEEL));
    public static final Blueprint RIFLE_MAGAZINE_RECIPE = Shaped.bench(RIFLE_MAGAZINE, Category.COMBAT, RIFLE_MAGAZINE, 1,
            List.of("T ", " T"), Map.of('T', STEEL));
    public static final Blueprint MACHINE_GUN_BOX_RECIPE = Shaped.bench(MACHINE_GUN_BOX, Category.COMBAT, MACHINE_GUN_BOX, 1,
            List.of("TT"), Map.of('T', STEEL));

    /**
     * The weapons workbench (D-0029): a steel top over planks and a crafting table. Made at the
     * crafting table, the one recipe on the way to a gun that is; holding steel reveals it.
     */
    public static final Blueprint WEAPONS_WORKBENCH_RECIPE = new Shaped(WEAPONS_WORKBENCH, Category.MISC, WEAPONS_WORKBENCH, 1,
            List.of("SSS", "PCP", "PPP"), Map.of('S', STEEL, 'P', PLANKS, 'C', CRAFTING_TABLE), List.of(STEEL));

    /**
     * The lock-on chip (D-0029): a comparator on a quartz board with gold contacts and redstone
     * traces. Made at the bench and fitted there, into nothing else.
     */
    public static final Blueprint LOCK_ON_CHIP_RECIPE = Shaped.bench(LOCK_ON_CHIP, Category.MISC, LOCK_ON_CHIP, 1,
            List.of("NRN", "QCQ", "NRN"), Map.of('N', GOLD_NUGGET, 'R', REDSTONE, 'Q', QUARTZ, 'C', COMPARATOR));

    private static final List<Blueprint> ALL = List.of(
            LOWER_RECEIVER_RECIPE, UPPER_RECEIVER_RECIPE, BARREL_RECIPE, HEAVY_BARREL_RECIPE,
            STOCK_RECIPE, PUMP_RECIPE, SCOPE_RECIPE, LAUNCH_TUBE_RECIPE, SEEKER_RECIPE,
            PISTOL_RECIPE, REVOLVER_RECIPE, SHOTGUN_RECIPE, RIFLE_RECIPE, SCOPED_RIFLE_RECIPE, MACHINE_GUN_RECIPE,
            ROCKET_LAUNCHER_RECIPE,
            SMALL_ROUND_RECIPE, ROUND_RECIPE, SHELL_RECIPE, SLUG_RECIPE, ROCKET_RECIPE,
            PISTOL_MAGAZINE_RECIPE, RIFLE_MAGAZINE_RECIPE, MACHINE_GUN_BOX_RECIPE,
            LOCK_ON_CHIP_RECIPE, WEAPONS_WORKBENCH_RECIPE);

    /** Recipes another mod makes that the tally counts through: steel's. */
    private static final List<Blueprint> EXTERNAL = List.of(STEEL_INGOT_RECIPE);
    /** Ours and the known external ones: what a tally walks. */
    private static final List<Blueprint> KNOWN = concat(ALL, EXTERNAL);

    /** The tags a known part fills: a recipe taking the tag is, for the tally, taking the part. */
    private static final Map<String, String> FILLS = Map.of(STEEL, STEEL_INGOT);

    private static final List<String> GUNS = List.of(PISTOL, REVOLVER, SHOTGUN, RIFLE, SCOPED_RIFLE, MACHINE_GUN);
    /** Launchers fire rockets, not rounds: no barrel, so they are not among the guns. */
    private static final List<String> LAUNCHERS = List.of(ROCKET_LAUNCHER);
    private static final List<String> PARTS = List.of(LOWER_RECEIVER, UPPER_RECEIVER, BARREL, HEAVY_BARREL, STOCK, PUMP, SCOPE,
            LAUNCH_TUBE, SEEKER);

    private static List<Blueprint> concat(List<Blueprint> a, List<Blueprint> b) {
        List<Blueprint> both = new ArrayList<>(a);
        both.addAll(b);
        return List.copyOf(both);
    }

    /** effects: returns every recipe of ours, parts first, then guns, ammunition, magazines, the chip and the bench */
    public static List<Blueprint> all() {
        return ALL;
    }

    /** effects: returns the recipes of other mods this catalogue counts through (steel's) */
    public static List<Blueprint> external() {
        return EXTERNAL;
    }

    /** effects: returns the guns' item ids, in creative-tab order */
    public static List<String> guns() {
        return GUNS;
    }

    /** effects: returns the launchers' item ids, in creative-tab order (after the guns) */
    public static List<String> launchers() {
        return LAUNCHERS;
    }

    /** effects: returns the ammunition's item ids, in the order the tab shows them */
    public static List<String> ammunition() {
        return List.of(SMALL_ROUND, ROUND, SHELL, SLUG, ROCKET);
    }

    /** effects: returns the magazines' item ids, in the order the tab shows them */
    public static List<String> magazines() {
        return List.of(PISTOL_MAGAZINE, RIFLE_MAGAZINE, MACHINE_GUN_BOX);
    }

    /** effects: returns the parts' item ids: the ones this mod makes, so not steel */
    public static List<String> parts() {
        return PARTS;
    }

    /** effects: returns the item ids of what is fitted to a weapon at the bench, in tab order (D-0029) */
    public static List<String> fittings() {
        return List.of(LOCK_ON_CHIP);
    }

    /** effects: returns the item ids of the stations this mod makes: the weapons workbench (D-0029) */
    public static List<String> stations() {
        return List.of(WEAPONS_WORKBENCH);
    }

    /** effects: returns the recipe whose result is {@code itemId}, if this mod makes it */
    public static Optional<Blueprint> byResult(String itemId) {
        return ALL.stream().filter(b -> b.result().equals(itemId)).findFirst();
    }

    /**
     * effects: returns the recipe that makes {@code ingredient}, ours or a
     * known external one: by result for an item, and for a tag, the recipe
     * of the part that fills it; empty if nothing known makes it
     */
    public static Optional<Blueprint> maker(String ingredient) {
        return makerIn(KNOWN, ingredient);
    }

    /**
     * effects: returns the raw materials one craft of {@code itemId} costs,
     * tallied through every part it is made of -- a part is made in whole
     * crafts, so three barrels wanted from a recipe yielding one cost three
     * crafts' worth; anything no recipe here makes is raw<br>
     * throws: {@link IllegalStateException} if a recipe is, through its
     * parts, an ingredient of itself
     */
    public static Map<String, Integer> rawMaterials(String itemId) {
        return rawMaterialsOf(KNOWN, itemId);
    }

    /** effects: as {@link #rawMaterials}, over {@code catalogue} instead of this mod's recipes */
    public static Map<String, Integer> rawMaterialsOf(List<Blueprint> catalogue, String itemId) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        tally(catalogue, itemId, 1, raw, new ArrayList<>());
        return raw;
    }

    private static Optional<Blueprint> makerIn(List<Blueprint> catalogue, String ingredient) {
        String made = FILLS.getOrDefault(ingredient, ingredient);
        return catalogue.stream().filter(b -> b.result().equals(made)).findFirst();
    }

    private static void tally(List<Blueprint> catalogue, String itemId, int wanted, Map<String, Integer> raw, List<String> trail) {
        Optional<Blueprint> made = makerIn(catalogue, itemId);
        if (made.isEmpty()) {
            raw.merge(itemId, wanted, Integer::sum);
            return;
        }
        if (trail.contains(itemId)) {
            throw new IllegalStateException("a recipe is an ingredient of itself: " + trail + " -> " + itemId);
        }
        trail.add(itemId);
        Blueprint recipe = made.get();
        int crafts = Math.ceilDiv(wanted, recipe.count());
        for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
            tally(catalogue, ingredient.getKey(), ingredient.getValue() * crafts, raw, trail);
        }
        trail.remove(trail.size() - 1);
    }

    /**
     * effects: returns how many of each part one craft of {@code itemId}
     * takes, through every part it is made of; raw materials are not parts
     */
    public static Map<String, Integer> partsOf(String itemId) {
        Map<String, Integer> parts = new LinkedHashMap<>();
        Optional<Blueprint> made = byResult(itemId);
        if (made.isEmpty()) {
            return parts;
        }
        for (Map.Entry<String, Integer> ingredient : made.get().ingredients().entrySet()) {
            Optional<Blueprint> part = maker(ingredient.getKey());
            if (part.isPresent()) {
                String partId = part.get().result();
                parts.merge(partId, ingredient.getValue(), Integer::sum);
                for (Map.Entry<String, Integer> inner : partsOf(partId).entrySet()) {
                    parts.merge(inner.getKey(), inner.getValue() * ingredient.getValue(), Integer::sum);
                }
            }
        }
        return parts;
    }

    /**
     * effects: returns the iron one craft of {@code itemId} costs, counting
     * steel as the iron it was made from -- the one number the guns are
     * ordered by
     */
    public static int ironEquivalent(String itemId) {
        Map<String, Integer> raw = rawMaterials(itemId);
        return raw.getOrDefault(IRON, 0);
    }

    /**
     * effects: returns a chain of results in which the last is an
     * ingredient of the first, if the catalogue has one; empty otherwise
     */
    public static Optional<List<String>> cycle() {
        for (Blueprint recipe : ALL) {
            List<String> trail = new ArrayList<>();
            if (findCycle(recipe.result(), trail, new HashSet<>())) {
                return Optional.of(trail);
            }
        }
        return Optional.empty();
    }

    private static boolean findCycle(String itemId, List<String> trail, Set<String> done) {
        if (trail.contains(itemId)) {
            trail.add(itemId);
            return true;
        }
        Optional<Blueprint> made = maker(itemId);
        if (made.isEmpty() || done.contains(itemId)) {
            return false;
        }
        trail.add(itemId);
        for (String ingredient : made.get().ingredients().keySet()) {
            if (findCycle(ingredient, trail, done)) {
                return true;
            }
        }
        trail.remove(trail.size() - 1);
        done.add(itemId);
        return false;
    }
}
