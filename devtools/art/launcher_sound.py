"""Launcher recordings and periodic electronic seeker tones (D-0028).

Copyright 2026 Rusty Shackleford and nfx. AGPL-3.0-or-later.
"""

import math
import struct
import subprocess
from pathlib import Path

from sound_export import write_ogg

RATE = 44100
ROOT = Path(__file__).resolve().parents[2]
SOURCES = Path(__file__).resolve().parent / "sounds/src"
ASSETS = ROOT / "src/main/resources/assets/rangedweaponsmod"
EVENTS = (
    "launcher_fire",
    "launcher_reload",
    "rocket_motor",
    "rocket_dud",
    "seeker_growl",
    "seeker_lock",
    "seeker_lost",
)


def _decode(name: str) -> list[float]:
    raw = subprocess.run(
        [
            "ffmpeg",
            "-v",
            "error",
            "-i",
            str(SOURCES / name),
            "-ar",
            str(RATE),
            "-ac",
            "1",
            "-f",
            "f32le",
            "pipe:1",
        ],
        capture_output=True,
        check=True,
    ).stdout
    return [s[0] for s in struct.iter_unpack("<f", raw)]


def _peak(samples: list[float], peak: float) -> list[float]:
    maximum = max(abs(s) for s in samples)
    if maximum == 0:
        raise ValueError("Silent source")
    return [s * peak / maximum for s in samples]


def _fade(samples: list[float], fade_in: float, fade_out: float) -> list[float]:
    result = samples.copy()
    for i in range(min(len(result), round(fade_in * RATE))):
        result[i] *= 0.5 - 0.5 * math.cos(math.pi * i / max(1, round(fade_in * RATE)))
    for i in range(min(len(result), round(fade_out * RATE))):
        result[-1 - i] *= 0.5 - 0.5 * math.cos(
            math.pi * i / max(1, round(fade_out * RATE))
        )
    return result


def _cut(
    name: str,
    start: float,
    end: float,
    peak: float,
    fade_in: float = 0.003,
    fade_out: float = 0.05,
) -> list[float]:
    return _peak(
        _fade(
            _decode(name)[round(start * RATE) : round(end * RATE)], fade_in, fade_out
        ),
        peak,
    )


def _mix(
    length: float, parts: list[tuple[float, list[float]]], peak: float
) -> list[float]:
    out = [0.0] * round(length * RATE)
    for when, samples in parts:
        offset = round(when * RATE)
        if offset + len(samples) > len(out):
            raise ValueError("A take exceeds the mix duration")
        for i, sample in enumerate(samples):
            out[offset + i] += sample
    return _peak(out, peak)


def _motor() -> list[float]:
    source = _decode("453408-blowtorch.mp3")[18 * RATE : 21 * RATE]
    mean = sum(source) / len(source)
    source = [s - mean for s in source]
    overlap = RATE // 4
    # Rotate the cut, overlapping its original tail and head; both ends now meet
    # at adjacent samples of the original recording. Equal-power weights keep
    # uncorrelated exhaust noise from dropping in level at the seam.
    out = source[overlap:-overlap]
    for i in range(overlap):
        angle = math.pi / 2 * i / (overlap - 1)
        out.append(source[-overlap + i] * math.cos(angle) + source[i] * math.sin(angle))
    return _peak(out, 0.72)


def _growl() -> list[float]:
    # One exact second: every oscillator completes whole cycles, so the sample
    # repeats at any playback pitch without a splice or a fade-to-silence pulse.
    result: list[float] = []
    for i in range(RATE):
        t = i / RATE
        phase = math.tau * 220 * t + 1.1 * math.sin(math.tau * 55 * t)
        carrier = math.sin(phase) + 0.22 * math.sin(2 * phase)
        envelope = 0.72 + 0.28 * math.cos(math.tau * 8 * t)
        result.append(carrier * envelope)
    return _peak(result, 0.28)


def _lock() -> list[float]:
    return [
        0.20 * math.sin(math.tau * 660 * i / RATE)
        + 0.035 * math.sin(math.tau * 1320 * i / RATE)
        for i in range(RATE)
    ]


def _lost() -> list[float]:
    duration = 0.3
    samples = [
        0.24
        * math.sin(
            math.tau * (660 * (i / RATE) - 480 / (2 * duration) * (i / RATE) ** 2)
        )
        for i in range(round(duration * RATE))
    ]
    return _fade(samples, 0.008, 0.09)


def generate() -> None:
    """requires: ffmpeg/libvorbis and the credited source recordings are available.

    effects: writes seven mono Vorbis files, verifying decoded peak <= .98.
    throws: ValueError for invalid cuts, OSError/CalledProcessError on I/O failure,
    RuntimeError if encoded headroom cannot be met.
    """
    clips = {
        # Real launch, including its exhaust, starting 11 ms before the report.
        "launcher_fire": _cut(
            "182794-rocket-launch.mp3", 0.575, 2.475, 0.95, 0.002, 0.4
        ),
        # Seat the rocket, slide it home, then close the latch inside the 2.5 s reload.
        "launcher_reload": _mix(
            2.35,
            [
                (0.04, _cut("104407-1911-magazine-out.ogg", 0.19, 0.49, 0.35)),
                (0.38, _cut("14167-metal-slide.mp3", 0, 0.80, 0.65, 0.01, 0.09)),
                (
                    1.70,
                    _cut("802673-mauser-98k-bolt.ogg", 1.20, 1.70, 0.8, 0.005, 0.08),
                ),
            ],
            0.83,
        ),
        "rocket_motor": _motor(),
        # A dry metallic impact and a dying gas puff, with no explosive report.
        "rocket_dud": _mix(
            0.78,
            [
                (
                    0,
                    _cut("104407-1911-magazine-out.ogg", 0.19, 0.49, 0.60, 0.002, 0.08),
                ),
                (0.05, _cut("453408-blowtorch.mp3", 43.8, 44.5, 0.22, 0.003, 0.2)),
            ],
            0.68,
        ),
        "seeker_growl": _growl(),
        "seeker_lock": _lock(),
        "seeker_lost": _lost(),
    }
    for name, samples in clips.items():
        write_ogg(ASSETS / f"sounds/{name}.ogg", samples, RATE)
        print(f"{name}: {len(samples) / RATE:.3f}s, mono, decoded peak checked")


if __name__ == "__main__":
    generate()
