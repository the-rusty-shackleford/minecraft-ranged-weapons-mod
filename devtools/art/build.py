"""The art, as code: textures, the voxel model and the sounds of the machine gun.

Run from the repository root:

    uv run --no-project python devtools/art/build.py

Everything it writes lands under src/main/resources/assets/rangedweaponsmod/
and is committed; this script is the source of truth for those files, and the
photo booth run (`./gradlew photoBooth`) is how the result is looked at.
Original work throughout -- nothing here is derived from another mod's assets.
Sounds need ffmpeg with libvorbis on the PATH.
"""
from __future__ import annotations

import json
import math
import struct
import subprocess
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/rangedweaponsmod"
MODID = "rangedweaponsmod"


# ---------------------------------------------------------------- PNG writing

def write_png(path: Path, width: int, height: int, pixels) -> None:
    """pixels: rows of (r, g, b, a) tuples, top row first."""
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


class Noise:
    """A deterministic grain so flat colours read as material, not plastic."""

    def __init__(self, seed: int) -> None:
        self.state = seed & 0xFFFFFFFF

    def next(self) -> float:
        self.state = (1664525 * self.state + 1013904223) & 0xFFFFFFFF
        return self.state / 0xFFFFFFFF


def shade(rgb, delta):
    return tuple(max(0, min(255, c + delta)) for c in rgb)


# ------------------------------------------------------------ the gun's atlas
#
# A 32x32 atlas of 8x8 material patches; the model's faces point at a patch
# by its grid cell. In UV terms (0..16 over the texture) a cell is 4 units.

PALETTE = {
    "metal_mid":   (74, 80, 90),
    "metal_dark":  (43, 47, 54),
    "metal_light": (118, 126, 140),
    "barrel":      (28, 30, 34),
    "wood":        (120, 82, 44),
    "wood_dark":   (78, 54, 32),
    "brass":       (176, 141, 60),
}

CELLS = {  # name -> (column, row)
    "metal_mid": (0, 0), "metal_dark": (1, 0), "metal_light": (2, 0), "barrel": (3, 0),
    "wood": (0, 1), "wood_dark": (1, 1), "brass": (2, 1), "vent": (3, 1),
    "receiver": (0, 2), "grip": (1, 2), "magazine": (2, 2), "muzzle": (3, 2),
}


def gun_atlas():
    noise = Noise(0x4C4A31)
    px = [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]

    def fill(cell, base, grain=6):
        cx, cy = CELLS[cell]
        for y in range(8):
            for x in range(8):
                d = int((noise.next() - 0.5) * 2 * grain)
                px[cy * 8 + y][cx * 8 + x] = (*shade(base, d), 255)

    for name in ("metal_mid", "metal_dark", "metal_light", "barrel"):
        fill(name, PALETTE[name])
    fill("wood", PALETTE["wood"], 10)
    fill("wood_dark", PALETTE["wood_dark"], 8)
    fill("brass", PALETTE["brass"], 8)

    # Vent: dark metal with two rows of cooling holes, lit from above.
    fill("vent", PALETTE["metal_dark"])
    cx, cy = CELLS["vent"]
    for y in (2, 5):
        for x in (1, 4, 7):
            px[cy * 8 + y][cx * 8 + x] = (*PALETTE["barrel"], 255)
            px[cy * 8 + y - 1][cx * 8 + x] = (*shade(PALETTE["metal_light"], -20), 255)

    # Receiver side: mid metal with a seam and a line of rivets.
    fill("receiver", PALETTE["metal_mid"])
    cx, cy = CELLS["receiver"]
    for x in range(8):
        px[cy * 8 + 2][cx * 8 + x] = (*shade(PALETTE["metal_mid"], -22), 255)
        px[cy * 8 + 5][cx * 8 + x] = (*shade(PALETTE["metal_mid"], 18), 255)
    for x in (1, 4, 7):
        px[cy * 8 + 6][cx * 8 + x] = (*PALETTE["metal_light"], 255)

    # Grip: dark wood with a diagonal checkering.
    fill("grip", PALETTE["wood_dark"], 6)
    cx, cy = CELLS["grip"]
    for y in range(8):
        for x in range(8):
            if (x + y) % 3 == 0:
                px[cy * 8 + y][cx * 8 + x] = (*shade(PALETTE["wood_dark"], -18), 255)

    # Magazine: dark metal with a lighter band where the rounds sit.
    fill("magazine", PALETTE["metal_dark"])
    cx, cy = CELLS["magazine"]
    for x in range(8):
        px[cy * 8 + 3][cx * 8 + x] = (*shade(PALETTE["metal_mid"], 4), 255)
        px[cy * 8 + 4][cx * 8 + x] = (*shade(PALETTE["metal_mid"], -6), 255)

    # Muzzle: black with a bright ring.
    fill("muzzle", PALETTE["barrel"])
    cx, cy = CELLS["muzzle"]
    for x in (0, 7):
        for y in range(8):
            px[cy * 8 + y][cx * 8 + x] = (*PALETTE["metal_light"], 255)
    return px


