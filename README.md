# Ranged Weapons Mod

Guns for players, on the [Ranged Weapons](../minecraft-ranged-weapons)
protocol. NeoForge 1.21.1.

Five guns: a pistol, a shotgun, a rifle, a scoped rifle and a machine gun.
The mod is the part of a gun that is about the player holding it: a trigger
you pull or hold, a fire clock that is not the item cooldown, a reload from
your inventory, recoil that settles instead of fighting your mouse, sights to
aim down, and an ammo counter beside the hotbar. What each gun *is*
-- how much it holds, how fast it fires, how hard it hits -- is data in the
protocol's `rangedweapons:weapons` map, so a datapack can retune it, and any
mob that arms itself through the protocol can carry it. [Armed
Pillagers](../minecraft-armed-pillagers) does, subject to its own class policy
(automatic weapons are denied by default, so no pillager is issued this one).

## What it does

**Pull, or hold.** With a gun in the main hand, the use key first offers the
click to whatever is under the crosshair, with vanilla's own calls and reach,
so a door, a chest or a villager gets it. If nothing takes it, the client
cancels vanilla's click and tells the server the trigger is down. The
machine gun, the one `automatic` gun, then fires a round every
`fire_rate_ticks` until the client reports the key up; every other gun fires
once per pull, and `fire_rate_ticks` is only how soon the next pull can fire:
a pistol's quick reset, a shotgun's pump, a bolt worked. Vanilla's own item use never runs: it
would drop the hand out of view on every press (its re-equip animation), the
item is never "in use" and never on cooldown, so there is no hotbar strobe,
no 0.2x movement slowdown, and the cadence is the server's, not the client's
frame rate. Firing breaks a sprint, the way drawing a bow does. A gun in the
off hand does nothing.

**Reload.** `R` reloads; so does pulling the trigger on an empty magazine when
you carry ammunition the gun takes -- any round of its family, from any mod,
in inventory order; the tooltip names the family (with none, the gun clicks
once per pull). A
reload takes the profile's full reload time, the gun is unusable meanwhile, and
on completion it loads as many rounds as the magazine has room for and your
inventory can supply. Creative has unlimited ammunition, as it has unlimited
arrows: every gun fires without spending a round, never needs a reload, and
the counter shows an infinity sign. The reload is a
property of the gun (a data component), so a gun dropped mid-reload is still
mid-reload when picked up; if the clock it was started on is gone (another
world), the reload is abandoned rather than finished early.

**Recoil.** Each shot kicks the camera up by the gun's `recoil_pitch` and a
random side by `recoil_yaw`, and the kick recovers exponentially at `recovery`
per tick. Kicks add and settle: firing full auto lifts the aim to a plateau
and lets it back down, with no snap between shots. The player's actual look
angles are never written -- the offset is added to the camera as each frame is
computed -- so nothing fights the mouse and nothing drifts. The gun in hand
is pushed back toward the shoulder with the same offset, lifted enough to
hold its place on screen as it comes closer, and tilted muzzle-up about its
grip; how much is four client config knobs. Both are scaled by the client
config, down to zero.

A spent round is the same gun: vanilla would otherwise play its re-equip
animation, the hand dropping out of view and coming back, on every change to
the held item's data, which at this cadence is a hand that never stops
dropping. Only a different gun, a different slot, or the magazine going out
or in re-equips; that last is the reload's dip.

**Two hands.** In third person the gun is carried in both hands, the way a
crossbow is. In first person, if Hold My Items is installed, the guns write
themselves into its exclusion list on the first client tick so they are held
as their models say rather than in that mod's one-handed pose; see the
protocol's README.

**Blocks and bullets** are the protocol's: a round throws debris and sparks
off what it hits, and a player's rounds shatter glass at once, wear stone
down over several, and never mark obsidian. Whose rounds may break what is
the protocol's config.

**Aim down the sights.** Hold the aim key (Left Alt by default; rebindable)
and spread tightens by the gun's `aiming` factor and the view leans in a
little. Through the scoped rifle it is a scope: the view narrows four times,
the scope's mask covers all but a circle, the crosshair stays, and the gun is
out of the way. Both zooms are client config.

**The counter.** Rounds over capacity beside the hotbar while a gun is held,
red at a fifth of a magazine, with a progress bar during a reload. Hidden with
the rest of the HUD (`F1`).

**Where a round goes.** It leaves the muzzle -- forward, to the main-hand
side, a little below the eye; on the line of sight when aiming, or a scope
would show it leaving from the corner of the view -- aimed at the point the
crosshair is on: the
first block in the eye's line of sight, or far along the look if there is
none. A round fired parallel to the look from a muzzle below the eye would
land below the crosshair at every distance, and did until this was fixed.

**The tracer and the noise** are the protocol's: nearby players hear
the gun's shot sound, players between sixteen and sixty-four blocks away hear
its far report, and a puff of smoke marks the muzzle for onlookers.

