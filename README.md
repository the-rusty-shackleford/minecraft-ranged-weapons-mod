# Ranged Weapons Mod

Guns for players, on the [Ranged Weapons](../minecraft-ranged-weapons)
protocol. NeoForge 1.21.1.

Six guns: a pistol, a revolver, a shotgun, a rifle, a scoped rifle and a machine gun.
The mod is the part of a gun that is about the player holding it: a trigger
you pull or hold, a fire clock that is not the item cooldown, a reload from
your inventory, recoil that settles instead of fighting your mouse, sights to
aim down, and an ammo counter in the lower right. What each gun *is*
-- how much it holds, how fast it fires, how hard it hits -- is data in the
protocol's `rangedweapons:weapons` map, so a datapack can retune it, and any
mob that arms itself through the protocol can carry it. [Armed
Pillagers](../minecraft-armed-pillagers) does, subject to its own class policy
(automatic weapons are denied by default, so no pillager is issued this one).

The ammo panel sits above Quick Slot and Backpacks+' gear row beside it, and below
that row where it lifts above the status icons on the smallest screens. While a gear
gesture is open in the bottom-right corner, Backpacks+ 0.3.0 or later says so and the
panel rises above the gesture's cells and text; with an older Backpacks+ present the
loader refuses to load this mod rather than let the two overlap. The panel stays on the
right at different GUI scales, shortens long magazine names with an ellipsis, and keeps
the count and reload bar inside the screen.

## What it does

**Pull, or hold.** The trigger is the attack key -- left click. With a gun
in the main hand the client cancels vanilla's attack outright, so there is
no swing, no melee hit and no mining (a gun is not a pickaxe: nothing is
mined while one is in hand), and tells the server the trigger is down. The
machine gun, the one `automatic` gun, then fires a round every
`fire_rate_ticks` until the client reports the key up; every other gun fires
once per pull, and `fire_rate_ticks` is only how soon the next pull can fire:
a pistol's quick reset, a shotgun's pump, a bolt worked. The use key is left
to vanilla: right-clicking a door, a chest or a villager with a gun in hand
does what it does with anything else in hand, and the gun's own use passes.
Vanilla's item-use path never runs for a gun: it would drop the hand out of
view on every press (its re-equip animation), the item is never "in use" and
never on cooldown, so there is no hotbar strobe, no 0.2x movement slowdown,
and the cadence is the server's, not the client's frame rate. Firing breaks
a sprint, the way drawing a bow does. A gun in the off hand does nothing.

**Magazines.** The pistol, rifles and machine gun take detachable magazines:
a pistol magazine (15 small rounds), a rifle magazine (30 medium, the rifle
and the scoped rifle share it) or a machine gun box (75 medium), each two
steel. Those guns hold nothing without one. A magazine is an ordered load: the
rounds go in as runs of one kind, the first run fires first, and a mixed
magazine -- twelve shells' worth of one kind then three of another -- is the
point, not an accident. Which magazines a gun takes is a tag, not the
rounds: the pistol takes `#rangedweaponsmod:magazines/pistol`, the rifles
`magazines/rifle`, the machine gun `magazines/machine_gun` (its handling
names the tag), so a rifle magazine never goes into the machine gun though
both hold medium rounds, and an extended magazine joins its gun's tag. Right-click a magazine to fill it: a row of slots
for the runs, first to fire on the left, that take the magazine's family and
nothing else, and a *Fill* button that takes the accepted rounds from your
inventory in inventory order, the first kind first. Name one in an anvil
and dye it in a crafting grid, and it says so in the HUD. Capacity is the
magazine item's, so an extended or a double-stack magazine is another item
that every gun of the family takes. Guns saved before magazines existed
have their loose rounds adopted into a magazine on first sight. Magazines
stack, eight to a slot, by the game's own rule for stacking: the same item
with the same load, name and dye. Eight full pistol magazines are one slot;
a half-spent one sits alone; a magazine fired empty stacks with new ones.
The screen fills one magazine at a time: right-click a stack and one stays
in your hand while the rest go onto a like stack or into a free slot first,
and with no room for them the screen does not open and says so.