def uv(cell):
    cx, cy = CELLS[cell]
    return [cx * 4, cy * 4, cx * 4 + 4, cy * 4 + 4]


# ------------------------------------------------------------- the round icon

def round_icon():
    """A cartridge, 16x16, standing upright: brass case, copper bullet, a primer."""
    brass, brass_l, brass_d = (176, 141, 60), (214, 180, 96), (128, 98, 38)
    copper, copper_l, copper_d = (168, 96, 62), (204, 136, 96), (120, 64, 40)
    px = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[y][x] = (*c, 255)

    # The case: columns 6..9 from row 6 to 14, straight, lit from the left.
    for y in range(6, 15):
        for x in range(6, 10):
            c = brass_l if x == 6 else brass_d if x == 9 else brass
            put(x, y, c)
    # The rim and primer.
    for x in range(5, 11):
        put(x, 14, brass_d)
    put(7, 15, brass_d)
    put(8, 15, (60, 60, 60))
    # The bullet: rows 1..5, narrowing to a point.
    for y, (x0, x1) in zip(range(1, 6), [(8, 8), (7, 9), (7, 9), (7, 10), (7, 10)]):
        for x in range(x0, x1 + 1):
            c = copper_l if x == x0 else copper_d if x == x1 else copper
            put(x, y, c)
    put(8, 0, copper_d)
    # A neck line where bullet meets case.
    for x in range(7, 11):
        put(x, 6, brass_d)
    return px


def small_round_icon():
    """A pistol cartridge, 16x16, upright: a stubby brass case and a round-nosed copper bullet."""
    brass, brass_l, brass_d = (176, 141, 60), (214, 180, 96), (128, 98, 38)
    copper, copper_l, copper_d = (168, 96, 62), (204, 136, 96), (120, 64, 40)
    px = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[y][x] = (*c, 255)

    for y in range(8, 14):
        for x in range(6, 10):
            put(x, y, brass_l if x == 6 else brass_d if x == 9 else brass)
    for x in range(5, 11):
        put(x, 13, brass_d)
    put(7, 14, brass_d)
    put(8, 14, (60, 60, 60))
    for y, (x0, x1) in zip(range(4, 8), [(7, 8), (6, 9), (6, 9), (6, 9)]):
        for x in range(x0, x1 + 1):
            put(x, y, copper_l if x == x0 else copper_d if x == x1 else copper)
    for x in range(6, 10):
        put(x, 8, brass_d)
    return px


def shell_icon():
    """A shotgun shell, 16x16, upright: a red plastic hull with a crimped top over a brass head."""
    red, red_l, red_d = (168, 40, 36), (206, 70, 60), (120, 26, 24)
    brass, brass_l, brass_d = (176, 141, 60), (214, 180, 96), (128, 98, 38)
    px = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[y][x] = (*c, 255)

    for y in range(2, 11):
        for x in range(5, 11):
            put(x, y, red_l if x == 5 else red_d if x == 10 else red)
    for x in range(5, 11):
        put(x, 2, red_d)
    for x in (6, 8):
        put(x, 1, red_d)
    for y in range(11, 15):
        for x in range(5, 11):
            put(x, y, brass_l if x == 5 else brass_d if x == 10 else brass)
    for x in range(4, 12):
        put(x, 14, brass_d)
    put(7, 15, brass_d)
    put(8, 15, (60, 60, 60))
    return px


def scope_mask(size=256, radius=0.46, edge=6):
    """The scope's mask: opaque black to the edges, clear through a circle,
    a soft rim between; a fine crosshair line is not drawn, the game's is."""
    px = [[(0, 0, 0, 255) for _ in range(size)] for _ in range(size)]
    c = (size - 1) / 2.0
    r = radius * size
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            if d < r - edge:
                px[y][x] = (0, 0, 0, 0)
            elif d < r:
                a = int(255 * (d - (r - edge)) / edge)
                px[y][x] = (0, 0, 0, a)
    return px


# ------------------------------------------------------------ the voxel model

def box(name, frm, to, faces, rotation=None):
    element = {"name": name, "from": frm, "to": to,
               "faces": {side: {"uv": uv(cell), "texture": "#gun"} for side, cell in faces.items()}}
    if rotation:
        element["rotation"] = rotation
    return element


