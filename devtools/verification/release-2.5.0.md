# Release 2.5.0 verification — 2026-09-18

Rusty authorized fixing the shotgun pump recipe with creative license and
releasing it with the revolver. The pump now uses a vertical plank-stick-plank
column (D-0020); cost, item IDs and assembled shotgun recipe are unchanged.

- `./gradlew --offline --no-watch-fs runData`: generated the new recipe.
- `./gradlew --offline --no-watch-fs clean build`: passed 124 JUnit tests and
  50 required real-server GameTests, including crafting every generated recipe
  through the real recipe manager.
- `./gradlew --offline --no-watch-fs runPhotoBooth -PboothRevolver`: passed the
  real input, six-shot, R-reload and observer gates with Iris/Complementary and
  the animation packs documented in [the revolver review](revolver.md).
- Recursive jar audit: production revolver classes, geometry and recipe present;
  protocol 1.7.0 nested; no test classes or embedded Metals and Materials.
- Recipe scan: 20 recipes checked against 5,495 foreign recipes, with no
  collisions. Input combines client pack jars and recipe/item-tag resources
  read from all 125 installed server jars, including nested dependencies.
  The scanner still reports 60 unresolved foreign tags and conservatively
  treats `neoforge:difference` as matching anything; it is not an exhaustive
  proof about arbitrary third-party ingredient types.

The eleven pump/roof overlaps reported in issue #1 are resolved. Existing
visual geometry is unchanged from the inspected pre-release artifact.

Artifact: `rangedweaponsmod-2.5.0.jar` (428974 bytes).

- SHA-1: `b3f3e3f6b7c4a60a52e0e63be36601d658fe2ad0`
- SHA-256: `64c10168806f9564e0b2e54b87f62fd6ccdcc9ab189e4349b24a81d39c90db2d`
- SHA-512: `e43fb52e24882062f9216f972127afc4473daf62f86cb8eca352c9423ce2e27516878473252d75e6414de0f9a17a6b0099ade61038817f7c1378f04f5feb517c`

These are pre-publication gate results. Deployment is recorded in the project
store after the published download and running server are verified.
