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
the gun itself as data. Six guns: pistol, revolver, shotgun, rifle, scoped rifle,
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
generation (`D-0008`); a one-handed gun is held out by a pose added to the
game's own enum (`D-0009`) whose arm is the crossbow's trigger arm number
for number, the one pose animation packs recognise without vanilla's
use state (`D-0015`); rounds of one family may differ and the
gun loads one kind at a time (`D-0010`); steel is Metals and Materials',
installed separately, with our old id aliased to its ingot (`D-0018`, superseding
only the packaging choice in `D-0014`).

## How it is verified

`./gradlew check`: 124 plain-JUnit tests against `domain`, and fifty gametests
on a headless server. `devtools/recipes/collisions.py` checks the recipes
against the pack's jars. `./gradlew runPhotoBooth` photographs the art for
review and judges the trigger arm's angles off the rendered model at every
gun's third-person frames, with whatever animation mods and player packs
are dropped into `run/booth` (the pack's Not Enough Animations; Entity
Model Features with Fresh Animations: Player Extension, which is what
Rusty's own client runs). The feel -- cadence, recoil, reload -- is judged
by hand in a client and on the shared server.

## Next

2.5.0 was published and deployed in pack 1.42.0 on 2026-09-18, with the
revolver (D-0019), vertical shotgun-pump recipe (D-0020), and separate-materials
packaging (D-0018). The published jar matches the installed server jar and
Mod Hub reports parity. Rusty updates Prism through the published pack. Its client gate uses one observer and a server actor, with
Rusty's shader and player-animation setup; it does not claim two independently
connected interactive clients.

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

- `minecraft-metals-and-materials` 1.0.0 or later within 1.x (installed separately;
  built to Maven Local first): steel, since 2.3.0.
- `minecraft-ranged-weapons` 1.7.0 or later within 1.x (nested Jar-in-Jar;
  built to Maven Local first). Needs 1.5 for damage falloff; 1.4 for
  ammunition families; 1.3 for
  the block impact and the Hold My Items hook; 1.2 for the synced data map, the muzzle origin in
  `ShotReport.play` and the non-saving fallback bullet.


2026-09-16 presentation polish: D-0017 repairs long magazine labels with bounded styled text and full hover help,
adds bevels to the original part/magazine icons, and verifies decoded Vorbis headroom.
The real Fill-button path loads a long-named addon round in the booth. All eleven
clips pass the decoded .98 peak ceiling. Preferred bundled steel is 1.0.1. Release is held.

## License

AGPL-3.0-or-later. The art is original and generated by code in this repo;
nothing was taken from the mod it replaces.


## Release approval - 2026-09-16

Rusty approved the final review, completing their earlier conditional release go.
Version 2.4.0 was published on 2026-09-16 and deployed in pack 1.35.1
after the clean release build and asset verification. The deployed server matched
the published pack and ran at 20 TPS. This supersedes the earlier release holds
and pending presentation/listening review recorded above.

## Shared materials dependency — 2026-09-18, unreleased

Version 2.4.1 implements [D-0018](decisions/D-0018.md): Metals and Materials
is required and installed separately, with no embedded copy. Items, recipes,
steel aliases and gameplay are unchanged. Unit/server checks, recursive jar/payload audits and complete-pack startup passed; release is held.

Validation: see Metals and Materials `devtools/verification/separate-dependency.md`;
all six packaging builds and the complete-pack client/server check passed.


## Revolver — 2026-09-18, unreleased

Version 2.5.0 adds [D-0019](decisions/D-0019.md): six medium rounds, 10 damage,
one shot per pull every 12 ticks, R loading loose rounds into its built-in
cylinder, and animated cylinder, hammer and reload crane. It includes the held
2.4.1 separate-materials packaging change. The earlier 2.4.0 release approval
does not authorize this version; no tag, push or pack update is authorized.

Validation and visual evidence are recorded in `devtools/verification/revolver.md`.


## Release authorization — 2026-09-18

Rusty requested the shotgun recipe fix with creative license, followed by release
of the revolver and fix together. This supersedes the 2.5.0 release holds above.
The pump now uses a vertical plank-stick-plank column (D-0020); ingredient cost,
the assembled shotgun recipe and existing items are unchanged.


## Verified 2.5.0 deployment — 2026-09-18

Release commit `993e831b4d3c2185ca6114153eb77f065b471a36` is tagged `v2.5.0`.
The [public release](https://github.com/the-rusty-shackleford/minecraft-ranged-weapons-mod/releases/tag/v2.5.0)
is included in pack **1.42.0**, replacing 2.4.0 on both sides. The published
download matches the clean, tested jar; SHA-1 `b3f3e3f6b7c4a60a52e0e63be36601d658fe2ad0`.

The server was empty immediately before restart. Fresh startup explicitly
loaded 2.5.0 and reached ready at 23:40:15 UTC. The installed hash matches,
the old jar is absent, and Mod Hub reports no differences. Overall performance
sampled at 20 TPS. World selection, seed, operators and Distant Horizons
configuration were preserved. The same 36 third-party startup errors remain,
with none naming Ranged Weapons Mod. The public player download serves
`minecraft-client-v1.42.0.mrpack` with the verified staged hash.

Rusty's personal Prism installation was not changed. The other repositories'
separate-materials packaging updates still await their own release go. This
record supersedes the historical 2.4.1/2.5.0 release holds above. Issue #1 is closed.