ALL = lambda cell: {s: cell for s in ("north", "south", "east", "west", "up", "down")}


def sides(side_cell, other_cell):
    return {"north": side_cell, "south": side_cell, "east": other_cell, "west": other_cell,
            "up": other_cell, "down": other_cell}


# Calibrated with the photo booth's axes model, not reasoned from the
# format: in first person a Y rotation of 90 points +X downrange; carried
# two-handed (the crossbow hold) a Y rotation of 90 points +X forward; held
# one-handed (the plain item pose) the arm's frame is turned, and it is a Z
# rotation of 90. Translation and scale are per gun.
def display(first_translation, first_scale, third_rotation, third_translation, third_scale, gui_scale):
    return {
        "firstperson_righthand": {"rotation": [0, 92, 0], "translation": first_translation, "scale": [first_scale] * 3},
        "thirdperson_righthand": {"rotation": third_rotation, "translation": third_translation, "scale": [third_scale] * 3},
        "gui": {"rotation": [0, 0, 0], "translation": [-0.8, 0, 0], "scale": [gui_scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.35, 0.35, 0.35]},
        "fixed": {"rotation": [0, 0, 0], "translation": [-0.8, 0, 0], "scale": [0.45, 0.45, 0.45]},
    }


def gun_model(elements, display_block):
    return {
        "credit": "Ranged Weapons Mod, generated by devtools/art/build.py",
        "texture_size": [32, 32],
        "textures": {"gun": f"{MODID}:item/gun_atlas", "particle": f"{MODID}:item/gun_atlas"},
        "elements": elements,
        "display": display_block,
    }


TWO_HANDED_DISPLAY = display([1.0, -1.0, -3.0], 0.58, [0, 90, 0], [0, 1.0, 1.0], 0.6, 0.46)
# The shotgun and the rifles sit lower in the hand than the machine gun's
# top-fed receiver does, so they are carried a little higher.
LONG_GUN_DISPLAY = display([1.0, -0.5, -3.0], 0.58, [0, 90, 0], [0, 1.0, 1.0], 0.6, 0.46)
# Smaller and nearer in first person; the one-handed pose's frame in third.
PISTOL_DISPLAY = display([0.5, 2.5, -1.2], 0.5, [0, 0, 90], [0, 0, 0], 0.5, 0.7)


def pistol_model():
    """A boxy service pistol along +X: slide over a frame, a raked grip with
    its magazine base showing, a trigger in its guard, sights fore and aft.
    From x=2 (the back of the slide) to x=13.5 (the muzzle) at z=8."""
    elements = [
        box("slide", [2, 6.5, 6.9], [12, 9.5, 9.1], sides("receiver", "metal_mid")),
        box("slide_serrations", [2.5, 7, 6.8], [4.5, 9, 9.2], ALL("vent")),
        box("frame", [3, 5, 7], [11, 6.5, 9], ALL("metal_dark")),
        box("barrel_tip", [12, 7.3, 7.3], [13.5, 8.7, 8.7], ALL("muzzle")),
        box("grip", [3, 0, 7], [6, 5, 9], ALL("grip"),
            rotation={"origin": [4.5, 5, 8], "axis": "z", "angle": -22.5}),
        box("magazine_base", [3.2, -0.7, 7.2], [5.9, 0.1, 8.8], ALL("metal_dark"),
            rotation={"origin": [4.5, 5, 8], "axis": "z", "angle": -22.5}),
        box("trigger_guard_bottom", [6.5, 3.4, 7.5], [9.6, 4, 8.5], ALL("metal_dark")),
        box("trigger_guard_front", [9, 3.4, 7.5], [9.6, 5, 8.5], ALL("metal_dark")),
        box("trigger", [7.3, 4, 7.7], [7.9, 5, 8.3], ALL("brass")),
        box("rear_sight", [2.4, 9.5, 7.6], [3.4, 10.3, 8.4], ALL("metal_dark")),
        box("front_sight", [11, 9.5, 7.7], [11.7, 10.4, 8.3], ALL("metal_dark")),
    ]
    return gun_model(elements, PISTOL_DISPLAY)


