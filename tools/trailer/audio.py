#!/usr/bin/env python3
"""Chronicle-Trailer: Musik + Sounds komplett prozedural (numpy), synchron zu render.py.

  python3 tools/trailer/audio.py audio.wav
  ffmpeg -i video.mp4 -i audio.wav -c:v copy -c:a aac -b:a 256k -shortest out.mp4
"""
import sys, wave
import numpy as np

SR, DUR = 44100, 20.0
N = int(SR * DUR)
rng = np.random.default_rng(11)
TAU = 2 * np.pi

def mf(m): return 440.0 * 2 ** ((m - 69) / 12)
def tt(d): return np.arange(int(SR * d)) / SR

dry = np.zeros((N, 2), np.float32)
wet = np.zeros((N, 2), np.float32)

def add(bus, x, t0, pan=0.0, gain=1.0):
    i = int(t0 * SR)
    if i >= N or i + len(x) <= 0: return
    if i < 0: x = x[-i:]; i = 0
    x = x[:N - i]
    a = (pan + 1) * np.pi / 4
    bus[i:i + len(x), 0] += x * np.cos(a) * gain
    bus[i:i + len(x), 1] += x * np.sin(a) * gain

def env(n, att, rel, curve=1.0):
    e = np.ones(n)
    a, r = int(att * SR), int(rel * SR)
    if a > 0: e[:a] = np.linspace(0, 1, min(a, n)) ** curve
    if r > 0 and r < n: e[-r:] *= np.linspace(1, 0, r) ** curve
    return e

def lp(x, a):
    """Ein-Pol-Tiefpass, a skalar oder Array (0..1)."""
    y = np.empty_like(x); s = 0.0
    if np.isscalar(a): a = np.full(len(x), a)
    for i in range(len(x)):
        s += a[i] * (x[i] - s); y[i] = s
    return y

def hp(x, a): return x - lp(x, a)

# ---------- Akkorde ----------
CH = {
    'Am': (45, [57, 60, 64, 69]), 'F': (41, [53, 57, 60, 65]),
    'C': (36, [55, 60, 64, 67]), 'G': (43, [55, 59, 62, 67]),
    'Am9': (45, [57, 60, 64, 71]), 'Cadd9': (36, [55, 62, 64, 67]),
}
SEG = [(0, 4.0, 'Am'), (4.0, 6.6, 'F'), (6.6, 9.2, 'C'), (9.2, 11.8, 'G'),
       (11.8, 14.0, 'Am'), (14.0, 16.0, 'Am9'), (16.0, 18.0, 'F'), (18.0, 20.0, 'Cadd9')]

def brightness(t):
    if t < 4: return .35 + .2 * t / 4
    if t < 11.8: return .55 + .25 * (t - 4) / 7.8
    if t < 14: return .8
    return 1.0

def pad_voice(f, d, harm):
    t = tt(d)
    out = np.zeros(len(t))
    for det in (-.007, 0.0, .007):
        ff = f * (1 + det)
        ph = rng.uniform(0, TAU)
        for h in range(1, harm + 1):
            if ff * h > 7000: break
            out += np.sin(TAU * ff * h * t + ph * h) / h ** 1.25
    return out / 3

# Pads + Bass
for t0, t1, name in SEG:
    bass, notes = CH[name]
    ext = 1.2
    d = (t1 - t0) + ext
    harm = int(5 + 12 * brightness(t0))
    lvl = {0: .5, 4: .6, 6.6: .65, 9.2: .7, 11.8: .75, 14: .9, 16: .85, 18: .8}.get(t0, .7)
    for k, m in enumerate(notes):
        v = pad_voice(mf(m), d, harm)
        e = env(len(v), 0.9 if t0 < 14 else .25, 1.1, 1.5)
        add(wet, v * e * .11, t0, pan=(-.5, -.15, .15, .5)[k], gain=lvl)
        if t0 >= 14:
            v2 = pad_voice(mf(m + 12), d, harm)
            add(wet, v2 * e * .05, t0, pan=(.5, .15, -.15, -.5)[k], gain=lvl)
    t = tt(d)
    b = np.sin(TAU * mf(bass) * t) + .35 * np.sin(TAU * mf(bass) * 2 * t)
    b *= env(len(t), .06, .5, 1.2)
    add(dry, b * .33, t0, 0, .45 + .55 * min(1, t0 / 4))

