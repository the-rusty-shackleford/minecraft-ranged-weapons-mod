# 2.12.0 verification: the weapons workbench and the lock-on chip (D-0029)

Built and checked 2026-10-03. Released 2026-10-04 in pack 1.72.0, this jar as the asset (sha1 matched).

## The gate

- **Clean build:** `./gradlew clean build` is green.
  - 190 JUnit, 0 failures. New: `ChipTest` (5), `BlueprintTest`'s two station tests, and four
    `BlueprintsTest` tests.
  - 90 required GameTests: "All 90 required tests passed". New: `WorkbenchGameTests` (7) and six
    chip tests in `LauncherGameTests`.
- **Jar:** `build/libs/rangedweaponsmod-2.12.0.jar`, 671752 bytes, sha1
  `c5eaec8881e04bd9bc2d9a1bcb4b2f5587382d7e`.
- **Recipe collisions:** `collisions.py --mods <Rusty's Prism instance mods>` checks 26 of ours
  against 5,498 recipes from 115 jars plus vanilla and NeoForge, and finds none. A bench recipe
  cannot collide: no other mod has its type.

## Mutations

Each new rule ran once against a mutation (`mutations-2.12.0.py` beside this record, which
restores the sources after each run). The tests that failed are exactly the ones the rule should fail:

| Run | Mutation | Tests that failed |
|---|---|---|
| A | the seeker ungated (`isLauncher` for `canLock`) | `aChiplessLauncherNeverLocksAndLocksOnceAChipIsFitted`, `theLastChargeBurnsTheChipOutAndTheSeekerIdlesAfter` |
| A | no chip back from a worn-out launcher | `aLauncherWornOutHandsItsChipBack` |
| A | the weapon kept on closing | `theGridAndTheWeaponGoBackToThePlayerOnClosing` |
| B | no wear on a guided launch | `aGuidedLaunchSpendsExactlyOneCharge`, `theLastChargeBurnsTheChipOutAndTheSeekerIdlesAfter` |
| B | the chip slot writes nothing | `aChipFittedAtTheBenchRidesTheLauncherAndComesOffWithItsWear`, `shiftClickSendsALauncherAndAChipToTheirSlotsAndTheRestToTheGrid` |
| C | wear on every launch | `aStraightLaunchSpendsNoCharge`, `aLauncherWornOutHandsItsChipBack` (its straight launch wore the chip) |
| C | creative needs a chip | `creativeLocksWithoutAChipAndWearsNone` |
| C | shift-click ignores chips | `shiftClickSendsALauncherAndAChipToTheirSlotsAndTheRestToTheGrid` |
| C | bench recipes typed as crafting | `aRifleIsAssembledAtTheBenchAndNotAtACraftingTable`, `everyBlueprintIsTheRecipeTheServerFindsAtItsStationAndNoneAtTheOther`, `noRecipeOfOursIsOutsideTheBlueprints` |

Run A had 4 failures, B 4 and C 7, with no other test failing in any run.

## Booths

**The workbench booth** (`-PboothWorkbench`, on the booth's Xephyr `:7`, software rendering, EMI
1.1.24 copied in) passes all 17 checks. It found two expectations of its own to correct:
- Shift-click sends a launcher out of the bench to the main inventory first, as the crafting table
  sends its grid.
- EMI's page for an item opens on its first category, which for the rifle is vanilla's repair
  recipe. The booth now opens the bench's own page.

Frames, judged by eye (`workbench/`):
- `faces-beside-vanilla-12x.png`: the four faces beside the smithing table's and the fletching
  table's.
  - The first draft's front and side failed: the steel tools had the walnut's luminance (about
    80) and vanished.
  - The panel was darkened and the tools lightened, and the sides took a wrench and a shelf of
    brass rounds, which read at a glance.
- `blocks-front-3x.png` and `blocks-corner-3x.png`: the bench between a smithing table and a
  crafting table.
  - The dark iron frame and steel top put it in the smithing table's family.
  - The side reads crisply, and the front's rifle and pistol read.
  - The front is the dimmest face, as north faces are under the game's side shading.
- `workbench-hud-no-chip.png`: "No lock-on chip" under the crosshair, no ring.
- `workbench-screen-launcher.png`: the chip slot opens under the launcher, showing its empty chip
  icon.
- `workbench-screen-fitted.png`: the chip shows in its slot.
- `workbench-emi-rifle.png`: EMI's "Weapons Workbench" page, with the bench as tab and workstation
  and the rifle in the 3×3 layout. All 20 bench recipes are listed.
- `workbench-emi-filled.png`: EMI's own "+" (its recipe filler) filled the grid from the inventory
  and the bench offers a rifle. The launcher's tooltip, caught under the cursor, reads "Lock-on
  chip: 8 of 8 locks left".
- `workbench-hud-chipped.png`: the fitted launcher's sight shows its ring and no hint.

**The launcher booth** (`-PboothLauncher`), its launcher now fitted with a chip:
- **On the GPU through `run_iconified.sh`,** as 2.11.0 was verified: all 14 checks pass, the
  biplane locked and brought down, and the live rocket photographed side-on.
- **On Xephyr's software renderer:** 12 of 14. The biplane shot and the crossing photograph failed;
  both depend on frame timing, and llvmpipe is slow.
- Every lock check passed in both places, and the GPU run settles that the chip change did not
  cause the two failures.

## Not verified

- **Real players.** No two-client test was run; the booth is one client on an integrated server.
- **The box.** Not deployed.
- **The protocol bump.** The network version is "5", so a 2.11.0 client cannot join a 2.12.0
  server: the pack must go out with it.
