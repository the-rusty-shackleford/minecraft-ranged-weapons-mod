# Revolver verification — 2026-09-18

Initial local verification of D-0019 before release authorization. Rusty later
authorized the recipe fix and release; see [release gates](release-2.5.0.md).
The artifact hash below identifies the earlier revolver-only build.

## Checks run in this session

- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-watch-fs build`:
  passed, 124 JUnit tests and 50 required real-server GameTests.
- Server coverage includes six accepted shots, 12-tick cooldown rejection,
  held/dry trigger behavior, chamber wrap, full and partial loose-medium reloads,
  rejected small rounds/shells, creative without ammunition or wear, saved
  independent stack state, and a real projectile dealing 10 close-range damage.
- `./gradlew --offline --no-watch-fs runPhotoBooth -PboothRevolver`: passed.
  Real attack-key input produced six acknowledged shots; R started a reload and
  consumed exactly six medium rounds. A tracked server actor's accepted cycle
  and reload arrived on the observing client. [Client verdicts](revolver/client-results.txt).
- Recursive jar audit: revolver production classes, five models and recipe present;
  protocol 1.7.0 nested; no GameTest/booth classes or nested Metals and Materials.
- The model generator and datagen produced the committed model/recipe sources.

Jar: `build/libs/rangedweaponsmod-2.5.0.jar`.
SHA-256: `205da9fa7beae0fd74353e76591e1c48b5f083430e0b0746cb9ce1606b3ed412`.

## Visual review

One muted, offscreen rendering client: Iris 1.8.14-beta.1, Sodium 0.8.13-beta.2,
Complementary Unbound r5.8.1, NEA 1.12.4, EMF 3.2.4, ETF 7.1,
Fresh Animations 1.10.4 and Player Extension 1.1. Iris reload is bound to `=`,
matching Rusty's Prism, so R tests the gun without reloading shaders.

Inspected full screenshots and enlarged crops, including the hammer pivot,
rotating cylinder, hinged reload crane and both hand grips. Review criteria:
5/5 passed — recognizable six-chamber cylinder, visible cylinder indexing,
hammer strike/recock, attached reload hinge, upright forward-pointing grip in
both hands. No supplied reference image was used. The finer cylinder bands keep
its round silhouette through the six indexed positions.

- [First-person firing](revolver/firing.gif), [reload](revolver/reload.gif).
- [Observer action](revolver/observer-action.gif), [observer reload](revolver/revolver-observer-reloading.png).
- [Right hand](revolver/revolver-first-right.png), [left hand](revolver/revolver-first-left.png).
- [Third-person right](revolver/revolver-third-front.png), [third-person left](revolver/revolver-third-left.png), [side](revolver/revolver-third-side.png).
- [Inventory](revolver/revolver-inventory.png).

The observer is a server-controlled actor using normal equipment tracking;
this is not a two-interactive-client network test. The server owns every gun
component in the booth. Key release waits for the accepted shot, avoiding taps
that fall between rendered input polls. Loose ammunition is supplied after the
sixth shot so the existing empty-trigger auto-reload cannot preempt the R test.

## Recipe scan

The pack scan compared 20 of our recipes with 5,495 recipes from 110 jars plus
vanilla and NeoForge. No revolver collision was found. The scan is not globally
green: eleven existing shotgun-pump/Macaw's Roofs overlaps are tracked in
[issue #1](https://github.com/the-rusty-shackleford/minecraft-ranged-weapons-mod/issues/1).
Sixty unresolved tags and the conservative treatment of `neoforge:difference`
limit the scan; real-server crafting tests also verify the new recipe and tab.