## The lineup

| | Pistol | Shotgun | Rifle | Scoped rifle | Machine gun |
|---|---|---|---|---|---|
| Class | sidearm | shotgun | rifle | rifle | automatic |
| Fire | one per pull, 5 ticks | one per pull, 13 ticks (the pump) | one per pull, 6 ticks | one per pull, 10 ticks (the bolt) | held, every 3 ticks |
| Magazine | 12 small rounds | 6 shells | 10 medium rounds | 5 medium rounds | 50 medium rounds |
| Damage | 4 | 6 pellets of 3.5 | 9 | 14 | 5.5 |
| Falls off | to half past 28 blocks, from 10 | to a fifth past 18 blocks, from 5 | no | no | no |
| Spread | 0.03 | 0.11 | 0.012 | 0.006 | 0.05 |
| Reach | 20 | 12 | 40 | 64 | 28 |
| Reload | 24 ticks | 48 ticks | 30 ticks | 30 ticks | 50 ticks |
| Kick | 1.6 up | 4.5 up | 2.2 up | 3.0 up | 0.55 up |
| Aiming spread | 0.5 | 0.7 | 0.35 | 0.15 | 0.35 |
| Held | one hand | two | two | two | two |
| Wear | 800 shots | 500 | 700 | 600 | 1200 |

Reach is the profile's engagement range, what a mob armed with it closes to;
rounds fly farther. Kick is degrees of camera per shot, before the in-hand
exaggeration. Every number is data: the profile in
`data/rangedweapons/data_maps/item/weapons.json`, the feel in
`data/rangedweaponsmod/data_maps/item/handling.json` (`recoil_pitch`,
`recoil_yaw`, `recovery`, and the `spread` stance factors, `aiming` among
them). A datapack that ships either path with the same item key overrides it.
A gun with no `handling` entry gets a default for its profile's class.

Ammunition: small rounds (an iron nugget over gunpowder over an iron nugget
makes ten), medium rounds (an iron nugget over gunpowder over a copper ingot
makes eight) and shells (paper over gunpowder over an iron nugget makes
four). Each is tagged into the protocol's family of that name, and each gun
takes its family, so another mod's rounds of the same family load too.

### Crafting

A gun is assembled from parts made separately. Materials are taken by their
common tags, so any mod's iron, coal, planks or glass panes serve.

| Part | Recipe |
|---|---|
| **Steel Ingot** ×3 | three iron ingots and a coal, anywhere in the grid: iron with a little carbon |
| **Lower Receiver** | three steel across the top, a redstone under the middle: the body over the trigger group |
| **Upper Receiver** | four steel in a square |
| **Gun Barrel** | three iron ingots in a row |
| **Heavy Barrel** | a barrel and two steel, anywhere in the grid |
| **Gun Stock** | two planks over a plank and a stick |
| **Shotgun Pump** | a plank, a stick, a plank in a row |
| **Rifle Scope** | a glass pane, an iron ingot, a glass pane in a row |

| Gun | Assembly (as laid out in the grid) |
|---|---|
| **Pistol** | lower receiver, barrel -- in a row |
| **Shotgun** | stock, lower receiver, barrel across; the pump under the barrel |
| **Rifle** | the upper receiver over the lower; stock to its left, barrel to its right |
| **Scoped Rifle** | a scope over a rifle |
| **Machine Gun** | as the rifle, with a heavy barrel |

By iron, counting steel as the iron it came from: pistol 6, shotgun 6 and
some wood, rifle 10, scoped rifle 11 and two panes, machine gun 12. Every
recipe unlocks in the recipe book the moment a player holds one of its
ingredients, and every ingredient and part is in the Ingredients tab, the
guns and ammunition in Combat.

The recipes are not written by hand. `Blueprints` in the `domain` source
set is the one description of the tree; `./gradlew runData` writes the
recipe files and their unlocks from it into `src/generated/resources`
(committed); the plain-JUnit tests hold the tree to its rules -- the pistol
cheapest, every long gun with one stock and the pistol none, one lower
receiver and one barrel in every gun, the scoped rifle exactly a rifle and
a scope, no cycles; and a gametest asks the running server for every grid
and expects exactly our recipe back. `devtools/recipes/collisions.py`
checks the part and ammunition recipes against every recipe in a pack's
jars for a grid two recipes would both answer to (the game would pick one
at random), and is run against the pack this mod ships in.

## Adding a gun

1. Register an item with `GunItem` in `ModItems` (durability is the shot count).
2. Give it a profile in `weapons.json` -- its `ammo_family` and its native
   `ammo` round, which you tag into the family -- and, if the class default
   is wrong for it, a `handling.json` entry.
3. A model, a texture, a shot sound and a far report: `devtools/art/build.py`
   is the generator for the ones shipped, run with `uv run devtools/art/build.py`.
