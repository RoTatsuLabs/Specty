#!/usr/bin/env python3
"""Deterministic synthetic base signals for the Hi-Res corpus. Writes stereo float32 WAV."""
import argparse
import numpy as np
from scipy.io import wavfile

LSB16_VAR = (2.0 ** -15) ** 2 / 12.0
DURATION = 30.0


def body(f, tilt, edge, width=1000.0):
    a = np.zeros_like(f)
    hi = f > 1000.0
    a[hi] = -tilt * np.log2(f[hi] / 1000.0)
    lo = f < 40.0
    a[lo] = -12.0 * np.log2(40.0 / np.maximum(f[lo], 1.0))
    t = np.clip((f - (edge - width)) / width, 0.0, 1.0)
    return a - 200.0 * (0.5 - 0.5 * np.cos(np.pi * t))


def shaped(n, sr, rng, atten):
    spec = np.fft.rfft(rng.standard_normal(n))
    spec *= 10.0 ** (atten(np.fft.rfftfreq(n, 1.0 / sr)) / 20.0)
    return np.fft.irfft(spec, n)


def envelope(n, sr, rng):
    """Level moves within 18 dB; the first and last 3 s are room tone, so the noise floor can be read."""
    step = 0.25
    m = int(n / sr / step) + 2
    v = np.convolve(rng.standard_normal(m), np.ones(8) / 8.0, mode="same")
    lo, hi = np.quantile(v, 0.10), np.quantile(v, 0.90)
    music = -18.0 + 18.0 * np.clip((v - lo) / (hi - lo), 0.0, 1.0)
    t = np.arange(n) / sr
    gate = np.interp(t, [0.0, 2.7, 3.0, DURATION - 3.0, DURATION - 2.7, DURATION], [-75.0, -75.0, 0.0, 0.0, -75.0, -75.0])
    return 10.0 ** ((np.interp(t, np.arange(m) * step, music) + gate) / 20.0)


def make(kind, sr, floor_db, floor_rate, seed):
    rng = np.random.default_rng(seed)
    n = int(sr * DURATION)
    env = envelope(n, sr, rng)
    out = []
    for _ in range(2):
        if kind == "bright":
            x = shaped(n, sr, rng, lambda f: body(f, 10.0, 60_000.0)) * env
        elif kind == "dark":
            x = shaped(n, sr, rng, lambda f: body(f, 16.0, 60_000.0)) * env
        elif kind == "sporadic":
            x = shaped(n, sr, rng, lambda f: body(f, 16.0, 20_000.0)) * env
            hit = np.zeros(n)
            for start in np.arange(3.0, DURATION - 1.0, 6.0):
                a, b = int(start * sr), int((start + 0.25) * sr)
                hit[a:b] = np.hanning(b - a)
            burst = shaped(n, sr, rng, lambda f: body(f, 10.0, 60_000.0) - 200.0 * np.clip((24_000.0 - f) / 2000.0, 0.0, 1.0))
            x = x + burst * hit
        elif kind == "dsd":
            x = shaped(n, sr, rng, lambda f: body(f, 16.0, 20_000.0)) * env
            rise = shaped(n, sr, rng, lambda f: np.where((f > 25_000.0) & (f < 47_500.0), -110.0 + 44.0 * np.log2(np.maximum(f, 1.0) / 25_000.0), -200.0))
            x = x + rise
        else:
            raise SystemExit(f"unknown kind {kind}")
        out.append(x)
    x = np.stack(out, axis=1)
    x *= 10.0 ** (-18.0 / 20.0) / np.sqrt(np.mean(x[:, 0] ** 2))
    sigma = np.sqrt(LSB16_VAR * 10.0 ** (floor_db / 10.0) * (sr / floor_rate))
    x += rng.standard_normal(x.shape) * sigma
    return np.clip(x, -1.0, 1.0).astype(np.float32)


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("out")
    p.add_argument("--kind", required=True)
    p.add_argument("--rate", type=int, default=192_000)
    p.add_argument("--floor-db", type=float, default=-20.0, help="analog floor, dB above 16-bit quantisation noise")
    p.add_argument("--floor-rate", type=int, default=44_100, help="rate the floor level is stated at")
    p.add_argument("--seed", type=int, default=1)
    a = p.parse_args()
    wavfile.write(a.out, a.rate, make(a.kind, a.rate, a.floor_db, a.floor_rate, a.seed))