def shotgun_model():
    """A pump shotgun along +X: a long barrel over a tube magazine, a wooden
    pump on the tube, a wooden stock, a bead at the muzzle. From x=-8 (the
    butt) to x=27.5 (the muzzle) at z=8."""
    elements = [
        box("stock_butt", [-8, 4, 6.8], [-2, 9.5, 9.2], sides("wood", "wood_dark")),
        box("stock_neck", [-2, 5.5, 7], [1, 9.5, 9], ALL("wood")),
        box("receiver", [1, 5.5, 6.6], [8, 10, 9.4], sides("receiver", "metal_mid")),
        box("ejection_port", [3, 7.5, 9.35], [6, 9.2, 9.5], ALL("barrel")),
        box("barrel", [8, 8, 7.2], [26, 9.6, 8.8], ALL("barrel")),
        box("tube_magazine", [8, 6, 7.3], [22, 7.4, 8.7], ALL("metal_dark")),
        box("pump", [12, 5.4, 6.7], [18, 7.7, 9.3], ALL("grip")),
        box("muzzle", [26, 7.8, 7], [27.5, 9.8, 9], ALL("muzzle")),
        box("bead", [25, 9.6, 7.7], [25.8, 10.3, 8.3], ALL("brass")),
        box("trigger_guard_bottom", [3.5, 3.6, 7.5], [7, 4.2, 8.5], ALL("metal_dark")),
        box("trigger_guard_front", [6.4, 3.6, 7.5], [7, 5.5, 8.5], ALL("metal_dark")),
        box("trigger", [4.8, 4.2, 7.7], [5.4, 5.5, 8.3], ALL("brass")),
        box("grip", [1, 1.5, 7], [3.5, 5.5, 9], ALL("grip"),
            rotation={"origin": [2.2, 5.5, 8], "axis": "z", "angle": -22.5}),
    ]
    return gun_model(elements, LONG_GUN_DISPLAY)


def rifle_elements(barrel_to, with_magazine=True):
    """The rifle both rifles share: wooden stock, receiver, a wooden handguard
    on a long barrel, iron sights. The scoped one adds to it."""
    elements = [
        box("stock_butt", [-9, 3.5, 6.8], [-3, 9.5, 9.2], sides("wood", "wood_dark")),
        box("stock_neck", [-3, 5.5, 7], [-1, 9.5, 9], ALL("wood")),
        box("receiver", [-1, 5.5, 6.6], [9, 10.5, 9.4], sides("receiver", "metal_mid")),
        box("barrel", [9, 7.5, 7.2], [barrel_to, 9, 8.8], ALL("barrel")),
        box("handguard", [9, 6.5, 6.7], [17, 10, 9.3], ALL("wood_dark")),
        box("gas_block", [barrel_to - 4, 7, 7], [barrel_to - 2.5, 9.6, 9], ALL("metal_dark")),
        box("front_sight", [barrel_to - 3.6, 9.6, 7.7], [barrel_to - 2.9, 11.2, 8.3], ALL("metal_dark")),
        box("muzzle", [barrel_to, 7.3, 7], [barrel_to + 1.5, 9.2, 9], ALL("muzzle")),
        box("trigger_guard_bottom", [2, 3.6, 7.5], [5.5, 4.2, 8.5], ALL("metal_dark")),
        box("trigger_guard_front", [4.9, 3.6, 7.5], [5.5, 5.5, 8.5], ALL("metal_dark")),
        box("trigger", [3.3, 4.2, 7.7], [3.9, 5.5, 8.3], ALL("brass")),
        box("grip", [-0.5, 1.5, 7], [2, 5.5, 9], ALL("grip"),
            rotation={"origin": [0.7, 5.5, 8], "axis": "z", "angle": -22.5}),
    ]
    if with_magazine:
        elements.append(box("magazine", [5.5, 1, 7], [8.5, 5.5, 9], sides("magazine", "metal_dark"),
                            rotation={"origin": [7, 5.5, 8], "axis": "z", "angle": 22.5}))
    return elements


def rifle_model():
    """A semi-automatic rifle along +X, x=-9 to x=28.5 at z=8, with a
    detachable magazine and iron sights."""
    elements = rifle_elements(27) + [
        box("rear_sight", [0, 10.5, 7.6], [1.2, 12, 8.4], ALL("metal_dark")),
    ]
    return gun_model(elements, LONG_GUN_DISPLAY)


def scoped_rifle_model():
    """A bolt-action rifle along +X, x=-9 to x=31.5 at z=8: the rifle with a
    longer barrel, a scope on two rings over the receiver, a bolt handle out
    to the right, and no magazine below."""
    elements = rifle_elements(30, with_magazine=False) + [
        box("scope_ring_rear", [1, 10.5, 7], [2.2, 14.2, 9], ALL("metal_dark")),
        box("scope_ring_front", [7, 10.5, 7], [8.2, 14.2, 9], ALL("metal_dark")),
        box("scope_tube", [-1, 11.8, 7.2], [10.5, 13.8, 8.8], ALL("barrel")),
        box("scope_objective", [10.5, 11.5, 6.9], [12.5, 14.1, 9.1], ALL("metal_dark")),
        box("scope_lens", [12.5, 11.9, 7.3], [12.8, 13.7, 8.7], ALL("metal_light")),
        box("scope_eyepiece", [-2.5, 11.6, 7.1], [-1, 14, 8.9], ALL("metal_dark")),
        box("bolt_handle", [4, 8, 9.4], [5, 9, 12.5], ALL("metal_light")),
        box("bolt_knob", [3.6, 7.6, 12.3], [5.4, 9.4, 13.6], ALL("metal_dark")),
        box("internal_magazine", [4, 4.8, 7.2], [8, 5.5, 8.8], ALL("metal_dark")),
    ]
    return gun_model(elements, LONG_GUN_DISPLAY)


