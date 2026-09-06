# Ranged Weapons Mod

Guns for players, on the [Ranged Weapons](../minecraft-ranged-weapons)
protocol. NeoForge 1.21.1.

One gun so far, a machine gun. The mod is the part of a gun that is about the
player holding it: a trigger you hold, a fire clock that is not the item
cooldown, a reload from your inventory, recoil that settles instead of
fighting your mouse, and an ammo counter beside the hotbar. What the gun *is*
-- how much it holds, how fast it fires, how hard it hits -- is data in the
protocol's `rangedweapons:weapons` map, so a datapack can retune it, and any
mob that arms itself through the protocol can carry it. [Armed
Pillagers](../minecraft-armed-pillagers) does, subject to its own class policy
(automatic weapons are denied by default, so no pillager is issued this one).

## What it does

**Hold to fire.** With a gun in the main hand, the use key first offers the
click to whatever is under the crosshair, with vanilla's own calls and reach,
so a door, a chest or a villager gets it. If nothing takes it, the client
cancels vanilla's click and tells the server the trigger is down, and the
server runs a per-player clock that fires a round every `fire_rate_ticks`
until the client reports the key up. Vanilla's own item use never runs: it
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

**The counter.** Rounds over capacity beside the hotbar while a gun is held,
red at a fifth of a magazine, with a progress bar during a reload. Hidden with
the rest of the HUD (`F1`).

**The tracer and the noise** are the protocol's: the shot starts at the muzzle
(forward, to the main-hand side, a little below the eye), nearby players hear
the gun's shot sound, players between sixteen and sixty-four blocks away hear
its far report, and a puff of smoke marks the muzzle for onlookers.

## The machine gun

| | |
|---|---|
| Magazine | 50 rounds; takes any `#rangedweapons:ammo/medium` round, its own `rangedweaponsmod:round` among them |
| Rate | a round every 3 ticks (about 6.7 per second) |
| Damage | 5.5 per round, 1 round per shot |
| Spread | 0.05, times 0.7 crouching, 1.4 moving, 2.0 sprinting, 1.8 airborne |
| Reach | 28 blocks engagement range; rounds fly 4 blocks a tick for 5 seconds |
| Reload | 50 ticks (2.5 s) |
| Kick | 0.55 degrees up, 0.25 to a side, 35% recovered per tick |
| Wear | 1200 shots of durability; repairs like anything with durability |

Crafted from an iron block, three iron ingots, redstone and two sticks; a
stack of eight rounds from an iron nugget over gunpowder over a copper ingot.
Both are in the Combat tab.

Every number above is data. The profile is
`data/rangedweapons/data_maps/item/weapons.json`; the feel is
`data/rangedweaponsmod/data_maps/item/handling.json`
(`recoil_pitch`, `recoil_yaw`, `recovery`, and the `spread` stance factors).
A datapack that ships either path with the same item key overrides it. A gun
with no `handling` entry gets a default for its profile's class.

## Adding a gun

1. Register an item with `GunItem` in `ModItems` (durability is the shot count).
2. Give it a profile in `weapons.json` -- its `ammo_family` and its native
   `ammo` round, which you tag into the family -- and, if the class default
   is wrong for it, a `handling.json` entry.
3. A model, a texture, a shot sound and a far report: `devtools/art/build.py`
   is the generator for the ones shipped, run with `uv run devtools/art/build.py`.
4. Photograph it: `./gradlew runPhotoBooth` (below) and look at the pictures.
5. A recipe and lang entries.

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
  model, stance spread. That source set is compiled against nothing but the
  JDK, so a `net.minecraft` import there is a compile error. Partitions are
  written at the top of each test class.
- `./gradlew runGameTestServer` -- gametests on a real headless server. The
  tests are a mod of their own (`src/gametest`) so they exercise the mod from
  outside: the data resolves to a weapon with the right capacity; a held
  trigger fires at the profile's rate and a release stops it; an empty pull
  starts a reload only with ammunition in the inventory; a reload takes its
  full time, consumes exactly what it loads, and blocks fire meanwhile; a
  reload takes any round of the gun's family and none of another; the
  gun's use is the press, consumed without a swing, a repeat is not a new
  pull, and the off hand is not operated; creative fires an empty gun for
  free; and the real `PlayerTickEvent` path drives a placed player. **The server's exit
  code is not the assertion** -- it is zero when no test ran -- so the task
  reads the framework's "All N required tests passed" line from
  `run/logs/latest.log` and fails without it. `-PskipGameTests` drops it from
  `check` for fast iteration on the pure tests.
- `./gradlew runPhotoBooth` -- a dev client that quick-plays the gametest
  world, poses the gun and takes pictures into `run/screenshots/booth-*.png`:
  first person, each term of the in-hand kick alone under a big kick, the
  shipped kick at a machine gun's plateau, a real five-round burst in
  survival fired by pressing the use key itself (this is the frame that
  shows what a player sees; a synthetic kick, or even the trigger message
  sent directly, does not), third person from behind and in
  front, the inventory, and each calibration item the gametest mod registers
  (an axes model under candidate display transforms, carried two-handed like
  the gun). It quits when done. Leave it alone while it runs: any input
  becomes part of the photos. This is how the display transforms and the
  kick's signs were found; none of them are what one would derive.
- A feel test in `./gradlew runClient`: `/give @s rangedweaponsmod:machine_gun`
  and a stack of rounds. Cadence, recoil and reload are judged by hand; the
  numbers to tune are in the data files above.
