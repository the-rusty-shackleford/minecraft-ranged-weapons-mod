"""Which of this mod's recipes another mod's recipe would also answer to.

The game resolves a crafting grid to the first recipe that matches it, in
an order that depends on how the pack's files hashed -- so a grid two
recipes both accept is a coin toss, silently, and differently in every
pack. Every recipe here that takes only raw materials (a part, a round)
could collide with a recipe from any of a pack's hundred mods; a recipe
that takes one of our own parts cannot, since no other mod names them.

Run from the repository root, against the jars of the pack the mod ships in:

    uv run --no-project python devtools/recipes/collisions.py --mods <dir of jars>

Vanilla's recipes and NeoForge's common tags are read from the local
Gradle caches by default. Exit status 1 on any collision. Standard library only.
"""

from __future__ import annotations

import argparse
import glob
import io
import json
import os
import sys
import zipfile
from pathlib import Path

WILDCARD = "*"  # an ingredient of a kind this script cannot resolve: matches anything
ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OURS = ROOT / "src/generated/resources"
DEFAULT_VANILLA = os.path.expanduser("~/.gradle/caches/neoformruntime/intermediate_results/extractServer_*_output.jar")
DEFAULT_NEOFORGE = os.path.expanduser(
    "~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/*/*/neoforge-*-universal.jar")


# ---------------------------------------------------------------- reading


def read_zip_tree(archive: zipfile.ZipFile, source: str, recipes: dict, tags: dict, depth: int = 0) -> None:
    """effects: adds every recipe and item tag in `archive` (and the jars it
    nests, one level) to `recipes` and `tags`, keyed by resource id."""
    for name in archive.namelist():
        parts = name.split("/")
        if len(parts) >= 4 and parts[0] == "data" and parts[2] == "recipe" and name.endswith(".json"):
            rid = f"{parts[1]}:{'/'.join(parts[3:])[:-5]}"
            try:
                recipes[rid] = (source, json.loads(archive.read(name)))
            except (json.JSONDecodeError, UnicodeDecodeError):
                pass
        elif len(parts) >= 5 and parts[0] == "data" and parts[2] == "tags" and parts[3] == "item" and name.endswith(".json"):
            tid = f"{parts[1]}:{'/'.join(parts[4:])[:-5]}"
            try:
                tags.setdefault(tid, []).append(json.loads(archive.read(name)))
            except (json.JSONDecodeError, UnicodeDecodeError):
                pass
        elif depth == 0 and name.startswith("META-INF/jarjar/") and name.endswith(".jar"):
            try:
                nested = zipfile.ZipFile(io.BytesIO(archive.read(name)))
            except zipfile.BadZipFile:
                continue
            read_zip_tree(nested, f"{source}!{parts[-1]}", recipes, tags, depth + 1)


def read_dir_tree(root: Path, source: str, recipes: dict, tags: dict) -> None:
    for path in root.rglob("*.json"):
        rel = path.relative_to(root).as_posix().split("/")
        if len(rel) >= 4 and rel[0] == "data" and rel[2] == "recipe":
            recipes[f"{rel[1]}:{'/'.join(rel[3:])[:-5]}"] = (source, json.loads(path.read_text()))
        elif len(rel) >= 5 and rel[0] == "data" and rel[2] == "tags" and rel[3] == "item":
            tags.setdefault(f"{rel[1]}:{'/'.join(rel[4:])[:-5]}", []).append(json.loads(path.read_text()))


# ---------------------------------------------------------------- resolving


class Tags:
    """Item tags resolved to item ids, unions across every jar, references followed."""

    def __init__(self, raw: dict):
        self.raw = raw
        self.cache: dict[str, frozenset] = {}
        self.unresolved: set[str] = set()

    def items(self, tag: str, trail: tuple = ()) -> frozenset:
        if tag in self.cache:
            return self.cache[tag]
        if tag in trail:
            return frozenset()
        found = set()
        entries = self.raw.get(tag)
        if entries is None:
            self.unresolved.add(tag)
            self.cache[tag] = frozenset()
            return self.cache[tag]
        for body in entries:
            for value in body.get("values", []):
                required = True
                if isinstance(value, dict):
                    required = value.get("required", True)
                    value = value.get("id", "")
                if value.startswith("#"):
                    inner = self.items(value[1:], trail + (tag,))
                    if not inner and required and value[1:] not in self.raw:
                        self.unresolved.add(value[1:])
                    found |= inner
                else:
                    found.add(value)
        self.cache[tag] = frozenset(found)
        return self.cache[tag]


