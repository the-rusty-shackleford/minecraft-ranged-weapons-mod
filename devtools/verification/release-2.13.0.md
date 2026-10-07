# 2.13.0 verification: rounds crack, never break (the protocol's D-0011)

Built and checked 2026-10-07, for pack 1.76.0 on Rusty's "Build it, test and confirm it, then
release everything".

Rusty: "I don't want the ranged weapons mod guns to break blocks anymore. They should still break
glass and other glass items such as lanterns, but thats it", then "I'm open to them cracking
blocks, but never breaking them (besides the glass, which should break with a single shot
regardless of weapon). Rocket launcher should still break shit as usual since its more of an
explosive than a bullet". The rule lives in the protocol (`minecraft-ranged-weapons` 1.8.0, its
D-0011); this release nests it at `[1.8,2.0)`, so no copy of 1.7.0 another mod nests (Armed
Pillagers 1.3.0, `[1.7,2.0)`) can be chosen. The launcher's blast is `Rocket`'s explosion, its own
code and config, and is untouched.

## The gate

- **The protocol, 1.8.0:** `./gradlew clean build` green: 104 JUnit, 20 GameTests. Four new or
  rewritten GameTests failed on 1.7.0's rule and pass: the lantern gone in one round with nothing
  dropped (it stood), stone through eight rounds, a slime block (hardness 0), ice (each destroyed).
  Jar `rangedweapons-1.8.0.jar` sha1 `a45c68a87ea5e3389ac438f57453d4aef7212991`.
- **This mod:** `./gradlew clean build` green: 190 JUnit, 91 GameTests. New:
  `aBurstCracksStoneButNeverBreaksIt`, ten machine-gun rounds of six into stone through the real
  trigger path; on protocol 1.7.0 it failed ("Expected Stone, got Air"), the ten rounds fired.
- **The default booth** (Xephyr `:7`, Iris and Complementary on llvmpipe): 11 checks, "all checks
  ran". The burst frames show the stone cracking (part-way at two rounds, heavily at four, where
  1.7.0 took it down) and standing; the cracks are drawn client-side, which no GameTest sees.
- **Jar:** `build/libs/rangedweaponsmod-2.13.0.jar`, 671949 bytes, sha1
  `015adf3fd5cd4794958e0b0e464c9d58c2150701`; it nests that protocol jar (same sha1) and Carried
  1.0.0 (`46850745`).

## Not verified

- The modded lanterns in the tag (Amendments' wall lanterns, Bosses'Rise's ship lantern, Dusty
  Decorations' nautilus lantern) are optional entries for mods absent from the GameTest server;
  that they resolve is checked on the box after the release.
- A shot at a lantern in live play.
