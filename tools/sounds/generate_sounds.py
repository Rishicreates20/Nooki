#!/usr/bin/env python3
"""Generates Nooki's UI sounds (PRD section 4.2) into app/src/main/res/raw/.

  sound_pet_pop.ogg      80 ms, mono, soft "pop" with exponential decay (chip tap / snooze)
  sound_pet_sparkle.ogg  420 ms, stereo, ascending C6 -> E6 -> G6 chime (task done)

Requires numpy and ffmpeg with libvorbis. Run from the repo root:
  python3 tools/sounds/generate_sounds.py
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "raw")


def write_ogg(name, samples):
    """samples: float array, shape (n,) mono or (n, 2) stereo, range -1..1."""
    samples = np.clip(samples, -1.0, 1.0)
    pcm = (samples * 32767).astype("<i2")
    channels = 1 if pcm.ndim == 1 else pcm.shape[1]
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(channels)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    out = os.path.normpath(os.path.join(OUT, name))
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "5",
         "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", out],
        check=True,
    )
    os.unlink(wav_path)
    print("wrote", out, os.path.getsize(out), "bytes")


def pop():
    n = int(RATE * 0.080)
    t = np.arange(n) / RATE
    # Pitch drops quickly from ~900 Hz to ~380 Hz, like a bubble popping.
    freq = 380 + 520 * np.exp(-t / 0.012)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    env = np.exp(-t / 0.018)
    attack = np.minimum(1.0, t / 0.002)
    return 0.7 * np.sin(phase) * env * attack


def sparkle():
    n = int(RATE * 0.420)
    t = np.arange(n) / RATE
    left = np.zeros(n)
    right = np.zeros(n)
    notes = [(1046.50, 0.000, -0.4), (1318.51, 0.090, 0.0), (1567.98, 0.180, 0.4)]  # C6, E6, G6
    for freq, start, pan in notes:
        local = t - start
        on = local >= 0
        lt = np.where(on, local, 0)
        env = np.where(on, np.minimum(1.0, lt / 0.004) * np.exp(-lt / 0.11), 0)
        # A touch of the octave makes it bell-like.
        tone = (np.sin(2 * np.pi * freq * lt) + 0.25 * np.sin(2 * np.pi * 2 * freq * lt)) * env
        left += tone * (1 - pan) / 2
        right += tone * (1 + pan) / 2
    fade = np.minimum(1.0, (t[-1] - t) / 0.03)
    stereo = np.stack([left * fade, right * fade], axis=1)
    return 0.8 * stereo / np.max(np.abs(stereo))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    write_ogg("sound_pet_pop.ogg", pop())
    write_ogg("sound_pet_sparkle.ogg", sparkle())