def ingredient_items(ingredient, tags: Tags, notes: set) -> frozenset:
    """effects: returns the item ids an ingredient accepts; WILDCARD alone if unknown."""
    if isinstance(ingredient, list):
        out = set()
        for entry in ingredient:
            out |= ingredient_items(entry, tags, notes)
        return frozenset(out)
    if isinstance(ingredient, str):
        # The bare form: "#tag" or "namespace:item".
        return tags.items(ingredient[1:]) if ingredient.startswith("#") else frozenset({ingredient})
    if not isinstance(ingredient, dict):
        return frozenset({WILDCARD})
    if "item" in ingredient:
        return frozenset({ingredient["item"]})
    if "tag" in ingredient:
        return tags.items(ingredient["tag"])
    kind = ingredient.get("type", "")
    if kind == "neoforge:compound":
        out = set()
        for child in ingredient.get("children", []):
            out |= ingredient_items(child, tags, notes)
        return frozenset(out)
    if kind in ("neoforge:strict_nbt", "neoforge:data_component", "neoforge:partial_nbt") and "item" in ingredient.get("item", {}):
        return frozenset({ingredient["item"]["item"]})
    notes.add(f"ingredient type {kind or '?'} treated as matching anything")
    return frozenset({WILDCARD})


def intersect(a: frozenset, b: frozenset) -> bool:
    return WILDCARD in a or WILDCARD in b or bool(a & b)


# ---------------------------------------------------------------- shapes


def shaped_grid(recipe: dict, tags: Tags, notes: set):
    """effects: returns the recipe's grid as rows of item sets (None for an
    empty cell), shrunk to its content, or None if it is not readable."""
    pattern = recipe.get("pattern")
    key = recipe.get("key", {})
    if not isinstance(pattern, list) or not pattern:
        return None
    cells = {}
    for symbol, ingredient in key.items():
        cells[symbol] = ingredient_items(ingredient, tags, notes)
    rows = []
    for row in pattern:
        rows.append([None if c == " " else cells.get(c, frozenset({WILDCARD})) for c in row])
    # shrink: drop empty leading/trailing rows and columns
    while rows and all(c is None for c in rows[0]):
        rows.pop(0)
    while rows and all(c is None for c in rows[-1]):
        rows.pop()
    if not rows:
        return None
    width = max(len(r) for r in rows)
    rows = [r + [None] * (width - len(r)) for r in rows]
    while width and all(r[0] is None for r in rows):
        rows = [r[1:] for r in rows]
        width -= 1
    while width and all(r[-1] is None for r in rows):
        rows = [r[:-1] for r in rows]
        width -= 1
    return rows


def grids_collide(a, b) -> bool:
    """Two shrunk grids accept a common arrangement: same size, every cell
    pair both empty or overlapping, tried against b's mirror too."""
    if len(a) != len(b) or len(a[0]) != len(b[0]):
        return False
    for candidate in (b, [list(reversed(r)) for r in b]):
        if all(
            (x is None and y is None) or (x is not None and y is not None and intersect(x, y))
            for ra, rb in zip(a, candidate) for x, y in zip(ra, rb)
        ):
            return True
    return False


def can_match_all(left: list, right: list) -> bool:
    """A perfect matching between two equal-length lists of item sets, pairs
    that intersect (n <= 9, augmenting paths)."""
    if len(left) != len(right):
        return False
    match = [-1] * len(right)

    def try_assign(i: int, seen: set) -> bool:
        for j in range(len(right)):
            if j in seen or not intersect(left[i], right[j]):
                continue
            seen.add(j)
            if match[j] == -1 or try_assign(match[j], seen):
                match[j] = i
                return True
        return False

    return all(try_assign(i, set()) for i in range(len(left)))