# ---------- Pluck / Arpeggio ----------
def pluck(f, d=.55):
    t = tt(d)
    x = np.sin(TAU * f * t) + .45 * np.sin(TAU * 2 * f * t) * np.exp(-t * 9) + .2 * np.sin(TAU * 3 * f * t) * np.exp(-t * 14)
    return x * np.exp(-t * 7.5) * env(len(t), .003, .02)

def chord_at(t):
    for t0, t1, name in SEG:
        if t0 <= t < t1: return CH[name][1]
    return CH['Cadd9'][1]

PAT = [0, 1, 2, 3, 2, 1, 2, 3, 0, 2, 1, 3, 2, 1, 3, 2]
def arp(ta, tb, step, gain0, gain1):
    n = int((tb - ta) / step)
    for i in range(n):
        t = ta + i * step
        notes = chord_at(t)
        m = notes[PAT[i % len(PAT)]] + 12
        if i % 8 == 7: m += 12
        g = lerp(gain0, gain1, i / max(1, n))
        p = np.sin(i * 1.7) * .7
        add(wet, pluck(mf(m)), t, p, g * .22)
        add(dry, pluck(mf(m)), t, p, g * .08)
def lerp(a, b, t): return a + (b - a) * t

arp(2.5, 6.6, .25, .6, .9)
arp(6.6, 11.8, .125, .8, 1.15)
arp(11.8, 13.95, .125, 1.0, 1.3)
arp(14.0, 18.0, .125, 1.15, 1.2)
arp(18.0, 19.6, .25, .9, .5)

# ---------- Drums ----------
def kick(g=1.0):
    t = tt(.5)
    f = 42 + 90 * np.exp(-t * 28)
    ph = np.cumsum(f) / SR * TAU
    x = np.sin(ph) * np.exp(-t * 9)
    c = (rng.standard_normal(len(t))) * np.exp(-t * 150) * .15
    return (x + c) * g

def hat(g=1.0, d=.06):
    t = tt(d)
    return hp(rng.standard_normal(len(t)), .45) * np.exp(-t * 70) * g

K = kick()
for st, en, g in ((4.0, 11.8, .5), (14.0, 18.0, .65)):
    t = st
    while t < en - .2:
        gg = g * (.7 + .3 * min(1, (t - st) / 4)) if st < 14 else g
        add(dry, K, t, 0, gg * .75)
        t += .5
H = hat()
for st, en, g in ((6.6, 11.8, .35), (14.0, 18.0, .4)):
    t = st + .25
    while t < en - .1:
        add(dry, H, t, .35 * np.sin(t * 3), g * .5)
        t += .5
        if st >= 14 and int(t * 8) % 2 == 0: add(dry, H, t - .125, -.3, g * .22)

# ---------- Whooshes / Uebergaenge ----------
def whoosh(d, a0, a1, g=1.0, up=True):
    n = int(SR * d)
    x = rng.standard_normal(n)
    p = np.linspace(0, 1, n)
    a = lerp(a0, a1, p ** 1.6) if up else lerp(a1, a0, p ** 1.6)
    y = lp(x, a)
    y = hp(y, .004)
    e = np.sin(np.pi * p) ** 1.5 if True else 1
    return y * e * g

for tw in (4.0, 6.6, 9.2):
    w = whoosh(1.1, .01, .22, 1.1)
    add(wet, w, tw - .45, -.4, .5)
    add(wet, w[::-1].copy(), tw - .2, .4, .25)

# Intro: Buchstaben-Pings + Glitzern
PENT = [69, 72, 74, 76, 79, 81, 84, 79, 88]
for i in range(9):
    t = 1.0 + i * .09
    add(wet, pluck(mf(PENT[i] + 12), .6) * 1.3, t, np.sin(i) * .6, .26)
for i in range(14):
    t = 2.5 + i * .09 + rng.uniform(0, .04)
    m = [93, 96, 98, 100, 105][int(rng.integers(0, 5))]
    add(wet, pluck(mf(m), .5), t, rng.uniform(-.8, .8), .09 * (1 - i / 18))
# Ring-Aufbau 0.15-1.9 s: leises Anschwellen
sw = whoosh(1.8, .003, .08, .6)
add(wet, sw, .1, 0, .4)

