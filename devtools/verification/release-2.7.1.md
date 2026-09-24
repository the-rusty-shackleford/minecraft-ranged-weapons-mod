# 2.7.1 verification — 2026-09-23 (evening)

Rusty, on Backpacks+ 0.2.3: their friend's four-mount bar went compact with the Expedition
bag; "When you fix this, make sure it does not clash with the ammo indicator UI." Backpacks+
0.3.0 (its D-0025) keeps the row beside the hotbar by closing the cells up; this release
makes the ammo panel take Backpacks+' own word for where a gear gesture is
(`GearClient.browsingBottomRight()`) instead of recomputing the old fit formula, and requires
Backpacks+ 0.3.0 or later when it is present.

- `./gradlew runPhotoBooth -PboothHud` on the desktop display with the GPU, launched through
  `tools/booth/run_iconified.sh` (the window iconified while still on the loading screen;
  the first attempt's launcher found no process and ran in the open, which Rusty saw), with
  the sibling checkout's `backpacksplus-0.3.0.jar` and the booth's Quick Slot 0.1.2. Twelve
  photos in `hud-2.7.1/`, the gesture's gun swapped back into the hand after every release
  so the counter is in every frame. Judged:
  - [normal-gear](hud-2.7.1/hud-normal-gear.png) (640 wide): as in 2.7.0, four mounts at the
    usual pitch with the held-deposit cell at the row's end, the panel raised above the text.
  - [scale3-gear](hud-2.7.1/hud-scale3-gear.png) (427 wide, the 1280 window at GUI scale 3,
    the friend's likely case): the four mounts close up to touching beside the quick slot at
    the bottom right; the held-deposit cell sits one row up over the quick slot's column with
    its arrow; the gesture text above it; the panel raised above all of it with a visible gap.
  - [scale3-idle](hud-2.7.1/hud-scale3-idle.png): the row idle at the bottom right at the
    same pitch; the panel in its usual place directly above, its box ending on the row's top
    edge, nothing overlapping.
  - [fullhd-gear](hud-2.7.1/hud-fullhd-gear.png) (480 wide, a 1920x1080 window at the auto
    scale, a friend's full screen): four mounts at the usual pitch (they fit), the held cell
    one row up (five columns do not), the panel raised, no overlap.
  - [fullhd-idle](hud-2.7.1/hud-fullhd-idle.png): idle, the panel flush above the row.
  - [fullhd-hotbar-indicator-gear](hud-2.7.1/hud-fullhd-hotbar-indicator-gear.png): with the
    attack indicator on the hotbar the quick slot moves out and the four mounts close up to
    touching, ending at the screen's edge; the held cell one row up; the panel raised.
  - [compact-gear](hud-2.7.1/hud-compact-gear.png) and [compact-left](hud-2.7.1/hud-compact-left.png)
    (320 wide, the minimum): the row lifts above the status icons as before, with the held
    cell in the row; the panel stays low on the right (the gesture is not in its corner) and
    clear of the lifted row.
  - [normal-reload](hud-2.7.1/hud-normal-reload.png), [loose-rounds](hud-2.7.1/hud-loose-rounds.png),
    [magazines-adopted](hud-2.7.1/hud-magazines-adopted.png): unchanged from 2.7.0.
- `./gradlew --offline --no-watch-fs test runGameTestServer`: 133 JUnit tests, all 59
  required real-server GameTests passed; the change is client-only and no test exercises the
  HUD, so these guard against a regression elsewhere only.
- `./gradlew --offline --no-watch-fs build -PskipGameTests -x test` after those runs: green;
  jar `rangedweaponsmod-2.7.1.jar` SHA-1 `65dc99d9bc2127f5f4d7a348a896e4f1d1fd7c6f` (448977
  bytes). Audit: the manifest's optional `backpacksplus` dependency is `[0.3.0,)`; no gametest
  or Backpacks+ classes inside.
- Not verified: a real full-screen client; Rusty's friend's own settings (the two likely
  widths are covered, 427 and 480, with both attack-indicator settings at 480).
