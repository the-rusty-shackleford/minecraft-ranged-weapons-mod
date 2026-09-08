---
title: Ranged Weapons Mod — project
type: overview
layer: store
tags: [overview]
---

# Ranged Weapons Mod

## What this is

A NeoForge 1.21.1 mod: guns for players, built on the Ranged Weapons
protocol. The player half of the contract the protocol already fulfils for
mobs -- a trigger, a fire clock, a reload, recoil, sights, a counter -- with
the gun itself as data. Five guns: pistol, shotgun, rifle, scoped rifle,
machine gun. AGPL-3.0-or-later,
authored by Rusty Shackleford, to the MIT 6.031 bar: specs, rep invariants, a
pure layer with tests, a gametest gate on a real server.

## Why it exists

The shared server's pack carried F708's Another Gun Mod, whose machine gun
felt stuttery. Read from its decompiled jar (for diagnosis only; nothing was
copied), the cause was structural: recoil applied by rewriting the player's
real look angles every frame on a two-tick curve that each shot restarts, fire
rate through the per-item cooldown, hold-to-fire through vanilla's use path
and its movement slowdown. All-rights-reserved with no source, so no fix could
be carried upstream. Rusty's call was to build our own and, as the guns are
protocol weapons, get Armed Pillagers' compatibility with them for free.

## Shape

Three source sets, one direction of dependency:

- `domain` -- the decisions, over plain numbers: the fire clock, the trigger's
  rule table (`Trigger.tick(Inputs) -> Action`), the reload plan, the recoil
  model (an immutable record with exponential recovery), stance spread, and
  the recipe tree (`Blueprints`, the one description of every recipe).
  Compiled against the JDK only.
- `main` -- adapters: `PlayerGunnery` reads a player into the trigger each
  server tick and applies its action to the gun, the inventory and the level;
  three payloads; the client's input, recoil camera, HUD and config; the
  items, the creative tab, sounds, reload component, gunnery attachment and handling data map;
  `ModRecipes` writes the blueprints out as recipe files and unlocks under
  the loader's data generation.
- `gametest` -- a mod of its own (`rangedweaponsmod_gametest`), the pattern
  from Armed Pillagers' D-0003, which here also carries the photo booth and
  its calibration items.

Decisions: the protocol's fallback tier operates our guns (`D-0001`); the
trigger is the attack key, cancelled outright so nothing is mined or struck,
and the use key is vanilla's (`D-0011`, superseding `D-0006`); recoil is a camera
offset that recovers, never the player's rotation (`D-0003`); the art is
generated and its display transforms and the kick's signs were calibrated by
photograph (`D-0004`); a gun's class decides whether it fires once per
pull or for as long as the trigger is held (`D-0007`); guns are assembled
from parts described once, in the pure layer, and written out by data
generation (`D-0008`); a one-handed gun is held out level by a pose added
to the game's own enum (`D-0009`); rounds of one family may differ and the
gun loads one kind at a time (`D-0010`).

## How it is verified

`./gradlew check`: 113 plain-JUnit tests against `domain`, and thirty-eight gametests
on a headless server. `devtools/recipes/collisions.py` checks the recipes
against the pack's jars. `./gradlew runPhotoBooth` photographs the art for
review. The feel -- cadence, recoil, reload -- is judged by hand in a client
and on the shared server.

## Next

Decided by Rusty on 2026-09-06, in this order:

1. Done (protocol 1.4, this mod's round is medium): ammo families.
2. Done (protocol 1.5 for the shotgun's falloff): the lineup -- pistol
   (small), shotgun (shell), rifle and scoped rifle (medium), each with
   everything the machine gun got, the scoped rifle with a scope, not a
   spyglass. What remains is Rusty's feel test of each.
4. Done (1.3.0): slugs for the shotgun, knockback on every gun, Another
   Gun Mod's damage numbers, a tighter shotgun; every pellet counts now
   that the protocol's bullet bypasses the hurt cooldown (protocol 1.6);
   the shots re-synthesized from a blast pulse, spray, thump and tail,
   no tones.
5. Done (1.4.0, asked 2026-09-07): fire on left click, the use key left to
   vanilla for doors, chests and villagers, nothing mined with a gun in
   hand; the action sounds (reloads, empty click, pump and bolt) rebuilt
   as modal impacts and stick-slip slides, no tones.
6. Done (1.5.0, asked 2026-09-07 -- "still kinda fruity"): every sound
   replaced by a cut of a CC0 field recording of a real firearm
   (`devtools/art/sounds/`, D-0012); synthesis retired.
7. Done (2.0.0, decided 2026-09-07): magazines -- D-0013. Every gun but
   the shotgun is fed from a detachable magazine (pistol 15, rifle 30
   shared with the scoped rifle, machine-gun box 75; capacity the item's,
   so extended and double-stack variants are further items), an ordered
   load of runs filled in a screen on right-click with a Fill button,
   labelled by anvil, coloured by dye; R changes for the first loaded
   magazine carried and keeps the old one; Shift+R walks the carried
   magazines (on the shotgun: unloads the tube and loads the next kind);
   the HUD shows the next round's icon and the magazine's name in its
   colour; loose rounds are adopted on first sight. 2.0.1: a change takes
   the gun's `magazine_change_ticks` (handling), a quarter more per
   doubling of the standard capacity, never a time per round. 2.2.0:
   magazines have tags (`magazines/pistol`, `/rifle`, `/machine_gun`) and
   a gun's handling names the tag it takes. Extended
   magazines, when they come: another `MagazineItem` with its capacity, a
   recipe, a texture; nothing else changes.
3. Done (1.2.0): crafting as assembly -- receivers of steel, a barrel, a
   stock, a scope, then the gun in a shape that follows its silhouette;
   the pistol cheapest, the machine gun the most involved.

## Depends on

- `minecraft-ranged-weapons` 1.5.0 or later within 1.x (nested Jar-in-Jar;
  built to Maven Local first). Needs 1.5 for damage falloff; 1.4 for
  ammunition families; 1.3 for
  the block impact and the Hold My Items hook; 1.2 for the synced data map, the muzzle origin in
  `ShotReport.play` and the non-saving fallback bullet.

## License

AGPL-3.0-or-later. The art is original and generated by code in this repo;
nothing was taken from the mod it replaces.
