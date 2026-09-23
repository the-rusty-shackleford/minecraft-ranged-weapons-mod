# Release 2.6.0 verification — 2026-09-23

Rusty: "Can you make the magazines in ranged weapons mod stackable? At least like kind
magazines"; "Max stack size for mags should be 8". D-0022: magazines stack to eight by the
game's own rule (same item, same load, name and dye), `R` takes one off a stack, and the
fill screen fills exactly one magazine, setting the rest of a stack aside. The domain is
unchanged; the change is `MagazineItem`, `MagazineMenu` and `PlayerGunnery`.

- `./gradlew --offline --no-watch-fs check`: 124 JUnit tests (unchanged) and all 52
  required real-server GameTests passed, three of them on stacks: two new
  (`theReloadKeyTakesOneMagazineOfAStackAndTheOldOneFindsRoom`: one of three full boxes
  goes in, two keep their slot, the part magazine takes the first free slot;
  `theMagazineScreenFillsOneMagazineOfAStackAndSetsTheRestAside`: opening on a stack of
  three leaves one in the hand and two aside, a fill takes thirty rounds once, a stack of
  two reaching the hand mid-screen is set aside before the next fill so sixty are not
  taken, and with nowhere to set a stack aside the screen stays shut) and one rewritten
  (`magazinesStackToEightByLoadTakeADyeAndKnowTheirFamily`: max stack eight, same load
  shares a stack, a different count or round does not, an emptied magazine stacks with a
  new one, the inventory merges like magazines).
- `./gradlew --offline --no-watch-fs build -PskipBooth -PskipGameTests` after that run:
  green; jar `rangedweaponsmod-2.6.0.jar` SHA-1 `f470a8689f2ec4365a45d12e0193ae50c305c9ee`
  (432093 bytes). Recursive audit: no gametest or test classes, protocol 1.7.0 nested,
  no embedded Metals and Materials, `version="2.6.0"` in the mod metadata.
- The photo booth was not run: nothing rendered changed (the magazine screen's slots and
  button are as they were; a stack shows the game's own count badge).
- Not verified: a real client against the live server. The dupe guard (a stack reaching
  the locked hand slot through a number-key swap or a pickup while the screen is open)
  is exercised by the gametest by placing the stack in the hand directly, not by the
  click path that would put it there.