# ---------- Riser + Impact ----------
d = 2.15
n = int(SR * d); p = np.linspace(0, 1, n)
nz = lp(rng.standard_normal(n), lerp(.02, .75, p ** 2.2)) * (p ** 2.3)
sweep_f = 180 * (11 ** p)
sw_sine = np.sin(np.cumsum(sweep_f) / SR * TAU) * (p ** 2.6)
sw_sine2 = np.sin(np.cumsum(sweep_f * 1.5) / SR * TAU) * (p ** 3) * .5
add(wet, nz * .9, 11.88, 0, .55)
add(wet, (sw_sine + sw_sine2) * .5, 11.88, 0, .45)
# Herzschlag-Toms im Build (Beschleunigung)
tm = 11.9
step = .5
while tm < 13.9:
    add(dry, kick(1.0), tm, 0, .55 * (.6 + .4 * (tm - 11.9) / 2.0))
    step = max(.12, step * .86)
    tm += step

# Einschlag bei 14.0
t = tt(2.4)
boom = np.sin(np.cumsum(30 + 70 * np.exp(-t * 6)) / SR * TAU) * np.exp(-t * 2.2)
crash = hp(lp(rng.standard_normal(len(t)), .5), .01) * np.exp(-t * 2.0)
add(dry, boom, 14.0, 0, 1.0)
add(wet, crash, 14.0, 0, .75)
add(dry, K, 14.0, 0, 1.0)
# Heller Akkord-Stab
for m in (57, 64, 69, 72, 76, 81):
    v = pad_voice(mf(m), 2.2, 14) * np.exp(-tt(2.2) * 1.3) * env(len(tt(2.2)), .003, .3)
    add(wet, v * .12, 14.0, rng.uniform(-.6, .6), 1.0)
# Schockwellen-Glitzern
for i in range(22):
    m = [81, 84, 88, 91, 93, 96][int(rng.integers(0, 6))]
    add(wet, pluck(mf(m + 12), .7), 14.2 + i * .1, rng.uniform(-.9, .9), .1 * (1 - i / 26))

# ---------- Zaehler (15.7 - 18) ----------
def blip(f):
    t = tt(.05)
    return np.sin(TAU * f * t) * np.exp(-t * 60) * env(len(t), .001, .01)
for k in range(3):
    ts = 15.7 + k * .22
    for j in range(24):
        tb = ts + j * (1.3 / 24) * (.9 + .1 * j / 24)
        f = 900 * (1 + j / 24 * (1.3 + k * .2))
        add(wet, blip(f), tb, (k - 1) * .6, .09)
    add(wet, pluck(mf(93 + k * 4), .9), ts + 1.3, (k - 1) * .6, .16)
    add(dry, K, ts + 1.3, 0, .0)

# ---------- Finale ----------
for i in range(9):
    t = 18.4 + i * .045
    add(wet, pluck(mf(PENT[i] + 12), .9) * 1.2, t, np.sin(i) * .6, .24)
t = tt(2.0)
shim = sum(np.sin(TAU * mf(m) * t) for m in (84, 88, 91, 96)) * np.exp(-t * 1.1) * env(len(t), .02, .8)
add(wet, shim * .06, 18.9, 0, 1.0)

# ---------- Reverb ----------
def reverb(x):
    L = int(SR * 3.2)
    t = np.arange(L) / SR
    ir = np.empty((L, 2))
    for c in range(2):
        r = rng.standard_normal(L)
        r = lp(r, .22)   # dunkler Hall
        ir[:, c] = r * np.exp(-t / 1.1) * (1 - np.exp(-t * 60))
    ir /= np.sqrt((ir ** 2).sum(0))
    M = 1 << int(np.ceil(np.log2(N + L)))
    out = np.zeros((N, 2), np.float32)
    for c in range(2):
        X = np.fft.rfft(x[:, c], M) * np.fft.rfft(ir[:, c], M)
        out[:, c] = np.fft.irfft(X, M)[:N]
    return out

rv = reverb(wet)
mix = dry * .9 + wet * .45 + rv * 1.0

# ---------- Master ----------
tm_ = np.arange(N) / SR
fade = np.clip(tm_ / .4, 0, 1) * np.clip((DUR - tm_) / 0.9, 0, 1) ** 1.5
mix *= fade[:, None]
mix = mix - mix.mean(0)
mix /= np.abs(mix).max()
mix = np.tanh(mix * 1.5) / np.tanh(1.5)   # sanfte Sättigung
mix *= .89

def main():
    out = sys.argv[1] if len(sys.argv) > 1 else 'chronicle-audio.wav'
    pcm = (mix * 32767).astype('<i2')
    with wave.open(out, 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print('fertig', out, 'Peak', float(np.abs(mix).max()))

if __name__ == '__main__':
    main()
