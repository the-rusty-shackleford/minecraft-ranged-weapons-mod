# Launcher art and sound — 2026-10-02

Local work on Ranged Weapons Mod `main`, version 2.11.0, on top of `28d4491`.
The asset pipeline changes no mechanics, Gradle configuration, version, tag, remote or pack.
Rusty subsequently authorized client HUD animation changes after reporting stepped lock markers;
that follow-up is described below.
Claude separately supplied the extended `LauncherBooth` source and its completed captures; that
Java edit is not part of the asset changes. **Rusty accepted this work on 2026-10-02 and
asked Claude to release it for their hands-on test.** This work is committed locally; this
session does not push, tag, release or update the pack.

## Assets and rebuilding

`uv run --no-project python devtools/art/build.py textures model sounds` regenerates:

- Five models: launcher, in-flight projectile, rocket inventory item, tube and seeker parts.
- Three sixteen-pixel inventory textures, using `material_bevel.py`.
- Seven dedicated sound clips and their `sounds.json` entries.

`launcher_art.py` uses the unchanged gun atlas for both 3D models. The tube points along +X;
the projectile nose points along +Z, its bounds are centred on (8, 8, 8), and its length is
22 pixels. It has no display transform. The launcher retains the rifle's display keys;
photographs supported retaining the initial launcher pose values with the new geometry.

The physical sounds use three newly credited CC0 Freesound previews plus two already credited
recordings. Only the electronic seeker is synthesized. The exact source files, download hashes
and edits are in [SOURCES.md](../art/sounds/SOURCES.md).

## Checks in this session

- `./gradlew --offline --no-watch-fs build` passed after the asset replacement: all 77 required
  real-server GameTests passed. Gradle reused the unchanged passing JUnit outputs: 173 tests,
  zero failures/errors; these were not reported as a fresh JUnit execution.
- Strict mypy and Ruff passed for both new generation modules.
- Complete regeneration reproduced all 16 launcher output files byte for byte. Existing gun
  models, atlas, textures and sounds remained byte-identical to HEAD.
- Five models have no `PLACEHOLDER` credits; all texture references resolve. The shipped JAR's
  asset bytes were compared against the resource tree.
- The default rifle photo booth passed. The launcher booth passed all 14 checks after the
  replacement sounds were present, including a locked cow shot, reload, and the biplane intercept.
- Booths ran one at a time through `run_iconified.sh`, with master volume zero, the monitor on,
  and RTX 4070 rendering with Complementary Unbound r5.8.1. Each rendering client exited.

The final exhaust revision replaces a projecting painted cap with four collar walls and an
inset bore, keeping the bounding-box centre exactly (8, 8, 8). Claude subsequently added six model studies through the actual rocket renderer and two live
side-on crossing frames. The resulting run completed successfully with 15 booth verdicts; those
fresh captures were inspected here, including nose, exhaust and fin roots. The Java edit was
supplied by Claude, not authored or altered in this asset session.

## Review evidence

[Review page](launcher/index.html): three-times nearest-neighbour crops beside fresh rifle frames,
all twenty-seven launcher frames expandable at three times, and manual playback controls for all
seven clips. The motor/growl/lock controls loop; the growl slider changes pitch from .8 to 1.6.
No sound autoplays. [Booth verdicts](launcher/client-results.txt).

Inspected the first-person hip and sight views, reload, third-person front/side/aimed hold,
inventory parts alongside the rifle/scope, acquiring and locked HUD, offscreen indicator, all
flight frames, and the biplane sequence. The launcher reads as the rifle's material family,
with a thicker tube, recessed mouth, wooden grip, bands and a compact optical seeker. The
seeker icon is distinct from the scope, and the rocket icon is distinct from the old firework.
The muzzle clears the hands, the reticle remains visible, and icons stay inside their slots.

The original flight captures obscure the small projectile with smoke and flame. The added
side and quarter studies show the stepped nose, attached fins and recessed exhaust; live crossing
frames show the nose leading, with the tail behind. Rusty reviewed the frames and audible live
run and accepted the work, with the remaining marker-motion concern recorded below.

## Audio export measurements

Decoded mono 44.1 kHz peaks (all below .98): launch .9628, reload .8093, motor .6839, dud .6313,
growl .2946, lock .2214, lost .2439. Original source recordings are preserved unchanged.

FFmpeg's decoded output for the periodic seeker files ended 128 samples early (43,972 rather
than 44,100), producing a misleading loop-boundary jump. The actual installed Minecraft
LWJGL 3.3.3 STB Vorbis library was checked directly through both its whole-file and streaming
APIs, from a scratch Python ctypes harness; no Java was changed. Both return the full 44,100
samples for each seeker and 121,275 for the motor. Streaming decoded boundary differences:
growl .02092, lock .02343, motor .02640. Peaks match the headroom check. No compensating samples
were added for FFmpeg's different trimming. These checks establish decoding and boundary
behavior; Rusty's listening judgement remains required.

## One-time audible review run

Rusty explicitly authorized a visible run at 35% master volume on 2026-10-02.
The booth was launched through `run_iconified.sh`, then its own window was restored and maximized.
OpenAL initialized on the desktop speakers, and all 15 checks passed. The temporary test-only
mute exception and booth options were restored immediately afterward; master volume is zero.
The muted test harness was recompiled. Rusty subsequently accepted the work; their HUD feedback
led to the animation follow-up below.

## Continuous lock-marker animation follow-up

Rusty found the markers stepped while the rest of the scene remained smooth. A first
client-tick interpolation pass did not satisfy their live review. The replacement uses the
JDK-only immutable `SeekerAnimation`: each new confirmed progress value starts a 100 ms
transition from the current displayed value, without restarting on unchanged frames or
predicting progress beyond what the server has confirmed. New targets, acquisition restarts,
loss and lock reset the visual history. Lock completion still comes directly from the server.

Acquisition geometry, and the locked diamond together with its label, use fractional GUI
coordinates. The animation clock pauses with the game and cannot rewind at freeze/unfreeze.
This change does not batch drawing calls or alter the booth's scripted camera or screenshots.

Six JUnit regression tests cover continuous packet updates between ticks, jittered delivery,
unchanged updates/grace gaps, fresh and restarted acquisitions, target changes, loss/lock,
clock bounds and endpoint bounds. The full build passed on 2026-10-02, with 179 JUnit tests
and all 77 required server GameTests passing. The final visible muted booth passed all 15
checks with Complementary Unbound on the RTX 4070, then exited. Master volume remains zero.

The [recorded acquisition replay](launcher/lock-animation.mp4) is a 60 fps crop of that actual
booth window. The cow acquisition contains 61 distinct detected bracket widths over about
1.4 seconds, with no detected width increases. This demonstrates intermediate sizes; it does
not establish perceptual smoothness or diagnose the remaining motion concern.

## Handoff to Claude

Rusty's final call: "Still stuttery, but now I am thinking that might be an artifact of how your
automation controls looking around. It's probably not as smooth as me moving my mouse. I am
happy to call it here for now and let claude release it so I can test it myself for reals".

Stop further tuning. The booth turns the player toward the plane once per client tick and takes
synchronous screenshots; either may affect the review, but neither is proven to explain the
remaining marker motion. Rusty's next gate is their own mouse-controlled playtest after Claude's
release. The server lock rules and timing are unchanged. No temporary audible exception remains,
no booth client is running, and no release or pack operation was performed by this session.
