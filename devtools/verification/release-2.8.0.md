# 2.8.0 verification — 2026-09-24 UTC

Rusty, told the feed mode was the world's server config plus a restart: "It should be a
per-player config setting." Design D-0024: the mode in each player's client config, told to
the server by their client at login and on change, kept per connected player.

- `./gradlew --offline --no-watch-fs test`: 133 JUnit tests, unchanged (the domain's
  `FeedMode` and `Pockets` are as they were; what moved is where the mode lives).
- `./gradlew --offline --no-watch-fs runGameTestServer`: all 59 required real-server
  GameTests passed. The five loose-mode tests now put their own mock gunner in loose mode
  (`loose(gunner(...))`, the server-side `FeedModes.set` a client's payload would make) and
  run in the default batch beside the magazines-mode tests, which is the point: two players
  in two modes at once, on one server, in one tick. The magazines-mode assertion checks a
  player the server has never heard from is in `MAGAZINES`.
- `./gradlew runPhotoBooth -PboothHud` through `tools/booth/run_iconified.sh` (window
  iconified while loading, pid 1276334; nothing on Rusty's screen). The booth chooses the
  mode as a client does, `FeedSync.choose(FeedMode.LOOSE)` then `MAGAZINES`, which writes the
  client config and sends the payload to the integrated server. Twelve photos in
  `hud-2.8.0/`; the two that depend on the mode judged:
  [loose-rounds](hud-2.8.0/hud-loose-rounds.png): the counter reads "40 / 75" over "Round",
  the loaded kind, never "No magazine", and the stack of eight boxes on the hotbar shows its
  count; [magazines-adopted](hud-2.8.0/hud-magazines-adopted.png): back in magazines mode
  the counter reads "40 / 75" over "Machine Gun Box", the box the server adopted the loose
  rounds into on the gunnery's next look, so the server followed the client's choice both
  ways. The other ten frames are those of 2.7.1, unchanged.
- `./gradlew --offline --no-watch-fs build -PskipGameTests -x test` after those runs: green;
  jar `rangedweaponsmod-2.8.0.jar` SHA-1 `1a44f1ed036dc7ba54063509ae77d513e558d89f` (453260
  bytes). Audit: no gametest or Backpacks+ classes, no `ServerConfig`, `version="2.8.0"`,
  the optional Backpacks+ dependency `[0.3.0,)` as in 2.7.1.
- Protocol "3": a 2.7.x client is refused at connection with the version mismatch message
  rather than left in the wrong mode; the pack updates everyone together.
- Not verified: the Mods screen's config page in a real client (NeoForge's own
  `ConfigurationScreen`, registered in the mod constructor on the client only; its save fires
  `ModConfigEvent.Reloading`, which `FeedSync` listens for), and a player changing mode
  mid-session on the shared server. Rusty's own switch is the first live test.

## Published — 2026-09-24 03:00 UTC

Rusty: "release all that". `./gradlew --offline --no-watch-fs clean build` on the same
commit (ad5e76a) green again, all 59 required gametests passed, the jar byte-identical
(SHA-1 `1a44f1ed036dc7ba54063509ae77d513e558d89f`, 453260 bytes). Tag `v2.8.0`, pushed;
[release](https://github.com/the-rusty-shackleford/minecraft-ranged-weapons-mod/releases/tag/v2.8.0)
asset downloaded back and matching by SHA-1. Pack 1.59.0 assembled on the box at 03:02:33
UTC by `modhub add-file --replaces mods/rangedweaponsmod-2.7.1.jar`, `set-version 1.59.0`,
`assemble` (client sha256 `b9b809ed…`, server `663baa4c…`); the deployment is in the server
repo's `knowledge/releases/pack-1.59.0.md`.
