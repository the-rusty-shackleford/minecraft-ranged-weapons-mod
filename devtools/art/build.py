"""The art, as code: textures, the voxel models and the sounds of the guns, and the icons of their parts.

Run from the repository root:

    uv run --no-project python devtools/art/build.py

Everything it writes lands under src/main/resources/assets/rangedweaponsmod/
and is committed; this script is the source of truth for those files, and the
photo booth run (`./gradlew photoBooth`) is how the result is looked at.
The textures and models are original work -- nothing here is derived from
another mod's assets. The sounds are cut from field recordings of real
firearms dedicated to the public domain (CC0) by their recordists, listed in
sounds/SOURCES.md. Sounds need ffmpeg with libvorbis on the PATH.
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


def slug_icon():
    """A shotgun slug, 16x16, upright: a black hull with a rounded lead nose standing proud of it, over a brass head."""
    hull, hull_l, hull_d = (44, 44, 48), (78, 78, 84), (24, 24, 28)
    lead, lead_l, lead_d = (132, 132, 140), (176, 176, 184), (92, 92, 100)
    brass, brass_l, brass_d = (176, 141, 60), (214, 180, 96), (128, 98, 38)
    px = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[y][x] = (*c, 255)

    for y in range(5, 11):                       # the hull
        for x in range(5, 11):
            put(x, y, hull_l if x == 5 else hull_d if x == 10 else hull)
    for y in range(1, 5):                        # the slug's nose, rounded
        left, right = (6, 10) if y >= 3 else (7, 9)
        for x in range(left, right):
            put(x, y, lead_l if x == left else lead_d if x == right - 1 else lead)
    put(7, 0, lead_d), put(8, 0, lead_d)
    for y in range(11, 15):                      # brass head
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


# ----------------------------------------------------------- the part icons

def _canvas():
    """A transparent 16x16 and a bounded plotter for it."""
    px = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[y][x] = (*c, 255)

    return px, put


def _tube(put, x0, y0, x1, y1, thick, body, light, dark):
    """A straight tube from (x0, y0) to (x1, y1), `thick` pixels across:
    every pixel within half the thickness of the centre line, lit on the
    side facing up-left and shaded on the side facing down-right."""
    dx, dy = x1 - x0, y1 - y0
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    nx, ny = -uy, ux                          # a normal, pointing up-left for a rising line
    half = thick / 2.0
    for y in range(16):
        for x in range(16):
            px_, py_ = x + 0.5 - x0, y + 0.5 - y0
            along = px_ * ux + py_ * uy
            across = px_ * nx + py_ * ny
            if -0.5 <= along <= length + 0.5 and abs(across) <= half:
                c = light if across < -half + 1.0 else dark if across > half - 1.0 else body
                put(x, y, c)


def steel_ingot_icon():
    """An ingot in steel: cooler and darker than iron, lit along the top face."""
    light, mid, dark = (150, 158, 170), (104, 112, 124), (58, 64, 74)
    px, put = _canvas()
    for y in range(5, 12):
        for x in range(2, 14):
            slant = (11 - y) // 2
            xx = x + slant
            put(xx, y, light if y == 5 else dark if y == 11 or xx >= 13 + slant - 1 else mid)
    for x in range(3, 15):
        put(x, 5, light)
    return px


def lower_receiver_icon():
    """A lower receiver side-on: the frame, a grip sloping down at the back,
    a trigger guard below and a brass trigger inside it."""
    m, ml, md = PALETTE["metal_mid"], PALETTE["metal_light"], PALETTE["metal_dark"]
    brass = PALETTE["brass"]
    px, put = _canvas()
    for y in range(4, 8):
        for x in range(1, 15):
            put(x, y, ml if y == 4 else md if y == 7 else m)
    for y in range(8, 14):                      # the grip, raked back
        for x in range(2 + (y - 8) // 2, 6 + (y - 8) // 2):
            put(x, y, md if x == 2 + (y - 8) // 2 else m)
    for x in range(8, 13):                      # trigger guard
        put(x, 11, md)
    for y in range(8, 11):
        put(8, y, md)
        put(12, y, md)
    put(10, 8, brass)
    put(10, 9, brass)
    put(11, 10, brass)
    return px


def upper_receiver_icon():
    """An upper receiver: a box of steel with an ejection port and two rivets."""
    m, ml, md = PALETTE["metal_mid"], PALETTE["metal_light"], PALETTE["metal_dark"]
    px, put = _canvas()
    for y in range(4, 12):
        for x in range(1, 15):
            put(x, y, ml if y == 4 or x == 1 else md if y == 11 or x == 14 else m)
    for y in range(6, 9):                       # ejection port
        for x in range(8, 13):
            put(x, y, md if y == 6 else PALETTE["barrel"])
    put(3, 6, md), put(3, 9, md), put(6, 6, md), put(6, 9, md)
    return px


def barrel_icon():
    """A barrel: a tube from lower left to upper right, a bright ring at the muzzle."""
    px, put = _canvas()
    _tube(put, 2, 13, 13, 2, 3.4, PALETTE["barrel"], PALETTE["metal_mid"], (14, 15, 17))
    _tube(put, 12, 3, 13, 2, 3.4, PALETTE["metal_light"], PALETTE["metal_light"], PALETTE["metal_mid"])
    return px


def heavy_barrel_icon():
    """A heavy barrel: the same tube, thicker, with a row of cooling holes along its length."""
    px, put = _canvas()
    _tube(put, 1, 14, 14, 1, 5.4, PALETTE["barrel"], PALETTE["metal_mid"], (14, 15, 17))
    for i in range(3, 12, 2):                   # holes down the centre line
        put(1 + i, 14 - i, PALETTE["metal_dark"])
    _tube(put, 13, 2, 14, 1, 5.4, PALETTE["metal_light"], PALETTE["metal_light"], PALETTE["metal_mid"])
    return px


def stock_icon():
    """A stock side-on: a broad wooden butt at the left, tapering to a wrist at the right."""
    w, wd = PALETTE["wood"], PALETTE["wood_dark"]
    wl = shade(w, 28)
    px, put = _canvas()
    for y in range(3, 14):
        left = 1
        right = 7 + max(0, 9 - abs(y - 8) * 2) // 2
        if 6 <= y <= 9:
            right = 15
        for x in range(left, right):
            put(x, y, wl if y == 3 or x == 1 else wd if y == 13 or x == right - 1 else w)
    for y in (4, 8, 11):                        # grain
        for x in range(3, 6):
            put(x, y, wd)
    return px


def pump_icon():
    """A shotgun pump: a wooden cylinder with grip grooves, side-on."""
    w, wd = PALETTE["wood"], PALETTE["wood_dark"]
    wl = shade(w, 28)
    px, put = _canvas()
    for y in range(5, 11):
        for x in range(1, 15):
            put(x, y, wl if y == 5 else wd if y == 10 else w)
    for x in range(3, 14, 2):                   # grooves
        for y in range(6, 10):
            put(x, y, wd)
    return px


def scope_icon():
    """A scope: a tube with a pale lens at each end and two mounts beneath."""
    m, ml, md = PALETTE["metal_mid"], PALETTE["metal_light"], PALETTE["metal_dark"]
    lens, lens_d = (168, 214, 232), (96, 150, 176)
    px, put = _canvas()
    for y in range(5, 10):
        for x in range(1, 15):
            put(x, y, ml if y == 5 else md if y == 9 else m)
    for y in range(4, 11):                      # the bells at each end
        for x in (1, 2, 13, 14):
            put(x, y, md if y in (4, 10) else m)
    for y in range(5, 10):
        put(0, y, lens_d if y in (5, 9) else lens)
        put(15, y, lens_d if y in (5, 9) else lens)
    for x in (4, 5, 10, 11):                    # mounts
        put(x, 10, md)
        put(x, 11, md)
    return px


PART_ICONS = {
    "steel_ingot": steel_ingot_icon,
    "lower_receiver": lower_receiver_icon,
    "upper_receiver": upper_receiver_icon,
    "barrel": barrel_icon,
    "heavy_barrel": heavy_barrel_icon,
    "stock": stock_icon,
    "pump": pump_icon,
    "scope": scope_icon,
}


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
# one-handed (the plain item pose) it is [0, 90, 78]: forward, top up, and
# the barrel levelled against the hanging arm's tilt. Translation and scale
# are per gun.
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
# The pistol's third-person translation lifts the model nine units so the
# fist closes on the grip rather than the slide: the model's centre, which
# is what sits in the hand, is the middle of the slide. Read from six
# translations photographed on the pistol itself.
# Third person: the arm is held out level (client/ArmPoses), so the hand's
# frame is the raised arm's -- x sideways, y up, -z down the arm toward the
# muzzle. [90, 90, -90] lays the barrel along the arm and upright (the one of
# six candidates that pointed at the camera, booth items a..f); 2 up puts the
# slide above the fist with the grip in it, 1 down the arm keeps the muzzle
# clear of the wrist. Calibrated by photograph, never derived.
PISTOL_DISPLAY = display([0.5, 2.5, -1.2], 0.5, [90, 90, -90], [0, 2, -1], 0.5, 0.7)


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
# pose, -PboothPose=one), [0, 90, 78]: the Y of 90 puts +X forward with +Y
# up -- [0, 0, 90] also put +X forward but rolled the model onto its side,
# read from the green tip -- and the Z, applied first and so a turn about
# the model's own lateral axis, levels a barrel the hanging arm tilts up.
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
#
# Every sound is cut from a field recording of a real firearm -- see
# sounds/SOURCES.md for what was recorded, by whom, and the CC0 dedication
# that lets it be shipped and committed. Nothing is synthesized: three rounds
# of synthesis (sines, then a blast-wave model, then modal impacts) each
# measured "realistic" and each was heard as arcade, because a recording
# carries a thousand details no model of it does.
#
# What this stage does is editing, not sound design: cut a take out of a
# recording, move it in time, fade its edges, mix a few takes, normalize. The
# guns' cycle times force the re-timing -- a pump-action shotgun can fire
# again 0.65 s after a shot, a bolt-action rifle 0.5 s, so the pump and the
# bolt recorded a second and more after the shot are moved up under its tail,
# where a fast shooter's hands put them.

RATE = 44100
SOURCES = Path(__file__).resolve().parent / "sounds" / "src"


def decode(stem):
    """Reads sounds/src/<stem>.ogg through ffmpeg as mono float samples at RATE."""
    path = SOURCES / f"{stem}.ogg"
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", str(path), "-f", "f32le", "-ac", "1",
                          "-ar", str(RATE), "pipe:1"], capture_output=True, check=True).stdout
    return list(struct.unpack(f"<{len(raw) // 4}f", raw))


def take(stem, start, end=None, at=0.0, gain=1.0, fade_in=0.003, fade_out=0.03):
    """One cut of a recording: the samples of `stem` from `start` to `end`
    seconds (`None`: its end), faded in and out over the given seconds so a
    cut never clicks, to be placed `at` seconds into the result at `gain`."""
    return (stem, start, end, at, gain, fade_in, fade_out)


def assemble(takes, peak):
    """Mixes the takes into one clip and normalizes it to `peak`. The clip is
    as long as the latest take runs; nothing is added -- no reverb, no tone,
    no filtering -- so what is heard is the recordings and their timing."""
    cuts = []
    for stem, start, end, at, gain, fade_in, fade_out in takes:
        samples = decode(stem)
        i0 = int(start * RATE)
        i1 = len(samples) if end is None else min(len(samples), int(end * RATE))
        cut = samples[i0:i1]
        n_in, n_out = int(fade_in * RATE), int(fade_out * RATE)
        for i in range(min(n_in, len(cut))):
            cut[i] *= i / n_in
        for i in range(min(n_out, len(cut))):
            cut[len(cut) - 1 - i] *= i / n_out
        cuts.append((int(at * RATE), [c * gain for c in cut]))
    out = [0.0] * max(i0 + len(c) for i0, c in cuts)
    for i0, cut in cuts:
        for i, s in enumerate(cut):
            out[i0 + i] += s
    return normalize(out, peak)


def normalize(samples, peak=0.95):
    top = max(abs(x) for x in samples) or 1.0
    return [x * peak / top for x in samples]


def write_ogg(path: Path, samples) -> None:
    """Writes 16-bit mono PCM through ffmpeg into Ogg Vorbis. Bit-exact, so
    the same samples give the same bytes and an unchanged sound is an
    unchanged file in the diff."""
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(RATE), "-ac", "1",
                    "-i", "pipe:0", "-c:a", "libvorbis", "-q:a", "5", "-fflags", "+bitexact", "-flags", "+bitexact", str(path)],
                   input=pcm, check=True)


# The shipped sounds: which recording, which seconds of it, placed where.
# Times are in seconds. A shot's take starts about 20 ms before the report so
# the attack is the recording's own; a tail is cut where it has fallen 40 dB
# and faded over its last stretch. In-game volumes: shots 1.0, reloads 0.8,
# the click 0.6 -- the peaks here keep the old balance between them.
RECORDINGS = {
    # A 9mm pistol on a range: the report and its outdoor tail, whole.
    "pistol_shot": (0.95, [take("427592-9mm-pistol-shot", 0.12)]),
    # An AR-15, the same range, the same day.
    "rifle_shot": (0.95, [take("427596-ar15-rifle-shot", 0.06)]),
    # A .303 Lee-Enfield: a short, hard report, already close to a machine
    # gun's per-round length, faded a touch sooner so seven a second stack
    # rather than smear.
    "machine_gun_shot": (0.95, [take("450852-lee-enfield-303-shot", 0.21, fade_out=0.08)]),
    # A 20-gauge on the same range as the pistol and the AR-15, whole, with
    # a Mossberg 500's pump under its tail from another recording: the two
    # strokes (back at 1.30 s, forward at 1.72 s in that take, where a
    # limiter had squeezed the blast down to their level) at 0.29 s and
    # 0.53 s, inside the 0.65 s the gun takes to fire again, and at a
    # quarter gain, which puts a pump about ten decibels under a report.
    "shotgun_shot": (0.95, [
        take("427595-20-gauge-shotgun-shot", 0.05),
        take("159710-mossberg-500a-shot-and-pump", 1.27, 1.68, at=0.26, gain=0.25, fade_in=0.008),
        take("159710-mossberg-500a-shot-and-pump", 1.69, 2.5, at=0.50, gain=0.25, fade_in=0.008, fade_out=0.2),
    ]),
    # A 1903 Springfield in .30-06 -- a bolt-action's crack, sharper than the
    # AR-15's report -- with a Mauser 98k's bolt worked over its tail: lifted
    # and drawn at 0.22 s, driven home at 0.50 s (the gun fires again at
    # 0.5 s). The bolt recording is hot and noisy next to the rifle's, so it
    # sits at a quarter gain under the tail, a bolt's distance under a crack.
    "scoped_rifle_shot": (0.95, [
        take("169261-springfield-1903-30-06-shot", 0.58, 2.0, fade_out=0.3),
        take("802673-mauser-98k-bolt", 0.20, 0.80, at=0.22, gain=0.25, fade_in=0.01, fade_out=0.06),
        take("802673-mauser-98k-bolt", 1.20, 1.70, at=0.50, gain=0.25, fade_in=0.01, fade_out=0.08),
    ]),
    # An M110 fired at a distance with the land answering: the report and its
    # echo, as heard a long way off.
    "far_shot": (0.85, [take("815476-m110-shot-echo", 0.25, fade_out=0.15)]),
    # A rifle dry-fired: the striker on an empty chamber.
    "empty_click": (0.7, [take("725402-rifle-dry-fire", 0.02)]),
    # A 1911's magazine dropping out, in a dead room.
    "reload_start": (0.8, [take("104407-1911-magazine-out", 0.19)]),
    # The 9mm's magazine seated (0.56 s in the recording) and the slide run
    # (1.55 s and 1.70 s), the wait between them shortened so the slide
    # follows the seat by 0.45 s and the whole fits the reload's end.
    "reload_end": (0.8, [
        take("427593-9mm-pistol-load-and-chamber", 0.50, 1.35, fade_out=0.1),
        take("427593-9mm-pistol-load-and-chamber", 1.50, at=0.45, fade_in=0.005),
    ]),
}

SOUNDS = {name: (lambda spec=spec: assemble(spec[1], spec[0])) for name, spec in RECORDINGS.items()}


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
        write_png(ASSETS / "textures/item/slug.png", 16, 16, slug_icon())
        write_png(ASSETS / "textures/gui/scope.png", 256, 256, scope_mask())
        for name, fn in PART_ICONS.items():
            write_png(ASSETS / f"textures/item/{name}.png", 16, 16, fn())
        stale = ASSETS / "textures/item/machine_gun.png"
        if stale.exists():
            stale.unlink()
        print(f"textures: gun_atlas.png (32x32), round.png, small_round.png, shell.png, {', '.join(n + '.png' for n in PART_ICONS)} (16x16), gui/scope.png (256x256)")
    if "model" in want:
        (ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
        for name, build in GUN_MODELS.items():
            model = build()
            (ASSETS / f"models/item/{name}.json").write_text(json.dumps(model, indent=2) + "\n")
            print(f"model: {name}.json ({len(model['elements'])} elements)")
        for name in ("round", "small_round", "shell", "slug", *PART_ICONS):
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