def machine_gun_model():
    """A belt-fed-looking light machine gun laid along +X: wooden stock, boxy
    receiver with a top-mounted magazine, a long ventilated barrel jacket, a
    bipod. Coordinates are model units (16 to a block), the gun runs from
    x=-7 (butt) to x=26.5 (muzzle) at z=8."""
    all_ = ALL

    elements = [
        box("stock_butt", [-7, 4.5, 6.8], [-2, 10, 9.2], sides("wood", "wood_dark")),
        box("stock_neck", [-2, 6, 7], [3, 9.5, 9], all_("wood")),
        box("receiver", [3, 5.5, 6.5], [14, 10.5, 9.5], sides("receiver", "metal_mid")),
        box("rail", [4, 10.5, 7.2], [12, 11.2, 8.8], all_("metal_dark")),
        box("rear_sight", [5, 11.2, 7.6], [6, 12.6, 8.4], all_("metal_dark")),
        box("magazine", [7, 11.2, 7], [10.5, 16.5, 9], sides("magazine", "metal_dark")),
        box("magazine_lip", [6.8, 10.3, 6.9], [10.7, 11.3, 9.1], all_("metal_dark")),
        box("trigger_guard_front", [6, 3.4, 7.5], [6.6, 5.5, 8.5], all_("metal_dark")),
        box("trigger_guard_bottom", [6, 3.4, 7.5], [9.5, 4, 8.5], all_("metal_dark")),
        box("trigger", [7.4, 4, 7.7], [8, 5.5, 8.3], all_("brass")),
        box("grip", [3, 1, 7], [5.2, 5.5, 9], all_("grip"),
            rotation={"origin": [4.1, 5.5, 8], "axis": "z", "angle": -22.5}),
        box("barrel", [14, 7.2, 7.2], [25, 8.8, 8.8], all_("barrel")),
        box("jacket", [14, 6.5, 6.5], [21, 9.5, 9.5], all_("vent")),
        box("gas_block", [21, 6.8, 6.8], [22.5, 9.6, 9.2], all_("metal_dark")),
        box("front_sight", [21.5, 9.6, 7.7], [22.2, 11.2, 8.3], all_("metal_dark")),
        box("muzzle", [25, 6.9, 6.9], [26.5, 9.1, 9.1], all_("muzzle")),
        box("bipod_left", [18, 0.5, 5.2], [18.8, 7, 6], all_("metal_dark"),
            rotation={"origin": [18.4, 7, 5.6], "axis": "x", "angle": 22.5}),
        box("bipod_right", [18, 0.5, 10], [18.8, 7, 10.8], all_("metal_dark"),
            rotation={"origin": [18.4, 7, 10.4], "axis": "x", "angle": -22.5}),
    ]
    return gun_model(elements, TWO_HANDED_DISPLAY)


GUN_MODELS = {
    "pistol": pistol_model,
    "shotgun": shotgun_model,
    "rifle": rifle_model,
    "scoped_rifle": scoped_rifle_model,
    "machine_gun": machine_gun_model,
}


AXES_COLOURS = {"red": (0, 0), "green": (1, 0), "blue": (2, 0), "yellow": (3, 0), "magenta": (0, 1), "gray": (1, 1)}


def axes_texture():
    rgb = {"red": (230, 40, 40), "green": (40, 200, 60), "blue": (50, 80, 230), "yellow": (240, 220, 40),
           "magenta": (220, 50, 220), "gray": (120, 120, 120)}
    px = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
    for name, (cx, cy) in AXES_COLOURS.items():
        for y in range(4):
            for x in range(4):
                px[cy * 4 + y][cx * 4 + x] = (*rgb[name], 255)
    return px


