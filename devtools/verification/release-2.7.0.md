# Release 2.7.0 verification — 2026-09-23

Rusty: "Make 'requires magazines' a mode for the mod, and make another mode that just
pulls bullets from anywhere in your inventory at all (including backpacks). Raise all
ranged weapon mod bullet/round max stack sizes to 128", then the ack of the preview with
its five calls (99 not 128; loose reload in the gun's change time; pockets before bags;
bag reach only in loose mode; a handed-back magazine stays whole), and "there should be
no com.nfx anywhere". D-0023.

- `./gradlew --offline --no-watch-fs test`: 133 JUnit tests, nine new: `PocketsTest`
  (kinds pocket by pocket each once and only the accepted; counts over every pocket; a
  plan first pocket first and first stack first, emptying each before the next; more than
  in reach or negative refused; a plan takes exactly what it says; stacks and takes of no
  rounds refused and the rows copied; equality by contents) and `FeedModeTest`.
- `./gradlew --offline --no-watch-fs check`: all 59 required real-server GameTests
  passed, seven new: rounds stack to the game's ceiling of 99; in loose mode a
  magazine-fed gun loads loose rounds in its change time and conjures no magazine; a
  magazine in the gun is handed back with its load into the first free slot; Shift+R
  changes kind, the loaded rounds come back to the inventory and a full box is not
  ammunition; the worn Expedition bag is drawn on after the pockets, its storage cell
  emptied, its mount untouched and its revision advanced once; a bag carried in a slot is
  a pocket in loose mode; no bag is a pocket in magazines mode. The loose tests run in
  their own batch with before/after hooks setting the server config, since tests in a
  batch run concurrently and a per-test switch was flipped under them. Backpacks+ is on
  the gametest classpath (the unreleased 0.2.2 jar from the adjacent workspace, never in
  this jar) so the bag pocket is exercised against the real thing; that run exposed
  Backpacks+ 0.2.1 and Quick Slot 0.1.1 sending payloads to a mock server player's
  unnegotiated connection, fixed in Backpacks+ 0.2.2 and Quick Slot 0.1.2.
- `./gradlew runPhotoBooth -PboothHud` on the desktop display with the GPU (NVIDIA RTX
  4070, driver 595.84, Complementary Unbound r5.8.1 through Iris), muted, window
  iconified beside Rusty's own client, 46 s: all seven HUD frames captured. Inspected:
  [loose mode](hud-2.7.0/hud-loose-rounds.png) shows `40 / 75` with the round's icon and
  the kind "Round" under it, never "No magazine", and the stack of eight boxes shows its
  count on the hotbar; [the switch back](hud-2.7.0/hud-magazines-adopted.png) shows the
  same forty rounds adopted into a "Machine Gun Box"; [magazines mode idle](hud-2.7.0/hud-normal-idle.png)
  is unchanged (a renamed magazine's label, shortened). The single-player booth shares
  the config object between its client and integrated server, so it proves the HUD and
  the mode switch, not the login sync to a remote client; that is NeoForge's `ConfigSync`
  for server configs and is verified on the box after deployment.
- `./gradlew --offline --no-watch-fs build -PskipBooth -PskipGameTests` after those runs:
  green; jar `rangedweaponsmod-2.7.0.jar` SHA-1 `c4df20f0c317f2613cd8ce2cc2c7e8d15cfdcb83`
  (449743 bytes). Recursive audit: no gametest or test classes, no Backpacks+ or Quick
  Slot class inside, protocol 1.7.0 nested, `version="2.7.0"`, the Backpacks+ dependency
  still optional at `[0.2.1,)` since the bag pocket uses only that release's public API.
- Package: `com.chunkworks.rangedweaponsmod` throughout (`git mv` of the four source
  trees, every reference rewritten, the mod group id with it); the protocol library's
  `com.nfx.rangedweapons` is untouched.
- Not verified: a real client against the live server in loose mode (the mode ships
  defaulting to magazines, so the deployment changes nothing until the box's config is
  flipped: `knowledge/operations/feed-mode.md` in the server repo).
