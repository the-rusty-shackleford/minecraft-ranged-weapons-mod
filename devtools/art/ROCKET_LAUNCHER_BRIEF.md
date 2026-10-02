# Rocket launcher: art and sound brief (for Astra)

The rocket launcher (D-0028) is built and tested, but its art and sound are placeholders: vanilla
textures on plain cuboids, and sound events pointing at vanilla sounds. They must not ship. This
brief lists every asset, where it goes, and what the code already expects. Replacing a file is all
it takes. No Java changes are needed unless a note below says otherwise.

## What it is

A shoulder-fired launcher with an electronic seeker.
1. The player holds right click on a creature or vehicle. Amber brackets close on the target while a seeker tone growls and rises in pitch (1.5 s).
2. The seeker locks: a red diamond and a steady tone.
3. Left click launches a rocket. It leaves the tube slowly for 3 ticks, then its motor lights, and it accelerates and curves onto the target.
4. Without a lock it flies straight.

Rockets are dear (TNT, blaze powder, steel), so the launch should feel heavy.

## House style

Look at the other guns in-game before starting: `item/rifle.json`, `item/machine_gun.json` and
their atlas `textures/item/gun_atlas.png`, made by `devtools/art/build.py`.
- The vanilla palette, and the 16-pixel item grid.
- Bevels as in `material_bevel.py`.
- No photo textures, no outlines.

D-0004: display transforms are calibrated by photograph in the booth (`./gradlew runPhotoBooth`, see
`PhotoBooth` in the gametest source set). Don't reason them out; photograph and adjust.

## Models and textures

All paths are under `src/main/resources/assets/rangedweaponsmod/`.

| File | What | Notes |
|---|---|---|
| `models/item/rocket_launcher.json` | The launcher, 3D, as the guns are | Two-handed, carried as the rifle is (crossbow-hold arm pose). The tube runs along +X in model space like the rifle's barrel, the muzzle at +X. The seeker box sits on top, forward of the grip. Keep the rifle's `display` keys; recalibrate the values by photo. |
| `models/item/rocket.json` (+ texture) | The rocket's inventory icon | A 2D generated item is fine. |
| `models/item/rocket_projectile.json` (+ texture) | **The rocket in flight** | A 3D model drawn by `RocketRenderer` with no display transform. The nose must point to **+Z**, and the model is centred on (8, 8, 8). About 4 × 4 × 22 pixels today; keep it within a block and a half. |
| `models/item/launch_tube.json`, `models/item/seeker.json` (+ textures) | The two crafting parts | 2D icons, in the family of `barrel.png` and `scope.png`. |

The HUD (`client/LockHud.java`) is drawn with plain fills, so it needs no sprites. Its colours are:
- the ring: white at 60 %;
- acquiring: amber `#FFB000`;
- locked: red `#FF4040`.

If you'd rather draw it with sprites, say so and I'll wire them in.

## Sound events

The names are already registered (`ModSounds`). Replace each entry in `sounds.json` with your
files under `sounds/`. The subtitles exist in `lang/en_us.json`.

| Event | Loop? | Plays | Today's stand-in |
|---|---|---|---|
| `launcher_fire` | no | At the shooter on launch: a thump and a whoosh, heavy | firework launch |
| `launcher_reload` | no | Start of the 2.5 s reload: a rocket slid into the tube and locked | crossbow loading end |
| `rocket_motor` | **yes** | On the rocket for its whole flight, moving with it, heard about 30 blocks | elytra wind |
| `rocket_dud` | no | A rocket breaking without a blast (it hit within 4 blocks) | fire extinguished |
| `seeker_growl` | **yes** | In the holder's ears while acquiring. The code raises the pitch from 0.8 to 1.6 as the lock nears, so record it at a pitch that bends well | beacon hum |
| `seeker_lock` | **yes** | In the holder's ears while locked: a steady tone | note-block bit |
| `seeker_lost` | no | Once, when a lock is lost rather than fired | beacon deactivate |

The explosion is vanilla's own sound.

**Licensing (D-0012):**
- **Recordings** are freesound.org CC0 originals only. Read each sound's page for the licence, and credit it in `devtools/art/sounds/SOURCES.md`. Mixkit's and Pixabay's licences bar redistribution with source, and Pixabay's gun clips are freesound mirrors.
- **The seeker tones** may be synthesized. A real seeker's tone is electronic, so D-0012's rule against synthesized gun sounds doesn't cover them. Rusty hears them before release.
- **Volume:** every clip must pass the decoded 0.98 peak ceiling (D-0017; see `sound_export.py`).

## Seeing your work: the launcher booth

```
"/home/rusty/Code/minecraft mods/tools/booth/run_iconified.sh" \
    "/home/rusty/Code/minecraft mods/minecraft-ranged-weapons-mod" run/booth runPhotoBooth -PboothLauncher
```

It plays the launcher on a real client through the real keys, checks every step in its log
(`run/booth/logs/latest.log`, `booth: PASS` / `booth: FAIL`), and writes these frames to
`run/booth/screenshots/`:

| Frame | Shows |
|---|---|
| `launcher-first-hip`, `launcher-first-sight` | First person, sight down and up |
| `launcher-hud-acquiring`, `launcher-hud-locked`, `launcher-hud-offscreen` | The HUD's brackets, diamond and edge marker on a cow 24 blocks out |
| `launcher-rocket-leaving`, `launcher-rocket-lit`, `launcher-rocket-flight` | **Your in-flight rocket model**: leaving the tube, the motor lit, in flight |
| `launcher-first-reloading` | The reload |
| `launcher-third-front`, `launcher-third-front-sight`, `launcher-third-side` | Third person: the hold and the display transforms |
| `launcher-inventory` | The icons beside the rifle, the rockets, the tube, the seeker and the scope |
| `launcher-plane-acquiring`, `launcher-plane-locked`, `launcher-plane-intercept-1..3`, `launcher-plane-down` | Immersive Aircraft's biplane tracked, locked and brought down |
| `launcher-rocket-model-side`, `-side-close`, `-front-quarter`, `-rear-quarter`, `-nose`, `-tail` | **The in-flight model up close, no smoke**: a rocket drawn by its own renderer against the sky, 1.6 blocks out (1.0 for the close side), HUD and hand hidden, photographed before it makes any smoke. The camera faces south, so the side views show the nose on the left |
| `launcher-rocket-live-side`, `launcher-rocket-live-side-past` | A live rocket crossing the view side-on, four blocks out, at the centre and a little past: its smoke trails behind it |

How booths run on this machine:
- Only one Minecraft client at a time.
- Always through `run_iconified.sh`, which hides the window on Rusty's desktop.
- Volume at zero.
- With Rusty's own client up, set `disableConfigWatcher = true` in `run/booth/config/fml.toml` first.

Rusty's monitor must be on: with it off the client finds no display, and the run hangs until it
fails.

## When it's done

- Run `./gradlew build` (JUnit and GameTests stay green), then the launcher booth.
- Look at every frame above at three times the size, beside the rifle's frames from the default booth (`runPhotoBooth`).
- Delete the word PLACEHOLDER from each model's `credit`.
- Commit on local `main`, as Rusty. Don't push, tag or release.