def axes_model():
    def cell_uv(name):
        cx, cy = AXES_COLOURS[name]
        return [cx * 4, cy * 4, cx * 4 + 4, cy * 4 + 4]

    def cube(name, frm, to, colour):
        return {"name": name, "from": frm, "to": to,
                "faces": {s: {"uv": cell_uv(colour), "texture": "#axes"} for s in ("north", "south", "east", "west", "up", "down")}}

    return {
        "textures": {"axes": "rangedweaponsmod_gametest:item/axes", "particle": "rangedweaponsmod_gametest:item/axes"},
        "elements": [
            cube("shaft", [0, 7, 7], [24, 9, 9], "gray"),
            cube("tip_plus_x", [24, 5, 5], [30, 11, 11], "red"),
            cube("tail_minus_x", [-6, 5, 5], [0, 11, 11], "blue"),
            cube("plus_y", [10, 9, 7], [14, 15, 9], "green"),
            cube("plus_z", [10, 7, 9], [14, 9, 15], "yellow"),
            cube("minus_z", [10, 7, 1], [14, 9, 7], "magenta"),
        ],
    }


# The candidates the booth photographs: name -> display block. Rotations are
# the question; translation and scale are held at one plausible value. Read
# so far: first person, [0, 90, 0] points +X downrange; two-handed (the
# crossbow hold), [0, 90, 0] points +X forward; one-handed (the plain item
# pose, -PboothPose=one), [0, 0, 90] points +X forward, a little up.
BOOTH_VARIANTS = {
    "a": {"firstperson_righthand": {"rotation": [0, 0, 0], "translation": [1.5, 1.5, 1.5], "scale": [0.55, 0.55, 0.55]},
          "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1.5], "scale": [0.6, 0.6, 0.6]}},
    "b": {"firstperson_righthand": {"rotation": [0, 90, 0], "translation": [1.5, 1.5, 1.5], "scale": [0.55, 0.55, 0.55]},
          "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 2.5, 1.5], "scale": [0.6, 0.6, 0.6]}},
    "c": {"firstperson_righthand": {"rotation": [0, -90, 0], "translation": [1.5, 1.5, 1.5], "scale": [0.55, 0.55, 0.55]},
          "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 2.5, 1.5], "scale": [0.6, 0.6, 0.6]}},
    "d": {"firstperson_righthand": {"rotation": [0, 180, 0], "translation": [1.5, 1.5, 1.5], "scale": [0.55, 0.55, 0.55]},
          "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 2.5, 1.5], "scale": [0.6, 0.6, 0.6]}},
    "e": {"firstperson_righthand": {"rotation": [90, 0, 0], "translation": [1.5, 1.5, 1.5], "scale": [0.55, 0.55, 0.55]},
          "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [0, 2.5, 1.5], "scale": [0.6, 0.6, 0.6]}},
    "f": {"firstperson_righthand": {"rotation": [0, 0, 90], "translation": [1.5, 1.5, 1.5], "scale": [0.55, 0.55, 0.55]},
          "thirdperson_righthand": {"rotation": [0, 0, 90], "translation": [0, 2.5, 1.5], "scale": [0.6, 0.6, 0.6]}},
}


# ------------------------------------------------------------------- sounds

RATE = 44100


def synth(seconds, fn):
    n = int(RATE * seconds)
    return [max(-1.0, min(1.0, fn(i / RATE, i / n))) for i in range(n)]


def write_ogg(path: Path, samples) -> None:
    """Writes 16-bit mono PCM through ffmpeg into Ogg Vorbis."""
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = b"".join(struct.pack("<h", int(s * 32767)) for s in samples)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(RATE), "-ac", "1",
                    "-i", "pipe:0", "-c:a", "libvorbis", "-q:a", "5", str(path)],
                   input=pcm, check=True)


def lowpass(samples, hz):
    """One-pole lowpass; hz is the -3 dB point."""
    a = 1.0 - math.exp(-2.0 * math.pi * hz / RATE)
    out, y = [], 0.0
    for x in samples:
        y += (x - y) * a
        out.append(y)
    return out


def lowpass2(samples, hz):
    """Two poles: 12 dB an octave, enough to keep noise from reading as hiss."""
    return lowpass(lowpass(samples, hz), hz)


def highpass(samples, hz):
    """One-pole highpass: the input minus its lowpass."""
    return [x - y for x, y in zip(samples, lowpass(samples, hz))]


def saturate(samples, drive):
    """Soft clipping: tanh, so the peaks compress and grow harmonics instead of cracking."""
    top = math.tanh(drive)
    return [math.tanh(x * drive) / top for x in samples]


def normalize(samples, peak=0.95):
    top = max(abs(x) for x in samples) or 1.0
    return [x * peak / top for x in samples]


def slapback(samples, delays_ms, gains):
    """Discrete early reflections: the outdoors answering the shot."""
    out = list(samples)
    for ms, g in zip(delays_ms, gains):
        d = int(RATE * ms / 1000)
        for i in range(d, len(out)):
            out[i] += samples[i - d] * g
    return out


def gunshot(seed, seconds, crack, body, sweep_hz, sub_hz, sub_decay, tail_hz, tail_decay, bolt):
    """
    A gunshot in five parts, mixed then saturated:

    crack  the first few milliseconds of full-band noise, the supersonic snap
    body   noise between 150 Hz and 1.5 kHz decaying fast, with a sine that
           sweeps from sweep_hz down to sub_hz in the first tens of ms --
           the muzzle blast, and where the "boom" lives
    sub    a sine at sub_hz with a slow decay, the weight in the chest
    tail   lowpassed noise decaying slowly with slapback, the report
           rolling away
    bolt   a metallic ring 30 ms in, the action cycling
    """
    n = int(RATE * seconds)
    noise = Noise(seed)
    raw = [noise.next() * 2 - 1 for _ in range(n)]
    ts = [i / RATE for i in range(n)]

    crack_part = [r * math.exp(-t * 900) * crack for r, t in zip(raw, ts)]

    banded = highpass(lowpass2(raw, 1500), 150)
    body_part = [b * math.exp(-t * 35) * body for b, t in zip(banded, ts)]

    phase, sweep_part = 0.0, []
    for t in ts:
        f = sub_hz + (sweep_hz - sub_hz) * math.exp(-t * 30)
        phase += 2 * math.pi * f / RATE
        sweep_part.append(math.sin(phase) * math.exp(-t * 28) * 0.9)

    sub_part = [math.sin(2 * math.pi * sub_hz * t) * math.exp(-t * sub_decay) * (1 - math.exp(-t * 400)) * 0.8
                for t in ts]

    tail_src = lowpass2(raw, tail_hz)
    tail_part = [s * math.exp(-t * tail_decay) * (1 - math.exp(-t * 150)) * 0.7 for s, t in zip(tail_src, ts)]
    tail_part = slapback(tail_part, (58, 131, 227), (0.4, 0.25, 0.12))

    bolt_part = [(math.sin(2 * math.pi * 2600 * (t - 0.03)) + 0.5 * math.sin(2 * math.pi * 4100 * (t - 0.03)))
                 * math.exp(-(t - 0.03) * 350) * bolt if t >= 0.03 else 0.0 for t in ts]

    mix = [c + b + s + u + tl + bo for c, b, s, u, tl, bo in
           zip(crack_part, body_part, sweep_part, sub_part, tail_part, bolt_part)]
    # The saturation grows harmonics without limit; roll off the fizz.
    return normalize(lowpass2(saturate(mix, 2.4), 7000))


def machine_gun_shot():
    # Short and hard: the tail is cut so seven a second stay distinct.
    return gunshot(seed=0xB4E7, seconds=0.42, crack=1.0, body=1.6, sweep_hz=190, sub_hz=52,
                   sub_decay=22, tail_hz=1100, tail_decay=11, bolt=0.3)


def pistol_shot():
    # Snappier and higher than the machine gun; a short slide clack.
    return gunshot(seed=0x51A7, seconds=0.32, crack=1.0, body=1.2, sweep_hz=260, sub_hz=70,
                   sub_decay=30, tail_hz=1500, tail_decay=16, bolt=0.45)


def shotgun_shot():
    # A deep boom with weight and a long roll; almost no mechanism in it.
    return gunshot(seed=0x5406, seconds=0.62, crack=0.8, body=2.2, sweep_hz=140, sub_hz=44,
                   sub_decay=12, tail_hz=700, tail_decay=7, bolt=0.12)


def rifle_shot():
    # A hard crack and a long tail: the sound of a round going a long way.
    return gunshot(seed=0x21F1, seconds=0.5, crack=1.3, body=1.5, sweep_hz=210, sub_hz=50,
                   sub_decay=20, tail_hz=1200, tail_decay=9, bolt=0.5)


def scoped_rifle_shot():
    # Heavier still, and the bolt rings as it is worked.
    return gunshot(seed=0x5C0E, seconds=0.6, crack=1.4, body=1.7, sweep_hz=180, sub_hz=46,
                   sub_decay=16, tail_hz=1000, tail_decay=8, bolt=0.7)


def far_shot():
    # The same shot a hundred blocks off: no crack, the body dulled, and a
    # long low tail that is mostly reflections.
    shot = gunshot(seed=0x7A5, seconds=1.1, crack=0.0, body=0.5, sweep_hz=120, sub_hz=48,
                   sub_decay=7, tail_hz=350, tail_decay=4, bolt=0.0)
    return normalize(lowpass(shot, 600), 0.85)


def empty_click():
    noise = Noise(0x11)

    def fn(t, u):
        tick = math.sin(2 * math.pi * 2400 * t) * math.exp(-t * 400)
        body = (noise.next() * 2 - 1) * 0.25 * math.exp(-t * 300)
        return 0.7 * (tick + body)

    return synth(0.06, fn)


def reload_start():
    noise = Noise(0x33)
    state = {"lp": 0.0}

    def fn(t, u):
        raw = noise.next() * 2 - 1
        state["lp"] += (raw - state["lp"]) * 0.2
        # Magazine release clack, then the slide of it coming out.
        clack = math.sin(2 * math.pi * 900 * t) * math.exp(-t * 120) * 0.8
        slide = state["lp"] * 0.35 * (1 if 0.08 < t < 0.28 else 0) * math.sin(math.pi * (t - 0.08) / 0.2)
        return 0.8 * (clack + slide)

    return synth(0.32, fn)


def reload_end():
    noise = Noise(0x55)
    state = {"lp": 0.0}

    def fn(t, u):
        raw = noise.next() * 2 - 1
        state["lp"] += (raw - state["lp"]) * 0.25
        # The magazine seating home: a slide, then a firm double clack.
        slide = state["lp"] * 0.3 * (1 if t < 0.12 else 0) * math.sin(math.pi * t / 0.12)
        c1 = math.sin(2 * math.pi * 700 * t) * math.exp(-(t - 0.13) * 150) * (1 if t >= 0.13 else 0)
        c2 = math.sin(2 * math.pi * 1100 * t) * math.exp(-(t - 0.2) * 200) * (1 if t >= 0.2 else 0)
        return 0.8 * (slide + 0.9 * c1 + 0.6 * c2)

    return synth(0.3, fn)


SOUNDS = {
    "machine_gun_shot": machine_gun_shot,
    "pistol_shot": pistol_shot,
    "shotgun_shot": shotgun_shot,
    "rifle_shot": rifle_shot,
    "scoped_rifle_shot": scoped_rifle_shot,
    "far_shot": far_shot,
    "empty_click": empty_click,
    "reload_start": reload_start,
    "reload_end": reload_end,
}


def sounds_json():
    return {name: {"subtitle": f"subtitles.{MODID}.{name}", "sounds": [f"{MODID}:{name}"]} for name in SOUNDS}


# ---------------------------------------------------------------------- main

def main(argv) -> int:
    want = set(argv[1:]) or {"textures", "model", "sounds", "booth"}
    if "textures" in want:
        write_png(ASSETS / "textures/item/gun_atlas.png", 32, 32, gun_atlas())
        write_png(ASSETS / "textures/item/round.png", 16, 16, round_icon())
        write_png(ASSETS / "textures/item/small_round.png", 16, 16, small_round_icon())
        write_png(ASSETS / "textures/item/shell.png", 16, 16, shell_icon())
        write_png(ASSETS / "textures/gui/scope.png", 256, 256, scope_mask())
        stale = ASSETS / "textures/item/machine_gun.png"
        if stale.exists():
            stale.unlink()
        print("textures: gun_atlas.png (32x32), round.png, small_round.png, shell.png (16x16), gui/scope.png (256x256)")
    if "model" in want:
        (ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
        for name, build in GUN_MODELS.items():
            model = build()
            (ASSETS / f"models/item/{name}.json").write_text(json.dumps(model, indent=2) + "\n")
            print(f"model: {name}.json ({len(model['elements'])} elements)")
        for name in ("small_round", "shell"):
            (ASSETS / f"models/item/{name}.json").write_text(json.dumps(
                {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}}, indent=2) + "\n")
    if "booth" in want:
        # Calibration models for the photo booth, as items of the gametest mod:
        # an unmistakable axes model (red tip on +X, blue tail on -X, green
        # on +Y, yellow on +Z, magenta on -Z) under one candidate display
        # transform each, so a screenshot says which way each axis went.
        booth_assets = ROOT / "src/gametest/resources/assets/rangedweaponsmod_gametest"
        write_png(booth_assets / "textures/item/axes.png", 16, 16, axes_texture())
        (booth_assets / "models/item").mkdir(parents=True, exist_ok=True)
        for name, display in BOOTH_VARIANTS.items():
            variant = axes_model()
            variant["display"] = display
            (booth_assets / f"models/item/booth_{name}.json").write_text(json.dumps(variant, indent=2) + "\n")
        print(f"booth: axes model under {', '.join(BOOTH_VARIANTS)}")
    if "sounds" in want:
        for name, fn in SOUNDS.items():
            write_ogg(ASSETS / f"sounds/{name}.ogg", fn())
        (ASSETS / "sounds.json").write_text(json.dumps(sounds_json(), indent=2) + "\n")
        print(f"sounds: {', '.join(SOUNDS)} + sounds.json")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
