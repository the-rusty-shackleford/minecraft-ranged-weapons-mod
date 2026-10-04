"""Mutation runs for 2.12.0 (D-0029): apply a set of mutations, run the gametest server, report
which tests failed, restore the sources. From the repository root:

    uv run --no-project python devtools/verification/mutations-2.12.0.py A|B|C

Copyright 2026 Rusty Shackleford and nfx. AGPL-3.0-or-later.
"""
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SRC = REPO / "src/main/java/com/chunkworks/rangedweaponsmod"
SCRATCH = REPO / "run"

MUTATIONS = {
    "gate": (SRC / "Seeking.java",
             "!LauncherItem.canLock(player, player.getMainHandItem())",
             "!LauncherItem.isLauncher(player.getMainHandItem())"),
    "chip-back": (SRC / "PlayerGunnery.java",
                  "            Carried.giveOrDrop(player, chip);\n",
                  "            // mutated: no chip back\n"),
    "close-weapon": (SRC / "WorkbenchMenu.java",
                     "            clearContainer(player, weapon);\n",
                     "            // mutated: weapon kept\n"),
    "no-wear": (SRC / "LauncherWeapon.java",
                "                LauncherItem.wearChip(player, stack);\n",
                "                // mutated: no wear\n"),
    "view-no-write": (SRC / "WorkbenchMenu.java",
                      "            LauncherItem.fit(launcher, stack);\n            weapon.setChanged();\n",
                      "            weapon.setChanged();   // mutated: no write\n"),
    "wear-always": (SRC / "LauncherWeapon.java",
                    "            if (target != null) {\n                LauncherItem.wearChip(player, stack);\n            }\n",
                    "            LauncherItem.wearChip(player, stack);   // mutated: every launch\n"),
    "creative-needs-chip": (SRC / "LauncherItem.java",
                            "(player.hasInfiniteMaterials() || !chip(launcher).isEmpty())",
                            "(!chip(launcher).isEmpty())"),
    "shift-no-chip": (SRC / "WorkbenchMenu.java",
                      "} else if (ChipItem.isChip(stack) && slots.get(CHIP).mayPlace(stack) && !slots.get(CHIP).hasItem()) {",
                      "} else if (false && ChipItem.isChip(stack)) {"),
    "type-crafting": (SRC / "AssemblyRecipe.java",
                      "        return TYPE.get();\n",
                      "        return RecipeType.CRAFTING;   // mutated\n"),
}
RUNS = {
    "A": ["gate", "chip-back", "close-weapon"],
    "B": ["no-wear", "view-no-write"],
    "C": ["wear-always", "creative-needs-chip", "shift-no-chip", "type-crafting"],
}


def main(run: str) -> int:
    names = RUNS[run]
    backups = {}
    try:
        for name in names:
            path, old, new = MUTATIONS[name]
            if path not in backups:
                backups[path] = path.read_text()
            text = path.read_text()
            assert text.count(old) == 1, (name, old)
            path.write_text(text.replace(old, new))
        env = dict(os.environ, JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64")
        out = SCRATCH / f"mutation-{run}.log"
        with out.open("w") as f:
            code = subprocess.run(["./gradlew", "runGameTestServer", "--console=plain", "-q"], cwd=REPO, env=env,
                                  stdout=f, stderr=subprocess.STDOUT).returncode
        log = (REPO / "run/logs/latest.log").read_text(errors="replace")
        shutil.copy(REPO / "run/logs/latest.log", SCRATCH / f"mutation-{run}-server.log")
        failed = sorted(set(re.findall(r"(\w+) failed at", log)))
        summary = re.findall(r"(\d+ required tests failed.*|All \d+ required tests passed.*)", log)
        print(f"run {run} mutations {names}: gradle exit {code}")
        print("summary:", summary[-1] if summary else "(none)")
        print("failed tests:")
        for t in failed:
            print("  ", t)
        return 0
    finally:
        for path, text in backups.items():
            path.write_text(text)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