**Reload.** `R` changes magazines: the one in the gun comes out, with what
it still holds, and the first loaded magazine the gun takes in your
inventory, hotbar first, goes in, trading places with it -- so a half-spent
magazine is kept, never lost. From a stack, one magazine goes in and the
rest keep their slot; the old one then goes onto a like stack or into a free
slot, and only with your inventory full does it land at your feet. Rounds stack to
99, the game's ceiling. Pulling the trigger on an empty gun does the
same when you carry a loaded magazine (with none, the gun clicks once per
pull; an empty magazine does not count). `Shift+R` swaps: the next loaded
magazine after the one the last swap took, in inventory order, wrapping
round, so pressing again walks through everything you carry. The revolver has a
built-in six-round cylinder: `R` tops it up from loose medium rounds in your
inventory, taking 48 ticks (2.4 seconds); `Shift+R` unloads it and selects the
next compatible kind. Its cylinder swings out on a hinged crane during reload. The shotgun's
tube is loaded directly, as it always was: `R` tops it up from the shells
or slugs you carry, and `Shift+R` unloads it back into your inventory and
loads the next kind you carry. A change or a swap takes the gun's magazine
change time from its handling (`magazine_change_ticks`: pistol 24, rifles
30, machine gun 50) whatever the magazine holds -- pulling a magazine and
seating another is the same pair of movements full or empty -- and a
quarter longer per doubling of the standard capacity for a larger magazine,
heavier and clumsier in the hand; an internal tube or cylinder takes the profile's
full reload time. The gun is unusable meanwhile, and the reload is a
property of the gun (a data component), so a gun dropped mid-reload is still mid-reload
when picked up; if the clock it was started on is gone (another world), the
reload is abandoned rather than finished early. Creative has unlimited
ammunition, as it has unlimited arrows: every gun fires without spending a
round, needs no magazine, never reloads, and the counter shows an infinity
sign.

**Two modes**, the server's choice (`feed` in the world's
`serverconfig/rangedweaponsmod-server.toml`). **Magazines**, the default, is
everything above. **Loose** makes every gun an internal store like the shotgun:
`R` and the empty trigger load loose rounds, one kind at a time, `Shift+R` changes
kind and hands the loaded ones back, capacity is the gun's own (15, 30, 30, 75), and
a load into the pistol, rifles or machine gun takes the gun's change time, the same
number as its magazine change. Rounds come from your pockets first, hotbar first,
then from the bag on your back and any Backpacks+ bag you carry, storage cells
only, never the mounts: the bag is the reserve, drawn on when the pockets are
empty, and rounds a swap hands back go to your pockets, never into a bag. A gun
holding a magazine hands it back on first sight, load and all, and no gun takes
one; magazines stay craftable and fillable so nothing is lost, and switching back
adopts loose rounds into a magazine as before. Every client receives the mode at
login, so the counter and the tooltips show the rule in force: change the file,
then restart the server.

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

**The counter.** Beside the hotbar while a gun is held: the round that fires
next as its own icon, rounds over capacity beside it, red at a fifth of a
magazine, and under them the magazine's name in its dye colour (or *No
magazine* in red; for the shotgun and revolver, the kind of round loaded), with a
progress bar during a reload. Hidden with the rest of the HUD (`F1`).

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

| | Pistol | Revolver | Shotgun | Rifle | Scoped rifle | Machine gun |
|---|---|---|---|---|---|---|
| Class | sidearm | sidearm | shotgun | rifle | rifle | automatic |
| Fire | one per pull, 5 ticks | one per pull, 12 ticks | one per pull, 15 ticks | one per pull, 6 ticks | one per pull, 20 ticks | held, every 3 ticks |
| Feed | magazine, 15 small | cylinder, 6 medium | tube, 6 shells | magazine, 30 medium | magazine, 30 medium | box, 75 medium |
| Damage | 6 | 10 | 6 pellets of 4 | 12 | 16 | 6 |
| Falls off | half past 28 blocks, from 10 | half past 36 blocks, from 14 | fifth past 18 blocks, from 5 | no | no | no |
| Spread | 0.03 | 0.025 | 0.07 | 0.012 | 0.006 | 0.05 |
| Reach | 20 | 28 | 12 | 40 | 64 | 28 |
| Reload | 24 ticks | 48 ticks | 48 ticks | 30 ticks | 30 ticks | 50 ticks |
| After the shot | -- | cylinder indexes, hammer strikes and recocks | racked, 5 ticks on | -- | bolt worked, 5 ticks on | -- |
| Kick | 1.6 up | 3.0 up | 4.5 up | 2.2 up | 3.0 up | 0.55 up |
| Aiming spread | 0.5 | 0.45 | 0.7 | 0.35 | 0.15 | 0.35 |
| Held | one hand | one hand | two | two | two | two |
| Wear | 800 shots | 700 | 500 | 700 | 600 | 1200 |

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
makes eight), shells (paper over gunpowder over an iron nugget makes four)
and slugs (paper over gunpowder over an iron ingot makes four). Each is
tagged into the protocol's family of that name, and each gun takes its
family, so another mod's rounds of the same family load too.