4. Photograph it: `./gradlew runPhotoBooth` (below) and look at the pictures.
5. A recipe: a `Blueprint` in `Blueprints` (with a unit test pinning where it
   sits in the tree), then `./gradlew runData`; and lang entries.

No gunnery code changes. The trigger, the reload, the recoil and the counter
read everything from the profile and the handling.

## Client config

`config/rangedweaponsmod-client.toml`:

| Key | Default | Meaning |
|---|---|---|
| `recoil.recoilScale` | 1.0 | multiplies the camera kick of every shot; 0 turns it off |
| `recoil.modelKickScale` | 1.0 | multiplies how much the gun in hand jumps, separately |
| `recoil.modelBackPerDegree` | 0.07 | blocks the gun in hand is pushed back toward the shoulder per degree of camera kick |
| `recoil.modelRisePerDegree` | 0.06 | blocks it rises per degree; about 0.7 of the push holds it level on screen, more lifts it |
| `recoil.modelPitchPerDegree` | 3.0 | degrees it tilts about its grip per degree of kick; positive is muzzle up |
| `recoil.modelYawPerDegree` | 1.0 | degrees it swings sideways per degree of sideways kick |
| `aim.zoom` | 1.25 | how much the view narrows aiming a gun without a scope |
| `aim.scopeZoom` | 4.0 | how much it narrows through a scope |
| `hud.enabled` | true | the ammo counter and reload bar |

## Building

```
./gradlew build
```

produces `build/libs/rangedweaponsmod-<version>.jar` with the protocol nested
inside (Jar-in-Jar), so it installs alone. The protocol is resolved from Maven
Local: build [minecraft-ranged-weapons](../minecraft-ranged-weapons) with
`./gradlew publishToMavenLocal` first.

## Testing

Four tiers. The first two run under `./gradlew check` (and so `build`); the
other two are for eyes and hands.

- `./gradlew test` -- plain JUnit against the `domain` source set, the pure
  layer: the fire clock, the trigger's rule table, the reload plan, the recoil
  model, stance spread, and the recipe tree. That source set is compiled against nothing but the
  JDK, so a `net.minecraft` import there is a compile error. Partitions are
  written at the top of each test class.
- `./gradlew runGameTestServer` -- gametests on a real headless server. The
  tests are a mod of their own (`src/gametest`) so they exercise the mod from
  outside: the data resolves to a weapon with the right capacity; a held
  trigger fires at the profile's rate and a release stops it; an empty pull
  starts a reload only with ammunition in the inventory; a reload takes its
  full time, consumes exactly what it loads, and blocks fire meanwhile; a
  reload takes any round of the gun's family and none of another; a round
  goes where the crosshair points and not parallel to it; the
  gun's use is the press, consumed without a swing, a repeat is not a new
  pull, and the off hand is not operated; creative fires an empty gun for
  free; the real `PlayerTickEvent` path drives a placed player; every gun
  resolves with its own numbers; a semi-automatic fires once however long
  the trigger is held and again on the next pull; the shotgun throws six
  pellets for one shell; aiming is remembered; and an aimed round leaves
  from the line of sight where a hip shot leaves from beside it. **The server's exit
  code is not the assertion** -- it is zero when no test ran -- so the task
  reads the framework's "All N required tests passed" line from
  `run/logs/latest.log` and fails without it. `-PskipGameTests` drops it from
  `check` for fast iteration on the pure tests.
- `./gradlew runPhotoBooth` -- a dev client that quick-plays the gametest
  world, poses the gun and takes pictures into `run/screenshots/booth-*.png`:
  first person, each term of the in-hand kick alone under a big kick, the
  shipped kick at a machine gun's plateau, a real five-round burst in
  survival fired by pressing the use key itself into a stone block placed
  three blocks ahead (this is the frame that shows what a player sees --
  the hand, the impact, the cracks, the sparks; a synthetic kick, or even
  the trigger message sent directly, does not), third person from behind and in
  front, every other gun in first and third person, the scoped rifle aimed,
  the inventory, and each calibration item the gametest mod registers (an
  axes model under candidate display transforms, carried two-handed like the
  long guns, or one-handed with `-PboothPose=one` for a pistol's frame). The
  booth runs in its own game directory, `run/booth`; jars dropped into its
  `mods/` load with it, which is how the pack's Not Enough Animations is
  put in the picture, since a photo of a pose without the mods that re-pose
  the player answers the wrong question. It quits when done. Leave it alone while it runs: any input
  becomes part of the photos. This is how the display transforms and the
  kick's signs were found; none of them are what one would derive.
- A feel test in `./gradlew runClient`: `/give @s rangedweaponsmod:machine_gun`
  and a stack of rounds. Cadence, recoil and reload are judged by hand; the
  numbers to tune are in the data files above.
