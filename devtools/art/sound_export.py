"""Deterministic Vorbis export with headroom verified after lossy decoding.

Copyright 2026 Rusty Shackleford and nfx. AGPL-3.0-or-later.
"""

import math
import struct
import subprocess
from collections.abc import Sequence
from pathlib import Path


def write_ogg(path: Path, samples: Sequence[float], rate: int = 44100) -> None:
    """requires: nonempty finite mono samples in [-1, 1], positive sample rate.

    effects: writes Vorbis and verifies its decoded peak <= .98. Encoding can
    overshoot its PCM input; retry with gain reduced to leave .96 headroom.
    Passing encodes retain exactly their initial bytes. throws: ValueError on bad
    samples; OSError/CalledProcessError on tool failure; RuntimeError if five
    attempts cannot produce safe decoded samples. Does not compress or synthesize.
    """
    if (
        rate <= 0
        or not samples
        or any(not math.isfinite(s) or abs(s) > 1 for s in samples)
    ):
        raise ValueError(
            "Expected finite normalized mono PCM and a positive sample rate"
        )
    path.parent.mkdir(parents=True, exist_ok=True)
    gain = 1.0
    for _ in range(5):
        pcm = b"".join(struct.pack("<h", int(s * gain * 32767)) for s in samples)
        subprocess.run(
            [
                "ffmpeg",
                "-y",
                "-loglevel",
                "error",
                "-f",
                "s16le",
                "-ar",
                str(rate),
                "-ac",
                "1",
                "-i",
                "pipe:0",
                "-c:a",
                "libvorbis",
                "-q:a",
                "5",
                "-fflags",
                "+bitexact",
                "-flags",
                "+bitexact",
                str(path),
            ],
            input=pcm,
            check=True,
        )
        decoded = subprocess.run(
            ["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "pipe:1"],
            capture_output=True,
            check=True,
        ).stdout
        if not decoded:
            raise RuntimeError("Vorbis decoded to no samples")
        peak = max(abs(value[0]) for value in struct.iter_unpack("<f", decoded))
        if not math.isfinite(peak):
            raise RuntimeError("Vorbis decoded to a nonfinite sample")
        if peak <= 0.98:
            return
        gain *= 0.96 / peak
    raise RuntimeError(f"Vorbis headroom could not be achieved for {path}")