The shotgun takes shells or slugs. A shell is buckshot: six pellets of 4,
a spread of 0.07, falling off past five blocks. A slug is one round of 18
with almost no spread and reach to thirty blocks, and the same push. The
tube holds one kind at a time: while any rounds remain, a reload tops up
with the kind loaded; once empty, it takes the first kind it finds in the
inventory, hotbar first; `Shift+R` unloads and changes to the next kind.
In a magazine the kinds mix, run by run, and the gun's numbers follow the
round that fires next -- the protocol's store is told the next round after
every shot, so the stats, the falloff and the counter's icon are its. What a
round changes is the protocol's `rangedweapons:ammo` data map
(`data/rangedweapons/data_maps/item/ammo.json`); the shotgun's profile is
its buckshot.

Every shot pushes. `knockback` in the profile is the push of a full hit
in the game's own units (the bow's Punch I is one), shared out among the
pellets: the shotgun's 3 lands whole when all six pellets do. Damage per
projectile: pistol 6, revolver 10, shotgun 4 a pellet, rifle 12, scoped rifle 16,
machine gun 6 -- the numbers of the gun mod the players compared these to.
Every pellet and every round counts: the protocol's bullet bypasses the
game's hurt cooldown, which had been turning six pellets into one pellet's
worth of damage and swallowing two rounds in three of a burst.

### Crafting

A gun is assembled from parts made separately. Materials are taken by their
common tags, so any mod's iron, planks or glass panes serve. Steel is
`#c:ingots/steel`: in the pack that is Metals and Materials' ingot (three
iron and a coal make three). Install Metals and Materials separately on both
client and server; the steel this
mod used to make itself is that ingot now, and any saved under the old id
loads as it.

| Part | Recipe |
|---|---|
| **Lower Receiver** | three steel across the top, a redstone under the middle: the body over the trigger group |
| **Upper Receiver** | four steel in a square |
| **Gun Barrel** | three iron ingots in a row |
| **Heavy Barrel** | a barrel and two steel, anywhere in the grid |
| **Gun Stock** | two planks over a plank and a stick |
| **Shotgun Pump** | a plank above a stick above a plank, in one column |
| **Rifle Scope** | a glass pane, an iron ingot, a glass pane in a row |

| Gun | Assembly (as laid out in the grid) |
|---|---|
| **Pistol** | lower receiver, barrel -- in a row |
| **Revolver** | upper receiver beside barrel, lower receiver below the upper |
| **Shotgun** | stock, lower receiver, barrel across; the pump under the barrel |
| **Rifle** | the upper receiver over the lower; stock to its left, barrel to its right |
| **Scoped Rifle** | a scope over a rifle |
| **Machine Gun** | as the rifle, with a heavy barrel |

By iron, counting steel as the iron it came from: pistol 6, shotgun 6 and
some wood, revolver 10, rifle 10, scoped rifle 11 and two panes, machine gun 12. Every
recipe unlocks in the recipe book the moment a player holds one of its
ingredients. In creative, the **Ranged Weapons** tab holds everything of
this mod's in the order of the tree -- guns, ammunition, parts; the parts
are in Ingredients and the guns and ammunition in Combat as well.

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

### The hold

Since 2.4, each gun declares `grip` in its shared Ranged Weapons profile:
`one_handed` for the pistol and revolver, `two_handed` for the long guns. The client reads
the resolved profile while posing, including after datapack reloads. An older
profile without the field retains the item's previous hold.

The server-accepted aim flag is available on every observing client through
`ModData.AIMING`, a transient synchronized NeoForge attachment. It is sent on
first tracking and on changes; switching away or dying clears it. Reload timing
already travels on the equipped stack's `ModData.RELOAD` component, including
its start and duration, so it needs no second packet or duplicate clock. These
are observable state; the hold below retains its animation-pack fingerprint.