def shapeless_list(recipe: dict, tags: Tags, notes: set):
    ingredients = recipe.get("ingredients")
    if not isinstance(ingredients, list) or not ingredients:
        return None
    return [ingredient_items(i, tags, notes) for i in ingredients]


def collides(ours: dict, theirs: dict, tags: Tags, notes: set) -> bool:
    our_type, their_type = ours.get("type"), theirs.get("type")
    if our_type == "minecraft:crafting_shaped" and their_type == "minecraft:crafting_shaped":
        a, b = shaped_grid(ours, tags, notes), shaped_grid(theirs, tags, notes)
        return a is not None and b is not None and grids_collide(a, b)
    if our_type == "minecraft:crafting_shapeless" and their_type == "minecraft:crafting_shapeless":
        a, b = shapeless_list(ours, tags, notes), shapeless_list(theirs, tags, notes)
        return a is not None and b is not None and can_match_all(a, b)
    # A shapeless recipe accepts any arrangement, so a shaped grid with the
    # same filled cells answers to both.
    if {our_type, their_type} == {"minecraft:crafting_shaped", "minecraft:crafting_shapeless"}:
        shaped, loose = (ours, theirs) if our_type == "minecraft:crafting_shaped" else (theirs, ours)
        grid = shaped_grid(shaped, tags, notes)
        flat = shapeless_list(loose, tags, notes)
        if grid is None or flat is None:
            return False
        filled = [c for row in grid for c in row if c is not None]
        return can_match_all(filled, flat)
    return False


# ---------------------------------------------------------------- main


def main(argv) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--ours", default=str(DEFAULT_OURS), help="directory holding data/<ns>/recipe (default: the generated resources)")
    parser.add_argument("--mods", action="append", default=[], help="directory of mod jars; repeatable")
    parser.add_argument("--vanilla", default=DEFAULT_VANILLA, help="the vanilla server jar (glob)")
    parser.add_argument("--neoforge", default=DEFAULT_NEOFORGE, help="the NeoForge universal jar (glob), for the c: tags")
    parser.add_argument("--namespace", default="rangedweaponsmod")
    args = parser.parse_args(argv)

    ours: dict = {}
    theirs: dict = {}
    raw_tags: dict = {}
    read_dir_tree(Path(args.ours), "ours", ours, raw_tags)
    read_dir_tree(ROOT / "src/main/resources", "ours", {}, raw_tags)
    for pattern in (args.vanilla, args.neoforge):
        for jar in sorted(glob.glob(pattern))[-1:]:
            with zipfile.ZipFile(jar) as archive:
                read_zip_tree(archive, Path(jar).name, theirs, raw_tags)
    jars = 0
    for directory in args.mods:
        for jar in sorted(Path(directory).glob("*.jar")):
            jars += 1
            try:
                with zipfile.ZipFile(jar) as archive:
                    read_zip_tree(archive, jar.name, theirs, raw_tags)
            except zipfile.BadZipFile:
                print(f"skipped (not a zip): {jar.name}")
    # Our own copies, if the pack still carries an older build, are not foreign.
    theirs = {rid: v for rid, v in theirs.items() if not rid.startswith(args.namespace + ":")}

    tags = Tags(raw_tags)
    notes: set = set()
    hits = 0
    ours_sorted = sorted(rid for rid in ours if rid.startswith(args.namespace + ":"))
    for rid in ours_sorted:
        _, recipe = ours[rid]
        for their_id, (source, their_recipe) in theirs.items():
            if collides(recipe, their_recipe, tags, notes):
                hits += 1
                print(f"COLLISION {rid}  <->  {their_id}  ({source})")
    print(f"checked {len(ours_sorted)} of ours against {len(theirs)} recipes from {jars} jars plus vanilla and NeoForge")
    for note in sorted(notes):
        print(f"note: {note}")
    if tags.unresolved:
        print(f"note: {len(tags.unresolved)} tags could not be resolved (treated as empty): {', '.join(sorted(tags.unresolved)[:8])}{' ...' if len(tags.unresolved) > 8 else ''}")
    print("no collisions" if hits == 0 else f"{hits} collision(s)")
    return 1 if hits else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