Others see a two-handed gun carried like a crossbow, and a one-handed gun
held out along the look by one arm -- a pose of this mod's own, added to
the game's list of arm poses (`client/ArmPoses`, declared in
`META-INF/enumextensions.json`), whose arm is the crossbow's trigger arm
number for number (`domain/HoldOut`). That identity is what animation
packs need. They cannot see a mod's pose, only the vanilla model's arm
angles, and Fresh Animations: Player Extension recognises the crossbow
hold by matching those (yaw within 1e-4 of the head's less 0.3, pitch
within 0.05 of the head's less a right angle plus 0.1); an arm matching
none of its fingerprints is a plain held item to it and hangs at the hip,
which is how the pistol looked under that pack before 2.3.1. Not Enough
Animations leaves both poses alone. The pistol sits upright on top of the
fist with its grip in the hand; the display transforms behind that were
calibrated by photograph in the booth, never derived. The booth reads the
trigger arm off the rendered model at every gun's third-person frames and
fails on a mismatch, whatever animation mods are in its `mods/` folder.

## Adding a gun

1. Register an item with `GunItem` in `ModItems` (durability is the shot count).
2. Give it a profile in `weapons.json` -- its `ammo_family` and its native
   `ammo` round, which you tag into the family, and its `grip` -- and, if the class default
   is wrong for it, a `handling.json` entry.
3. A model and a texture: `devtools/art/build.py` is the generator for the
   ones shipped, run with `uv run devtools/art/build.py`. A shot sound and
   any action sound: a field recording of a real firearm, cut and re-timed
   by the same script. Nothing is synthesized any more -- three rounds of
   synthesis each measured realistic and each was heard as arcade. Put the
   recording under `devtools/art/sounds/src/`, credit it in
   `devtools/art/sounds/SOURCES.md` (Creative Commons Zero only, so it can
   be committed and shipped without terms), and describe the cut in
   `RECORDINGS`: which seconds of which recording, placed where, at what
   gain. A pump or a bolt recorded a second after the shot is moved up under
   its tail, inside the gun's `fire_rate_ticks`.
4. Photograph it: `./gradlew runPhotoBooth` (below) and look at the pictures.
5. A recipe: a `Blueprint` in `Blueprints` (with a unit test pinning where it
   sits in the tree), then `./gradlew runData`; and lang entries.

No gunnery code changes. The trigger, the reload, the recoil and the counter
read everything from the profile and the handling.

## Third-party recordings

Every sound the mod plays is cut from a recording listed in
`devtools/art/sounds/SOURCES.md`, each dedicated to the public domain by its
recordist under Creative Commons Zero on freesound.org. Attribution is not
required by that dedication; the recordists are named there because they
should be. The code and the art stay under this repository's own license.

Long magazine titles and next-round names stay inside their panel, ending in an
ellipsis when needed. Hover over shortened text to read its full name. The Fill button
and the order of rounds work as before. Parts and magazines use bevelled shading at
their original pixel size; dye bands remain independently tintable.

Sound exports are checked **after Vorbis decoding** for headroom, correcting codec
overshoot that can clip even when the source PCM was normalized. Rebuild with
`uv run --no-project python devtools/art/build.py sounds`; the exporter fails if it
cannot keep every decoded sample at or below .98. Recordings and action timing remain
as credited in the sound sources. See D-0017 for the reproduced UI/audio defects.

## Server config

`serverconfig/rangedweaponsmod-server.toml` in the world folder, written with its
defaults the first time the world starts, and sent to every client at login. One
value: `feed`, `MAGAZINES` (the default) or `LOOSE`, described under *Two modes*
above. Change it, then restart the server; a client connected while it changes
keeps the old mode until it logs in again.

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
inside (Jar-in-Jar). Metals and Materials must be installed separately. The protocol is resolved from Maven
Local: build [minecraft-ranged-weapons](../minecraft-ranged-weapons) with
`./gradlew publishToMavenLocal` first.

The revolver uses four baked model parts: frame, cylinder, hammer and crane.
Each accepted shot records its chamber and world tick on that gun's saved,
synchronized stack. Its cylinder indexes 60 degrees and the hammer strikes and
recocks over eight ticks; dry pulls and cooldown rejections do not animate a shot.
The same action is visible in either hand, to observers and in item displays.
Its shot sound reuses the credited rifle recording at 0.85 pitch.

## Testing

`./gradlew runPhotoBooth -PboothRevolver` exercises six real attack-key pulls
and an R reload, then checks the action and reload on a tracked remote player.
It captures both first-person hands, third-person, inventory and moving parts
under the booth's shader and animation packs. Only one rendering client is needed;
the remote actor is server controlled. The booth is test code and is not shipped.

Four tiers. The first two run under `./gradlew check` (and so `build`); the
other two are for eyes and hands.

- `./gradlew test` -- plain JUnit against the `domain` source set, the pure
  layer: the fire clock, the trigger's rule table (the swap among them), the
  reload plan, the magazine (runs, order, fill) and which magazine or kind a
  change takes, the pockets a loose reload draws on (kinds in the order met,
  counts, the draw plan first pocket first) and the feed mode, the recoil
  model, stance spread, and the recipe tree. That source set is compiled against nothing but the
  JDK, so a `net.minecraft` import there is a compile error. Partitions are
  written at the top of each test class.
- `./gradlew runGameTestServer` -- gametests on a real headless server. The
  tests are a mod of their own (`src/gametest`) so they exercise the mod from
  outside: the data resolves to a weapon with the right capacity; a held
  trigger fires at the profile's rate and a release stops it; loose rounds
  in a magazine-fed gun become a magazine; an empty pull takes the first
  loaded magazine carried and only a loaded one; `R` changes a part
  magazine for the first loaded one and keeps it; `Shift+R` walks the
  carried magazines in order and wraps; a mixed magazine fires in order
  with the next round's stats; the screen fills in inventory order from the
  family only; magazines stack to eight by load, `R` takes one of a stack
  and the old one finds room, and the screen fills one of a stack and sets
  the rest aside, or stays shut with no room; in loose mode a magazine-fed
  gun loads loose rounds in its change time and conjures no magazine, a
  magazine in the gun is handed back with its load, `Shift+R` changes kind
  and a magazine is not ammunition, the worn Backpacks+ bag is a pocket
  after the inventory's and its mounts never, a bag carried in a slot is a
  pocket in loose mode and no bag is in magazines mode (Backpacks+ is on
  the gametest classpath for this, never in the jar), and rounds stack to
  the game's ceiling of 99; the shotgun's tube reloads from the inventory, takes its full
  time, consumes exactly what it loads, blocks fire meanwhile, and `Shift+R`
  unloads it and loads the next kind, or nothing with nothing to change to;
  a magazine of another mod's medium round goes in and a small one does
  not; a round
  goes where the crosshair points and not parallel to it; the
  use key passes to vanilla, the trigger is driven separately, a repeat is not a new
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
  survival fired by pressing the attack key itself into a stone block placed
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
  the player answers the wrong question -- and Entity Model Features with
  Entity Texture Features the same way, with a player pack such as Fresh
  Animations: Player Extension in `run/booth/resourcepacks/` and named in
  its `options.txt` (`resourcePacks:["vanilla","file/<zip>"]`). At each
  gun's third-person frames the booth logs the trigger arm's angles off
  the rendered model and passes or fails them against the crossbow's
  (`booth: PASS` / `booth: FAIL` lines; a FAIL fails the task). It quits
  when done. Leave it alone while it runs: any input becomes part of the
  photos. This is how the display transforms and the
  kick's signs were found; none of them are what one would derive.
- A feel test in `./gradlew runClient`: `/give @s rangedweaponsmod:machine_gun`
  and a stack of rounds. Cadence, recoil and reload are judged by hand; the
  numbers to tune are in the data files above.

`./gradlew runPhotoBooth -PboothObservers` runs a shorter network regression.
Put the current Armed Pillagers jar in `run/booth/mods` for the pillager check.
One real client observes a server-controlled player actor: initial aim/reload,
changed aim, reload start/completion, weapon removal, and both declared grips.
The observer receives ordinary tracker/equipment/attachment packets; its state
is never set directly by the harness. The local aim key is exercised separately
through its real input and server acknowledgement. This is not a claim that two
interactive clients were tested. Frames are in `run/booth/screenshots/observer-*.png`.


## 2.5.0 release checks

[2.5.0 is released](https://github.com/the-rusty-shackleford/minecraft-ranged-weapons-mod/releases/tag/v2.5.0)
and deployed in pack 1.42.0. Update the client pack through Mod Hub; both sides
need the new version. Server jar hashes and pack parity were verified at 20 TPS.

The clean build passes 124 JUnit tests and 50 server GameTests. The shader client
gate verifies six real trigger pulls, R loading loose medium rounds, and the
observer's cylinder/hammer and reload state. The vertical shotgun-pump recipe
avoids the Macaw's Roofs conflict, with no collisions found in the current pack
scan. See [the release verification](devtools/verification/release-2.5.0.md).
